package com.anhourtec.camgrid.tv

import android.util.Log
import android.view.SurfaceHolder
import com.anhourtec.camgrid.client.gateway.DeviceLink
import com.anhourtec.camgrid.client.gateway.GatewaySession
import com.anhourtec.camgrid.client.media.MediaSession
import com.anhourtec.camgrid.client.media.RelayAck
import com.anhourtec.camgrid.client.protocol.StreamQuality
import com.anhourtec.camgrid.ipcam.RtspClient
import com.anhourtec.camgrid.layouts.CameraRef
import com.anhourtec.camgrid.tv.video.TileDecoder
import com.anhourtec.camgrid.tv.video.TileView
import kotlinx.coroutines.Job

/** One tile of the current layout and its live state. [camera] is null for an empty tile. */
internal class Tile(
    var index: Int,
    val camera: CameraRef?,
    val name: String,
    val quality: StreamQuality,
) {
    var view: TileView? = null
    var holder: SurfaceHolder? = null
    var decoder: TileDecoder? = null
    var media: MediaSession? = null
    var ack: RelayAck? = null
    var session: GatewaySession? = null
    var link: DeviceLink? = null

    /** The RTSP connection of an IP camera tile (closed from the main thread to stop it). */
    @Volatile var rtsp: RtspClient? = null
    var job: Job? = null
    var retryJob: Job? = null
    var startedAt = 0L
    var failures = 0
    var live = false
    private var lastRendered = 0L

    /** True once automatic retries are used up and the tile waits for OK. */
    val waitingForUser: Boolean get() = camera != null && failures >= MAX_ATTEMPTS && decoder == null

    fun fail(e: Exception) {
        Log.w(TAG, "$name: no video (${e.message})")
    }

    /**
     * Updates the tile label once a second (debug builds add decoder stats). Returns true the
     * first time video appears.
     */
    fun refreshLabel(debug: Boolean): Boolean {
        val label = view?.label ?: return false
        label.text = name
        val s = decoder?.stats ?: return false
        val rendered = s.rendered.get()
        val fps = rendered - lastRendered
        lastRendered = rendered
        if (debug) {
            val first = if (s.firstFrameAt > 0) (s.firstFrameAt - startedAt) / MS_PER_S else 0.0
            val kind = if (s.decoder.hardware) "hw" else "sw"
            label.text = label.context.getString(R.string.tile_debug, name, quality.name, s.size, kind, fps, first, s.dropped.get())
        }
        val firstVideo = s.firstFrameAt > 0 && !live
        if (firstVideo) {
            live = true
            failures = 0
            view?.showLive()
            Log.i(TAG, "$name live in ${s.firstFrameAt - startedAt} ms (${s.decoder.name})")
        }
        return firstVideo
    }

    companion object {
        const val MAX_ATTEMPTS = 2
        private const val TAG = "CamGrid"
        private const val MS_PER_S = 1000.0
    }
}
