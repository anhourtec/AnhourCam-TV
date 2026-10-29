package com.anhourtec.camgrid.tv.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.anhourtec.camgrid.tv.R

/**
 * The app's 2×2 grid mark as a loading animation: the squares light up one after another,
 * clockwise. Pure Canvas drawing, no images; respects the system's animation scale.
 */
class GridLoader
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.accent) }
        private val rect = RectF()
        private var phase = 0f
        private val animator =
            ValueAnimator.ofFloat(0f, CELLS.toFloat()).apply {
                duration = CYCLE_MS
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    invalidate()
                }
            }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            animator.start()
        }

        override fun onDetachedFromWindow() {
            animator.cancel()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            val size = minOf(width, height).toFloat()
            val cell = size * CELL_RATIO
            val gap = size - 2 * cell
            val left = (width - size) / 2
            val top = (height - size) / 2
            val radius = cell * CORNER_RATIO
            // Clockwise order: top-left, top-right, bottom-right, bottom-left.
            val positions = arrayOf(0 to 0, 1 to 0, 1 to 1, 0 to 1)
            positions.forEachIndexed { i, (col, row) ->
                val distance = ((phase - i) % CELLS + CELLS) % CELLS
                val glow = (1f - distance).coerceAtLeast(0f)
                paint.alpha = ((MIN_ALPHA + (1f - MIN_ALPHA) * glow) * FULL_ALPHA).toInt()
                val x = left + col * (cell + gap)
                val y = top + row * (cell + gap)
                rect.set(x, y, x + cell, y + cell)
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
        }

        private companion object {
            const val CELLS = 4
            const val CYCLE_MS = 1200L
            const val CELL_RATIO = 0.44f
            const val CORNER_RATIO = 0.22f
            const val MIN_ALPHA = 0.18f
            const val FULL_ALPHA = 255
        }
    }
