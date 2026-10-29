package com.anhourtec.camgrid.client.media

import java.nio.ByteBuffer

/**
 * The 20-byte binary reply to `RelayAskPro` on the media socket. Its two values are echoed in
 * playVideo (8224) and stopVideo (8232) as `nRelaySessionID` / `nRelaySockFd`.
 */
data class RelayAck(
    val relaySessionId: Long,
    val relaySockFd: Long,
) {
    companion object {
        const val SIZE = 20
        private const val MAGIC = 0x1234_5678L
        private const val TYPE = 0x111L
        private const val BODY_LENGTH = 8L
        private const val OFF_TYPE = 4
        private const val OFF_BODY_LENGTH = 8
        private const val OFF_SESSION_ID = 12
        private const val OFF_SOCK_FD = 16

        fun parse(buf: ByteBuffer): RelayAck? {
            val base = buf.position()
            if (buf.limit() - base != SIZE) return null
            if (LittleEndian.u32(buf, base) != MAGIC) return null
            if (LittleEndian.u32(buf, base + OFF_TYPE) != TYPE || LittleEndian.u32(buf, base + OFF_BODY_LENGTH) != BODY_LENGTH) return null
            return RelayAck(
                relaySessionId = LittleEndian.u32(buf, base + OFF_SESSION_ID),
                relaySockFd = LittleEndian.u32(buf, base + OFF_SOCK_FD),
            )
        }
    }
}
