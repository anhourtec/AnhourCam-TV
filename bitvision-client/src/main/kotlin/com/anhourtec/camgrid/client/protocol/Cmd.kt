package com.anhourtec.camgrid.client.protocol

/** Command IDs on the signalling and media sockets. Names follow docs/streaming.md. */
object Cmd {
    // cmdType
    const val TYPE_GATEWAY_REQUEST = 257
    const val TYPE_GATEWAY_RESPONSE = 258
    const val TYPE_DEVICE_MESSAGE = 513
    const val TYPE_DEVICE_RESPONSE = 514

    // Gateway (cmdType 257/258)
    const val LOGIN = 4101
    const val LOGIN_RES = 4102
    const val GET_CLIENT_INFO = 4103
    const val GET_CLIENT_INFO_RES = 4104

    // Device messages (cmdType 513)
    const val CREATE_SESSION = 8208
    const val CREATE_SESSION_RES = 8209
    const val CREATE_SUB_SESSION = 8216
    const val CREATE_RELAY_SESSION_RES = 8218
    const val PLAY_VIDEO = 8224
    const val PLAY_VIDEO_RES = 8225
    const val PLAY_AUDIO = 8228
    const val PLAY_AUDIO_RES = 8229
    const val STOP_VIDEO = 8232
    const val STOP_VIDEO_RES = 8233
    const val STOP_AUDIO = 8236
    const val ROUTED_ACK = 8447
    const val SDK_RES = 8448

    // Fixed values the portal sends
    const val CLIENT_ROLE_USER = 6
    const val CLIENT_ROLE_DEVICE = 1
    const val GATEWAY_TYPE_DEVICE = 1
    const val GATEWAY_TYPE_USER = 2
    const val CONN_TYPE_RELAY = 6
    const val SESSION_TYPE_LIVE = 2
    const val SUB_SESSION_CMD_TYPE = 1
    const val LIVE_TIME = 30

    // INFERRED: constant in the portal source, not per device.
    const val DEV_UUID = "1481703573"
}

/** Stream IDs as the portal names them. */
enum class StreamQuality(
    val streamId: Int,
) {
    /** Main stream, 1080p on the tested NVRs. */
    HD(0),

    /** Sub-stream, 720×480 on the tested NVRs. Use for grid tiles. */
    SD(1),
}
