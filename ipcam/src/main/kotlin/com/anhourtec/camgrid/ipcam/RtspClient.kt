package com.anhourtec.camgrid.ipcam

import com.anhourtec.camgrid.client.media.FrameSink
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI

/** An RTSP request failed (status, unsupported stream, or no answer). */
class RtspException(
    message: String,
    val status: Int = 0,
) : IOException(message)

/**
 * Plays the video of one RTSP stream over TCP (RTP interleaved on the RTSP connection, RFC 2326
 * §10.12), so it works without opening UDP ports. [play] blocks on the calling thread, handing whole
 * frames to [sink], until [close] or an error. Credentials are sent only after a 401 challenge.
 */
class RtspClient(
    url: String,
    user: String?,
    password: String?,
    private val sink: FrameSink,
    private val width: Int = DEFAULT_WIDTH,
    private val height: Int = DEFAULT_HEIGHT,
) : Closeable {
    private val target = RtspUrl.parse(url, user, password)
    private val auth = target.user?.let { RtspAuth(it, target.password.orEmpty()) }
    private val socket = Socket()
    private lateinit var input: InputStream
    private lateinit var output: OutputStream
    private var cseq = 0
    private var session: String? = null
    private var keepAliveMs = DEFAULT_KEEP_ALIVE_MS

    @Volatile private var closed = false

    /** Connects and negotiates; returns the video track that [play] will stream. */
    fun open(): VideoTrack {
        socket.connect(InetSocketAddress(target.host, target.port), CONNECT_TIMEOUT_MS)
        socket.soTimeout = READ_TIMEOUT_MS
        socket.tcpNoDelay = true
        input = BufferedInputStream(socket.getInputStream(), BUFFER_BYTES)
        output = socket.getOutputStream()
        request("OPTIONS", target.url)
        val describe = request("DESCRIBE", target.url, "Accept: application/sdp\r\n")
        val base = describe.headers["content-base"] ?: describe.headers["content-location"] ?: target.url
        val track = Sdp.videoTrack(describe.body, base) ?: throw RtspException("no H.264/H.265 video in this stream")
        val setup = request("SETUP", track.control, "Transport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n")
        val sessionHeader = setup.headers["session"] ?: throw RtspException("no session from camera")
        session = sessionHeader.substringBefore(';').trim()
        Regex("""timeout=(\d+)""").find(sessionHeader)?.let {
            keepAliveMs = (it.groupValues[1].toLong() * MS_PER_S / 2).coerceIn(MIN_KEEP_ALIVE_MS, DEFAULT_KEEP_ALIVE_MS)
        }
        request("PLAY", base, "Range: npt=0.000-\r\n")
        currentTrack = track
        return track
    }

    private var currentTrack: VideoTrack? = null

    /** Streams until [close]; throws [IOException] when the camera stops or the network drops. */
    fun play() {
        val track = currentTrack ?: throw RtspException("not open")
        val rtp = RtpDepacketizer(track.hevc, track.parameterSets, width, height, sink)
        var packet = ByteArray(PACKET_BYTES)
        var lastKeepAlive = System.currentTimeMillis()
        while (!closed) {
            val first =
                try {
                    input.read()
                } catch (_: SocketTimeoutException) {
                    throw RtspException("no video from camera")
                }
            if (first < 0) connectionClosed()
            if (first == INTERLEAVED_MARK) {
                val channel = input.read()
                val length = (input.read() shl 8) or input.read()
                if (length < 0) connectionClosed()
                if (length > packet.size) packet = ByteArray(length)
                readFully(packet, length)
                if (channel == VIDEO_CHANNEL) rtp.onPacket(packet, 0, length)
            } else {
                // A reply to a keep-alive, interleaved with media: read and ignore it.
                readResponse(first)
            }
            val now = System.currentTimeMillis()
            if (now - lastKeepAlive > keepAliveMs) {
                lastKeepAlive = now
                send("GET_PARAMETER", target.url, "")
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { if (session != null) send("TEARDOWN", target.url, "") }
        runCatching { socket.close() }
    }

    private fun request(
        method: String,
        uri: String,
        extra: String = "",
    ): Response {
        var response = exchange(method, uri, extra)
        if (response.status == UNAUTHORIZED && auth != null) {
            val challenges = response.allHeaders.filter { it.first == "www-authenticate" }.map { it.second }
            val accepted =
                challenges.firstOrNull { it.startsWith("Digest", true) && auth.accept(it) } ?: challenges.firstOrNull { auth.accept(it) }
            if (accepted != null) response = exchange(method, uri, extra)
        }
        if (response.status == UNAUTHORIZED) throw RtspException("wrong user name or password", UNAUTHORIZED)
        if (response.status !in OK_RANGE) throw RtspException("$method failed: ${response.status}", response.status)
        return response
    }

    private fun exchange(
        method: String,
        uri: String,
        extra: String,
    ): Response {
        send(method, uri, extra)
        while (true) {
            val first = input.read()
            if (first < 0) connectionClosed()
            if (first == INTERLEAVED_MARK) {
                // Media can arrive before the PLAY reply: skip it.
                input.read()
                val length = (input.read() shl 8) or input.read()
                skip(length)
                continue
            }
            return readResponse(first)
        }
    }

    private fun send(
        method: String,
        uri: String,
        extra: String,
    ) {
        val text =
            buildString {
                append("$method $uri RTSP/1.0\r\n")
                append("CSeq: ${++cseq}\r\n")
                append("User-Agent: $USER_AGENT\r\n")
                session?.let { append("Session: $it\r\n") }
                auth?.takeIf { it.ready }?.header(method, uri)?.let { append("Authorization: $it\r\n") }
                append(extra)
                append("\r\n")
            }
        synchronized(output) {
            output.write(text.toByteArray(Charsets.ISO_8859_1))
            output.flush()
        }
    }

    private fun readResponse(first: Int): Response {
        val head = StringBuilder().append(first.toChar())
        while (!head.endsWith("\r\n\r\n")) {
            val b = input.read()
            if (b < 0) connectionClosed()
            head.append(b.toChar())
            if (head.length > MAX_HEADER_CHARS) badReply()
        }
        val lines = head.trim().split("\r\n")
        val status =
            lines
                .first()
                .split(' ')
                .getOrNull(1)
                ?.toIntOrNull() ?: badReply()
        val headers = lines.drop(1).map { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        val length = headers.firstOrNull { it.first == "content-length" }?.second?.toIntOrNull() ?: 0
        if (length > MAX_BODY_BYTES) badReply()
        val body = ByteArray(length)
        readFully(body, length)
        return Response(status, headers, String(body, Charsets.UTF_8))
    }

    // Not InputStream.skipNBytes: that needs Android 13; the app runs on Android 7.1+.
    private fun skip(length: Int) {
        var left = length
        while (left > 0) {
            val n = input.skip(left.toLong()).toInt()
            if (n <= 0) {
                if (input.read() < 0) connectionClosed()
                left--
            } else {
                left -= n
            }
        }
    }

    private fun readFully(
        into: ByteArray,
        length: Int,
    ) {
        var read = 0
        while (read < length) {
            val n = input.read(into, read, length - read)
            if (n < 0) connectionClosed()
            read += n
        }
    }

    private fun badReply(): Nothing = throw RtspException("bad reply from camera")

    private fun connectionClosed(): Nothing = throw RtspException("camera closed the connection")

    private class Response(
        val status: Int,
        val allHeaders: List<Pair<String, String>>,
        val body: String,
    ) {
        val headers: Map<String, String> = allHeaders.toMap()
    }

    companion object {
        const val DEFAULT_WIDTH = 1920
        const val DEFAULT_HEIGHT = 1080
        private const val USER_AGENT = "AnhourCamTV"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val BUFFER_BYTES = 64 * 1024
        private const val PACKET_BYTES = 64 * 1024
        private const val INTERLEAVED_MARK = '$'.code
        private const val VIDEO_CHANNEL = 0
        private const val UNAUTHORIZED = 401
        private val OK_RANGE = 200..299
        private const val MAX_HEADER_CHARS = 16 * 1024
        private const val MAX_BODY_BYTES = 256 * 1024
        private const val MS_PER_S = 1000L
        private const val DEFAULT_KEEP_ALIVE_MS = 30_000L
        private const val MIN_KEEP_ALIVE_MS = 5_000L
    }
}

/** An rtsp:// URL with credentials taken out (they go in the Authorization header, never the URL). */
data class RtspUrl(
    val url: String,
    val host: String,
    val port: Int,
    val user: String?,
    val password: String?,
) {
    companion object {
        private const val DEFAULT_PORT = 554

        fun parse(
            url: String,
            user: String?,
            password: String?,
        ): RtspUrl {
            val uri = runCatching { URI(url.trim()) }.getOrElse { throw RtspException("not a valid address") }
            if (!uri.scheme.equals("rtsp", ignoreCase = true) || uri.host == null) throw RtspException("address must start with rtsp://")
            val info = uri.rawUserInfo?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
            val port = if (uri.port > 0) uri.port else DEFAULT_PORT
            val clean = URI("rtsp", null, uri.host, port, uri.path?.ifEmpty { "/" } ?: "/", uri.query, null).toString()
            return RtspUrl(
                url = clean,
                host = uri.host,
                port = port,
                user = user?.takeIf { it.isNotEmpty() } ?: info?.substringBefore(':')?.takeIf { it.isNotEmpty() },
                password = password ?: info?.substringAfter(':', ""),
            )
        }
    }
}
