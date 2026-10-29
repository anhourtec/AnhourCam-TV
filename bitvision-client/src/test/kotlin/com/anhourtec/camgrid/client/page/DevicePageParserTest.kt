package com.anhourtec.camgrid.client.page

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DevicePageParserTest {
    @Test
    fun `parses device info and gateway endpoints from single-quoted JS literals`() {
        val detail = DevicePageParser.parse(Fixtures.devicePage(Fixtures.DEVICE_JSON, Fixtures.SERVICE_JSON))

        assertEquals(Fixtures.DEVICE_A, detail.deviceId)
        assertEquals("Bob's NVR", detail.name)
        assertEquals("NVR_16", detail.model)
        assertEquals(16, detail.channelCount)
        assertTrue(detail.online)
        assertEquals("admin", detail.credentials.user)
        assertEquals("p'w\"d", detail.credentials.password)
        assertEquals("wss://gw.example.com/userGateWay/1", detail.service.userGateUrl)
        assertEquals("wss://gw.example.com/stream/1", detail.service.streamUrl)
        assertEquals("192.0.2.11", detail.service.streamRelayIp)
        assertEquals(35009, detail.service.streamPort)
    }

    @Test
    fun `never prints NVR credentials`() {
        val detail = DevicePageParser.parse(Fixtures.devicePage(Fixtures.DEVICE_JSON, Fixtures.SERVICE_JSON))
        assertFalse(detail.toString().contains("p'w"))
        assertFalse(detail.toString().contains("admin"))
    }

    @Test
    fun `refuses gateway URLs without TLS`() {
        val plain = Fixtures.SERVICE_JSON.replace("wss:", "ws:")
        assertThrows<PageFormatException> { DevicePageParser.parse(Fixtures.devicePage(Fixtures.DEVICE_JSON, plain)) }
    }

    @Test
    fun `fails clearly when the page has no device data`() {
        assertThrows<PageFormatException> { DevicePageParser.parse("<html></html>") }
    }
}
