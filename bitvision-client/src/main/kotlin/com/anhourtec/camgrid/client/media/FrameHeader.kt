package com.anhourtec.camgrid.client.media

/**
 * Header of one media message. Mutable so the video path can reuse a single instance per stream
 * instead of allocating one per frame.
 */
class FrameHeader {
    var type: Int = 0
    var sequence: Long = 0
    var height: Int = 0
    var width: Int = 0
    var timestampMs: Long = 0
    var frameRate: Int = 0
    var payloadOffset: Int = 0
    var payloadLength: Int = 0

    /** H.265 (BitVision, most IP cameras) or H.264 (many IP cameras). */
    var hevc: Boolean = true

    val isKeyFrame: Boolean get() = type == TYPE_KEY
    val isVideo: Boolean get() = type == TYPE_KEY || type == TYPE_P || type == TYPE_P_REFRESH

    override fun toString(): String = "FrameHeader(type=$type, seq=$sequence, ${width}x$height, fps=$frameRate, payload=$payloadLength)"

    companion object {
        const val TYPE_KEY = 1
        const val TYPE_P = 2

        // INFERRED: audio frames; the portal sets streamIsAudio = 5. Not yet observed on the wire.
        const val TYPE_AUDIO = 5

        // INFERRED: large P frame every ~75 frames ("virtual I-frame"); decodes as a P frame.
        const val TYPE_P_REFRESH = 7
    }
}
