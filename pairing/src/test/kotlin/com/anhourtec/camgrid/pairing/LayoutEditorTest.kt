package com.anhourtec.camgrid.pairing

import com.anhourtec.camgrid.layouts.CameraRef
import com.anhourtec.camgrid.layouts.Layout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.Socket
import java.net.URLEncoder

class LayoutEditorTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val state = PairingState()
    private val cams =
        listOf(
            CameraOption(CameraRef("NVR_A", 0), "HOME", "CH1"),
            CameraOption(CameraRef("NVR_A", 1), "HOME", "CH2"),
            CameraOption(CameraRef("NVR_B", 0), "<b>Shop</b>", "CH1 <script>"),
        )
    private var current = Layout("l1", "My cameras", "4", listOf(cams[0].ref, cams[1].ref))
    private val saved = mutableListOf<Layout>()
    private val layouts =
        object : LayoutBackend {
            override fun snapshot() = LayoutSnapshot(current, cams)

            override fun save(layout: Layout) {
                saved += layout
                current = layout
            }
        }
    private val server = PairingServer(loopback, 0, layouts = layouts, state = state, onSignedIn = {}).also { it.start() }

    @AfterEach
    fun stop() = server.close()

    @Test
    fun `code page explains where to find the code in edit mode`() {
        assertTrue(request("GET", "/").body.contains("Edit from phone or computer"))
    }

    @Test
    fun `paired browser edits grid and cameras and saves to the TV`() {
        val cookie = pair()
        val editor = request("GET", "/", cookie)
        assertTrue(editor.headers.contains("set-cookie: $cookie"), "editing renews the cookie")
        assertTrue(editor.body.contains("name=\"t3\""))
        assertFalse(editor.body.contains("name=\"t4\""))
        assertTrue(editor.body.contains("&lt;b&gt;Shop&lt;/b&gt; · CH1 &lt;script&gt;"))
        assertTrue(editor.body.contains(">HOME · CH1</option>")) // NVR name visible even when the list is closed
        assertFalse(editor.body.contains("<script>"))

        // Apply a 3x3 grid without saving: 9 tiles, nothing saved yet.
        val nine = request("POST", "/layout", cookie, "shown=4&template=9&action=grid&name=Home&t0=${key(cams[1])}&t1=")
        assertTrue(nine.body.contains("name=\"t8\""))
        assertTrue(saved.isEmpty())

        // Save: tile 0 = HOME CH2, tile 1 = Shop CH1; unknown cameras are ignored.
        val save =
            request(
                "POST",
                "/layout",
                cookie,
                "shown=9&template=9&action=save&name=Garden&t0=${key(cams[1])}&t1=${key(cams[2])}&t2=EVIL%3A9",
            )
        assertTrue(save.body.contains("Saved. Your TV is showing it now."))
        val layout = saved.single()
        assertEquals("9", layout.templateId)
        assertEquals("Garden", layout.name)
        assertEquals(listOf(cams[1].ref, cams[2].ref), layout.cameras.take(2))
        assertFalse(layout.cameras.contains(CameraRef("EVIL", 9)))
    }

    @Test
    fun `editing needs pairing and sign-in routes are off`() {
        assertEquals(303, request("POST", "/layout", null, "action=save&shown=4&template=1").status)
        assertTrue(saved.isEmpty())
        assertEquals(403, request("GET", "/captcha", pair()).status)
    }

    private fun key(c: CameraOption) = URLEncoder.encode(c.key, "UTF-8")

    private fun pair(): String {
        val res = request("POST", "/pair", null, "code=${state.code}")
        return Regex("set-cookie: (pair=[0-9a-f]{64})").find(res.headers)!!.groupValues[1]
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
    ): Response =
        Socket(loopback, server.port).use { socket ->
            val req =
                buildString {
                    append("$method $path HTTP/1.1\r\nHost: ${loopback.hostAddress}:${server.port}\r\n")
                    cookie?.let { append("Cookie: $it\r\n") }
                    form?.let { append("Content-Type: application/x-www-form-urlencoded\r\nContent-Length: ${it.toByteArray().size}\r\n") }
                    append("\r\n")
                    form?.let { append(it) }
                }
            socket.getOutputStream().write(req.toByteArray(Charsets.UTF_8))
            val raw = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            val head = raw.substringBefore("\r\n\r\n")
            Response(head.substringAfter(' ').substringBefore(' ').toInt(), head.lowercase(), raw.substringAfter("\r\n\r\n"))
        }
}
