package com.anhourtec.camgrid.tv.video

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.SystemClock

/**
 * Decodes one H.265 key frame into a small bitmap with a software decoder that is not part of
 * the live budget. Used for still images; a few per minute cost the CPU very little.
 */
internal object KeyFrameDecoder {
    const val OUT_WIDTH = 320
    const val OUT_HEIGHT = 180
    private const val TIMEOUT_US = 50_000L
    private const val DEADLINE_MS = 2_000L

    fun decode(
        frame: KeyFrame,
        decoderName: String,
    ): Bitmap? {
        val codec = MediaCodec.createByCodecName(decoderName)
        return try {
            val format =
                MediaFormat.createVideoFormat(DecoderBudget.MIME, frame.width, frame.height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, frame.annexB.size + 1)
                }
            codec.configure(format, null, null, 0)
            codec.start()
            feed(codec, frame)
            drain(codec, frame)
        } catch (_: IllegalStateException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
    }

    private fun feed(
        codec: MediaCodec,
        frame: KeyFrame,
    ) {
        val input = codec.dequeueInputBuffer(TIMEOUT_US * 2)
        if (input < 0) return
        codec.getInputBuffer(input)?.apply {
            clear()
            put(frame.annexB)
        }
        codec.queueInputBuffer(input, 0, frame.annexB.size, 0, MediaCodec.BUFFER_FLAG_KEY_FRAME)
        val eos = codec.dequeueInputBuffer(TIMEOUT_US * 2)
        if (eos >= 0) codec.queueInputBuffer(eos, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
    }

    private fun drain(
        codec: MediaCodec,
        frame: KeyFrame,
    ): Bitmap? {
        val info = MediaCodec.BufferInfo()
        val deadline = SystemClock.elapsedRealtime() + DEADLINE_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (index < 0) continue
            val bitmap =
                codec.getOutputImage(index)?.use { image ->
                    val p = image.planes
                    val pixels =
                        Yuv.toArgb(
                            Plane(p[0].buffer, p[0].rowStride, p[0].pixelStride),
                            Plane(p[1].buffer, p[1].rowStride, p[1].pixelStride),
                            Plane(p[2].buffer, p[2].rowStride, p[2].pixelStride),
                            minOf(image.width, frame.width),
                            minOf(image.height, frame.height),
                            OUT_WIDTH,
                            OUT_HEIGHT,
                        )
                    Bitmap.createBitmap(pixels, OUT_WIDTH, OUT_HEIGHT, Bitmap.Config.ARGB_8888)
                }
            codec.releaseOutputBuffer(index, false)
            if (bitmap != null || info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return bitmap
        }
        return null
    }
}
