package com.anhourtec.camgrid.tv.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import com.anhourtec.camgrid.client.media.FrameHeader
import com.anhourtec.camgrid.client.media.FrameSink
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Decodes one tile's H.265 stream straight to [surface]. Frames are copied once, from the socket
 * buffer into the codec's input buffer, on the socket thread; a small output thread renders each
 * decoded frame as soon as it is ready (lowest latency, no queueing).
 *
 * If no input buffer frees up in time the frame is dropped and decoding waits for the next key
 * frame, so a slow decoder never builds up delay or shows corrupted images.
 */
class TileDecoder(
    private val surface: Surface,
    private val slot: DecoderBudget.Slot,
) : FrameSink,
    Closeable {
    val stats = Stats(slot.decoder)

    private val lock = Any()
    private var codec: MediaCodec? = null
    private var output: Thread? = null

    @Volatile private var running = true
    private var waitingForKeyFrame = true

    override fun onFrame(
        buffer: ByteBuffer,
        header: FrameHeader,
    ) {
        if (header.isVideo) synchronized(lock) { feed(buffer, header) }
    }

    private fun feed(
        buffer: ByteBuffer,
        header: FrameHeader,
    ) {
        if (!running || (waitingForKeyFrame && !header.isKeyFrame)) return
        if (waitingForKeyFrame) {
            if (codec == null) start(header.width, header.height, header.hevc)
            waitingForKeyFrame = false
        }
        stats.size = "${header.width}x${header.height}"
        codec?.let { queue(it, buffer, header) }
    }

    private fun queue(
        codec: MediaCodec,
        buffer: ByteBuffer,
        header: FrameHeader,
    ) {
        val index = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
        val input = if (index >= 0) codec.getInputBuffer(index) else null
        if (input == null || input.capacity() < header.payloadLength) {
            if (index >= 0) codec.queueInputBuffer(index, 0, 0, 0, 0)
            stats.dropped.incrementAndGet()
            waitingForKeyFrame = true
            return
        }
        val position = buffer.position()
        val limit = buffer.limit()
        buffer.limit(header.payloadOffset + header.payloadLength).position(header.payloadOffset)
        input.clear()
        input.put(buffer)
        buffer.limit(limit).position(position)
        val flags = if (header.isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        codec.queueInputBuffer(index, 0, header.payloadLength, header.timestampMs * US_PER_MS, flags)
        stats.received.incrementAndGet()
    }

    private fun start(
        width: Int,
        height: Int,
        hevc: Boolean,
    ) {
        // H.264 IP cameras: same slot accounting, the TV's AVC decoder of the same kind.
        // INFERRED: TVs share decoder hardware between codecs, so one budget fits both.
        val name = if (hevc) slot.decoder.name else DecoderBudget.avcDecoder(slot.decoder.hardware) ?: return
        val mime = if (hevc) DecoderBudget.MIME else DecoderBudget.AVC
        val format =
            MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_BYTES)
                setInteger(MediaFormat.KEY_PRIORITY, REALTIME_PRIORITY)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
        val created = MediaCodec.createByCodecName(name)
        created.configure(format, surface, null, 0)
        created.start()
        codec = created
        output = thread(name = "tile-out-${slot.decoder.name}") { render(created) }
    }

    private fun render(codec: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (running) {
            val index =
                try {
                    codec.dequeueOutputBuffer(info, OUTPUT_TIMEOUT_US)
                } catch (_: IllegalStateException) {
                    break
                }
            if (index >= 0) {
                codec.releaseOutputBuffer(index, true)
                if (stats.rendered.incrementAndGet() == 1L) stats.firstFrameAt = SystemClock.elapsedRealtime()
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            running = false
        }
        output?.join(JOIN_TIMEOUT_MS)
        synchronized(lock) {
            codec?.let {
                runCatching { it.stop() }
                it.release()
            }
            codec = null
        }
        slot.release()
    }

    /** Counters read by the overlay; written from the socket and output threads. */
    class Stats(
        val decoder: DecoderBudget.Decoder,
    ) {
        val received = AtomicLong()
        val rendered = AtomicLong()
        val dropped = AtomicLong()

        @Volatile var firstFrameAt = 0L

        @Volatile var size = ""
    }

    private companion object {
        const val INPUT_TIMEOUT_US = 20_000L
        const val OUTPUT_TIMEOUT_US = 10_000L
        const val JOIN_TIMEOUT_MS = 500L
        const val US_PER_MS = 1000L
        const val MAX_INPUT_BYTES = 1024 * 1024
        const val REALTIME_PRIORITY = 0
    }
}
