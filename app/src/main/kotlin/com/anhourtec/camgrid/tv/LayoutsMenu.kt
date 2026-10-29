package com.anhourtec.camgrid.tv

import android.app.Activity
import com.anhourtec.camgrid.layouts.LayoutState
import com.anhourtec.camgrid.tv.data.DeviceDirectory
import com.anhourtec.camgrid.tv.ui.TvDialog
import java.util.UUID

/** "Layouts": switch between saved layouts, add one, set the default, delete the current one. */
internal class LayoutsMenu(
    private val activity: Activity,
    private val directory: DeviceDirectory,
) {
    fun open(
        state: LayoutState,
        apply: (LayoutState) -> Unit,
    ) {
        val current = state.current ?: return
        val dialog = TvDialog(activity, null, activity.getString(R.string.menu_layouts))
        state.layouts.forEach { layout ->
            val label =
                if (layout.id == state.defaultId) activity.getString(R.string.layouts_default_badge, layout.name) else layout.name
            dialog.row(R.drawable.ic_layers, label, checked = layout.id == current.id) { apply(state.select(layout.id)) }
        }
        dialog.row(R.drawable.ic_add, activity.getString(R.string.layouts_new)) {
            val name = state.nextName(activity.getString(R.string.layout_new_name))
            val cameras = directory.cameras.filter { it.deviceOnline }.map { it.ref }
            apply(state.create(UUID.randomUUID().toString(), name, cameras))
        }
        if (current.id != state.defaultId) {
            dialog.row(R.drawable.ic_star, activity.getString(R.string.layouts_make_default, current.name)) {
                apply(state.withDefault(current.id))
            }
        }
        if (state.layouts.size > 1) {
            dialog.row(R.drawable.ic_delete, activity.getString(R.string.layouts_delete, current.name), danger = true) {
                apply(state.delete(current.id))
            }
        }
        dialog.show()
    }
}
