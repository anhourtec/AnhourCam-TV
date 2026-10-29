package com.anhourtec.camgrid.pairing

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PairingServerTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val state = PairingState()
    private val signedIn = CountDownLatch(1)
    private val logins = mutableListOf<Triple<String, String, String>>()
    private val backend =
        object : PairingBackend {
            override fun captcha() = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

            override fun login(
                user: String,
                password: String,
                captcha: String,
            ): LoginOutcome {
                logins += Triple(user, password, captcha)
                return if (captcha == "1234") LoginOutcome.Success else LoginOutcome.Failed("Wrong <code>")
            }
        }
    private val server =
        PairingServer(loopback, 0, backend = backend, state = state, onSignedIn = { signedIn.countDown() }).also { it.start() }
    private val host get() = "${loopback.hostAddress}:${server.port}"

    @AfterEach
    fun stop() = server.close()

    @Test
    fun `full flow - code, captcha, failed and successful login`() {
        val home = request("GET", "/")
        assertTrue(home.status == 200 && home.body.contains("name=\"code\""))
        assertTrue(home.headers.contains("content-security-policy: default-src 'none'"))

        assertTrue(request("POST", "/pair", form = "code=000000x").body.contains("doesn&#39;t match"))
        val paired = request("POST", "/pair", form = "code=${state.code}")
        assertEquals(303, paired.status)
        val cookie = Regex("set-cookie: (pair=[0-9a-f]{64})").find(paired.headers)!!.groupValues[1]
        assertTrue(paired.headers.contains("httponly") && paired.headers.contains("samesite=strict"))

        assertTrue(request("GET", "/", cookie = cookie).body.contains("name=\"password\""))
        assertTrue(request("GET", "/captcha", cookie = cookie).headers.contains("content-type: image/png"))

        val failed = request("POST", "/login", cookie = cookie, form = "email=u%40x.example&password=pw&captcha=9999")
        assertTrue(failed.body.contains("Wrong &lt;code&gt;"))
        assertTrue(failed.body.contains("value=\"u@x.example\""))

        val ok = request("POST", "/login", cookie = cookie, form = "email=u%40x.example&password=pw&captcha=1234")
        assertTrue(ok.body.contains("You&#39;re signed in"))
        assertTrue(signedIn.await(2, TimeUnit.SECONDS))
        assertEquals(Triple("u@x.example", "pw", "1234"), logins.last())
        assertFalse(state.isPaired(cookie.substringAfter('='), loopback.hostAddress))
    }

    @Test
    fun `captcha and login need a paired client`() {
        assertEquals(403, request("GET", "/captcha").status)
        assertEquals(403, request("GET", "/captcha", cookie = "pair=${"a".repeat(64)}").status)
        assertEquals(303, request("POST", "/login", form = "email=a&password=b&captcha=1234").status)
        assertTrue(logins.isEmpty())
    }

    @Test
    fun `rejects foreign host names, unknown paths and wrong methods`() {
        assertEquals(403, request("GET", "/", hostHeader = "attacker.example:${server.port}").status)
        assertEquals(404, request("GET", "/admin").status)
        assertEquals(405, request("PUT", "/captcha").status)
    }

    @Test
    fun `serves the favicon and sends stale steps back to the start`() {
        val icon = request("GET", "/favicon.svg")
        assertTrue(icon.headers.contains("content-type: image/svg+xml") && icon.body.startsWith("<svg"))
        assertEquals(200, request("GET", "/favicon.ico").status)
        assertEquals(303, request("GET", "/login").status)
        assertEquals(303, request("GET", "/pair").status)
        assertTrue(request("GET", "/").body.contains("rel=\"icon\" href=\"/favicon.svg\""))
    }

    @Test
    fun `language switch sets a cookie and translates the page`() {
        val jp = request("GET", "/?lang=jp")
        assertTrue(jp.headers.contains("set-cookie: lang=jp"))
        assertTrue(jp.body.contains("テレビに表示されたコードを入力"))
        assertTrue(request("GET", "/", cookie = "lang=py").body.contains("<html lang=\"ru\">"))
        assertTrue(request("GET", "/?lang=xx").body.contains("<html lang=\"en\">"))
    }

    private class Response(
        val status: Int,
        val headers: String,
        val body: String,
    )

    private fun request(
        method: String,
        path: String,
        cookie: String? = null,
        form: String? = null,
        hostHeader: String = host,
    ): Response =
        Socket(loopback, server.port).use { socket ->
            val req =
                buildString {
                    append("$method $path HTTP/1.1\r\nHost: $hostHeader\r\n")
                    cookie?.let { append("Cookie: $it\r\n") }
                    form?.let { append("Content-Type: application/x-www-form-urlencoded\r\nContent-Length: ${it.length}\r\n") }
                    append("\r\n")
                    form?.let { append(it) }
                }
            socket.getOutputStream().write(req.toByteArray(Charsets.UTF_8))
            val raw = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            val head = raw.substringBefore("\r\n\r\n")
            Response(head.substringAfter(' ').substringBefore(' ').toInt(), head.lowercase(), raw.substringAfter("\r\n\r\n"))
        }
}
