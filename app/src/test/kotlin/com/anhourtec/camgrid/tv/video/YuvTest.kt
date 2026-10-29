package com.anhourtec.camgrid.tv.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class YuvTest {
    private fun plane(
        width: Int,
        height: Int,
        value: Int,
        pixelStride: Int = 1,
    ): Plane {
        val rowStride = width * pixelStride
        val bytes = ByteArray(rowStride * height) { value.toByte() }
        return Plane(ByteBuffer.wrap(bytes), rowStride, pixelStride)
    }

    private fun rgb(argb: Int) = Triple((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    @Test
    fun `video-range black, white and grey`() {
        fun pixel(luma: Int) = Yuv.toArgb(plane(4, 4, luma), plane(2, 2, 128), plane(2, 2, 128), 4, 4, 2, 2)[0]
        assertEquals(Triple(0, 0, 0), rgb(pixel(16)))
        assertEquals(Triple(255, 255, 255), rgb(pixel(235)))
        val (r, g, b) = rgb(pixel(126))
        assertTrue(r in 126..130 && r == g && g == b, "grey $r $g $b")
    }

    @Test
    fun `strong V is red, strong U is blue, alpha opaque`() {
        val red = Yuv.toArgb(plane(2, 2, 82), plane(1, 1, 90), plane(1, 1, 240), 2, 2, 1, 1)[0]
        val (r, g, b) = rgb(red)
        assertTrue(r > 200 && g < 60 && b < 60, "red $r $g $b")
        assertEquals(0xFF, (red ushr 24) and 0xFF)
        val blue = Yuv.toArgb(plane(2, 2, 41), plane(1, 1, 240), plane(1, 1, 110), 2, 2, 1, 1)[0]
        val (r2, _, b2) = rgb(blue)
        assertTrue(b2 > 200 && r2 < 60, "blue $r2 $b2")
    }

    @Test
    fun `interleaved chroma planes and scaling down`() {
        val pixels = Yuv.toArgb(plane(720, 480, 126), plane(360, 240, 128, 2), plane(360, 240, 128, 2), 720, 480, 320, 180)
        assertEquals(320 * 180, pixels.size)
    }
}
