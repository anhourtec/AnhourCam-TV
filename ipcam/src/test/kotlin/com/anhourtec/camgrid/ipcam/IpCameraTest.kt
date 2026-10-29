package com.anhourtec.camgrid.ipcam

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IpCameraTest {
    private val cam = IpCamera("a1", "Gate", "192.0.2.5", "rtsp://192.0.2.5:80/0", "rtsp://192.0.2.5:80/1", "http://192.0.2.5/snap.jpg")

    @Test
    fun `camera list round-trips without any password field`() {
        val list = IpCameraList().put(cam).put(cam.copy(id = "b2", name = "Yard", ownLogin = true, user = "viewer"))
        val text = IpCameraList.encode(list)
        assertFalse("password" in text.lowercase())
        assertEquals(list, IpCameraList.decode(text))
        assertNull(IpCameraList.decode("not json"))
    }

    @Test
    fun `put replaces by id and remove deletes`() {
        val list = IpCameraList().put(cam).put(cam.copy(name = "Front gate"))
        assertEquals(listOf("Front gate"), list.cameras.map { it.name })
        assertTrue(list.remove("a1").cameras.isEmpty())
    }

    @Test
    fun `grid tiles use the sub-stream, fullscreen the main stream`() {
        assertEquals("rtsp://192.0.2.5:80/1", cam.streamUrl(fullscreen = false))
        assertEquals("rtsp://192.0.2.5:80/0", cam.streamUrl(fullscreen = true))
        assertEquals("rtsp://192.0.2.5:80/0", cam.copy(subUrl = null).streamUrl(fullscreen = false))
    }

    @Test
    fun `wrong RTSP port falls back to 554 but a wrong password does not retry`() {
        assertEquals("rtsp://192.0.2.5:554/1", CameraProbe.onDefaultPort("rtsp://192.0.2.5:80/1"))
        assertNull(CameraProbe.onDefaultPort("rtsp://192.0.2.5/1"))
        val tried = mutableListOf<String>()
        val result =
            CameraProbe.withRtspPortFallback("rtsp://192.0.2.5:80/1") {
                tried += it
                if (":80/" in it) throw RtspException("refused") else "ok"
            }
        assertEquals("ok", result)
        assertEquals(2, tried.size)
        tried.clear()
        runCatching {
            CameraProbe.withRtspPortFallback("rtsp://192.0.2.5:80/1") {
                tried += it
                throw RtspException("wrong user name or password", 401)
            }
        }
        assertEquals(1, tried.size)
    }

    @Test
    fun `Basic login is refused unless allowed`() {
        val strict = RtspAuth("admin", "pw", allowBasic = false)
        assertFalse(strict.accept("Basic realm=\"cam\""))
        assertFalse(strict.ready)
        val lenient = RtspAuth("admin", "pw")
        assertTrue(lenient.accept("Basic realm=\"cam\""))
        assertTrue(lenient.usedBasic)
    }
}
