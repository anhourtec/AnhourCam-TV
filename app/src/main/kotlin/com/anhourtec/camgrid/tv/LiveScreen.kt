package com.anhourtec.camgrid.tv

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.anhourtec.camgrid.client.ClientException
import com.anhourtec.camgrid.client.gateway.DeviceLink
import com.anhourtec.camgrid.client.gateway.GatewaySession
import com.anhourtec.camgrid.client.media.MediaSession
import com.anhourtec.camgrid.client.page.DeviceDetail
import com.anhourtec.camgrid.client.portal.PortalClient
import com.anhourtec.camgrid.client.portal.SessionExpiredException
import com.anhourtec.camgrid.client.protocol.StreamQuality
import com.anhourtec.camgrid.tv.video.DecoderBudget
import com.anhourtec.camgrid.tv.video.TileDecoder
import com.anhourtec.camgrid.tv.video.TileView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * Phase 2 live view: a 2×2 grid of sub-streams or one main stream fullscreen. OK toggles
 * grid/fullscreen, left/right pages through channels. A camera that sends no video keeps its
 * place and retries with backoff (5 → 10 → 20 → 30 s).
 */
class LiveScreen(
    private val activity: Activity,
    private val http: OkHttpClient,
    private val portal: PortalClient,
    private val scope: CoroutineScope,
    private val onSignedOut: () -> Unit,
) {
    private val budget = DecoderBudget()
    private val tiles = mutableListOf<Tile>()
    private val debug = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private lateinit var grid: LinearLayout
    private lateinit var loading: View
    private lateinit var loadingText: TextView
    private var gateway: GatewaySession? = null
    private var device: DeviceDetail? = null
    private var link: DeviceLink? = null
    private var picker: ChannelPicker? = null
    private var fullscreen = false
    private var statsJob: Job? = null
    private val tileCount get() = if (fullscreen) 1 else GRID_SIDE * GRID_SIDE

    fun show() {
        activity.setContentView(R.layout.activity_live)
        // Live view must never let the TV sleep or start the screensaver.
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        grid = activity.findViewById(R.id.grid)
        loading = activity.findViewById(R.id.loading)
        loadingText = activity.findViewById(R.id.loading_text)
        Log.i(TAG, "HEVC decoders: ${budget.decoders}")

        fun showError(e: Exception) {
            activity.findViewById<View>(R.id.loading_mark).visibility = View.GONE
            loadingText.text = activity.getString(R.string.live_error, e.message ?: e.javaClass.simpleName)
            Log.w(TAG, "live view failed", e)
        }
        scope.launch {
            try {
                if (connect()) layoutTiles() else loadingText.setText(R.string.live_no_device)
            } catch (_: SessionExpiredException) {
                onSignedOut()
            } catch (e: IOException) {
                showError(e)
            } catch (e: ClientException) {
                showError(e)
            }
        }
        statsJob = scope.launch { refreshStats() }
    }

    /** Returns false if the account has no online device. */
    private suspend fun connect(): Boolean {
        val started = SystemClock.elapsedRealtime()
        loadingText.setText(R.string.live_step_account)
        val overview = portal.overview()
        val first = overview.devices.firstOrNull { it.online } ?: return false
        loadingText.text = activity.getString(R.string.live_step_device, first.name)
        val detail = portal.deviceDetail(first.deviceId)
        val session = GatewaySession(http, detail.service, overview.accountName, scope)
        session.connect()
        link = session.openDevice(detail)
        gateway = session
        device = detail
        picker = ChannelPicker(detail.channelCount)
        loadingText.setText(R.string.live_step_cameras)
        Log.i(TAG, "connected to ${detail.name} (${detail.channelCount} ch) in ${SystemClock.elapsedRealtime() - started} ms")
        return true
    }

    /**
     * OK on a tile whose retries are used up tries it again; OK elsewhere toggles grid ↔
     * fullscreen of the focused camera. ◀ ▶ move between tiles and flip the page at the edge.
     */
    fun onKey(keyCode: Int): Boolean {
        val picker = picker ?: return false
        val focused = tiles.firstOrNull { it.view?.hasFocus() == true }
        val sideways = if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) View.FOCUS_RIGHT else View.FOCUS_LEFT
        val action: (() -> Unit)? =
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (focused != null && focused.waitingForUser) {
                        {
                            focused.failures = 0
                            startTile(focused)
                        }
                    } else {
                        {
                            fullscreen = !fullscreen
                            focused?.let { picker.startAt(it.channelNo) }
                            layoutTiles()
                        }
                    }
                }

                // Inside the grid the arrows move focus between tiles; at the edge they flip the page.
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (focused?.view?.focusSearch(sideways) is TileView) {
                        null
                    } else {
                        {
                            picker.shift(if (sideways == View.FOCUS_RIGHT) tileCount else -tileCount)
                            layoutTiles()
                        }
                    }
                }

                else -> {
                    null
                }
            }
        action?.invoke()
        return action != null
    }

    /**
     * Releases everything. Stop commands go out on [AppScope] so they still reach the NVR after
     * this screen is gone; the gateway socket closes only once they are acknowledged.
     */
    fun stop() {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        statsJob?.cancel()
        tiles.forEach { it.retryJob?.cancel() }
        val pending = tiles.mapNotNull { StreamStops.detach(it) }
        tiles.clear()
        val session = gateway ?: return
        val link = link
        gateway = null
        AppScope.launch {
            if (link != null) pending.map { launch { StreamStops.send(session, link, it) } }.joinAll()
            session.close()
        }
    }

    private fun layoutTiles() {
        val channels = picker?.page(tileCount) ?: return
        tiles.forEach {
            it.retryJob?.cancel()
            stopTile(it)
        }
        tiles.clear()
        grid.removeAllViews()
        val side = if (fullscreen) 1 else GRID_SIDE
        val quality = if (fullscreen) StreamQuality.HD else StreamQuality.SD
        repeat(side) { row ->
            val line = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            grid.addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            repeat(side) { col ->
                val cell = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                val channel = channels.getOrNull(row * side + col)
                if (channel == null) {
                    line.addView(View(activity), cell)
                } else {
                    val tile = Tile(channel, quality)
                    tile.view = TileView(activity) { _, holder -> onSurface(tile, holder) }.also { it.showConnecting() }
                    tiles += tile
                    line.addView(tile.view, cell)
                }
            }
        }
        loading.visibility = View.GONE
        tiles.firstOrNull()?.view?.requestFocus()
    }

    private fun onSurface(
        tile: Tile,
        holder: SurfaceHolder?,
    ) {
        tile.holder = holder
        if (holder == null) {
            tile.retryJob?.cancel()
            stopTile(tile)
        } else {
            startTile(tile)
        }
    }

    private fun startTile(tile: Tile) {
        val holder = tile.holder ?: return
        val slot = budget.acquire()
        if (slot == null) {
            tile.view?.showNoDecoder()
            return
        }
        val decoder = TileDecoder(holder.surface, slot)
        tile.decoder = decoder
        tile.live = false
        tile.startedAt = SystemClock.elapsedRealtime()
        tile.view?.showConnecting()
        tile.job = scope.launch { startStream(tile, decoder) }
    }

    private suspend fun startStream(
        tile: Tile,
        decoder: TileDecoder,
    ) {
        val session = gateway ?: return
        val link = link ?: return
        val detail = device ?: return
        try {
            val media = MediaSession(http, detail.service, detail.deviceId, scope, decoder)
            tile.media = media
            val ack = media.open()
            tile.ack = ack
            session.playVideo(link, tile.channelNo, tile.quality.streamId, ack)
            media.startStream(link, tile.channelNo, tile.quality.streamId)
        } catch (e: IOException) {
            tile.fail(e)
            retryLater(tile)
        } catch (e: ClientException) {
            tile.fail(e)
            retryLater(tile)
        }
    }

    /**
     * No video: the tile keeps its place. It gets [MAX_ATTEMPTS] tries in total (one automatic
     * retry); after that it waits for the user to press OK on it.
     */
    private fun retryLater(tile: Tile) {
        stopTile(tile)
        tile.failures++
        if (tile.failures >= MAX_ATTEMPTS) {
            tile.view?.showNoSignalManual()
            return
        }
        tile.view?.showNoSignal(RETRY_S)
        tile.retryJob =
            scope.launch {
                delay(RETRY_S * MS_PER_S)
                if (tile in tiles) startTile(tile)
            }
    }

    private fun stopTile(tile: Tile) {
        val request = StreamStops.detach(tile) ?: return
        val session = gateway ?: return
        val link = link ?: return
        AppScope.launch { StreamStops.send(session, link, request) }
    }

    private suspend fun refreshStats() {
        while (scope.isActive) {
            delay(STATS_INTERVAL_MS)
            val now = SystemClock.elapsedRealtime()
            tiles.toList().forEach { tile ->
                tile.refreshLabel(debug)
                val waiting = tile.decoder != null && !tile.live
                if (waiting && now - tile.startedAt > NO_PICTURE_MS) retryLater(tile)
            }
        }
    }

    private companion object {
        const val TAG = "CamGrid"
        const val GRID_SIDE = 2
        const val STATS_INTERVAL_MS = 1000L
        const val NO_PICTURE_MS = 20_000L
        const val MAX_ATTEMPTS = 2
        const val RETRY_S = 5
        const val MS_PER_S = 1000L
    }
}
