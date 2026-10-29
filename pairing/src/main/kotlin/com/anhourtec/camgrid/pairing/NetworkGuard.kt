package com.anhourtec.camgrid.pairing

import java.net.Inet6Address
import java.net.InetAddress

/** Who may talk to the pairing server, and how often. */
class NetworkGuard(
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxRequestsPerWindow: Int = DEFAULT_MAX_REQUESTS,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
) {
    private val hits = mutableMapOf<String, ArrayDeque<Long>>()

    /** Sliding-window rate limit per client address. */
    @Synchronized
    fun allow(ip: String): Boolean {
        val now = clock()
        val window = hits.getOrPut(ip) { ArrayDeque() }
        while (window.isNotEmpty() && window.first() <= now - windowMs) window.removeFirst()
        if (window.size >= maxRequestsPerWindow) return false
        window.addLast(now)
        return true
    }

    companion object {
        const val DEFAULT_MAX_REQUESTS = 30
        const val DEFAULT_WINDOW_MS = 60_000L
        private const val ULA_PREFIX = 0xFC
        private const val ULA_MASK = 0xFE

        /** RFC 1918, link-local, loopback and IPv6 unique-local addresses only. */
        fun isPrivate(address: InetAddress): Boolean =
            address.isSiteLocalAddress ||
                address.isLinkLocalAddress ||
                address.isLoopbackAddress ||
                (address is Inet6Address && (address.address[0].toInt() and ULA_MASK) == ULA_PREFIX)

        /**
         * DNS-rebinding defence: the browser must have addressed us by our own IP, never by a
         * hostname a malicious site could point at us.
         */
        fun isExpectedHost(
            hostHeader: String?,
            ownIp: String,
            port: Int,
        ): Boolean = hostHeader == "$ownIp:$port" || hostHeader == ownIp
    }
}
