package com.anhourtec.camgrid.client.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class RelayAckTest {
    @Test
    fun `reads relay session id and socket fd as unsigned`() {
        val ack = RelayAck.parse(ByteBuffer.wrap(Frames.relayAck(0xC000_0001L, 89)))
        assertEquals(RelayAck(0xC000_0001L, 89), ack)
    }

    @Test
    fun `rejects other messages`() {
        assertNull(RelayAck.parse(ByteBuffer.wrap(Frames.relayAck(1, 2).copyOf(19))))
        assertNull(RelayAck.parse(ByteBuffer.wrap(Frames.relayAck(1, 2).also { it[0] = 0 })))
        assertNull(RelayAck.parse(ByteBuffer.wrap(Frames.frame(1, 0, 480, 720, 0, 25, Frames.HEVC_KEY_PAYLOAD))))
    }
}
