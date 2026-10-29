package com.anhourtec.camgrid.pairing

import com.anhourtec.camgrid.layouts.CameraRef
import com.anhourtec.camgrid.layouts.LayoutState

/** One camera offered in the phone editor, e.g. group "HOME", label "CH3". */
data class CameraOption(
    val ref: CameraRef,
    val group: String,
    val label: String,
) {
    /** The form value: "deviceId:channelNo". */
    val key: String get() = "${ref.deviceId}:${ref.channelNo}"
}

/** The TV's saved layouts and the account's cameras. */
data class LayoutSnapshot(
    val state: LayoutState,
    val cameras: List<CameraOption>,
)

/** Lets the phone page read and change the TV's layouts. Calls run on server threads. */
interface LayoutBackend {
    fun snapshot(): LayoutSnapshot

    /** Saves [state] on the TV and shows its current layout. */
    fun apply(state: LayoutState)
}
