package com.anhourtec.camgrid.tv.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.anhourtec.camgrid.tv.R

/**
 * The app's TV dialog: a dark rounded card with an optional mint eyebrow, a title, and rows with
 * icons. The row under the remote gets a soft mint highlight. Long lists scroll.
 */
class TvDialog(
    private val activity: Activity,
    eyebrow: String?,
    title: String,
    wide: Boolean = false,
) {
    private val dialog = Dialog(activity, R.style.Theme_CamGrid_Dialog)
    private val density = activity.resources.displayMetrics.density
    private val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val rows = mutableListOf<View>()
    private var preferred: View? = null
    private val card =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dialog)
            val pad = dp(PAD_DP)
            setPadding(pad, pad, pad, dp(ROW_GAP_DP))
            minimumWidth = dp(if (wide) WIDE_DP else WIDTH_DP)
        }

    init {
        eyebrow?.takeIf { it.isNotEmpty() }?.let {
            card.addView(
                text(it.uppercase(), EYEBROW_SP, R.color.accent, bold = true).apply { letterSpacing = EYEBROW_SPACING },
            )
        }
        card.addView(text(title, TITLE_SP, R.color.text, bold = true).apply { setPadding(0, dp(2), 0, dp(TITLE_GAP_DP)) })
    }

    fun section(label: String): TvDialog {
        body.addView(
            text(label, SECTION_SP, R.color.text_dim, bold = true).apply { setPadding(dp(ROW_PAD_DP), dp(SECTION_TOP_DP), 0, dp(2)) },
        )
        return this
    }

    fun row(
        icon: Int?,
        label: String,
        checked: Boolean = false,
        danger: Boolean = false,
        onClick: () -> Unit,
    ): TvDialog {
        val row =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                setBackgroundResource(R.drawable.bg_row)
                setPadding(dp(ROW_PAD_DP), dp(ROW_V_PAD_DP), dp(ROW_PAD_DP), dp(ROW_V_PAD_DP))
                val tint = if (danger) DANGER else activity.getColor(R.color.accent)
                icon?.let {
                    addView(
                        image(it, tint),
                        LinearLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)).apply { marginEnd = dp(ROW_PAD_DP) },
                    )
                }
                val color = if (danger) R.color.danger else R.color.text
                addView(text(label, ROW_SP, color), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (checked) {
                    addView(
                        image(R.drawable.ic_check, activity.getColor(R.color.accent)),
                        LinearLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)),
                    )
                }
                setOnClickListener {
                    dialog.dismiss()
                    onClick()
                }
            }
        body.addView(
            row,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin =
                    dp(2)
            },
        )
        rows += row
        if (checked) preferred = row
        return this
    }

    /** Custom content (e.g. the grid-shape picker) in place of rows. */
    fun content(
        view: View,
        focus: View? = null,
    ): TvDialog {
        body.addView(view)
        preferred = focus
        return this
    }

    /** Closes the dialog; used by custom content. */
    fun dismiss() = dialog.dismiss()

    fun show() {
        val maxHeight = (activity.resources.displayMetrics.heightPixels * MAX_HEIGHT_RATIO).toInt()
        val scroll = ScrollView(activity).apply { isVerticalScrollBarEnabled = false }
        scroll.addView(body)
        card.addView(scroll)
        card.measure(
            View.MeasureSpec.makeMeasureSpec(activity.resources.displayMetrics.widthPixels, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        if (card.measuredHeight > maxHeight) {
            scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight - dp(HEADER_ALLOWANCE_DP))
        }
        dialog.setContentView(card)
        dialog.show()
        (preferred ?: rows.firstOrNull())?.requestFocus()
    }

    private fun text(
        value: String,
        sp: Float,
        color: Int,
        bold: Boolean = false,
    ) = TextView(activity).apply {
        text = value
        textSize = sp
        setTextColor(activity.getColor(color))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun image(
        res: Int,
        tint: Int,
    ) = ImageView(activity).apply {
        setImageResource(res)
        setColorFilter(tint)
    }

    private fun dp(value: Int) = (value * density).toInt()

    private companion object {
        const val WIDTH_DP = 520
        const val WIDE_DP = 760
        const val PAD_DP = 28
        const val ROW_PAD_DP = 16
        const val ROW_V_PAD_DP = 13
        const val ROW_GAP_DP = 20
        const val ICON_DP = 24
        const val TITLE_GAP_DP = 16
        const val SECTION_TOP_DP = 14
        const val HEADER_ALLOWANCE_DP = 110
        const val TITLE_SP = 26f
        const val EYEBROW_SP = 13f
        const val EYEBROW_SPACING = 0.08f
        const val SECTION_SP = 14f
        const val ROW_SP = 18f
        const val MAX_HEIGHT_RATIO = 0.86f
        const val DANGER = 0xFFFFB4AB.toInt()
    }
}
