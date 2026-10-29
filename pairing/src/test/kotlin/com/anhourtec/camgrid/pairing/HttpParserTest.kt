package com.anhourtec.camgrid.pairing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val TV_IP = "10.20.30.40" // check-redaction:allow fake test address

class HttpParserTest {
    private fun parse(raw: String) = HttpParser.read(raw.byteInputStream(Charsets.ISO_8859_1))

    @Test
    fun `parses a form post with cookies and query`() {
        val body = "email=a%40b.example&password=p%26w&captcha=1234"
        val request =
            parse(
                "POST /login?x=1 HTTP/1.1\r\nHost: $TV_IP:8743\r\nCookie: lang=jp; pair=abc\r\n" +
                    "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: ${body.length}\r\n\r\n$body",
            )
        assertEquals("POST", request.method)
        assertEquals("/login", request.path)
        assertEquals("1", request.queryParam("x"))
        assertEquals("abc", request.cookie("pair"))
        assertEquals("jp", request.cookie("lang"))
        assertEquals(mapOf("email" to "a@b.example", "password" to "p&w", "captcha" to "1234"), request.form())
    }

    @Test
    fun `rejects oversized heads and bodies, chunked bodies and bad lengths`() {
        assertEquals(STATUS_TOO_LARGE, assertThrows<HttpException> { parse("GET / HTTP/1.1\r\nX: ${"a".repeat(9000)}\r\n\r\n") }.status)
        assertEquals(STATUS_TOO_LARGE, assertThrows<HttpException> { parse("POST / HTTP/1.1\r\nContent-Length: 5000\r\n\r\n") }.status)
        assertEquals(
            STATUS_BAD_REQUEST,
            assertThrows<HttpException> { parse("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n") }.status,
        )
        assertEquals(STATUS_BAD_REQUEST, assertThrows<HttpException> { parse("POST / HTTP/1.1\r\nContent-Length: -1\r\n\r\n") }.status)
        assertEquals(STATUS_BAD_REQUEST, assertThrows<HttpException> { parse("POST / HTTP/1.1\r\nContent-Length: 10\r\n\r\nabc") }.status)
        assertEquals(STATUS_BAD_REQUEST, assertThrows<HttpException> { parse("GARBAGE\r\n\r\n") }.status)
        assertEquals(
            STATUS_BAD_REQUEST,
            assertThrows<HttpException> { parse("GET / HTTP/1.1\r\nHost: a\r\nHost: b\r\n\r\n") }.status,
        )
    }

    @Test
    fun `rejects malformed or non-form bodies`() {
        val bad = parse("POST / HTTP/1.1\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: 3\r\n\r\na=%")
        assertEquals(STATUS_BAD_REQUEST, assertThrows<HttpException> { bad.form() }.status)
        val json = parse("POST / HTTP/1.1\r\nContent-Type: application/json\r\nContent-Length: 2\r\n\r\n{}")
        assertEquals(STATUS_UNSUPPORTED_MEDIA, assertThrows<HttpException> { json.form() }.status)
    }

    @Test
    fun `escapes HTML`() {
        assertEquals("&lt;img src=x onerror=&quot;a&#39;&quot;&gt;&amp;", Pages.esc("<img src=x onerror=\"a'\">&"))
    }
}
