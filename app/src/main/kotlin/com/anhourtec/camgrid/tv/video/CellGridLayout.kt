package com.anhourtec.camgrid.tv.video

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.anhourtec.camgrid.layouts.Cell
import com.anhourtec.camgrid.layouts.Grid

/** Places each child on its [Cell] of the layout grid, scaled to this view's size. */
class CellGridLayout(
    context: Context,
) : ViewGroup(context) {
    private val cells = mutableMapOf<View, Cell>()

    fun addTile(
        view: View,
        cell: Cell,
    ) {
        cells[view] = cell
        addView(view)
    }

    /** Moves a tile to another cell without recreating it (its video keeps playing). */
    fun move(
        view: View,
        cell: Cell,
    ) {
        if (cells[view] == cell) return
        cells[view] = cell
        requestLayout()
    }

    fun removeTile(view: View) {
        cells.remove(view)
        removeView(view)
    }

    fun clear() {
        cells.clear()
        removeAllViews()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        cells.forEach { (view, cell) ->
            view.measure(
                MeasureSpec.makeMeasureSpec(cell.cols * width / Grid.UNITS, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cell.rows * height / Grid.UNITS, MeasureSpec.EXACTLY),
            )
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(
        changed: Boolean,
        l: Int,
        t: Int,
        r: Int,
        b: Int,
    ) {
        val width = r - l
        val height = b - t
        cells.forEach { (view, cell) ->
            val left = cell.col * width / Grid.UNITS
            val top = cell.row * height / Grid.UNITS
            view.layout(left, top, left + view.measuredWidth, top + view.measuredHeight)
        }
    }
}
