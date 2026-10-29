package com.anhourtec.camgrid.client.gateway

import com.anhourtec.camgrid.client.media.Frames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.util.concurrent.CopyOnWriteArrayList

/** Server side of the signalling socket, answering like the real gateway (docs/streaming.md). */
class FakeGatewayListener(
    private val refuseDeviceLogin: Boolean = false,
) : WebSocketListener() {
    val received = CopyOnWriteArrayList<JsonObject>()

    override fun onClosing(
        webSocket: WebSocket,
        code: Int,
        reason: String,
    ) {
        webSocket.close(code, null)
    }

    override fun onMessage(
        webSocket: WebSocket,
        text: String,
    ) {
        val root = Json.parseToJsonElement(text).jsonObject
        received += root
        val param = root["param"]?.jsonObject ?: return
        val cmdData = param["cmdData"]!!.jsonObject
        when (param["cmdID"]!!.jsonPrimitive.int) {
            4101 -> {
                webSocket.send(gateway(4102, """{"client_id":$OWN_ID,"online_status":1,"trans_port":35010}"""))
            }

            4103 -> {
                webSocket.send(
                    gateway(
                        4104,
                        """{"client_name":"x","client_id":$DEVICE_CLIENT_ID,"client_loginip":"192.0.2.1",""" +
                            """"client_loginport":35002,"cookie":7,"gate_type":1}""",
                    ),
                )
            }

            8208 -> {
                val msgId = cmdData["msg_id"]!!.jsonPrimitive.int
                webSocket.send(ROUTED_ACK)
                val result = if (refuseDeviceLogin) 5 else 0
                webSocket.send(device(8209, msgId, """{"devGatewayIP":"192.0.2.1","resultCode":$result,"devGatewayPort":35002}"""))
            }

            8224 -> {
                val msgId = cmdData["msg_id"]!!.jsonPrimitive.int
                webSocket.send(ROUTED_ACK)
                webSocket.send(device(8225, msgId, """{"channelNum":1,"rsp_info":[{"channelNo":0,"resultCode":1}]}"""))
                webSocket.send(device(8218, msgId, """{"resultCode":0,"channelNo":0,"relayPort":35009}"""))
            }

            8232 -> {
                webSocket.send(device(8233, cmdData["msg_id"]!!.jsonPrimitive.int, """{"channelNum":1,"rsp_info":[]}"""))
            }
        }
    }

    private fun gateway(
        cmdId: Int,
        cmdData: String,
    ) = """{"functionName":"ProcGateResponse","code":0,"msg":"succss","data":{"cmdType":258,"cmdID":$cmdId,"cmdData":$cmdData}}"""

    private fun device(
        cmdId: Int,
        msgId: Int,
        msgData: String,
    ) =
        """{"functionName":"TransMessage","code":0,"msg":"succss","data":{"cmdType":513,"cmdID":$cmdId,"cmdData":{"msg_id":$msgId,"cmd_id":$cmdId,"msg_data":$msgData}}}"""

    companion object {
        const val OWN_ID = 1_000_000_123L
        const val DEVICE_CLIENT_ID = 1_000_000_456L
        private const val ROUTED_ACK =
            """{"functionName":"ProcGateResponse","code":0,"msg":"succss","data":""" +
                """{"cmdType":514,"cmdID":8447,"cmdData":{"toClientID":1}}}"""
    }
}

/** Server side of the media socket: relay ack, then frames once the sub-session starts. */
class FakeMediaListener : WebSocketListener() {
    val received = CopyOnWriteArrayList<String>()

    override fun onClosing(
        webSocket: WebSocket,
        code: Int,
        reason: String,
    ) {
        webSocket.close(code, null)
    }

    override fun onMessage(
        webSocket: WebSocket,
        text: String,
    ) {
        received += text
        when {
            text.contains("RelayAskPro") -> {
                webSocket.send(Frames.relayAck(0xC000_0001L, 89).toByteString())
            }

            text.contains("\"cmdID\":8216") -> {
                webSocket.send(Frames.frame(1, 0, 480, 720, 0, 25, Frames.HEVC_KEY_PAYLOAD).toByteString())
                webSocket.send(Frames.frame(2, 1, 480, 720, 40, 25, Frames.HEVC_P_PAYLOAD).toByteString())
            }
        }
    }
}
