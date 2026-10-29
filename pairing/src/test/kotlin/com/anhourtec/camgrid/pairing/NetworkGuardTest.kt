package com.anhourtec.camgrid.pairing

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress

private const val TV_IP = "10.20.30.40" // check-redaction:allow fake test address

class NetworkGuardTest {
    @Test
    fun `only local network addresses are private`() {
        listOf("10.20.30.20", "10.0.0.5", "172.16.3.4", "169.254.1.1", "127.0.0.1", "fd12:3456::1", "fe80::1", "::1")
            .forEach { assertTrue(NetworkGuard.isPrivate(InetAddress.getByName(it)), it) }
        listOf("8.8.8.8", "203.0.113.9", "172.32.0.1", "2001:4860::8888")
            .forEach { assertFalse(NetworkGuard.isPrivate(InetAddress.getByName(it)), it) }
    }

    @Test
    fun `host header must be the TV's own address`() {
        assertTrue(NetworkGuard.isExpectedHost("$TV_IP:8743", TV_IP, 8743))
        assertTrue(NetworkGuard.isExpectedHost(TV_IP, TV_IP, 8743))
        assertFalse(NetworkGuard.isExpectedHost("evil.example.com:8743", TV_IP, 8743))
        assertFalse(NetworkGuard.isExpectedHost(null, TV_IP, 8743))
    }

    @Test
    fun `rate limit uses a sliding window per address`() {
        var now = 0L
        val guard = NetworkGuard(clock = { now }, maxRequestsPerWindow = 3, windowMs = 1000)
        repeat(3) { assertTrue(guard.allow("a")) }
        assertFalse(guard.allow("a"))
        assertTrue(guard.allow("b"))
        now = 1000
        assertTrue(guard.allow("a"))
    }
}
