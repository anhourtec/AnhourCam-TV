package com.anhourtec.camgrid.client.page

/** An NVR/DVR as listed on the account's device page. */
data class Device(
    val deviceId: String,
    val name: String,
    val typeName: String,
    val online: Boolean,
    val channels: List<Channel>,
)

/** What `/home/space` shows: the account name and its devices. */
data class AccountOverview(
    val accountName: String,
    val devices: List<Device>,
)

/** A camera channel. [channelNo] is 0-based (CH1 = 0), as the protocol uses it. */
data class Channel(
    val channelNo: Int,
    val name: String,
)

/** The NVR's own login. Never logged: [toString] is redacted. */
class NvrCredentials(
    val user: String,
    val password: String,
) {
    override fun toString(): String = "NvrCredentials(<redacted>)"
}

/** Gateway endpoints for a device, from `tipsDeviceInfo.getUserServiceAddr`. */
data class ServiceAddress(
    val userGateUrl: String,
    val streamUrl: String,
    val userGatewayIp: String,
    val streamRelayIp: String,
    val streamPort: Int,
)

/** Everything the device page provides to start live view. */
data class DeviceDetail(
    val deviceId: String,
    val name: String,
    val model: String,
    val typeName: String,
    val channelCount: Int,
    val online: Boolean,
    val credentials: NvrCredentials,
    val service: ServiceAddress,
)
