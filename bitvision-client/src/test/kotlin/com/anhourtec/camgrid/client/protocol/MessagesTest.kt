package com.anhourtec.camgrid.client.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessagesTest {
    private val route = Route(1_000_000_123L, 1_000_000_456L, "DEV", "192.0.2.1", 35002, 1)

    @Test
    fun `device message carries routing fields and msg_data`() {
        val text =
            Messages.deviceMessage(
                route,
                msgId = 2,
                cmdId = Cmd.PLAY_VIDEO,
                msgData = Messages.subSessionData(route.ownClientId, 3, 1),
                requestNo = 42,
            )
        val root = Json.parseToJsonElement(text).jsonObject
        assertEquals("SendToRouter", root["functionName"]!!.jsonPrimitive.content)
        assertEquals(30, root["liveTime"]!!.jsonPrimitive.content.toInt())
        val param = root["param"]!!.jsonObject
        assertEquals(Cmd.TYPE_DEVICE_MESSAGE.toString(), param["cmdType"]!!.jsonPrimitive.content)
        val cmdData = param["cmdData"]!!.jsonObject
        assertEquals(2, cmdData["msg_id"]!!.jsonPrimitive.content.toInt())
        assertEquals(1_000_000_123L, cmdData["from_client_id"]!!.jsonPrimitive.long)
        assertEquals("DEV", cmdData["to_client_name"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, cmdData["msg_data"]!!.jsonObject["recordType"])
    }

    @Test
    fun `sub session message omits liveTime like the portal`() {
        val text = Messages.deviceMessage(route, 3, Cmd.CREATE_SUB_SESSION, Messages.subSessionData(1, 0, 1), 1, withLiveTime = false)
        assertFalse(Json.parseToJsonElement(text).jsonObject.containsKey("liveTime"))
    }

    @Test
    fun `relay request array and heartbeat`() {
        val req =
            Messages
                .relayRequest(4, 0, 0xC000_0001L, 89)
                .jsonArray
                .single()
                .jsonObject
        assertEquals(0xC000_0001L, req["nRelaySessionID"]!!.jsonPrimitive.long)
        assertTrue(Messages.heartbeat(1).contains("\"functionName\":\"HeartBeat\""))
    }

    @Test
    fun `parses gateway replies and routed device replies`() {
        val login =
            Incoming.parse(
                """{"functionName":"ProcGateResponse","code":0,"msg":"succss","data":{"cmdType":258,"cmdID":4102,""" +
                    """"cmdData":{"client_id":1000000123,"online_status":1,"trans_port":35010}}}""", // check-redaction:allow fake ID
            )!!
        assertEquals(Cmd.LOGIN_RES, login.cmdId)
        assertEquals(1_000_000_123L, login.long(login.cmdData, "client_id"))

        val relay =
            Incoming.parse(
                """{"functionName":"TransMessage","code":0,"msg":"succss","data":{"cmdType":513,"cmdID":8218,""" +
                    """"cmdData":{"msg_id":6,"cmd_id":8218,"msg_data":{"resultCode":0,"channelNo":0}}}}""",
            )!!
        assertEquals(6, relay.msgId)
        assertEquals(0, relay.int(relay.msgData, "resultCode"))
    }
}
