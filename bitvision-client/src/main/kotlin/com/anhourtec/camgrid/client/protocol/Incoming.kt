package com.anhourtec.camgrid.client.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A JSON message received on a gateway socket. */
class Incoming(
    val functionName: String,
    val code: Int,
    val cmdType: Int,
    val cmdId: Int,
    val cmdData: JsonObject?,
) {
    /** `msg_id` of a routed device message, used to match replies to requests. */
    val msgId: Int? get() = cmdData?.get("msg_id")?.jsonPrimitive?.intOrNull

    /** `msg_data` of a routed device message. */
    val msgData: JsonObject? get() = (cmdData?.get("msg_data") as? JsonObject)

    fun long(
        obj: JsonObject?,
        key: String,
    ): Long = (obj?.get(key) as? JsonPrimitive)?.longOrNull ?: throw ProtocolException("missing $key in cmd $cmdId")

    fun int(
        obj: JsonObject?,
        key: String,
    ): Int = (obj?.get(key) as? JsonPrimitive)?.intOrNull ?: throw ProtocolException("missing $key in cmd $cmdId")

    fun string(
        obj: JsonObject?,
        key: String,
    ): String = (obj?.get(key) as? JsonPrimitive)?.content ?: throw ProtocolException("missing $key in cmd $cmdId")

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Parses a text frame. Returns null for anything that isn't a JSON object. */
        fun parse(text: String): Incoming? {
            val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
            val data = (root["data"] ?: root["param"]) as? JsonObject
            return Incoming(
                functionName = root["functionName"]?.jsonPrimitive?.content.orEmpty(),
                code = (root["code"] as? JsonPrimitive)?.intOrNull ?: 0,
                cmdType = (data?.get("cmdType") as? JsonPrimitive)?.int ?: 0,
                cmdId = (data?.get("cmdID") as? JsonPrimitive)?.int ?: 0,
                cmdData = data?.get("cmdData") as? JsonObject,
            )
        }
    }
}

class ProtocolException(
    message: String,
) : Exception(message)
