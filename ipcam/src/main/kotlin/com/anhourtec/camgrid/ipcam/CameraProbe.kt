package com.anhourtec.camgrid.ipcam

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI

/** What a quick connection test found. */
data class ProbeResult(
    val hevc: Boolean,
    val usedBasic: Boolean,
)

/** Checks a camera before it is saved, and fetches still images. Blocking: call off the main thread. */
class CameraProbe(
    private val http: OkHttpClient,
) {
    /** Opens [url] with the login and closes it again: proves the address, path and password. */
    fun test(
        url: String,
        user: String,
        password: String,
        allowBasic: Boolean,
    ): ProbeResult =
        withRtspPortFallback(url) { candidate ->
            RtspClient(candidate, user.ifEmpty { null }, password.ifEmpty { null }, { _, _ -> }, allowBasic = allowBasic).use {
                val track = it.open()
                ProbeResult(track.hevc, it.usedBasic)
            }
        }

    /** The camera's still image as JPEG bytes, or null if it has none or refuses. */
    fun snapshot(
        url: String,
        user: String,
        password: String,
    ): ByteArray? {
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .build()
        return DigestHttp(http, user, password).execute(request).use {
            val image = it.isSuccessful && it.header("Content-Type").orEmpty().startsWith("image/")
            val small = it.body.contentLength() <= MAX_SNAPSHOT_BYTES
            if (image && small) it.body.bytes().takeIf { bytes -> bytes.size <= MAX_SNAPSHOT_BYTES } else null
        }
    }

    companion object {
        private const val MAX_SNAPSHOT_BYTES = 4L * 1024 * 1024
        private const val RTSP_PORT = 554

        /**
         * Some cameras report their RTSP address with the wrong port (e.g. their web port). Try the
         * address as given, then the same address on 554, unless the camera rejected the login.
         */
        fun <T> withRtspPortFallback(
            url: String,
            block: (String) -> T,
        ): T =
            try {
                block(url)
            } catch (e: IOException) {
                val alternative = onDefaultPort(url)
                if (alternative == null || (e is RtspException && e.status == DigestHttp.UNAUTHORIZED)) throw e
                block(alternative)
            }

        /** The same RTSP URL on port 554, or null if it already uses it. */
        fun onDefaultPort(url: String): String? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            if (uri.port == -1 || uri.port == RTSP_PORT) return null
            return URI(uri.scheme, uri.rawUserInfo, uri.host, RTSP_PORT, uri.path, uri.query, null).toString()
        }
    }
}
