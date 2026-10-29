package com.anhourtec.camgrid.pairing

import com.anhourtec.camgrid.layouts.CameraRef
import com.anhourtec.camgrid.layouts.Layout

/** One camera offered in the phone editor, e.g. group "HOME", label "CH3". */
data class CameraOption(
    val ref: CameraRef,
    val group: String,
    val label: String,
) {
    /** The form value: "deviceId:channelNo". */
    val key: String get() = "${ref.deviceId}:${ref.channelNo}"
}

/** What the TV shows right now and which cameras the account has. */
data class LayoutSnapshot(
    val layout: Layout,
    val cameras: List<CameraOption>,
)

/** Lets the phone page read and change the TV's layout. Calls run on server threads. */
interface LayoutBackend {
    fun snapshot(): LayoutSnapshot

    /** Saves [layout] and shows it on the TV. */
    fun save(layout: Layout)
}
