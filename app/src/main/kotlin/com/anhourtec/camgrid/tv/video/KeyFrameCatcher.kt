package com.anhourtec.camgrid.tv.video

import com.anhourtec.camgrid.client.media.FrameHeader
import com.anhourtec.camgrid.client.media.FrameSink
import kotlinx.coroutines.CompletableDeferred
import java.nio.ByteBuffer

/** A key frame copied out of the stream, with its size. */
class KeyFrame(
    val annexB: ByteArray,
    val width: Int,
    val height: Int,
)

/** Keeps the first key frame of a stream (the NVR starts every stream with one) and ignores the rest. */
class KeyFrameCatcher : FrameSink {
    val frame = CompletableDeferred<KeyFrame>()

    override fun onFrame(
        buffer: ByteBuffer,
        header: FrameHeader,
    ) {
        if (frame.isCompleted || !header.isKeyFrame) return
        val bytes = ByteArray(header.payloadLength)
        buffer.duplicate().apply { position(header.payloadOffset) }.get(bytes)
        frame.complete(KeyFrame(bytes, header.width, header.height))
    }
}
