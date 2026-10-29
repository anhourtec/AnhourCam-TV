package com.anhourtec.camgrid.client.media

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FrameDepacketizerTest {
    private val header = FrameHeader()

    @Test
    fun `parses a sub-stream key frame`() {
        val msg = Frames.frame(1, 0, 480, 720, 1_793_275_882_866L, 25, Frames.HEVC_KEY_PAYLOAD)

        assertTrue(FrameDepacketizer.parse(ByteBuffer.wrap(msg), header))

        assertTrue(header.isKeyFrame)
        assertEquals(0L, header.sequence)
        assertEquals(480, header.height)
        assertEquals(720, header.width)
        assertEquals(1_793_275_882_866L, header.timestampMs)
        assertEquals(25, header.frameRate)
        assertEquals(44, header.payloadOffset)
        assertEquals(Frames.HEVC_KEY_PAYLOAD.size, header.payloadLength)
        assertArrayEquals(Frames.HEVC_KEY_PAYLOAD, msg.copyOfRange(header.payloadOffset, header.payloadOffset + header.payloadLength))
    }

    @Test
    fun `parses main-stream P frame and refresh frame types`() {
        assertTrue(FrameDepacketizer.parse(ByteBuffer.wrap(Frames.frame(2, 1, 1080, 1920, 0, 25, Frames.HEVC_P_PAYLOAD)), header))
        assertEquals(1920, header.width)
        assertEquals(1080, header.height)
        assertTrue(header.isVideo)
        assertFalse(header.isKeyFrame)

        assertTrue(FrameDepacketizer.parse(ByteBuffer.wrap(Frames.frame(7, 2, 1080, 1920, 0, 26, Frames.HEVC_P_PAYLOAD)), header))
        assertTrue(header.isVideo)
    }

    @Test
    fun `reads sequence numbers above Int MAX_VALUE as unsigned`() {
        assertTrue(FrameDepacketizer.parse(ByteBuffer.wrap(Frames.frame(2, -1, 480, 720, 0, 25, Frames.HEVC_P_PAYLOAD)), header))
        assertEquals(0xFFFF_FFFFL, header.sequence)
    }

    @Test
    fun `honours buffer position and leaves position and byte order untouched`() {
        val msg = Frames.frame(2, 5, 480, 720, 0, 25, Frames.HEVC_P_PAYLOAD)
        val buf = ByteBuffer.allocate(msg.size + 3).order(ByteOrder.BIG_ENDIAN)
        buf.position(3)
        buf.put(msg)
        buf.position(3)

        assertTrue(FrameDepacketizer.parse(buf, header))

        assertEquals(5L, header.sequence)
        assertEquals(3 + 44, header.payloadOffset)
        assertEquals(3, buf.position())
        assertEquals(ByteOrder.BIG_ENDIAN, buf.order())
    }

    @Test
    fun `rejects short messages, wrong marker and length mismatch`() {
        assertFalse(FrameDepacketizer.parse(ByteBuffer.allocate(43), header))

        val wrongMarker = Frames.frame(1, 0, 480, 720, 0, 25, Frames.HEVC_KEY_PAYLOAD).also { it[0] = 0x25 }
        assertFalse(FrameDepacketizer.parse(ByteBuffer.wrap(wrongMarker), header))

        val truncated = Frames.frame(1, 0, 480, 720, 0, 25, Frames.HEVC_KEY_PAYLOAD).copyOf(50)
        assertFalse(FrameDepacketizer.parse(ByteBuffer.wrap(truncated), header))
    }

    @Test
    fun `does not mistake a relay ack for a frame`() {
        assertFalse(FrameDepacketizer.parse(ByteBuffer.wrap(Frames.relayAck(1, 2)), header))
    }
}
