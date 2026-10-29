package com.anhourtec.camgrid.ipcam

import com.anhourtec.camgrid.client.media.FrameHeader
import com.anhourtec.camgrid.client.media.FrameSink
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class RtpDepacketizerTest {
    private class Frame(
        val bytes: ByteArray,
        val key: Boolean,
        val hevc: Boolean,
    )

    private val frames = mutableListOf<Frame>()
    private val sink =
        FrameSink { buffer: ByteBuffer, header: FrameHeader ->
            val bytes = ByteArray(header.payloadLength)
            buffer.duplicate().position(header.payloadOffset).get(bytes)
            frames += Frame(bytes, header.isKeyFrame, header.hevc)
        }

    private var seq = 100

    private fun rtp(
        payload: ByteArray,
        timestamp: Long,
        marker: Boolean,
        sequence: Int = seq++,
    ): ByteArray {
        val header = ByteArray(12)
        header[0] = 0x80.toByte()
        header[1] = ((if (marker) 0x80 else 0) or 96).toByte()
        header[2] = (sequence shr 8).toByte()
        header[3] = sequence.toByte()
        for (i in 0..3) header[4 + i] = (timestamp shr (24 - 8 * i)).toByte()
        return header + payload
    }

    private fun feed(
        d: RtpDepacketizer,
        packet: ByteArray,
    ) = d.onPacket(packet, 0, packet.size)

    private val sps = byteArrayOf(0x67, 1, 2)
    private val pps = byteArrayOf(0x68, 3)
    private val start = byteArrayOf(0, 0, 0, 1)

    @Test
    fun `single IDR NAL gets SDP parameter sets in front`() {
        val d = RtpDepacketizer(false, listOf(sps, pps), 640, 360, sink)
        feed(d, rtp(byteArrayOf(0x65, 9, 9), 3000, marker = true))
        assertEquals(1, frames.size)
        assertTrue(frames[0].key)
        assertFalse(frames[0].hevc)
        assertArrayEquals(start + sps + start + pps + start + byteArrayOf(0x65, 9, 9), frames[0].bytes)
    }

    @Test
    fun `FU-A fragments are joined into one NAL with the original header`() {
        val d = RtpDepacketizer(false, emptyList(), 640, 360, sink)
        val nri = 0x60
        feed(d, rtp(byteArrayOf((nri or 28).toByte(), (0x80 or 1).toByte(), 1, 2), 3000, marker = false))
        feed(d, rtp(byteArrayOf((nri or 28).toByte(), 1, 3), 3000, marker = false))
        feed(d, rtp(byteArrayOf((nri or 28).toByte(), (0x40 or 1).toByte(), 4), 3000, marker = true))
        assertEquals(1, frames.size)
        assertFalse(frames[0].key)
        assertArrayEquals(start + byteArrayOf((nri or 1).toByte(), 1, 2, 3, 4), frames[0].bytes)
    }

    @Test
    fun `STAP-A with in-band SPS and PPS is not given SDP sets again`() {
        val d = RtpDepacketizer(false, listOf(byteArrayOf(0x67, 7)), 640, 360, sink)
        val stap = byteArrayOf(24, 0, 3) + sps + byteArrayOf(0, 2) + pps
        feed(d, rtp(stap, 6000, marker = false))
        feed(d, rtp(byteArrayOf(0x65, 5), 6000, marker = true))
        assertArrayEquals(start + sps + start + pps + start + byteArrayOf(0x65, 5), frames.single().bytes)
        assertTrue(frames.single().key)
    }

    @Test
    fun `a lost packet drops the frame instead of showing a broken picture`() {
        val d = RtpDepacketizer(false, emptyList(), 640, 360, sink)
        feed(d, rtp(byteArrayOf(0x7C, (0x80 or 1).toByte(), 1), 9000, marker = false, sequence = 10))
        feed(d, rtp(byteArrayOf(0x7C, (0x40 or 1).toByte(), 2), 9000, marker = true, sequence = 12))
        assertTrue(frames.isEmpty())
        feed(d, rtp(byteArrayOf(0x41, 3), 12000, marker = true, sequence = 13))
        assertEquals(1, frames.size)
    }

    @Test
    fun `a new timestamp ends a frame that never got its marker`() {
        val d = RtpDepacketizer(false, emptyList(), 640, 360, sink)
        feed(d, rtp(byteArrayOf(0x41, 1), 1000, marker = false))
        feed(d, rtp(byteArrayOf(0x41, 2), 4000, marker = true))
        assertEquals(2, frames.size)
    }

    @Test
    fun `H265 FU rebuilds the two-byte NAL header and marks IRAP as key`() {
        val d = RtpDepacketizer(true, emptyList(), 1920, 1080, sink)
        // FU indicator: type 49, layer 0, tid 1 → 0x62 0x01; FU header S + type 19 (IDR_W_RADL).
        feed(d, rtp(byteArrayOf(0x62, 0x01, (0x80 or 19).toByte(), 7, 8), 3000, marker = false))
        feed(d, rtp(byteArrayOf(0x62, 0x01, (0x40 or 19).toByte(), 9), 3000, marker = true))
        val f = frames.single()
        assertTrue(f.key)
        assertTrue(f.hevc)
        assertArrayEquals(start + byteArrayOf((19 shl 1).toByte(), 0x01, 7, 8, 9), f.bytes)
    }

    @Test
    fun `H265 aggregation packet splits into NALs`() {
        val d = RtpDepacketizer(true, emptyList(), 1920, 1080, sink)
        val vps = byteArrayOf((32 shl 1).toByte(), 1, 5)
        val slice = byteArrayOf((1 shl 1).toByte(), 1, 6)
        val ap = byteArrayOf((48 shl 1).toByte(), 1, 0, 3) + vps + byteArrayOf(0, 3) + slice
        feed(d, rtp(ap, 3000, marker = true))
        assertArrayEquals(start + vps + start + slice, frames.single().bytes)
        assertFalse(frames.single().key)
    }

    @Test
    fun `garbage and non-RTP packets are ignored`() {
        val d = RtpDepacketizer(false, emptyList(), 640, 360, sink)
        d.onPacket(byteArrayOf(1, 2, 3), 0, 3)
        d.onPacket(ByteArray(20), 0, 20)
        assertTrue(frames.isEmpty())
    }
}
