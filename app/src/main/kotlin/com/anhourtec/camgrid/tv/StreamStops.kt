package com.anhourtec.camgrid.tv

import android.util.Log
import com.anhourtec.camgrid.client.ClientException
import com.anhourtec.camgrid.client.gateway.DeviceLink
import com.anhourtec.camgrid.client.gateway.GatewaySession
import com.anhourtec.camgrid.client.media.MediaSession
import com.anhourtec.camgrid.client.media.RelayAck
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException

/**
 * Stopping streams properly. An NVR keeps pushing a stream until it gets stopVideo (8232), even
 * after our sockets close, and it only has a few stream slots: skipping this leaks them.
 */
internal object StreamStops {
    private const val TAG = "CamGrid"
    private const val STOP_TIMEOUT_MS = 3000L

    /** What is needed to stop a tile's stream on the NVR after the tile itself is gone. */
    class Request(
        val channelNo: Int,
        val streamId: Int,
        val ack: RelayAck,
        val media: MediaSession?,
    )

    /** Frees the tile's decoder now; returns the stream to stop on the NVR, if one was started. */
    fun detach(tile: Tile): Request? {
        tile.job?.cancel()
        tile.decoder?.close()
        val request = tile.ack?.let { Request(tile.channelNo, tile.quality.streamId, it, tile.media) }
        if (request == null) tile.media?.close()
        tile.decoder = null
        tile.media = null
        tile.ack = null
        return request
    }

    /** Sends stopVideo and waits for the acknowledgement (3 s at most), then closes the media socket. */
    suspend fun send(
        session: GatewaySession,
        link: DeviceLink,
        request: Request,
    ) {
        val name = "CH${request.channelNo + 1}"
        try {
            withTimeout(STOP_TIMEOUT_MS) { session.stopVideo(link, request.channelNo, request.streamId, request.ack) }
            Log.i(TAG, "stopped $name")
        } catch (e: IOException) {
            Log.w(TAG, "stop $name failed", e)
        } catch (e: ClientException) {
            Log.w(TAG, "stop $name failed", e)
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "stop $name timed out", e)
        } finally {
            request.media?.close()
        }
    }
}
