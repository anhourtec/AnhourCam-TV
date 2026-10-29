package com.anhourtec.camgrid.tv.data

import com.anhourtec.camgrid.client.page.Device
import com.anhourtec.camgrid.client.page.DeviceDetail
import com.anhourtec.camgrid.client.portal.PortalClient
import com.anhourtec.camgrid.ipcam.IpCamera
import com.anhourtec.camgrid.layouts.CameraRef
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** One camera as the user sees it in pickers: "HOME · CH3". */
data class CameraInfo(
    val ref: CameraRef,
    val deviceName: String,
    val channelName: String,
    val deviceOnline: Boolean,
) {
    val label: String get() = "$deviceName · $channelName"
}

/**
 * Every camera the user can pick: BitVision NVRs and channels (loaded once per run; device pages
 * fetched on demand) plus IP cameras, shown as one more group named [ipGroupName]. Without a
 * BitVision account [portal] is null and only IP cameras are listed.
 */
class DeviceDirectory(
    private val portal: PortalClient?,
    private val ipGroupName: String,
) {
    private val lock = Mutex()
    private val details = mutableMapOf<String, DeviceDetail>()
    var accountName: String = ""
        private set

    @Volatile
    var devices: List<Device> = emptyList()
        private set

    @Volatile
    var ipCameras: List<IpCamera> = emptyList()

    val hasBitVision: Boolean get() = portal != null

    suspend fun load() {
        val overview = portal?.overview() ?: return
        accountName = overview.accountName
        devices = overview.devices
    }

    val cameras: List<CameraInfo>
        get() =
            devices.flatMap { d ->
                d.channels.map { CameraInfo(CameraRef(d.deviceId, it.channelNo), d.name, it.name, d.online) }
            } + ipCameraInfos

    val ipCameraInfos: List<CameraInfo>
        get() = ipCameras.map { CameraInfo(ref(it), ipGroupName, it.name, deviceOnline = true) }

    fun ipCamera(ref: CameraRef): IpCamera? =
        if (ref.deviceId.startsWith(IpCamera.REF_PREFIX)) ipCameras.firstOrNull { ref(it) == ref } else null

    fun camera(ref: CameraRef): CameraInfo? = cameras.firstOrNull { it.ref == ref }

    /** The device page (gateway URLs, NVR login), fetched once per device per run. */
    suspend fun detail(deviceId: String): DeviceDetail {
        val portal = portal ?: throw IOException("not signed in to BitVision")
        return lock.withLock { details[deviceId] ?: portal.deviceDetail(deviceId).also { details[deviceId] = it } }
    }

    companion object {
        fun ref(camera: IpCamera): CameraRef = CameraRef(IpCamera.REF_PREFIX + camera.id, 0)
    }
}
