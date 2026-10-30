package com.anhourtec.camgrid.ipcam

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/** One stream a camera offers (main stream, sub-stream, …). */
data class OnvifProfile(
    val token: String,
    val name: String,
    val width: Int,
    val height: Int,
    val encoding: String,
)

/** What we need to play a camera: its profiles with their RTSP and snapshot addresses. */
data class OnvifStream(
    val profile: OnvifProfile,
    val rtspUrl: String,
    val snapshotUrl: String?,
)

/**
 * Minimal ONVIF client (Device + Media services) to find a camera's RTSP addresses from just its IP
 * and login. Requests are signed with a WS-Security UsernameToken digest; the camera's own clock
 * is read first, because cameras reject tokens whose time is too far from theirs.
 */
class OnvifClient(
    private val http: OkHttpClient,
    private val deviceUrl: String,
    private val user: String,
    private val password: String,
) {
    private var clockOffsetMs = 0L
    private val digest = DigestHttp(http, user, password)

    /** All H.264/H.265 profiles, largest first, with their stream addresses. */
    fun streams(): List<OnvifStream> {
        syncClock()
        val media = mediaUrl()
        val profiles = profiles(call(media, GET_PROFILES))
        return profiles.mapNotNull { profile ->
            val uri = tag(call(media, getStreamUri(profile.token)), "Uri") ?: return@mapNotNull null
            val snapshot = runCatching { tag(call(media, getSnapshotUri(profile.token)), "Uri") }.getOrNull()
            OnvifStream(profile, unescape(uri), snapshot?.let(::unescape))
        }
    }

    private fun syncClock() {
        val reply = runCatching { post(deviceUrl, envelope(GET_DATE_TIME, secured = false)) }.getOrNull() ?: return
        val utc = reply.substringAfter("UTCDateTime", "")
        if (utc.isEmpty()) return
        val date = listOf("Year", "Month", "Day").map { tag(utc, it)?.toIntOrNull() ?: return }
        val time = listOf("Hour", "Minute", "Second").map { tag(utc, it)?.toIntOrNull() ?: return }
        cameraEpochSeconds(date, time)?.let { clockOffsetMs = it * MS_PER_S - System.currentTimeMillis() }
    }

    private fun mediaUrl(): String {
        val reply = call(deviceUrl, GET_CAPABILITIES)
        val media = reply.substringAfter("Media>", "").takeIf { it.isNotEmpty() }?.let { tag(it, "XAddr") }
        // Cameras behind NAT or with a wrong configured IP report an unreachable host: keep ours.
        return media?.let { sameHost(it) } ?: deviceUrl.replace("device_service", "media_service")
    }

    private fun sameHost(url: String): String {
        val reported = java.net.URI(url)
        val ours = java.net.URI(deviceUrl)
        return java.net.URI(ours.scheme, null, ours.host, ours.port, reported.path, reported.query, null).toString()
    }

    private fun call(
        url: String,
        body: String,
    ): String = post(url, envelope(body, secured = true))

    private fun post(
        url: String,
        envelope: String,
    ): String {
        val request =
            Request
                .Builder()
                .url(url)
                .post(envelope.toRequestBody(SOAP))
                .build()
        // Many cameras require HTTP Digest on top of (or instead of) WS-Security.
        digest.execute(request).use {
            val text = it.body.string()
            if (text.contains("NotAuthorized", true) || it.code == UNAUTHORIZED) throw OnvifException("wrong user name or password")
            if (!it.isSuccessful) throw OnvifException("camera answered ${it.code}")
            return text
        }
    }

    private fun envelope(
        body: String,
        secured: Boolean,
    ): String {
        val header = if (secured) "<s:Header>${security()}</s:Header>" else ""
        return """<?xml version="1.0" encoding="UTF-8"?><s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" """ +
            """xmlns:tds="http://www.onvif.org/ver10/device/wsdl" xmlns:trt="http://www.onvif.org/ver10/media/wsdl" """ +
            """xmlns:tt="http://www.onvif.org/ver10/schema">$header<s:Body>$body</s:Body></s:Envelope>"""
    }

    private fun security(): String {
        val nonce = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
        val created = utcTimestamp(System.currentTimeMillis() + clockOffsetMs)
        val digest = passwordDigest(nonce, created, password)
        return """<Security s:mustUnderstand="1" xmlns="$WSSE">""" +
            "<UsernameToken><Username>${escape(user)}</Username>" +
            """<Password Type="$PASSWORD_DIGEST">$digest</Password>""" +
            """<Nonce EncodingType="$BASE64_BINARY">${nonce.toByteString().base64()}</Nonce>""" +
            """<Created xmlns="$WSU">$created</Created>""" +
            "</UsernameToken></Security>"
    }

    companion object {
        private val SOAP = "application/soap+xml; charset=utf-8".toMediaType()
        private const val UNAUTHORIZED = 401
        private const val NONCE_BYTES = 16
        private const val GET_DATE_TIME = "<tds:GetSystemDateAndTime/>"
        private const val GET_CAPABILITIES = "<tds:GetCapabilities><tds:Category>Media</tds:Category></tds:GetCapabilities>"
        private const val GET_PROFILES = "<trt:GetProfiles/>"
        private const val HTTP_PORT = 80
        private const val OASIS = "http://docs.oasis-open.org/wss/2004/01/"
        private const val WSSE = OASIS + "oasis-200401-wss-wssecurity-secext-1.0.xsd"
        private const val WSU = OASIS + "oasis-200401-wss-wssecurity-utility-1.0.xsd"
        private const val PASSWORD_DIGEST = OASIS + "oasis-200401-wss-username-token-profile-1.0#PasswordDigest"
        private const val BASE64_BINARY = OASIS + "oasis-200401-wss-soap-message-security-1.0#Base64Binary"

        fun deviceServiceUrl(
            host: String,
            port: Int = HTTP_PORT,
        ): String = "http://$host${if (port == HTTP_PORT) "" else ":$port"}/onvif/device_service"

        private const val MS_PER_S = 1000L
        private const val SECONDS_PER_HOUR = 3600L
        private const val SECONDS_PER_MINUTE = 60L

        /**
         * The camera's clock as epoch seconds, by arithmetic rather than strict parsing: some cameras
         * report fields out of range (e.g. minute -15 from a broken time-zone offset). Null if the
         * date itself is invalid.
         */
        fun cameraEpochSeconds(
            date: List<Int>,
            time: List<Int>,
        ): Long? =
            runCatching {
                // java.util calendar, not java.time: java.time needs Android 8; TVs run Android 7.1+.
                val day =
                    GregorianCalendar(UTC).apply {
                        isLenient = false
                        clear()
                        set(date[0], date[1] - 1, date[2])
                    }
                day.timeInMillis / MS_PER_S + time[0] * SECONDS_PER_HOUR + time[1] * SECONDS_PER_MINUTE + time[2]
            }.getOrNull()

        private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

        /** WS-Security `Created`: ISO-8601 UTC to the second, e.g. 2026-10-29T12:00:00Z. */
        fun utcTimestamp(epochMs: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = UTC }.format(Date(epochMs))

        /** WS-Security: Base64(SHA-1(nonce + created + password)). */
        fun passwordDigest(
            nonce: ByteArray,
            created: String,
            password: String,
        ): String {
            val sha = MessageDigest.getInstance("SHA-1")
            sha.update(nonce)
            sha.update(created.toByteArray())
            sha.update(password.toByteArray())
            return sha.digest().toByteString().base64()
        }

        private fun getStreamUri(token: String) =
            "<trt:GetStreamUri><trt:StreamSetup><tt:Stream>RTP-Unicast</tt:Stream><tt:Transport><tt:Protocol>RTSP</tt:Protocol>" +
                "</tt:Transport></trt:StreamSetup><trt:ProfileToken>${escape(token)}</trt:ProfileToken></trt:GetStreamUri>"

        private fun getSnapshotUri(token: String) =
            "<trt:GetSnapshotUri><trt:ProfileToken>${escape(token)}</trt:ProfileToken></trt:GetSnapshotUri>"

        /** Profiles with a video encoder, largest first. Namespace prefixes vary by vendor, so match local names. */
        fun profiles(xml: String): List<OnvifProfile> =
            Regex("""<(?:\w+:)?Profiles\b([^>]*)>(.*?)</(?:\w+:)?Profiles>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(xml)
                .mapNotNull { m ->
                    val token = Regex("""token="([^"]+)"""").find(m.groupValues[1])?.groupValues?.get(1) ?: return@mapNotNull null
                    val body = m.groupValues[2]
                    val video = body.substringAfter("VideoEncoderConfiguration", "")
                    if (video.isEmpty()) return@mapNotNull null
                    val encoding = tag(video, "Encoding").orEmpty()
                    val resolution = video.substringAfter("Resolution", "")
                    OnvifProfile(
                        token = token,
                        name = tag(body, "Name") ?: token,
                        width = tag(resolution, "Width")?.toIntOrNull() ?: 0,
                        height = tag(resolution, "Height")?.toIntOrNull() ?: 0,
                        encoding = encoding,
                    )
                }.filter { it.encoding.uppercase() in setOf("H264", "H265", "HEVC", "") }
                .sortedByDescending { it.width * it.height }
                .toList()

        fun tag(
            xml: String,
            name: String,
        ): String? =
            Regex("""<(?:\w+:)?$name(?:\s[^>]*)?>([^<]*)</(?:\w+:)?$name>""")
                .find(xml)
                ?.groupValues
                ?.get(1)
                ?.trim()

        private fun unescape(s: String) =
            s
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")

        private fun escape(s: String) =
            s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
    }
}

class OnvifException(
    message: String,
) : IOException(message)
