package com.anhourtec.camgrid.client.protocol

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Where device messages are routed: our gateway client and the device's gateway client. */
data class Route(
    val ownClientId: Long,
    val deviceClientId: Long,
    val deviceId: String,
    val deviceGatewayIp: String,
    val deviceGatewayPort: Int,
    val deviceGatewayType: Int,
)

/** Builders for outgoing JSON messages. Field names and order match what the portal sends. */
object Messages {
    fun heartbeat(requestNo: Long): String =
        buildJsonObject {
            put("functionName", "HeartBeat")
            put("requestNo", requestNo)
            put("liveTime", Cmd.LIVE_TIME)
        }.toString()

    fun relayAsk(
        deviceId: String,
        requestNo: Long,
    ): String =
        buildJsonObject {
            put("functionName", "RelayAskPro")
            put("requestNo", requestNo)
            put("liveTime", Cmd.LIVE_TIME)
            put(
                "param",
                buildJsonObject {
                    put("deviceId", deviceId)
                    put("devUUID", Cmd.DEV_UUID)
                },
            )
        }.toString()

    fun gatewayLogin(
        accountName: String,
        requestNo: Long,
    ): String =
        gatewayRequest(
            Cmd.LOGIN,
            buildJsonObject {
                put("client_name", accountName)
                put("client_role", Cmd.CLIENT_ROLE_USER)
                // The portal sends these placeholder values verbatim.
                put("client_modle", "test")
                put("client_firm_ver", "123")
                put("client_hardware_ver", "456")
                put("client_sdk_ver", "789")
            },
            requestNo,
        )

    fun getClientInfo(
        deviceId: String,
        requestNo: Long,
    ): String =
        gatewayRequest(
            Cmd.GET_CLIENT_INFO,
            buildJsonObject {
                put("client_name", deviceId)
                put("gateway_type", Cmd.GATEWAY_TYPE_DEVICE)
                put("client_role", Cmd.CLIENT_ROLE_USER)
            },
            requestNo,
        )

    /** A message routed to the device. [withLiveTime] is false only for createSubSession (8216). */
    fun deviceMessage(
        route: Route,
        msgId: Int,
        cmdId: Int,
        msgData: JsonObject,
        requestNo: Long,
        withLiveTime: Boolean = true,
    ): String =
        buildJsonObject {
            put("functionName", "SendToRouter")
            put("requestNo", requestNo)
            if (withLiveTime) put("liveTime", Cmd.LIVE_TIME)
            put(
                "param",
                buildJsonObject {
                    put("cmdType", Cmd.TYPE_DEVICE_MESSAGE)
                    put("cmdID", cmdId)
                    put(
                        "cmdData",
                        buildJsonObject {
                            put("msg_id", msgId)
                            put("cmd_id", cmdId)
                            put("from_client_id", route.ownClientId)
                            put("to_client_id", route.deviceClientId)
                            put("to_client_name", route.deviceId)
                            put("to_client_role", Cmd.CLIENT_ROLE_DEVICE)
                            put("to_client_loginip", route.deviceGatewayIp)
                            put("to_client_loginport", route.deviceGatewayPort)
                            put("to_gate_type", route.deviceGatewayType)
                            put("msg_data", msgData)
                        },
                    )
                },
            )
        }.toString()

    fun relayRequest(
        channelNo: Int,
        streamId: Int,
        relaySessionId: Long,
        relaySockFd: Long,
    ): JsonElement =
        buildJsonArray {
            add(
                buildJsonObject {
                    put("channelNo", channelNo)
                    put("streamId", streamId)
                    put("nRelaySessionID", relaySessionId)
                    put("nRelaySockFd", relaySockFd)
                    put("reserve", 0)
                },
            )
        }

    fun subSessionData(
        userLoginId: Long,
        channelNo: Int,
        streamId: Int,
    ): JsonObject =
        buildJsonObject {
            put("userLoginId", userLoginId)
            put("cmdType", Cmd.SUB_SESSION_CMD_TYPE)
            put("channelNo", channelNo)
            put("streamId", streamId)
            put("sessionType", Cmd.SESSION_TYPE_LIVE)
            put("recordType", JsonNull)
        }

    private fun gatewayRequest(
        cmdId: Int,
        cmdData: JsonObject,
        requestNo: Long,
    ): String =
        buildJsonObject {
            put("functionName", "SendToRouter")
            put("requestNo", requestNo)
            put("liveTime", Cmd.LIVE_TIME)
            put(
                "param",
                buildJsonObject {
                    put("cmdType", Cmd.TYPE_GATEWAY_REQUEST)
                    put("cmdID", cmdId)
                    put("cmdData", cmdData)
                },
            )
        }.toString()
}
