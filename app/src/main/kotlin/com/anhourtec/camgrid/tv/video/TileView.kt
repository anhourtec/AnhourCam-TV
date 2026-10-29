package com.anhourtec.camgrid.tv.video

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
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
            textSize = LABEL_MAX_SP
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(PAD, PAD / 2, PAD, PAD / 2)
        }
    private val loader = GridLoader(context)
    private val still =
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            visibility = View.GONE
        }
    private val badge =
        TextView(context).apply {
            setText(R.string.tile_still)
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_hint)
            visibility = View.GONE
        }
    private val message =
        TextView(context).apply {
            setTextColor(context.getColor(R.color.text_dim))
            textSize = MESSAGE_SP
            gravity = Gravity.CENTER
            maxLines = MESSAGE_LINES
            setPadding(PAD, PAD, PAD, 0)
        }
    private val overlay =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(context.getColor(R.color.background))
            val size = (LOADER_DP * resources.displayMetrics.density).toInt()
            addView(loader, LinearLayout.LayoutParams(size, size))
            addView(message, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = true
        foreground = context.getDrawable(R.drawable.tile_focus)
        addView(surfaceView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(still, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END))
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

    /** Label, message and loader follow the tile's size: compact in 4×4, roomy fullscreen. */
    private var sizedForHeight = -1

    /**
     * Sizes text and loader for this tile's height before the children are measured (doing it
     * after layout left text boxes at their old height and cut off lines).
     */
    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val h = MeasureSpec.getSize(heightMeasureSpec)
        if (h > 0 && h != sizedForHeight) {
            sizedForHeight = h
            val density = resources.displayMetrics.density
            val heightDp = h / density
            val labelSp = (heightDp * LABEL_RATIO).coerceIn(LABEL_MIN_SP, LABEL_MAX_SP)
            label.textSize = labelSp
            badge.textSize = labelSp
            message.textSize = (heightDp * MESSAGE_RATIO).coerceIn(MESSAGE_MIN_SP, MESSAGE_MAX_SP)
            val pad = (labelSp * density * PAD_RATIO).toInt()
            label.setPadding(pad, pad / 2, pad, pad / 2)
            badge.setPadding(pad, pad / 2, pad, pad / 2)
            (badge.layoutParams as LayoutParams).setMargins(pad, pad, pad, pad)
            val loaderPx = (h * LOADER_RATIO).toInt().coerceIn((LOADER_MIN_DP * density).toInt(), (LOADER_DP * density).toInt())
            loader.layoutParams.width = loaderPx
            loader.layoutParams.height = loaderPx
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** Connecting: the loader, over the camera's last still image when there is one. */
    fun showConnecting(lastStill: Bitmap? = null) {
        showImage(lastStill, badge = false)
        showOverlay(loading = true, text = context.getString(R.string.tile_connecting))
        overlay.setBackgroundColor(context.getColor(if (lastStill != null) R.color.overlay else R.color.background))
    }

    /** No decoder free: a still image refreshed every few seconds, with a small badge. */
    fun showStill(bitmap: Bitmap) {
        showImage(bitmap, badge = true)
        overlay.visibility = View.GONE
    }

    private fun showImage(
        bitmap: Bitmap?,
        badge: Boolean,
    ) {
        still.setImageBitmap(bitmap)
        still.visibility = if (bitmap != null) View.VISIBLE else View.GONE
        this.badge.visibility = if (bitmap != null && badge) View.VISIBLE else View.GONE
    }

    fun showLive() {
        overlay.visibility = View.GONE
        showImage(null, badge = false)
    }

    fun showNoSignal(retryInSeconds: Int) = showOverlay(loading = false, text = context.getString(R.string.tile_no_signal, retryInSeconds))

    /** Automatic retries are used up: the user decides when to try again (OK on the tile). */
    fun showNoSignalManual() = showOverlay(loading = false, text = context.getString(R.string.tile_no_signal_manual))

    fun showEmpty() = showOverlay(loading = false, text = context.getString(R.string.tile_empty))

    fun showNoDecoder(capacity: Int) =
        showOverlay(loading = false, text = resources.getQuantityString(R.plurals.tile_no_decoder, capacity, capacity))

    private fun showOverlay(
        loading: Boolean,
        text: String,
    ) {
        overlay.setBackgroundColor(context.getColor(R.color.background))
        overlay.visibility = View.VISIBLE
        loader.visibility = if (loading) View.VISIBLE else View.GONE
        message.text = text
    }

    private companion object {
        const val LABEL_RATIO = 0.04f
        const val LABEL_MIN_SP = 8f
        const val LABEL_MAX_SP = 12f
        const val MESSAGE_RATIO = 0.055f
        const val MESSAGE_MIN_SP = 9f
        const val MESSAGE_MAX_SP = 15f
        const val MESSAGE_SP = MESSAGE_MAX_SP
        const val PAD_RATIO = 0.6f
        const val MESSAGE_LINES = 3
        const val LOADER_RATIO = 0.16f
        const val LOADER_MIN_DP = 20
        const val LOADER_DP = 40
        const val PAD = 12
    }
}
