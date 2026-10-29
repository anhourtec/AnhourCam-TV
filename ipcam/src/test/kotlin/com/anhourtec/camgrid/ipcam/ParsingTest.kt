package com.anhourtec.camgrid.ipcam

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

// Public test vectors from RFC 2617 §3.5 (not secrets).
private const val RFC_REALM = "testrealm@host.com" // check-redaction:allow
private const val RFC_NONCE = "dcd98b7102dd2f0e8b11d0f600bfb0c093" // check-redaction:allow
private const val RFC_RESPONSE_QOP = "6629fae49393a05397450978507c4ef1" // check-redaction:allow
private const val RFC_RESPONSE_PLAIN = "670fd8c2df070c60b045671b8b24ff02" // check-redaction:allow
private const val RFC_NC = "00000001" // check-redaction:allow

class ParsingTest {
    @Test
    fun `H264 SDP gives track, control URL and parameter sets`() {
        val sdp =
            """
            v=0
            o=- 1 1 IN IP4 0.0.0.0
            s=Session
            a=control:*
            m=audio 0 RTP/AVP 0
            a=control:trackID=2
            m=video 0 RTP/AVP 96
            a=rtpmap:96 H264/90000
            a=fmtp:96 packetization-mode=1;profile-level-id=4D001F;sprop-parameter-sets=Z00AHw==,aO48gA==
            a=control:trackID=1
            """.trimIndent().replace("\n", "\r\n")
        val track = Sdp.videoTrack(sdp, "rtsp://cam.local:554/stream1/")!!
        assertFalse(track.hevc)
        assertEquals(96, track.payloadType)
        assertEquals("rtsp://cam.local:554/stream1/trackID=1", track.control)
        assertArrayEquals(byteArrayOf(0x67, 0x4D, 0x00, 0x1F), track.parameterSets[0])
        assertEquals(2, track.parameterSets.size)
    }

    @Test
    fun `H265 SDP reads vps, sps and pps and keeps an absolute control`() {
        val sdp =
            """
            m=video 0 RTP/AVP 98
            a=rtpmap:98 H265/90000
            a=fmtp:98 sprop-vps=QAE=; sprop-sps=QgE=; sprop-pps=RAE=
            a=control:rtsp://cam.local/ch1/main/track1
            """.trimIndent()
        val track = Sdp.videoTrack(sdp, "rtsp://cam.local/ch1/main")!!
        assertTrue(track.hevc)
        assertEquals("rtsp://cam.local/ch1/main/track1", track.control)
        assertEquals(listOf(0x40, 0x42, 0x44), track.parameterSets.map { it[0].toInt() })
    }

    @Test
    fun `SDP without H264 or H265 has no usable track`() {
        assertNull(Sdp.videoTrack("m=video 0 RTP/AVP 26\r\na=rtpmap:26 JPEG/90000\r\n", "rtsp://x/"))
    }

    @Test
    fun `digest response matches RFC 2617 example with qop`() {
        val auth = RtspAuth("Mufasa", "Circle Of Life", cnonce = "0a4f113b")
        assertTrue(
            auth.accept(
                """Digest realm="$RFC_REALM", qop="auth,auth-int", nonce="$RFC_NONCE", opaque="5ccc"""",
            ),
        )
        val header = auth.header("GET", "/dir/index.html")!!
        assertTrue("response=\"$RFC_RESPONSE_QOP\"" in header, header)
        assertTrue("nc=$RFC_NC" in header)
        assertTrue("opaque=\"5ccc\"" in header)
    }

    @Test
    fun `digest without qop uses the short form`() {
        val auth = RtspAuth("Mufasa", "Circle Of Life")
        auth.accept("""Digest realm="$RFC_REALM", nonce="$RFC_NONCE"""")
        assertTrue("response=\"$RFC_RESPONSE_PLAIN\"" in auth.header("GET", "/dir/index.html")!!)
    }

    @Test
    fun `basic auth and unknown schemes`() {
        val auth = RtspAuth("admin", "pw")
        assertFalse(auth.ready)
        assertFalse(auth.accept("Bearer x"))
        assertTrue(auth.accept("Basic realm=\"cam\""))
        assertEquals("Basic YWRtaW46cHc=", auth.header("DESCRIBE", "rtsp://x/"))
    }

    @Test
    fun `credentials are taken out of the RTSP URL`() {
        val url = RtspUrl.parse("rtsp://admin:p%40ss@192.0.2.10/live/ch0", null, null)
        assertEquals("rtsp://192.0.2.10:554/live/ch0", url.url)
        assertEquals("admin", url.user)
        assertEquals("p@ss", url.password)
        val explicit = RtspUrl.parse("rtsp://u:x@192.0.2.10:8554/", "other", "secret")
        assertEquals("other", explicit.user)
        assertEquals("secret", explicit.password)
        assertEquals(8554, explicit.port)
        assertThrows<RtspException> { RtspUrl.parse("http://192.0.2.10/", null, null) }
    }

    @Test
    fun `WS-Security password digest`() {
        val nonce = ByteArray(16) { it.toByte() }
        assertEquals("H8i//QPMO3Drie7Tr3qjuU5YGa4=", OnvifClient.passwordDigest(nonce, "2026-10-29T12:00:00Z", "secret"))
    }

    @Test
    fun `ONVIF profiles are parsed whatever the namespace prefix, largest first`() {
        val xml =
            """
            <env:Body><trt:GetProfilesResponse>
            <trt:Profiles token="sub" fixed="true"><tt:Name>Sub</tt:Name>
              <tt:VideoEncoderConfiguration token="v2"><tt:Encoding>H264</tt:Encoding>
              <tt:Resolution><tt:Width>640</tt:Width><tt:Height>360</tt:Height></tt:Resolution></tt:VideoEncoderConfiguration>
            </trt:Profiles>
            <Profiles token="main"><Name>Main</Name>
              <VideoEncoderConfiguration><Encoding>H265</Encoding>
              <Resolution><Width>2560</Width><Height>1440</Height></Resolution></VideoEncoderConfiguration>
            </Profiles>
            <trt:Profiles token="audio"><tt:Name>Audio only</tt:Name></trt:Profiles>
            </trt:GetProfilesResponse></env:Body>
            """.trimIndent()
        val profiles = OnvifClient.profiles(xml)
        assertEquals(listOf("main", "sub"), profiles.map { it.token })
        assertEquals(2560, profiles[0].width)
        assertEquals("H264", profiles[1].encoding)
    }

    @Test
    fun `WS-Discovery reply gives the device service on the replying host`() {
        val reply =
            "<d:ProbeMatch><d:XAddrs>http://169.254.1.2/onvif/device_service http://192.0.2.7:8080/onvif/device_service</d:XAddrs></d:ProbeMatch>"
        assertEquals("http://192.0.2.7:8080/onvif/device_service", CameraScanner.xAddr(reply, "192.0.2.7"))
        assertNull(CameraScanner.xAddr("<x/>", "192.0.2.7"))
    }
}
