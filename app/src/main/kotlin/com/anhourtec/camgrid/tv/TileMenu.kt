package com.anhourtec.camgrid.tv

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.anhourtec.camgrid.layouts.Layout
import com.anhourtec.camgrid.layouts.Template
import com.anhourtec.camgrid.layouts.Templates
import com.anhourtec.camgrid.tv.data.CameraInfo
import com.anhourtec.camgrid.tv.data.DeviceDirectory
import com.anhourtec.camgrid.tv.ui.TemplatePreview
import com.anhourtec.camgrid.tv.ui.TvDialog
import kotlin.math.sqrt

/** The "hold OK on a tile" menu and its pickers, in the app's own dialog style. */
internal class TileMenu(
    private val activity: Activity,
    private val directory: DeviceDirectory,
) {
    interface Actions {
        fun changed(layout: Layout)

        fun move(index: Int)

        fun manageFromPhone()

        fun layouts()

        fun signOut()
    }

    fun open(
        index: Int,
        camera: CameraInfo?,
        layout: Layout,
        actions: Actions,
    ) {
        val title = camera?.channelName ?: activity.getString(R.string.tile_empty_title)
        val dialog =
            TvDialog(activity, camera?.deviceName, title)
                .row(R.drawable.ic_camera, activity.getString(R.string.menu_change_camera)) { pickCamera(index, layout, actions) }
                .row(R.drawable.ic_move, activity.getString(R.string.menu_move)) { actions.move(index) }
        if (camera !=
            null
        ) {
            dialog.row(R.drawable.ic_clear, activity.getString(R.string.menu_clear)) { actions.changed(layout.assign(index, null)) }
        }
        dialog
            .row(R.drawable.ic_grid, activity.getString(R.string.menu_grid)) { pickGrid(layout, actions) }
            .row(R.drawable.ic_layers, activity.getString(R.string.menu_layouts)) { actions.layouts() }
            .row(R.drawable.ic_phone, activity.getString(R.string.menu_manage_phone)) { actions.manageFromPhone() }
            .row(R.drawable.ic_logout, activity.getString(R.string.menu_sign_out), danger = true) { actions.signOut() }
            .show()
    }

    /** Cameras grouped by NVR, the current one ticked. */
    fun pickCamera(
        index: Int,
        layout: Layout,
        actions: Actions,
    ) {
        val current = layout.cameraAt(index)
        val dialog =
            TvDialog(activity, null, activity.getString(R.string.menu_change_camera))
                .row(R.drawable.ic_clear, activity.getString(R.string.picker_empty), checked = current == null) {
                    actions.changed(layout.assign(index, null))
                }
        directory.devices.forEach { device ->
            val header = if (device.online) device.name else activity.getString(R.string.nvr_offline, device.name)
            dialog.section(header)
            directory.cameras.filter { it.ref.deviceId == device.deviceId }.forEach { cam ->
                dialog.row(R.drawable.ic_camera, cam.channelName, checked = cam.ref == current) {
                    actions.changed(layout.assign(index, cam.ref))
                }
            }
        }
        dialog.show()
    }

    /** Every grid shape as a miniature, four per row. */
    private fun pickGrid(
        layout: Layout,
        actions: Actions,
    ) {
        val density = activity.resources.displayMetrics.density
        val dialog = TvDialog(activity, null, activity.getString(R.string.menu_grid), wide = true)
        val grid = GridLayout(activity).apply { columnCount = COLUMNS }
        var focus: LinearLayout? = null
        Templates.ALL.forEach { template ->
            val selected = template == layout.template
            val card =
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    isFocusable = true
                    isClickable = true
                    setBackgroundResource(R.drawable.bg_row)
                    val pad = (CARD_PAD_DP * density).toInt()
                    setPadding(pad, pad, pad, pad)
                    val preview = TemplatePreview(activity, template, selected)
                    addView(preview, LinearLayout.LayoutParams((PREVIEW_W_DP * density).toInt(), (PREVIEW_H_DP * density).toInt()))
                    addView(label(template.tileCount.toString(), BIG_SP, R.color.text, bold = true))
                    addView(
                        label(shape(template), SMALL_SP, R.color.text_dim).apply {
                            maxLines = 2
                            maxWidth = (PREVIEW_W_DP * density).toInt()
                        },
                    )
                    setOnFocusChangeListener { _, _ -> preview.invalidate() }
                    setOnClickListener {
                        dialog.dismiss()
                        actions.changed(layout.withTemplate(template.id, directory.cameras.map { it.ref }))
                    }
                }
            if (selected) focus = card
            grid.addView(card, GridLayout.LayoutParams().apply { width = ViewGroup.LayoutParams.WRAP_CONTENT })
        }
        dialog.content(grid, focus).show()
    }

    private fun label(
        value: String,
        sp: Float,
        color: Int,
        bold: Boolean = false,
    ) = TextView(activity).apply {
        text = value
        textSize = sp
        gravity = Gravity.CENTER
        setTextColor(activity.getColor(color))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun shape(t: Template): String =
        when (t.id) {
            "1" -> {
                activity.getString(R.string.shape_full)
            }

            "2" -> {
                activity.getString(R.string.shape_side)
            }

            "6", "8" -> {
                activity.getString(R.string.shape_one_large, t.tileCount - 1)
            }

            "10" -> {
                activity.getString(R.string.shape_two_large, t.tileCount - 2)
            }

            "13" -> {
                activity.getString(R.string.shape_center, t.tileCount - 1)
            }

            else -> {
                val side = sqrt(t.tileCount.toDouble()).toInt()
                activity.getString(R.string.shape_uniform, side, side)
            }
        }

    private companion object {
        const val COLUMNS = 4
        const val CARD_PAD_DP = 12
        const val PREVIEW_W_DP = 128
        const val PREVIEW_H_DP = 72
        const val BIG_SP = 20f
        const val SMALL_SP = 13f
    }
}
