package com.anhourtec.camgrid.tv.data

import android.content.Context
import android.util.Log
import com.anhourtec.camgrid.ipcam.IpCamera
import com.anhourtec.camgrid.ipcam.IpCameraList
import java.io.File
import java.io.IOException

/** A camera's login, resolved from its own or the shared one. Never logged or shown. */
class CameraLogin(
    val user: String,
    val password: String,
)

/**
 * The user's IP cameras: the list in `ipcams.json` (atomic replace, like layouts), passwords
 * encrypted in [SecretStore] (the shared login, plus one per camera that has its own).
 */
class IpCameraStore(
    context: Context,
    private val secrets: SecretStore,
) {
    private val file = File(context.filesDir, FILE)

    fun load(): IpCameraList =
        try {
            if (file.exists()) IpCameraList.decode(file.readText()) ?: IpCameraList() else IpCameraList()
        } catch (_: IOException) {
            IpCameraList()
        }

    fun save(list: IpCameraList) {
        try {
            val tmp = File(file.parentFile, "$FILE.tmp")
            tmp.writeText(IpCameraList.encode(list))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: IOException) {
            Log.w("CamGrid", "could not save IP cameras", e)
        }
    }

    fun login(
        list: IpCameraList,
        camera: IpCamera,
    ): CameraLogin =
        if (camera.ownLogin) {
            CameraLogin(camera.user, secrets.get(KEY_PREFIX + camera.id).orEmpty())
        } else {
            CameraLogin(list.sharedUser, secrets.get(SHARED).orEmpty())
        }

    val sharedPassword: String? get() = secrets.get(SHARED)

    fun ownPassword(id: String): String? = secrets.get(KEY_PREFIX + id)

    fun setSharedPassword(password: String) = secrets.put(SHARED, password)

    fun setPassword(
        id: String,
        password: String,
    ) = secrets.put(KEY_PREFIX + id, password)

    fun removePassword(id: String) = secrets.remove(KEY_PREFIX + id)

    private companion object {
        const val FILE = "ipcams.json"
        const val SHARED = "ipcam_shared_password"
        const val KEY_PREFIX = "ipcam_password_"
    }
}
