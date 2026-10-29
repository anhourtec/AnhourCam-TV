package com.anhourtec.camgrid.ipcam

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * An IP camera the user added. Passwords are never part of this: the TV keeps them encrypted
 * (shared login, or one per camera when [ownLogin] is set).
 */
@Serializable
data class IpCamera(
    val id: String,
    val name: String,
    val host: String,
    /** Full-quality stream (fullscreen). */
    val mainUrl: String,
    /** Smaller stream for grid tiles; the main stream when the camera has only one. */
    val subUrl: String? = null,
    /** JPEG still image, if the camera has one. */
    val snapshotUrl: String? = null,
    /** True: this camera has its own login. False: it uses the shared login. */
    val ownLogin: Boolean = false,
    /** User name for [ownLogin] (the password is stored separately, encrypted). */
    val user: String = "",
    /** The camera only accepted Basic login when it was added (password sent readable). */
    val basicLogin: Boolean = false,
    /** How it was added: "scan", "onvif", "brand:<id>" or "url". */
    val source: String = "url",
) {
    /** The stream for a tile: sub-stream in grids, main stream fullscreen. */
    fun streamUrl(fullscreen: Boolean): String = if (fullscreen) mainUrl else subUrl ?: mainUrl

    companion object {
        /** Prefix that marks IP cameras in layouts: `CameraRef("ip:<id>", 0)`. */
        const val REF_PREFIX = "ip:"
    }
}

/** Everything about IP cameras the TV saves (except passwords). */
@Serializable
data class IpCameraList(
    val cameras: List<IpCamera> = emptyList(),
    /** User name of the shared login used by cameras without their own. */
    val sharedUser: String = "admin",
) {
    fun find(id: String): IpCamera? = cameras.firstOrNull { it.id == id }

    fun put(camera: IpCamera): IpCameraList =
        copy(cameras = if (find(camera.id) == null) cameras + camera else cameras.map { if (it.id == camera.id) camera else it })

    fun remove(id: String): IpCameraList = copy(cameras = cameras.filterNot { it.id == id })

    companion object {
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            }

        fun encode(list: IpCameraList): String = json.encodeToString(serializer(), list)

        /** Null if the text is not a camera list (a damaged file never crashes the app). */
        fun decode(text: String): IpCameraList? =
            try {
                json.decodeFromString(serializer(), text)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
    }
}
