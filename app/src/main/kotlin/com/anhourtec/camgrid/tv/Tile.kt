package com.anhourtec.camgrid.tv

import android.util.Log
import android.view.SurfaceHolder
import com.anhourtec.camgrid.client.media.MediaSession
import com.anhourtec.camgrid.client.media.RelayAck
import com.anhourtec.camgrid.client.protocol.StreamQuality
import com.anhourtec.camgrid.tv.video.TileDecoder
import com.anhourtec.camgrid.tv.video.TileView
import kotlinx.coroutines.Job

/** One camera tile's live state: its view, decoder, media socket and stats. */
internal class Tile(
    val channelNo: Int,
    val quality: StreamQuality,
) {
    var view: TileView? = null
    var decoder: TileDecoder? = null
    var media: MediaSession? = null
    var ack: RelayAck? = null
    var job: Job? = null
    var startedAt = 0L
    var holder: SurfaceHolder? = null
    var retryJob: Job? = null
    var failures = 0
    var live = false

    /** True once automatic retries are used up and the tile waits for OK. */
    val waitingForUser: Boolean get() = failures >= MAX_ATTEMPTS && decoder == null
    private var lastRendered = 0L

    @Volatile var error: String? = null

    fun fail(e: Exception) {
        error = e.message ?: e.javaClass.simpleName
        Log.w(TAG, "CH${channelNo + 1}: no video (${e.message})")
    }

    /**
     * Updates the camera label once a second. Debug builds add decoder stats; release builds
     * show just the camera name. Returns true the first time video appears.
     */
    fun refreshLabel(debug: Boolean): Boolean {
        val label = view?.label ?: return false
        val s = decoder?.stats
        val res = label.context
        label.text = res.getString(R.string.tile_channel, channelNo + 1)
        if (s == null) return false
        val rendered = s.rendered.get()
        val fps = rendered - lastRendered
        lastRendered = rendered
        if (debug) {
            val first = if (s.firstFrameAt > 0) (s.firstFrameAt - startedAt) / MS_PER_S else 0.0
            val kind = if (s.decoder.hardware) "hw" else "sw"
            label.text = res.getString(R.string.tile_debug, channelNo + 1, quality.name, s.size, kind, fps, first, s.dropped.get())
        }
        val firstVideo = s.firstFrameAt > 0 && !live
        if (firstVideo) {
            live = true
            failures = 0
            view?.showLive()
            Log.i(TAG, "CH${channelNo + 1} live in ${s.firstFrameAt - startedAt} ms (${s.decoder.name})")
        }
        return firstVideo
    }

    private companion object {
        const val TAG = "CamGrid"
        const val MS_PER_S = 1000.0
        const val MAX_ATTEMPTS = 2
    }
}
