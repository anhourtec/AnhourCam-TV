package com.anhourtec.camgrid.pairing

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** A parsed request. Only what the pairing pages need; anything unusual is rejected. */
class HttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: ByteArray,
    val query: String = "",
) {
    /** A query parameter, undecoded; only used for short ASCII values like `lang=en`. */
    fun queryParam(name: String): String? =
        query
            .split('&')
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')

    fun header(name: String): String? = headers[name.lowercase()]

    fun cookie(name: String): String? =
        header("cookie")
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')

    /** Decodes an `application/x-www-form-urlencoded` body. Throws [HttpException] (400) if malformed. */
    fun form(): Map<String, String> {
        if (header("content-type")?.substringBefore(';')?.trim() != FORM_TYPE) throw HttpException(STATUS_UNSUPPORTED_MEDIA)
        return try {
            String(body, StandardCharsets.UTF_8)
                .split('&')
                .filter { it.isNotEmpty() }
                .associate { pair ->
                    val key = URLDecoder.decode(pair.substringBefore('='), UTF8)
                    val value = URLDecoder.decode(pair.substringAfter('=', ""), UTF8)
                    key to value
                }
        } catch (_: IllegalArgumentException) {
            throw HttpException(STATUS_BAD_REQUEST)
        }
    }

    companion object {
        const val FORM_TYPE = "application/x-www-form-urlencoded"

        // Charset-name overload: the Charset overload needs Android 13+.
        private const val UTF8 = "UTF-8"
    }
}

class HttpResponse(
    val status: Int,
    val contentType: String = "text/html; charset=utf-8",
    val body: ByteArray = ByteArray(0),
    val extraHeaders: List<Pair<String, String>> = emptyList(),
) {
    fun writeTo(out: OutputStream) {
        val head =
            buildString {
                append("HTTP/1.1 ")
                    .append(status)
                    .append(' ')
                    .append(reason(status))
                    .append("\r\n")
                append("Content-Type: ").append(contentType).append("\r\n")
                append("Content-Length: ").append(body.size).append("\r\n")
                append("Connection: close\r\n")
                SECURITY_HEADERS.forEach { (k, v) -> append(k).append(": ").append(v).append("\r\n") }
                extraHeaders.forEach { (k, v) -> append(k).append(": ").append(v).append("\r\n") }
                append("\r\n")
            }
        out.write(head.toByteArray(StandardCharsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    companion object {
        fun html(
            status: Int,
            page: String,
            headers: List<Pair<String, String>> = emptyList(),
        ) = HttpResponse(status, body = page.toByteArray(StandardCharsets.UTF_8), extraHeaders = headers)

        fun redirect(
            location: String,
            headers: List<Pair<String, String>> = emptyList(),
        ) = HttpResponse(STATUS_SEE_OTHER, extraHeaders = listOf("Location" to location) + headers)

        /** No scripts anywhere; pages are plain forms. */
        private val SECURITY_HEADERS =
            listOf(
                "Cache-Control" to "no-store",
                "X-Content-Type-Options" to "nosniff",
                "X-Frame-Options" to "DENY",
                "Referrer-Policy" to "no-referrer",
                "Content-Security-Policy" to
                    "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; " +
                    "form-action 'self'; base-uri 'none'; frame-ancestors 'none'",
            )

        private fun reason(status: Int) =
            when (status) {
                STATUS_OK -> "OK"
                STATUS_SEE_OTHER -> "See Other"
                STATUS_BAD_REQUEST -> "Bad Request"
                STATUS_FORBIDDEN -> "Forbidden"
                STATUS_NOT_FOUND -> "Not Found"
                STATUS_METHOD_NOT_ALLOWED -> "Method Not Allowed"
                STATUS_TOO_LARGE -> "Payload Too Large"
                STATUS_UNSUPPORTED_MEDIA -> "Unsupported Media Type"
                STATUS_TOO_MANY -> "Too Many Requests"
                else -> "Error"
            }
    }
}

class HttpException(
    val status: Int,
) : Exception("HTTP $status")

const val STATUS_OK = 200
const val STATUS_SEE_OTHER = 303
const val STATUS_BAD_REQUEST = 400
const val STATUS_FORBIDDEN = 403
const val STATUS_NOT_FOUND = 404
const val STATUS_METHOD_NOT_ALLOWED = 405
const val STATUS_TOO_LARGE = 413
const val STATUS_UNSUPPORTED_MEDIA = 415
const val STATUS_TOO_MANY = 429

/** Strict HTTP/1.1 request reader with hard size limits. One request per connection. */
object HttpParser {
    const val MAX_HEAD_BYTES = 8 * 1024
    const val MAX_BODY_BYTES = 4 * 1024
    private const val MAX_HEADERS = 50
    private const val REQUEST_LINE_PARTS = 3
    private val UNIQUE_HEADERS = listOf("host", "content-length")

    /** Rejects the request with [status] unless [condition] holds. */
    private fun check(
        condition: Boolean,
        status: Int = STATUS_BAD_REQUEST,
    ) {
        if (!condition) throw HttpException(status)
    }

    private const val CR = '\r'.code
    private const val LF = '\n'.code

    fun read(input: InputStream): HttpRequest {
        val lines = readHead(input)
        val requestLine = lines.firstOrNull()?.split(' ').orEmpty()
        check(requestLine.size == REQUEST_LINE_PARTS && requestLine[2].startsWith("HTTP/1."))
        check(lines.size - 1 <= MAX_HEADERS)
        val pairs =
            lines.drop(1).map { line ->
                val colon = line.indexOf(':')
                check(colon > 0)
                line.substring(0, colon).trim().lowercase() to line.substring(colon + 1).trim()
            }
        // Two Host or Content-Length headers are never legitimate; refuse rather than guess.
        check(UNIQUE_HEADERS.all { name -> pairs.count { it.first == name } <= 1 })
        val headers = pairs.toMap()
        check(!headers.containsKey("transfer-encoding"))
        val length = headers["content-length"]?.let { it.toIntOrNull() ?: -1 } ?: 0
        check(length >= 0)
        check(length <= MAX_BODY_BYTES, STATUS_TOO_LARGE)
        val body = readExactly(input, length)
        val target = requestLine[1]
        return HttpRequest(requestLine[0], target.substringBefore('?'), headers, body, target.substringAfter('?', ""))
    }

    // InputStream.readNBytes needs Android 13+; read in a loop instead.
    private fun readExactly(
        input: InputStream,
        length: Int,
    ): ByteArray {
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) throw HttpException(STATUS_BAD_REQUEST)
            read += n
        }
        return body
    }

    private fun readHead(input: InputStream): List<String> {
        val lines = mutableListOf<String>()
        val line = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw HttpException(STATUS_BAD_REQUEST)
            if (++total > MAX_HEAD_BYTES) throw HttpException(STATUS_TOO_LARGE)
            when (b) {
                CR -> {
                    continue
                }

                // tolerate bare LF line endings too

                LF -> {
                    if (line.size() == 0) return lines
                    // Not toString(Charset): that needs Android 13; TVs run Android 7.1+.
                    lines += String(line.toByteArray(), StandardCharsets.ISO_8859_1)
                    line.reset()
                }

                else -> {
                    line.write(b)
                }
            }
        }
    }
}
