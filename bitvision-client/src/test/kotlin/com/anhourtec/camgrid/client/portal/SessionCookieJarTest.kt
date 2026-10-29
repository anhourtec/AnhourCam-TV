package com.anhourtec.camgrid.client.portal

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionCookieJarTest {
    private val portal = "https://bitvision.app/home/space".toHttpUrl()

    @Test
    fun `restored session is sent only to the portal host over https`() {
        val jar = SessionCookieJar()
        jar.restore("abc")

        assertEquals(listOf("abc"), jar.loadForRequest(portal).map { it.value })
        assertTrue(jar.loadForRequest("http://bitvision.app/home/space".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://sub.bitvision.app/".toHttpUrl()).isEmpty())
        assertTrue(jar.loadForRequest("https://gw.example.com/".toHttpUrl()).isEmpty())
    }

    @Test
    fun `language cookie is sent alongside the session`() {
        val jar = SessionCookieJar()
        jar.restore("abc")
        jar.setLanguage("jp")
        assertEquals(mapOf("PHPSESSID" to "abc", "language" to "jp"), jar.loadForRequest(portal).associate { it.name to it.value })
    }

    @Test
    fun `cookies from other hosts never replace the session`() {
        val jar = SessionCookieJar()
        jar.restore("abc")
        val other = "https://evil.example.com/".toHttpUrl()
        jar.saveFromResponse(other, listOf(Cookie.parse(other, "PHPSESSID=x; Path=/")!!))

        assertEquals("abc", jar.sessionId)
    }
}
