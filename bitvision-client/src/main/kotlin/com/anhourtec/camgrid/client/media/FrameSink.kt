package com.anhourtec.camgrid.client.media

import java.nio.ByteBuffer

/**
 * Receives media frames on the socket's thread. [buffer] and [header] are only valid during the
 * call: copy the payload (e.g. into a MediaCodec input buffer) before returning.
 */
fun interface FrameSink {
    fun onFrame(
        buffer: ByteBuffer,
        header: FrameHeader,
    )
}
