package com.anhourtec.camgrid.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.anhourtec.camgrid.layouts.Grid
import com.anhourtec.camgrid.layouts.Template
import com.anhourtec.camgrid.tv.R

/** A miniature of a grid shape. Mint when its card has focus; brighter outline when it's the current grid. */
@SuppressLint("ViewConstructor")
class TemplatePreview(
    context: Context,
    private val template: Template,
    private val current: Boolean,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val gap = GAP_DP * resources.displayMetrics.density
    private val accent = context.getColor(R.color.accent)

    override fun onDraw(canvas: Canvas) {
        // A 16:9 screen centred in the view.
        val w = minOf(width.toFloat(), height * ASPECT)
        val h = w / ASPECT
        val left = (width - w) / 2
        val top = (height - h) / 2
        val active = (parent as? View)?.isFocused == true
        paint.color =
            if (active) {
                accent
            } else if (current) {
                MUTED_CURRENT
            } else {
                MUTED
            }
        template.cells.forEach { cell ->
            rect.set(
                left + cell.col * w / Grid.UNITS + gap / 2,
                top + cell.row * h / Grid.UNITS + gap / 2,
                left + (cell.col + cell.cols) * w / Grid.UNITS - gap / 2,
                top + (cell.row + cell.rows) * h / Grid.UNITS - gap / 2,
            )
            canvas.drawRoundRect(rect, gap, gap, paint)
        }
    }

    private companion object {
        const val ASPECT = 16f / 9f
        const val GAP_DP = 3
        const val MUTED = 0xFF3A434E.toInt()
        const val MUTED_CURRENT = 0xFF7C9E84.toInt()
    }
}
