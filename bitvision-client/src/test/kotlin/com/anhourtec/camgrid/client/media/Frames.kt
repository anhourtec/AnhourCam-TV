package com.anhourtec.camgrid.client.media

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds media messages with the layout documented in docs/streaming.md. */
object Frames {
    val HEVC_KEY_PAYLOAD = byteArrayOf(0, 0, 0, 1, 0x40, 0x01, 0x0c, 0x01)
    val HEVC_P_PAYLOAD = byteArrayOf(0, 0, 0, 1, 0x02, 0x01, 0xd0.toByte(), 0x09)

    fun frame(
        type: Int,
        sequence: Int,
        height: Int,
        width: Int,
        timestampMs: Long,
        fps: Int,
        payload: ByteArray,
    ): ByteArray =
        ByteBuffer
            .allocate(FrameDepacketizer.HEADER_SIZE + payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x24)
            .putInt(type)
            .putInt(sequence)
            .putInt(height)
            .putInt(width)
            .putLong(timestampMs)
            .putInt(fps)
            .putInt(0)
            .putInt(8)
            .putInt(payload.size)
            .put(payload)
            .array()

    fun relayAck(
        sessionId: Long,
        sockFd: Int,
    ): ByteArray =
        ByteBuffer
            .allocate(RelayAck.SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x12345678)
            .putInt(0x111)
            .putInt(8)
            .putInt(sessionId.toInt())
            .putInt(sockFd)
            .array()
}
