package com.anhourtec.camgrid.tv

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.anhourtec.camgrid.client.ClientException
import com.anhourtec.camgrid.client.portal.PortalClient
import com.anhourtec.camgrid.client.portal.SessionExpiredException
import com.anhourtec.camgrid.layouts.Layout
import com.anhourtec.camgrid.layouts.LayoutCodec
import com.anhourtec.camgrid.layouts.LayoutState
import com.anhourtec.camgrid.pairing.CameraOption
import com.anhourtec.camgrid.pairing.LayoutSnapshot
import com.anhourtec.camgrid.tv.data.DeviceDirectory
import com.anhourtec.camgrid.tv.data.LayoutRepository
import com.anhourtec.camgrid.tv.ui.Hint
import com.anhourtec.camgrid.tv.video.CellGridLayout
import com.anhourtec.camgrid.tv.video.DecoderBudget
import com.anhourtec.camgrid.tv.video.SnapshotCache
import com.anhourtec.camgrid.tv.video.TileView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.UUID

/**
 * Live view of the user's layout (SPEC §7). OK opens a camera fullscreen (Back returns), retries a
 * tile that gave up, or picks a camera for an empty tile. Hold OK (or Menu) for the tile menu.
 */
class LiveScreen(
    private val activity: Activity,
    private val http: OkHttpClient,
    portal: PortalClient,
    private val scope: CoroutineScope,
    private val onSignedOut: () -> Unit,
) {
    private val layouts = LayoutRepository(activity)
    val directory = DeviceDirectory(portal)
    private val budget = DecoderBudget()
    private val debug = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private val tiles = mutableListOf<Tile>()
    private var pool: GatewayPool? = null
    private var runner: TileRunner? = null
    private val stills = SnapshotCache()
    private var snapshots: SnapshotRunner? = null

    @Volatile private var state = LayoutState()
    private var manage: ManagePanel? = null
    private var fullscreen: Int? = null
    private var moving: Int? = null
    private var longPressed = false
    private var ticker: Job? = null
    private val focused: Tile? get() = tiles.firstOrNull { it.view?.hasFocus() == true }
    private lateinit var grid: CellGridLayout
    private lateinit var hint: Hint

    fun show() {
        activity.setContentView(R.layout.activity_live)
        // Live view must never let the TV sleep or start the screensaver.
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        grid = CellGridLayout(activity)
        activity.findViewById<FrameLayout>(R.id.grid_host).addView(grid)
        hint = Hint(activity.findViewById(R.id.hint))
        manage =
            ManagePanel(activity, scope, snapshot = {
                val cameras = directory.cameras.map { CameraOption(it.ref, it.deviceName, it.channelName) }
                LayoutSnapshot(state, cameras)
            }, onApply = { if (state.current != null) apply(it) })
        val loadingText = activity.findViewById<TextView>(R.id.loading_text)

        suspend fun prepare() {
            loadingText.setText(R.string.live_step_account)
            directory.load()
            pool = GatewayPool(http, scope, directory.accountName)
            runner = TileRunner(http, scope, directory, pool!!, budget, stills)
            snapshots = SnapshotRunner(http, scope, directory, pool!!, budget, stills)
            state = layouts.load()
            if (directory.cameras.isEmpty()) throw NoCamerasException()
            if (state.current == null) {
                // First run: every camera of every online NVR, in order, on the smallest grid that fits.
                val cameras = directory.cameras.filter { it.deviceOnline }.map { it.ref }
                val first = LayoutCodec.firstLayout(UUID.randomUUID().toString(), activity.getString(R.string.layout_default_name), cameras)
                state = state.put(first).copy(defaultId = first.id, lastId = first.id)
                layouts.save(state)
            }
            loadingText.setText(R.string.live_step_cameras)
        }

        fun fail(e: Exception) {
            activity.findViewById<View>(R.id.loading_mark).visibility = View.GONE
            loadingText.text = activity.getString(R.string.live_error, e.message ?: e.javaClass.simpleName)
            Log.w(TAG, "live view failed", e)
        }
        scope.launch {
            try {
                prepare()
                render()
                snapshots?.start { runner?.waitingTiles.orEmpty() }
                hint.show(activity.getString(R.string.live_hint))
            } catch (_: SessionExpiredException) {
                onSignedOut()
            } catch (_: NoCamerasException) {
                activity.findViewById<View>(R.id.loading_mark).visibility = View.GONE
                loadingText.setText(R.string.live_no_device)
            } catch (e: IOException) {
                fail(e)
            } catch (e: ClientException) {
                fail(e)
            }
        }
        ticker = scope.launch { tick() }
    }

    fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean =
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.repeatCount == 0) longPressed = false
                event.startTracking()
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                flip(-1, View.FOCUS_LEFT)
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                flip(1, View.FOCUS_RIGHT)
            }

            KeyEvent.KEYCODE_MENU -> {
                focused?.let { menu(it) }
                true
            }

            KeyEvent.KEYCODE_BACK -> {
                back()
            }

            else -> {
                false
            }
        }

    fun onKeyLongPress(keyCode: Int): Boolean {
        if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER) return false
        longPressed = true
        focused?.let { menu(it) }
        return true
    }

    /** A short OK press: drop a moving tile, retry, go fullscreen, or choose a camera for an empty tile. */
    fun onKeyUp(keyCode: Int): Boolean {
        if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER) return false
        val tile = focused
        val layout = state.current
        val from = moving
        when {
            longPressed || tile == null || layout == null -> {
                // The long press already opened the menu, or there is nothing to act on.
            }

            from != null -> {
                moving = null
                hint.hide()
                if (from != tile.index) apply(state.saved(layout.swap(from, tile.index)))
            }

            tile.waitingForUser -> {
                runner?.retryNow(tile)
            }

            tile.camera == null -> {
                TileMenu(activity, directory).pickCamera(tile.index, layout, actions)
            }

            fullscreen == null -> {
                fullscreen = tile.index
                render()
            }
        }
        longPressed = false
        return true
    }

    /**
     * Releases everything. Stop commands go out on [AppScope] so they still reach the NVRs after
     * this screen is gone; connections close only once they are acknowledged.
     */
    fun stop() {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        manage?.stop()
        snapshots?.stop()
        ticker?.cancel()
        tiles.forEach { it.retryJob?.cancel() }
        val pending = tiles.mapNotNull { StreamStops.detach(it) }
        tiles.clear()
        val pool = pool ?: return
        this.pool = null
        AppScope.launch {
            pending.map { launch { StreamStops.send(it) } }.joinAll()
            pool.close()
        }
    }

    private val actions =
        object : TileMenu.Actions {
            override fun changed(layout: Layout) = apply(state.saved(layout))

            override fun move(index: Int) {
                moving = index
                hint.show(activity.getString(R.string.move_hint), sticky = true)
            }

            override fun layouts() = LayoutsMenu(activity, directory).open(state) { apply(it) }

            override fun manageFromPhone() {
                manage?.start()
            }

            override fun signOut() = onSignedOut()
        }

    private fun render() {
        val layout = state.current ?: return
        val runner = runner ?: return
        val keepFocus = focused?.index ?: 0
        val next = TileGrid(activity, grid, directory, runner).show(layout, fullscreen, tiles)
        tiles.clear()
        tiles += next
        activity.findViewById<View>(R.id.loading).visibility = View.GONE
        (tiles.firstOrNull { it.index == keepFocus } ?: tiles.firstOrNull())?.view?.requestFocus()
        if (state.lastId != layout.id) {
            state = state.copy(lastId = layout.id)
            layouts.save(state)
        }
    }

    /** Back closes the phone card, cancels a move, or leaves fullscreen; otherwise it exits. */
    private fun back(): Boolean {
        when {
            manage?.isOpen == true -> {
                manage?.stop()
            }

            moving != null -> {
                moving = null
                hint.hide()
            }

            fullscreen != null -> {
                fullscreen = null
                render()
            }

            else -> {
                return false
            }
        }
        return true
    }

    /** At the grid's edge, ◀ ▶ flip between saved layouts (inside the grid they move focus). */
    private fun flip(
        by: Int,
        direction: Int,
    ): Boolean {
        val atEdge = focused?.view?.focusSearch(direction) !is TileView
        val flip = atEdge && fullscreen == null && moving == null && state.layouts.size > 1
        if (flip) apply(state.cycle(by))
        return flip
    }

    /** Saves and shows a new layout state; announces the layout name when it changed. */
    private fun apply(next: LayoutState) {
        val switched = next.current?.id != state.current?.id
        state = next
        layouts.save(next)
        fullscreen = null
        render()
        if (switched) next.current?.let { hint.show(activity.getString(R.string.layouts_switched, it.name)) }
    }

    private fun menu(tile: Tile) {
        val layout = state.current ?: return
        TileMenu(activity, directory).open(tile.index, tile.camera?.let(directory::camera), layout, actions)
    }

    private suspend fun tick() {
        while (scope.isActive) {
            delay(TICK_MS)
            val now = SystemClock.elapsedRealtime()
            tiles.toList().forEach { runner?.tick(it, now, debug) }
        }
    }

    private companion object {
        const val TAG = "CamGrid"
        const val TICK_MS = 1000L
    }
}

/** The account has no NVRs or cameras yet. */
private class NoCamerasException : Exception("no cameras on this account")
