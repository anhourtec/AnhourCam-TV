package com.anhourtec.camgrid.client.media

import com.anhourtec.camgrid.client.gateway.DeviceLink
import com.anhourtec.camgrid.client.gateway.GatewaySession
import com.anhourtec.camgrid.client.gateway.SocketEvents
import com.anhourtec.camgrid.client.page.ServiceAddress
import com.anhourtec.camgrid.client.protocol.Cmd
import com.anhourtec.camgrid.client.protocol.Messages
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import java.io.Closeable

/**
 * One media socket (`ws_stream_url`) carrying one channel's stream. Order of use:
 * [open] → `GatewaySession.playVideo` with the returned [RelayAck] → [startStream].
 */
class MediaSession(
    private val http: OkHttpClient,
    private val service: ServiceAddress,
    private val deviceId: String,
    private val scope: CoroutineScope,
    private val sink: FrameSink,
    private val clock: () -> Long = System::currentTimeMillis,
) : Closeable {
    private val ack = CompletableDeferred<RelayAck>()
    private val header = FrameHeader()
    private val events =
        object : SocketEvents() {
            override fun onMessage(
                webSocket: WebSocket,
                bytes: ByteString,
            ) {
                val buf = bytes.asByteBuffer()
                if (!ack.isCompleted) {
                    RelayAck.parse(buf)?.let { ack.complete(it) }
                    return
                }
                if (FrameDepacketizer.parse(buf, header)) sink.onFrame(buf, header)
            }
        }
    private var socket: WebSocket? = null
    private var heartbeat: Job? = null

    /** Completes when the socket closes (null) or fails. */
    val closed get() = events.closed

    /** Connects and opens a relay session. */
    suspend fun open(): RelayAck {
        val ws = http.newWebSocket(Request.Builder().url(service.streamUrl).build(), events)
        socket = ws
        GatewaySession.awaitOrFail("media connection") { events.opened.await() }
        ws.send(Messages.relayAsk(deviceId, clock()))
        val relay = GatewaySession.awaitOrFail("relay ack") { ack.await() }
        heartbeat =
            scope.launch {
                while (isActive) {
                    delay(HEARTBEAT_MS)
                    socket?.send(Messages.heartbeat(clock()))
                }
            }
        return relay
    }

    /** Starts frame delivery after the gateway bound the relay (createSubSession, 8216). */
    fun startStream(
        link: DeviceLink,
        channelNo: Int,
        streamId: Int,
    ) {
        val data = Messages.subSessionData(link.route.ownClientId, channelNo, streamId)
        socket?.send(Messages.deviceMessage(link.route, link.nextMsgId(), Cmd.CREATE_SUB_SESSION, data, clock(), withLiveTime = false))
    }

    override fun close() {
        heartbeat?.cancel()
        socket?.close(NORMAL_CLOSE, null)
    }

    private companion object {
        const val HEARTBEAT_MS = 200_000L
        const val NORMAL_CLOSE = 1000
    }
}
