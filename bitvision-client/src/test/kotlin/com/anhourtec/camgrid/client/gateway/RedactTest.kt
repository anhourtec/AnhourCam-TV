package com.anhourtec.camgrid.client.gateway

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RedactTest {
    @Test
    fun `masks token-like fields too`() {
        assertEquals("""{"token":"<redacted>","app_secret":"<redacted>"}""", Redact.secrets("""{"token":"abc","app_secret":"xyz"}"""))
    }

    @Test
    fun `masks credentials and emails`() {
        val raw = """{"devUserName":"admin","devPasswd":"p\"x","username":"a.b@example.com","channelNo":0}"""
        assertEquals(
            """{"devUserName":"<redacted>","devPasswd":"<redacted>","username":"<email>","channelNo":0}""",
            Redact.secrets(raw),
        )
    }
}
