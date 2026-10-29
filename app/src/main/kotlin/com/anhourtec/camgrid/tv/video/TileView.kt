package com.anhourtec.camgrid.tv.video

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.anhourtec.camgrid.tv.R
import com.anhourtec.camgrid.tv.ui.GridLoader

/**
 * One grid cell: video surface, a small camera label, and a centred state overlay (animated
 * loader while connecting, a calm message when the camera sends no video). Created only from code.
 */
@SuppressLint("ViewConstructor")
class TileView(
    context: Context,
    private val onSurface: (TileView, SurfaceHolder?) -> Unit,
) : FrameLayout(context) {
    val surfaceView = SurfaceView(context)
    val label =
        TextView(context).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(context.getColor(R.color.overlay))
            textSize = LABEL_SP
            setPadding(PAD, PAD / 2, PAD, PAD / 2)
        }
    private val loader = GridLoader(context)
    private val message =
        TextView(context).apply {
            setTextColor(context.getColor(R.color.text_dim))
            textSize = MESSAGE_SP
            gravity = Gravity.CENTER
            setPadding(PAD, PAD, PAD, 0)
        }
    private val overlay =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(context.getColor(R.color.background))
            val size = (LOADER_DP * resources.displayMetrics.density).toInt()
            addView(loader, LinearLayout.LayoutParams(size, size))
            addView(message, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = true
        foreground = context.getDrawable(R.drawable.tile_focus)
        addView(surfaceView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(label, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
        surfaceView.holder.addCallback(
            object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) = onSurface(this@TileView, holder)

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int,
                ) = Unit

                override fun surfaceDestroyed(holder: SurfaceHolder) = onSurface(this@TileView, null)
            },
        )
    }

    fun showConnecting() = showOverlay(loading = true, text = context.getString(R.string.tile_connecting))

    fun showLive() {
        overlay.visibility = View.GONE
    }

    fun showNoSignal(retryInSeconds: Int) = showOverlay(loading = false, text = context.getString(R.string.tile_no_signal, retryInSeconds))

    /** Automatic retries are used up: the user decides when to try again (OK on the tile). */
    fun showNoSignalManual() = showOverlay(loading = false, text = context.getString(R.string.tile_no_signal_manual))

    fun showNoDecoder() = showOverlay(loading = false, text = context.getString(R.string.tile_no_decoder))

    private fun showOverlay(
        loading: Boolean,
        text: String,
    ) {
        overlay.visibility = View.VISIBLE
        loader.visibility = if (loading) View.VISIBLE else View.GONE
        message.text = text
    }

    private companion object {
        const val LABEL_SP = 13f
        const val MESSAGE_SP = 15f
        const val LOADER_DP = 40
        const val PAD = 12
    }
}
