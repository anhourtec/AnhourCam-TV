package com.anhourtec.camgrid.ipcam

import java.util.Base64

/** The video track of an RTSP session description (RFC 4566), as far as we need it. */
data class VideoTrack(
    val hevc: Boolean,
    /** Absolute control URL for SETUP. */
    val control: String,
    /** SPS/PPS (H.264) or VPS/SPS/PPS (H.265) from the fmtp line, without start codes. */
    val parameterSets: List<ByteArray>,
    val payloadType: Int,
)

object Sdp {
    /** Finds the first H.264/H.265 video track; null if the camera offers neither. */
    fun videoTrack(
        sdp: String,
        baseUrl: String,
    ): VideoTrack? {
        var sessionControl: String? = null
        var media: MutableList<String>? = null
        val tracks = mutableListOf<List<String>>()
        for (raw in sdp.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("m=") -> {
                    media = mutableListOf(line).also { tracks += it }
                }

                media != null -> {
                    media += line
                }

                line.startsWith("a=control:") -> {
                    sessionControl = line.substringAfter("a=control:")
                }
            }
        }
        val base = sessionControl?.takeIf { it != "*" }?.let { resolve(baseUrl, it) } ?: baseUrl
        return tracks.firstNotNullOfOrNull { track(it, base) }
    }

    private fun track(
        lines: List<String>,
        base: String,
    ): VideoTrack? {
        if (!lines.first().startsWith("m=video")) return null
        val payloadType =
            lines
                .first()
                .split(' ')
                .getOrNull(FORMAT_FIELD)
                ?.toIntOrNull() ?: return null
        val encoding =
            lines
                .firstOrNull { it.startsWith("a=rtpmap:$payloadType ") }
                ?.substringAfter(' ')
                ?.substringBefore('/')
                ?.uppercase()
        val hevc =
            when (encoding) {
                "H265", "HEVC" -> true
                "H264" -> false
                else -> return null
            }
        val fmtp = lines.firstOrNull { it.startsWith("a=fmtp:$payloadType ") }?.substringAfter(' ').orEmpty()
        val params =
            fmtp
                .split(';')
                .map { it.trim() }
                .filter { '=' in it }
                .associate { it.substringBefore('=').lowercase() to it.substringAfter('=') }
        val sets =
            if (hevc) {
                listOf("sprop-vps", "sprop-sps", "sprop-pps").flatMap { decode(params[it]) }
            } else {
                decode(params["sprop-parameter-sets"])
            }
        val control = lines.firstOrNull { it.startsWith("a=control:") }?.substringAfter("a=control:") ?: base
        return VideoTrack(hevc, resolve(base, control), sets, payloadType)
    }

    private fun decode(value: String?): List<ByteArray> =
        value
            ?.split(',')
            ?.mapNotNull { runCatching { Base64.getDecoder().decode(it.trim()) }.getOrNull() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    /** Control attributes may be absolute, "*", or relative to the base URL. */
    fun resolve(
        base: String,
        control: String,
    ): String =
        when {
            control == "*" -> base
            control.startsWith("rtsp://", ignoreCase = true) || control.startsWith("rtsps://", ignoreCase = true) -> control
            else -> base.trimEnd('/') + "/" + control.trimStart('/')
        }

    private const val FORMAT_FIELD = 3
}
