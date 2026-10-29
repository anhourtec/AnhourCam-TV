package com.anhourtec.camgrid.client.gateway

import com.anhourtec.camgrid.client.media.RelayAck
import com.anhourtec.camgrid.client.page.DeviceDetail
import com.anhourtec.camgrid.client.page.ServiceAddress
import com.anhourtec.camgrid.client.protocol.Cmd
import com.anhourtec.camgrid.client.protocol.Incoming
import com.anhourtec.camgrid.client.protocol.Messages
import com.anhourtec.camgrid.client.protocol.ProtocolException
import com.anhourtec.camgrid.client.protocol.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import java.io.Closeable

/**
 * The signalling socket (`user_gate_url`): registers the account with the gateway, opens devices
 * and starts/stops channel streams. One instance per gateway; see docs/streaming.md for the flow.
 */
class GatewaySession(
    private val http: OkHttpClient,
    private val service: ServiceAddress,
    private val accountName: String,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : Closeable {
    private val incoming = MutableSharedFlow<Incoming>(extraBufferCapacity = INCOMING_BUFFER)
    private val events =
        object : SocketEvents() {
            override fun onMessage(
                webSocket: WebSocket,
                text: String,
            ) {
                Incoming.parse(text)?.let { incoming.tryEmit(it) }
            }
        }
    private var socket: WebSocket? = null
    private var heartbeat: Job? = null

    /** Our gateway client ID; the protocol calls it `userLoginId`. Valid after [connect]. */
    var ownClientId: Long = 0
        private set
    private var transPort: Int = 0

    /** Completes when the socket closes (null) or fails. */
    val closed get() = events.closed

    suspend fun connect() {
        socket = http.newWebSocket(Request.Builder().url(service.userGateUrl).build(), events)
        withTimeout(TIMEOUT_MS) { events.opened.await() }
        val res = request(Messages.gatewayLogin(accountName, clock())) { it.cmdId == Cmd.LOGIN_RES }
        if (res.code != 0) throw ProtocolException("gateway login failed: code ${res.code}")
        ownClientId = res.long(res.cmdData, "client_id")
        transPort = res.int(res.cmdData, "trans_port")
        heartbeat =
            scope.launch {
                while (isActive) {
                    delay(HEARTBEAT_MS)
                    socket?.send(Messages.heartbeat(clock()))
                }
            }
    }

    /** Looks up the device on the gateway and logs in to it with its own credentials. */
    suspend fun openDevice(device: DeviceDetail): DeviceLink {
        val info = request(Messages.getClientInfo(device.deviceId, clock())) { it.cmdId == Cmd.GET_CLIENT_INFO_RES }
        if (info.code != 0) throw ProtocolException("device ${device.deviceId} not found on gateway: code ${info.code}")
        val data = info.cmdData
        val deviceCookie = info.long(data, "cookie")
        val link =
            DeviceLink(
                Route(
                    ownClientId = ownClientId,
                    deviceClientId = info.long(data, "client_id"),
                    deviceId = device.deviceId,
                    deviceGatewayIp = info.string(data, "client_loginip"),
                    deviceGatewayPort = info.int(data, "client_loginport"),
                    deviceGatewayType = info.int(data, "gate_type"),
                ),
                device,
            )
        val res =
            deviceRequest(link, Cmd.CREATE_SESSION, Cmd.CREATE_SESSION_RES) {
                put("userLoginId", ownClientId)
                put("username", accountName)
                put("devSn", device.deviceId)
                put("devUserName", device.credentials.user)
                put("devPasswd", device.credentials.password)
                put("userGatewayIp", service.userGatewayIp)
                put("userGatewayPort", transPort)
                put("userGatewayType", Cmd.GATEWAY_TYPE_USER)
                put("userRole", Cmd.CLIENT_ROLE_USER)
                put("cookie", deviceCookie)
            }
        val result = res.int(res.msgData, "resultCode")
        if (result != 0) throw DeviceLoginException(device.deviceId, result)
        return link
    }

    /**
     * Asks the device to stream [channelNo] through the relay session from [ack]
     * (see [com.anhourtec.camgrid.client.media.MediaSession.open]). Returns when the relay is bound.
     */
    suspend fun playVideo(
        link: DeviceLink,
        channelNo: Int,
        streamId: Int,
        ack: RelayAck,
    ) {
        val res =
            deviceRequest(link, Cmd.PLAY_VIDEO, Cmd.CREATE_RELAY_SESSION_RES) {
                put("channelNum", 1)
                put("connType", Cmd.CONN_TYPE_RELAY)
                put("relayAddr", service.streamRelayIp)
                put("relayPort", service.streamPort)
                put("req_info", Messages.relayRequest(channelNo, streamId, ack.relaySessionId, ack.relaySockFd))
            }
        val result = res.int(res.msgData, "resultCode")
        if (result != 0) throw ProtocolException("playVideo channel $channelNo failed: resultCode $result")
    }

    suspend fun stopVideo(
        link: DeviceLink,
        channelNo: Int,
        streamId: Int,
        ack: RelayAck,
    ) {
        deviceRequest(link, Cmd.STOP_VIDEO, Cmd.STOP_VIDEO_RES) {
            put("channelNum", 1)
            put("req_info", Messages.relayRequest(channelNo, streamId, ack.relaySessionId, ack.relaySockFd))
        }
    }

    suspend fun stopAudio(
        link: DeviceLink,
        channelNo: Int,
    ) {
        deviceRequest(link, Cmd.STOP_AUDIO, Cmd.SDK_RES) { put("channelNo", channelNo) }
    }

    override fun close() {
        heartbeat?.cancel()
        socket?.close(NORMAL_CLOSE, null)
    }

    private suspend fun deviceRequest(
        link: DeviceLink,
        cmdId: Int,
        responseCmdId: Int,
        msgData: JsonObjectBuilder.() -> Unit,
    ): Incoming {
        val msgId = link.nextMsgId()
        val data: JsonObject = buildJsonObject(msgData)
        return request(Messages.deviceMessage(link.route, msgId, cmdId, data, clock())) {
            it.cmdId == responseCmdId && it.msgId == msgId
        }
    }

    /** Sends [message] and waits for the first incoming message matching [match]. */
    private suspend fun request(
        message: String,
        match: (Incoming) -> Boolean,
    ): Incoming =
        coroutineScope {
            val reply = async(start = CoroutineStart.UNDISPATCHED) { incoming.first(match) }
            val sent = socket?.send(message) ?: false
            if (!sent) {
                reply.cancel()
                throw ProtocolException("gateway socket is not open")
            }
            withTimeout(TIMEOUT_MS) { reply.await() }
        }

    companion object {
        /** The portal's heartbeat interval. */
        const val HEARTBEAT_MS = 200_000L
        private const val TIMEOUT_MS = 15_000L
        private const val INCOMING_BUFFER = 64
        private const val NORMAL_CLOSE = 1000
    }
}

class DeviceLoginException(
    deviceId: String,
    resultCode: Int,
) : Exception("device $deviceId refused login: resultCode $resultCode")
