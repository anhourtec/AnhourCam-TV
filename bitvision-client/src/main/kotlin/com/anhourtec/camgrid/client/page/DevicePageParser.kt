package com.anhourtec.camgrid.client.page

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reads `tipsDeviceInfo` from `/home/video/show?id=…`. Its values are single-quoted JS string
 * literals holding JSON. See docs/api.md.
 */
object DevicePageParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(html: String): DeviceDetail {
        val info = json.decodeFromString<RawDeviceInfo>(literal(html, "curDeviceInfo"))
        val addr = json.decodeFromString<RawServiceAddress>(literal(html, "getUserServiceAddr"))
        return DeviceDetail(
            deviceId = info.deviceId,
            name = info.deviceName,
            model = info.deviceModel,
            typeName = info.deviceTypeName,
            channelCount = info.channel.toIntOrNull() ?: 0,
            online = info.deviceStatus == "1",
            credentials = NvrCredentials(info.localUser, info.localPwd),
            service =
                ServiceAddress(
                    userGateUrl = requireSecure(addr.userGateUrl),
                    streamUrl = requireSecure(addr.wsStreamUrl),
                    userGatewayIp = addr.userGateWayIp,
                    streamRelayIp = addr.streamRelayIp,
                    streamPort = addr.wsStreamPort,
                ),
        )
    }

    /** The gateway receives the NVR's password (cmd 8208), so only TLS WebSockets are accepted. */
    private fun requireSecure(url: String): String {
        if (!url.startsWith("wss://")) throw PageFormatException("refusing non-TLS gateway URL")
        return url
    }

    private fun literal(
        html: String,
        key: String,
    ): String {
        val match =
            Regex("""$key:\s*'((?:\\.|[^'\\])*)'""").find(html)
                ?: throw PageFormatException("device page has no $key")
        return unescapeJs(match.groupValues[1])
    }

    @Serializable
    private class RawDeviceInfo(
        @SerialName("device_id") val deviceId: String,
        @SerialName("device_name") val deviceName: String = "",
        @SerialName("device_model") val deviceModel: String = "",
        @SerialName("device_type_name") val deviceTypeName: String = "",
        @SerialName("device_status") val deviceStatus: String = "",
        val channel: String = "",
        @SerialName("local_user") val localUser: String = "",
        @SerialName("local_pwd") val localPwd: String = "",
    )

    @Serializable
    private data class RawServiceAddress(
        @SerialName("user_gate_url") val userGateUrl: String,
        @SerialName("ws_stream_url") val wsStreamUrl: String,
        @SerialName("user_gate_way_ip") val userGateWayIp: String,
        @SerialName("stream_relay_ip") val streamRelayIp: String,
        @SerialName("ws_stream_port") val wsStreamPort: Int,
    )
}

class PageFormatException(
    message: String,
) : Exception(message)
