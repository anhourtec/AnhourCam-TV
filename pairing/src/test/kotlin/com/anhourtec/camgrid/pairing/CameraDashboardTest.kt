package com.anhourtec.camgrid.pairing

import com.anhourtec.camgrid.layouts.CameraRef
import com.anhourtec.camgrid.layouts.Layout
import com.anhourtec.camgrid.layouts.LayoutState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.Socket
import java.net.URLEncoder

class CameraDashboardTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val state = PairingState()
    private val saved = mutableListOf<CameraForm>()
    private var entries =
        mutableListOf(CameraEntry("c1", "Gate <script>", "192.0.2.5", ownLogin = false, user = "", hasOwnPassword = false))
    private var sharedUser = "admin"
    private var sharedPassword = ""
    private var nextOutcome: SaveOutcome = SaveOutcome.Saved(basicLogin = false)
    private val backend =
        object : CameraBackend {
            override fun cameras() =
                CamerasSnapshot(entries, sharedUser, sharedPassword.isNotEmpty(), listOf(BrandOption("HIKVISION", "Hikvision")))

            override fun form(id: String) =
                entries.firstOrNull { it.id == id }?.let {
                    CameraForm(it.id, AddMode.URL, it.name, it.address, null, 1, "rtsp://${it.address}/1", "", false, "", "")
                }

            override fun scan() =
                listOf(FoundCamera("192.0.2.5", 554, onvif = true, added = true), FoundCamera("192.0.2.9", 554, false, false))

            override fun save(form: CameraForm): SaveOutcome {
                if (form.name == "boom") throw IllegalStateException("bug in a backend")
                saved += form
                return nextOutcome
            }

            override fun delete(id: String) {
                entries.removeAll { it.id == id }
            }

            override fun setShared(
                user: String,
                password: String,
            ) {
                sharedUser = user
                if (password.isNotEmpty()) sharedPassword = password
            }
        }
    private val layouts =
        object : LayoutBackend {
            override fun snapshot() =
                LayoutSnapshot(
                    LayoutState().saved(Layout("l1", "Home", "1", listOf(CameraRef("ip:c1", 0)))).withDefault("l1"),
                    listOf(CameraOption(CameraRef("ip:c1", 0), "IP cameras", "Gate")),
                )

            override fun apply(state: LayoutState) = Unit
        }
    private val server =
        PairingServer(loopback, 0, layouts = layouts, cameras = backend, state = state, onSignedIn = {}).also { it.start() }

    @AfterEach
    fun stop() = server.close()

    @Test
    fun `a failing backend gives an error page and the server keeps running`() {
        val cookie = pair()
        assertEquals(500, request("POST", "/cameras/save", cookie, form("mode" to "onvif", "name" to "boom", "host" to "192.0.2.5")).status)
        assertEquals(200, request("GET", "/cameras", cookie).status)
    }

    @Test
    fun `camera pages need pairing`() {
        assertEquals(303, request("GET", "/cameras").status)
        assertEquals(303, request("POST", "/cameras/save", null, "mode=url&name=x&rtsp=rtsp%3A%2F%2F192.0.2.1%2F").status)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `list escapes names and offers scan, add and the shared login`() {
        val cookie = pair()
        val page = request("GET", "/cameras", cookie).body
        assertTrue("Gate &lt;script&gt;" in page)
        assertFalse("<script>" in page)
        assertTrue("href=\"/cameras/scan\"" in page && "href=\"/cameras/add\"" in page)
        assertTrue("aria-current=\"page\">IP cameras</a>" in page, "tabs mark the camera page")
        assertTrue("github.com/anhourtec/AnhourCam-TV/issues" in page)
        assertTrue("IP cameras</a>" in request("GET", "/", cookie).body, "layout editor shows the tabs too")
    }

    @Test
    fun `scan lists found cameras and links unadded ones to the add form`() {
        val page = request("GET", "/cameras/scan", pair()).body
        assertTrue("href=\"/cameras/add?host=192.0.2.9&amp;mode=brand\"" in page)
        assertFalse("host=192.0.2.5" in page, "already added cameras have no add link")
    }

    @Test
    fun `valid RTSP form is passed to the backend and the password is never echoed`() {
        val cookie = pair()
        nextOutcome = SaveOutcome.Failed(CameraError.WRONG_LOGIN)
        val failed =
            request(
                "POST",
                "/cameras/save",
                cookie,
                form(
                    "mode" to "url",
                    "name" to "Yard",
                    "rtsp" to "rtsp://192.0.2.7:554/1",
                    "login" to "own",
                    "user" to "u",
                    "password" to "s3cr3t-pw",
                ),
            )
        assertTrue("The camera refused the user name or password." in failed.body)
        assertFalse("s3cr3t-pw" in failed.body)
        val form = saved.single()
        assertEquals("rtsp://192.0.2.7:554/1", form.rtspUrl)
        assertTrue(form.ownLogin)
        assertEquals("s3cr3t-pw", form.password)
        assertFalse("s3cr3t-pw" in form.toString())

        nextOutcome = SaveOutcome.Saved(basicLogin = true)
        val ok =
            request(
                "POST",
                "/cameras/save",
                cookie,
                form(
                    "mode" to "onvif",
                    "name" to "Gate",
                    "host" to "192.0.2.5",
                    "login" to "shared",
                ),
            )
        assertTrue("Camera saved." in ok.body && "unencrypted" in ok.body)
    }

    @Test
    fun `invalid input never reaches the backend`() {
        val cookie = pair()
        val bad =
            listOf(
                form("mode" to "url", "name" to "x", "rtsp" to "http://192.0.2.7/"),
                form("mode" to "url", "name" to "x", "rtsp" to "rtsp://a b/"),
                form("mode" to "onvif", "name" to "x", "host" to "evil.com/../x"),
                form("mode" to "brand", "name" to "x", "host" to "192.0.2.5", "brand" to "NOPE"),
                form("mode" to "onvif", "name" to "", "host" to "192.0.2.5"),
                form("mode" to "onvif", "name" to "x", "host" to "192.0.2.5", "channel" to "999"),
                form("mode" to "url", "name" to "x", "rtsp" to "rtsp://192.0.2.7/", "snapshot" to "javascript:alert(1)"),
                form("mode" to "url", "name" to "x", "rtsp" to "rtsp://192.0.2.7/", "snapshot" to "http://u:pw@192.0.2.7/snap.jpg"),
            )
        bad.forEach { assertTrue("Please check the fields" in request("POST", "/cameras/save", cookie, it).body, it) }
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `edit only accepts known ids, delete removes, shared login keeps an empty password`() {
        val cookie = pair()
        assertTrue("name=\"id\" value=\"c1\"" in request("GET", "/cameras/add?id=c1", cookie).body)
        assertFalse("name=\"id\"" in request("GET", "/cameras/add?id=unknown", cookie).body)
        request("POST", "/cameras/save", cookie, form("id" to "forged", "mode" to "onvif", "name" to "x", "host" to "192.0.2.5"))
        assertNull(saved.single().id)

        request("POST", "/cameras/shared", cookie, form("user" to "viewer", "password" to "pw1"))
        request("POST", "/cameras/shared", cookie, form("user" to "viewer", "password" to ""))
        assertEquals("pw1", sharedPassword)
        assertFalse("pw1" in request("GET", "/cameras", cookie).body)

        val list = request("GET", "/cameras", cookie).body
        assertTrue("href=\"/cameras/remove?id=c1\"" in list, "every camera has a Remove button")
        val confirm = request("GET", "/cameras/remove?id=c1", cookie).body
        assertTrue("Remove “Gate &lt;script&gt;”?" in confirm && "action=\"/cameras/delete\"" in confirm)
        assertTrue(entries.isNotEmpty(), "asking is not removing")
        assertTrue("Camera deleted." in request("POST", "/cameras/delete", cookie, form("id" to "c1")).body)
        assertTrue(entries.isEmpty())
    }

    private fun form(vararg fields: Pair<String, String>) = fields.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

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
