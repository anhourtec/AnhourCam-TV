package com.anhourtec.camgrid.ipcam

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** A fake camera that, like the maintainer's, demands HTTP Digest for everything but the clock. */
class OnvifClientTest {
    private class Camera(
        private val password: String,
    ) : Dispatcher() {
        val authorizations = mutableListOf<String?>()

        override fun dispatch(request: RecordedRequest): MockResponse {
            val body = request.body?.utf8().orEmpty()
            if ("GetSystemDateAndTime" in body) return ok(CLOCK)
            val auth = request.headers["Authorization"]
            authorizations += auth
            if (!valid(auth, request)) {
                return MockResponse
                    .Builder()
                    .code(401)
                    .addHeader("WWW-Authenticate", "Digest qop=\"auth\", realm=\"IP Camera\", nonce=\"5396\"")
                    .build()
            }
            return when {
                "GetCapabilities" in body -> ok("<tt:Media><tt:XAddr>http://198.51.100.9/onvif/Media</tt:XAddr></tt:Media>")
                "GetProfiles" in body -> ok(PROFILES)
                "GetStreamUri" in body -> ok("<tt:Uri>rtsp://198.51.100.9:80/${if ("main" in body) 0 else 1}</tt:Uri>")
                "GetSnapshotUri" in body -> ok("<tt:Uri>http://198.51.100.9:80/snap.jpg</tt:Uri>")
                else -> MockResponse.Builder().code(400).build()
            }
        }

        private fun valid(
            auth: String?,
            request: RecordedRequest,
        ): Boolean {
            if (auth == null || !auth.startsWith("Digest ")) return false
            val p = RtspAuth.params(auth.removePrefix("Digest "))
            val ha1 = RtspAuth.md5("admin:IP Camera:$password")
            val ha2 = RtspAuth.md5("POST:${request.url.encodedPath}")
            val expected = RtspAuth.md5("$ha1:5396:${p["nc"]}:${p["cnonce"]}:auth:$ha2")
            return p["uri"] == request.url.encodedPath && p["response"] == expected
        }

        private fun ok(body: String) = MockResponse.Builder().body("<s:Envelope><s:Body>$body</s:Body></s:Envelope>").build()
    }

    @Test
    fun `answers the HTTP digest challenge and keeps the reachable host`() {
        MockWebServer().use { server ->
            val camera = Camera("right")
            server.dispatcher = camera
            server.start()
            val url = server.url("/onvif/device_service").toString()
            val streams = OnvifClient(OkHttpClient(), url, "admin", "right").streams()
            assertEquals(listOf(1920, 1280), streams.map { it.profile.width })
            assertEquals("rtsp://198.51.100.9:80/0", streams[0].rtspUrl)
            assertEquals("http://198.51.100.9:80/snap.jpg", streams[0].snapshotUrl)
            // The media service is asked on the host we reached, not the one the camera reports.
            assertTrue(camera.authorizations.filterNotNull().all { "realm=\"IP Camera\"" in it })
            assertTrue(camera.authorizations.none { it?.startsWith("Basic") == true })
        }
    }

    @Test
    fun `out-of-range camera clock fields never throw`() {
        // A real camera sent 18:-15:36: read as 17:45:36.
        val t = OnvifClient.cameraEpochSeconds(listOf(2026, 10, 29), listOf(18, -15, 36))!!
        assertEquals(
            java.time.Instant
                .parse("2026-10-29T17:45:36Z")
                .epochSecond,
            t,
        )
        assertEquals(null, OnvifClient.cameraEpochSeconds(listOf(2026, 13, 40), listOf(0, 0, 0)))
        assertEquals(null, OnvifClient.cameraEpochSeconds(listOf(2026, 2, 30), listOf(0, 0, 0)))
        assertEquals("2026-10-29T17:45:36Z", OnvifClient.utcTimestamp(t * 1000))
    }

    @Test
    fun `a wrong password is still reported as such`() {
        MockWebServer().use { server ->
            server.dispatcher = Camera("right")
            server.start()
            val e =
                assertThrows<OnvifException> {
                    OnvifClient(OkHttpClient(), server.url("/onvif/device_service").toString(), "admin", "wrong").streams()
                }
            assertEquals("wrong user name or password", e.message)
        }
    }

    private companion object {
        const val CLOCK =
            "<tt:UTCDateTime><tt:Time><tt:Hour>12</tt:Hour><tt:Minute>0</tt:Minute><tt:Second>0</tt:Second></tt:Time>" +
                "<tt:Date><tt:Year>2026</tt:Year><tt:Month>10</tt:Month><tt:Day>29</tt:Day></tt:Date></tt:UTCDateTime>"
        const val PROFILES =
            "<trt:Profiles token=\"sub\"><tt:Name>secondStream</tt:Name><tt:VideoEncoderConfiguration><tt:Encoding>H264</tt:Encoding>" +
                "<tt:Resolution><tt:Width>1280</tt:Width><tt:Height>720</tt:Height></tt:Resolution>" +
                "</tt:VideoEncoderConfiguration></trt:Profiles>" +
                "<trt:Profiles token=\"main\"><tt:Name>mainStream</tt:Name><tt:VideoEncoderConfiguration><tt:Encoding>H264</tt:Encoding>" +
                "<tt:Resolution><tt:Width>1920</tt:Width><tt:Height>1080</tt:Height></tt:Resolution>" +
                "</tt:VideoEncoderConfiguration></trt:Profiles>"
    }
}
