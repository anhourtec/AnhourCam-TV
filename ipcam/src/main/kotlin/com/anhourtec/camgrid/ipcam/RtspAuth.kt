package com.anhourtec.camgrid.ipcam

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Builds the Authorization header for an RTSP 401 challenge (Basic or Digest, RFC 2617). */
class RtspAuth(
    private val user: String,
    private val password: String,
    /** Basic sends the password readable: only for cameras known to need it. */
    private val allowBasic: Boolean = true,
    /** Client nonce for qop=auth; random unless a test fixes it. */
    private val cnonce: String = randomCnonce(),
) {
    private var challenge: Map<String, String>? = null
    private var basic = false
    private var nonceCount = 0

    /** Reads a WWW-Authenticate header. Returns false if the scheme is unsupported. */
    fun accept(header: String): Boolean {
        val scheme = header.substringBefore(' ').trim()
        return when {
            scheme.equals("Digest", ignoreCase = true) -> {
                challenge = params(header.substringAfter(' '))
                basic = false
                nonceCount = 0
                true
            }

            scheme.equals("Basic", ignoreCase = true) && challenge == null && allowBasic -> {
                basic = true
                true
            }

            else -> {
                false
            }
        }
    }

    val ready: Boolean get() = basic || challenge != null

    /** True when the camera asked for Basic and got it (so the user can be told / it can be remembered). */
    val usedBasic: Boolean get() = basic

    fun header(
        method: String,
        uri: String,
    ): String? {
        if (basic) return "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray())
        val c = challenge ?: return null
        val realm = c["realm"].orEmpty()
        val nonce = c["nonce"].orEmpty()
        val ha1 = md5("$user:$realm:$password")
        val ha2 = md5("$method:$uri")
        val qop = c["qop"]?.split(',')?.map { it.trim() }?.firstOrNull { it == "auth" }
        val parts =
            mutableListOf(
                "username=\"${quoted(user)}\"",
                "realm=\"$realm\"",
                "nonce=\"$nonce\"",
                "uri=\"$uri\"",
            )
        if (qop != null) {
            nonceCount++
            val nc = "%08x".format(nonceCount)
            parts += "response=\"${md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")}\""
            parts += listOf("qop=$qop", "nc=$nc", "cnonce=\"$cnonce\"")
        } else {
            parts += "response=\"${md5("$ha1:$nonce:$ha2")}\""
        }
        c["opaque"]?.let { parts += "opaque=\"$it\"" }
        parts += "algorithm=MD5"
        return "Digest " + parts.joinToString(", ")
    }

    companion object {
        private const val CNONCE_BYTES = 8
        private const val QUOTED = 2
        private const val BARE = 3

        private fun randomCnonce(): String =
            ByteArray(CNONCE_BYTES).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

        private fun quoted(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

        fun md5(text: String): String = MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        /** Parses `key="value", key=value` lists (quoted values may contain commas). */
        fun params(text: String): Map<String, String> {
            val out = mutableMapOf<String, String>()
            Regex("""(\w+)\s*=\s*(?:"((?:\\.|[^"\\])*)"|([^,\s]+))""").findAll(text).forEach {
                out[it.groupValues[1].lowercase()] = it.groupValues[QUOTED].ifEmpty { it.groupValues[BARE] }
            }
            return out
        }
    }
}
