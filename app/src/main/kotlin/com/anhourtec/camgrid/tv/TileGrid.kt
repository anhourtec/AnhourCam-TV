package com.anhourtec.camgrid.tv

import android.app.Activity
import com.anhourtec.camgrid.client.protocol.StreamQuality
import com.anhourtec.camgrid.layouts.Cell
import com.anhourtec.camgrid.layouts.Grid
import com.anhourtec.camgrid.layouts.Layout
import com.anhourtec.camgrid.tv.data.DeviceDirectory
import com.anhourtec.camgrid.tv.video.CellGridLayout
import com.anhourtec.camgrid.tv.video.TileView

/**
 * Puts a layout on screen, changing as little as possible: a tile whose camera (and quality) is
 * still wanted keeps its running stream and only moves to its new cell; only new or changed tiles
 * start streams, and tiles no longer wanted are stopped. Editing one tile restarts one stream,
 * a move/swap restarts none, and a 2×2 grid never runs more than 4 streams.
 */
internal class TileGrid(
    private val activity: Activity,
    private val grid: CellGridLayout,
    private val directory: DeviceDirectory,
    private val runner: TileRunner,
) {
    /** Returns the tiles now on screen. [fullscreen] is the index of a tile shown alone in HD. */
    fun show(
        layout: Layout,
        fullscreen: Int?,
        current: List<Tile>,
    ): List<Tile> {
        val quality = if (fullscreen != null) StreamQuality.HD else StreamQuality.SD
        val wanted =
            if (fullscreen != null) {
                listOf(fullscreen to Cell(0, 0, Grid.UNITS, Grid.UNITS))
            } else {
                layout.template.cells.mapIndexed { i, cell -> i to cell }
            }
        val reusable = current.filter { it.camera != null && it.quality == quality }.toMutableList()
        val next =
            wanted.map { (index, cell) ->
                val camera = layout.cameraAt(index)
                val kept = reusable.firstOrNull { camera != null && it.camera == camera }
                if (kept != null) {
                    reusable.remove(kept)
                    kept.index = index
                    kept.view?.let { grid.move(it, cell) }
                    kept
                } else {
                    create(layout, index, cell, quality)
                }
            }
        (current - next.toSet()).forEach { old ->
            runner.stop(old)
            old.view?.let { grid.removeTile(it) }
        }
        return next
    }

    private fun create(
        layout: Layout,
        index: Int,
        cell: Cell,
        quality: StreamQuality,
    ): Tile {
        val camera = layout.cameraAt(index)
        val tile = Tile(index, camera, camera?.let { directory.camera(it)?.label }.orEmpty(), quality)
        val view =
            TileView(activity) { _, holder ->
                tile.holder = holder
                if (holder == null) runner.stop(tile) else runner.start(tile)
            }
        if (camera == null) view.showEmpty() else view.showConnecting()
        tile.view = view
        grid.addTile(view, cell)
        return tile
    }
}
