package com.anhourtec.camgrid.tv.data

import com.anhourtec.camgrid.client.page.Device
import com.anhourtec.camgrid.client.page.DeviceDetail
import com.anhourtec.camgrid.client.portal.PortalClient
import com.anhourtec.camgrid.layouts.CameraRef
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One camera as the user sees it in pickers: "HOME · CH3". */
data class CameraInfo(
    val ref: CameraRef,
    val deviceName: String,
    val channelName: String,
    val deviceOnline: Boolean,
) {
    val label: String get() = "$deviceName · $channelName"
}

/** Every NVR and camera on the account, loaded once per run; device pages are fetched on demand. */
class DeviceDirectory(
    private val portal: PortalClient,
) {
    private val lock = Mutex()
    private val details = mutableMapOf<String, DeviceDetail>()
    var accountName: String = ""
        private set

    @Volatile
    var devices: List<Device> = emptyList()
        private set

    suspend fun load() {
        val overview = portal.overview()
        accountName = overview.accountName
        devices = overview.devices
    }

    val cameras: List<CameraInfo>
        get() =
            devices.flatMap { d ->
                d.channels.map { CameraInfo(CameraRef(d.deviceId, it.channelNo), d.name, it.name, d.online) }
            }

    fun camera(ref: CameraRef): CameraInfo? = cameras.firstOrNull { it.ref == ref }

    /** The device page (gateway URLs, NVR login), fetched once per device per run. */
    suspend fun detail(deviceId: String): DeviceDetail =
        lock.withLock { details[deviceId] ?: portal.deviceDetail(deviceId).also { details[deviceId] = it } }
}
