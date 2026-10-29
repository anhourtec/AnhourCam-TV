package com.anhourtec.camgrid.ipcam

import com.anhourtec.camgrid.client.media.FrameHeader
import com.anhourtec.camgrid.client.media.FrameSink
import java.nio.ByteBuffer

/**
 * Turns RTP packets of one H.264 (RFC 6184) or H.265 (RFC 7798) stream into whole Annex-B access
 * units for a [FrameSink]. One growing buffer and one [FrameHeader] are reused, so the video path
 * allocates nothing per frame. Parameter sets from the SDP are put in front of every key frame
 * that arrives without them, so a decoder can start on any key frame.
 */
class RtpDepacketizer(
    private val hevc: Boolean,
    parameterSets: List<ByteArray>,
    private val width: Int,
    private val height: Int,
    private val sink: FrameSink,
) {
    private val header = FrameHeader().also { it.hevc = hevc }
    private val parameterSets = parameterSets.toList()
    private var frame = ByteBuffer.allocate(INITIAL_BYTES)
    private var timestamp = -1L
    private var hasKeySlice = false
    private var hasParameterSets = false
    private var broken = false
    private var lastSequence = -1

    /** One RTP packet ([length] bytes at [offset]); a frame is delivered on its last packet. */
    fun onPacket(
        packet: ByteArray,
        offset: Int,
        length: Int,
    ) {
        if (length < RTP_HEADER || (packet[offset].toInt() and VERSION_MASK) != RTP_VERSION) return
        val payloadStart = payloadStart(packet, offset, length) ?: return
        val marker = (packet[offset + 1].toInt() and MARKER_BIT) != 0
        val sequence = u16(packet, offset + 2)
        val time = u32(packet, offset + 4)
        if (time != timestamp) {
            if (frame.position() > 0) deliver()
            timestamp = time
        }
        if (lastSequence >= 0 && sequence != (lastSequence + 1) and SEQUENCE_MASK) broken = true
        lastSequence = sequence
        val payloadLength = offset + length - payloadStart
        if (payloadLength > 0) {
            if (hevc) h265(packet, payloadStart, payloadLength) else h264(packet, payloadStart, payloadLength)
        }
        if (marker) deliver()
    }

    private fun payloadStart(
        packet: ByteArray,
        offset: Int,
        length: Int,
    ): Int? {
        val first = packet[offset].toInt()
        var start = offset + RTP_HEADER + (first and CSRC_MASK) * CSRC_BYTES
        if ((first and EXTENSION_BIT) != 0) {
            if (start + EXTENSION_HEADER > offset + length) return null
            start += EXTENSION_HEADER + u16(packet, start + 2) * EXTENSION_WORD
        }
        var end = offset + length
        if ((first and PADDING_BIT) != 0) end -= packet[end - 1].toInt() and BYTE
        return if (start <= end) start else null
    }

    private fun h264(
        p: ByteArray,
        start: Int,
        length: Int,
    ) {
        when (val type = p[start].toInt() and H264_TYPE_MASK) {
            in 1..H264_SINGLE_MAX -> {
                nal(p, start, length, h264Kind(type))
            }

            H264_STAP_A -> {
                aggregate(p, start + 1, length - 1) { s, _ -> h264Kind(p[s].toInt() and H264_TYPE_MASK) }
            }

            H264_FU_A -> {
                if (length < 2) return
                val fu = p[start + 1].toInt()
                val nalType = fu and H264_TYPE_MASK
                if (fu and FU_START != 0) {
                    startNal(h264Kind(nalType))
                    ensure(1)
                    frame.put(((p[start].toInt() and H264_NRI_MASK) or nalType).toByte())
                }
                fragment(p, start + 2, length - 2)
            }
        }
    }

    private fun h265(
        p: ByteArray,
        start: Int,
        length: Int,
    ) {
        if (length < H265_NAL_HEADER) return
        when (val type = (p[start].toInt() shr 1) and H265_TYPE_MASK) {
            H265_AP -> {
                aggregate(p, start + H265_NAL_HEADER, length - H265_NAL_HEADER) { s, _ ->
                    h265Kind((p[s].toInt() shr 1) and H265_TYPE_MASK)
                }
            }

            H265_FU -> {
                if (length < H265_FU_HEADER) return
                val fu = p[start + 2].toInt()
                val nalType = fu and H265_TYPE_MASK
                if (fu and FU_START != 0) {
                    startNal(h265Kind(nalType))
                    ensure(2)
                    frame.put(((p[start].toInt() and H265_LAYER_HIGH_MASK) or (nalType shl 1)).toByte())
                    frame.put(p[start + 1])
                }
                fragment(p, start + H265_FU_HEADER, length - H265_FU_HEADER)
            }

            else -> {
                if (type < H265_AP) nal(p, start, length, h265Kind(type))
            }
        }
    }

    private inline fun aggregate(
        p: ByteArray,
        from: Int,
        length: Int,
        kind: (Int, Int) -> Kind,
    ) {
        var i = from
        val end = from + length
        while (i + 2 <= end) {
            val size = u16(p, i)
            i += 2
            if (size == 0 || i + size > end) return
            nal(p, i, size, kind(i, size))
            i += size
        }
    }

    private fun nal(
        p: ByteArray,
        start: Int,
        length: Int,
        kind: Kind,
    ) {
        startNal(kind)
        ensure(length)
        frame.put(p, start, length)
    }

    private fun startNal(kind: Kind) {
        if (kind == Kind.KEY) hasKeySlice = true
        if (kind == Kind.PARAMETERS) hasParameterSets = true
        ensure(START_CODE.size)
        frame.put(START_CODE)
    }

    private fun fragment(
        p: ByteArray,
        start: Int,
        length: Int,
    ) {
        if (length <= 0) return
        ensure(length)
        frame.put(p, start, length)
    }

    private fun ensure(extra: Int) {
        if (frame.remaining() >= extra) return
        val bigger = ByteBuffer.allocate(maxOf(frame.capacity() * 2, frame.position() + extra))
        frame.flip()
        bigger.put(frame)
        frame = bigger
    }

    private fun deliver() {
        if (frame.position() == 0) return
        if (!broken) {
            if (hasKeySlice && !hasParameterSets && parameterSets.isNotEmpty()) prependParameterSets()
            header.type = if (hasKeySlice) FrameHeader.TYPE_KEY else FrameHeader.TYPE_P
            header.width = width
            header.height = height
            header.timestampMs = timestamp * MS_PER_S / CLOCK_RATE
            header.payloadOffset = 0
            header.payloadLength = frame.position()
            frame.flip()
            sink.onFrame(frame, header)
        }
        frame.clear()
        hasKeySlice = false
        hasParameterSets = false
        broken = false
    }

    private fun prependParameterSets() {
        val extra = parameterSets.sumOf { it.size + START_CODE.size }
        val body = frame.position()
        ensure(extra)
        val array = frame.array()
        System.arraycopy(array, 0, array, extra, body)
        var i = 0
        for (set in parameterSets) {
            START_CODE.copyInto(array, i)
            i += START_CODE.size
            set.copyInto(array, i)
            i += set.size
        }
        frame.position(body + extra)
    }

    private companion object {
        val START_CODE = byteArrayOf(0, 0, 0, 1)
        const val INITIAL_BYTES = 256 * 1024
        const val RTP_HEADER = 12
        const val RTP_VERSION = 0x80
        const val VERSION_MASK = 0xC0
        const val PADDING_BIT = 0x20
        const val EXTENSION_BIT = 0x10
        const val CSRC_MASK = 0x0F
        const val CSRC_BYTES = 4
        const val EXTENSION_HEADER = 4
        const val EXTENSION_WORD = 4
        const val MARKER_BIT = 0x80
        const val SEQUENCE_MASK = 0xFFFF
        const val BYTE = 0xFF
        const val CLOCK_RATE = 90_000L
        const val MS_PER_S = 1000L
        const val FU_START = 0x80

        const val H264_NRI_MASK = 0xE0
        const val H264_SINGLE_MAX = 23
        const val H264_STAP_A = 24
        const val H264_FU_A = 28

        const val H265_NAL_HEADER = 2
        const val H265_FU_HEADER = 3
        const val H265_TYPE_MASK = 0x3F
        const val H265_LAYER_HIGH_MASK = 0x81
        const val H265_AP = 48
        const val H265_FU = 49
    }
}

private const val BYTE_MASK = 0xFF
private const val BITS_8 = 8
private const val BITS_16 = 16

internal fun u16(
    p: ByteArray,
    i: Int,
): Int = ((p[i].toInt() and BYTE_MASK) shl BITS_8) or (p[i + 1].toInt() and BYTE_MASK)

internal fun u32(
    p: ByteArray,
    i: Int,
): Long = (u16(p, i).toLong() shl BITS_16) or u16(p, i + 2).toLong()

private fun h264Kind(type: Int): Kind =
    when (type) {
        H264_IDR -> Kind.KEY
        H264_SPS, H264_PPS -> Kind.PARAMETERS
        else -> Kind.OTHER
    }

private fun h265Kind(type: Int): Kind =
    when (type) {
        in H265_IRAP_FIRST..H265_IRAP_LAST -> Kind.KEY
        H265_VPS, H265_SPS, H265_PPS -> Kind.PARAMETERS
        else -> Kind.OTHER
    }

private enum class Kind { KEY, PARAMETERS, OTHER }

private const val H264_TYPE_MASK = 0x1F

private const val H264_IDR = 5

private const val H264_SPS = 7

private const val H264_PPS = 8

private const val H265_IRAP_FIRST = 16

private const val H265_IRAP_LAST = 21

private const val H265_VPS = 32

private const val H265_SPS = 33

private const val H265_PPS = 34
