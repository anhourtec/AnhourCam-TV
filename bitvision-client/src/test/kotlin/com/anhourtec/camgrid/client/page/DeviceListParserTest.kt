package com.anhourtec.camgrid.client.page

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceListParserTest {
    @Test
    fun `parses devices with names, status, type and channels`() {
        val devices = DeviceListParser.parse(Fixtures.spacePage())

        assertEquals(2, devices.size)
        val a = devices[0]
        assertEquals(Fixtures.DEVICE_A, a.deviceId)
        assertEquals("Front & Back", a.name)
        assertEquals("NVR", a.typeName)
        assertTrue(a.online)
        assertEquals(listOf(Channel(0, "CH1"), Channel(1, "Gate")), a.channels)

        val b = devices[1]
        assertEquals(Fixtures.DEVICE_B, b.deviceId)
        assertEquals("DVR", b.typeName)
        assertFalse(b.online)
        assertEquals(emptyList<Channel>(), b.channels)
    }

    @Test
    fun `reads the account name from the page header`() {
        assertEquals("user@example.com", DeviceListParser.accountName(Fixtures.spacePage()))
        assertEquals(null, DeviceListParser.accountName("<html></html>"))
    }

    @Test
    fun `detects the login page`() {
        assertTrue(DeviceListParser.isLoginPage("""<input name="yzm" id="yzm">"""))
        assertFalse(DeviceListParser.isLoginPage(Fixtures.spacePage()))
    }
}
