package com.anhourtec.camgrid.client.media

import java.nio.ByteBuffer

/** Absolute little-endian reads that ignore the buffer's byte order and never allocate. */
internal object LittleEndian {
    private const val BYTE_MASK = 0xFF
    private const val U32_MASK = 0xFFFF_FFFFL
    private const val U32_BYTES = 4
    private const val U32_BITS = 32

    fun u32(
        buf: ByteBuffer,
        index: Int,
    ): Long {
        val value =
            (buf.get(index).toInt() and BYTE_MASK) or
                ((buf.get(index + 1).toInt() and BYTE_MASK) shl 8) or
                ((buf.get(index + 2).toInt() and BYTE_MASK) shl 16) or
                ((buf.get(index + 3).toInt() and BYTE_MASK) shl 24)
        return value.toLong() and U32_MASK
    }

    fun u64(
        buf: ByteBuffer,
        index: Int,
    ): Long = u32(buf, index) or (u32(buf, index + U32_BYTES) shl U32_BITS)
}
