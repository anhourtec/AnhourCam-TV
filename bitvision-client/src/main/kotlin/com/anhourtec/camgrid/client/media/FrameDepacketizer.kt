package com.anhourtec.camgrid.client.media

import java.nio.ByteBuffer

/**
 * Parses the private 44-byte media header (see docs/streaming.md). The payload is left in place:
 * [FrameHeader.payloadOffset] and [FrameHeader.payloadLength] point into the same buffer, so no
 * bytes are copied and nothing is allocated per frame.
 */
object FrameDepacketizer {
    const val HEADER_SIZE = 44

    private const val MARKER = 0x24
    private const val OFF_TYPE = 4
    private const val OFF_SEQUENCE = 8
    private const val OFF_HEIGHT = 12
    private const val OFF_WIDTH = 16
    private const val OFF_TIMESTAMP = 20
    private const val OFF_FRAME_RATE = 28
    private const val OFF_PAYLOAD_LENGTH = 40

    /**
     * Reads the header of the message between [buf]'s position and limit into [out].
     * Returns false (leaving [out] unspecified) if the message is not a well-formed media frame.
     * Does not change [buf]'s position, limit or byte order.
     */
    fun parse(
        buf: ByteBuffer,
        out: FrameHeader,
    ): Boolean {
        val base = buf.position()
        val size = buf.limit() - base
        if (size < HEADER_SIZE || LittleEndian.u32(buf, base) != MARKER.toLong()) return false

        val payloadLength = LittleEndian.u32(buf, base + OFF_PAYLOAD_LENGTH)
        if (payloadLength != (size - HEADER_SIZE).toLong()) return false

        out.type = LittleEndian.u32(buf, base + OFF_TYPE).toInt()
        out.sequence = LittleEndian.u32(buf, base + OFF_SEQUENCE)
        out.height = LittleEndian.u32(buf, base + OFF_HEIGHT).toInt()
        out.width = LittleEndian.u32(buf, base + OFF_WIDTH).toInt()
        out.timestampMs = LittleEndian.u64(buf, base + OFF_TIMESTAMP)
        out.frameRate = LittleEndian.u32(buf, base + OFF_FRAME_RATE).toInt()
        out.payloadOffset = base + HEADER_SIZE
        out.payloadLength = payloadLength.toInt()
        return true
    }
}
