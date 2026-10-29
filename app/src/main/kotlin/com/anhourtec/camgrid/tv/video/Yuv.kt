package com.anhourtec.camgrid.tv.video

import java.nio.ByteBuffer

/** One plane of a decoded YUV 4:2:0 image (as MediaCodec's Image exposes it). */
class Plane(
    val buffer: ByteBuffer,
    val rowStride: Int,
    val pixelStride: Int,
)

/**
 * Converts a YUV 4:2:0 frame to ARGB pixels at a smaller size by sampling (BT.601, video range).
 * Small output (e.g. 320×180) keeps this cheap enough for still images on a slow TV.
 */
object Yuv {
    private const val Y_OFFSET = 16
    private const val UV_OFFSET = 128
    private const val Y_SCALE = 1192
    private const val V_TO_R = 1634
    private const val U_TO_G = 401
    private const val V_TO_G = 832
    private const val U_TO_B = 2066
    private const val SHIFT = 10
    private const val ROUND = 1 shl (SHIFT - 1)
    private const val MAX = 255
    private const val BYTE = 0xFF
    private const val OPAQUE = 0xFF shl 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    @Suppress("LongParameterList")
    fun toArgb(
        y: Plane,
        u: Plane,
        v: Plane,
        srcWidth: Int,
        srcHeight: Int,
        outWidth: Int,
        outHeight: Int,
    ): IntArray {
        val out = IntArray(outWidth * outHeight)
        for (oy in 0 until outHeight) {
            val sy = oy * srcHeight / outHeight
            for (ox in 0 until outWidth) {
                val sx = ox * srcWidth / outWidth
                val luma = (y.buffer.get(sy * y.rowStride + sx * y.pixelStride).toInt() and BYTE) - Y_OFFSET
                val cu = (u.buffer.get((sy / 2) * u.rowStride + (sx / 2) * u.pixelStride).toInt() and BYTE) - UV_OFFSET
                val cv = (v.buffer.get((sy / 2) * v.rowStride + (sx / 2) * v.pixelStride).toInt() and BYTE) - UV_OFFSET
                val l = Y_SCALE * maxOf(luma, 0) + ROUND
                val r = ((l + V_TO_R * cv) shr SHIFT).coerceIn(0, MAX)
                val g = ((l - U_TO_G * cu - V_TO_G * cv) shr SHIFT).coerceIn(0, MAX)
                val b = ((l + U_TO_B * cu) shr SHIFT).coerceIn(0, MAX)
                out[oy * outWidth + ox] = OPAQUE or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
            }
        }
        return out
    }
}
