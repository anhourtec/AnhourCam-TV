package com.anhourtec.camgrid.ipcam

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.UUID

/** A camera found on the local network. */
data class FoundCamera(
    val host: String,
    /** RTSP port, or null if only ONVIF answered. */
    val rtspPort: Int?,
    /** ONVIF device service URL, if the camera announced one. */
    val onvifUrl: String?,
)

/**
 * Finds cameras on the TV's own /24 subnet: an ONVIF WS-Discovery probe (many cameras answer it)
 * plus a quick TCP check of the usual RTSP ports on every address (many don't). Only private
 * (RFC 1918) subnets are scanned; nothing leaves the local network.
 */
class CameraScanner(
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val parallel: Int = PARALLEL,
) {
    suspend fun scan(localAddress: Inet4Address): List<FoundCamera> =
        coroutineScope {
            require(localAddress.isSiteLocalAddress) { "only private networks are scanned" }
            val onvif = async(Dispatchers.IO) { wsDiscovery() }
            val prefix = localAddress.hostAddress.substringBeforeLast('.')
            val gate = Semaphore(parallel)
            val rtsp =
                (1..LAST_HOST)
                    .map { "$prefix.$it" }
                    .filter { it != localAddress.hostAddress }
                    .map { host ->
                        async(Dispatchers.IO) { gate.withPermit { RTSP_PORTS.firstOrNull { open(host, it) }?.let { host to it } } }
                    }.awaitAll()
                    .filterNotNull()
                    .toMap()
            val onvifHosts = onvif.await().filterKeys { it.startsWith("$prefix.") }
            (rtsp.keys + onvifHosts.keys)
                .sortedBy { it.substringAfterLast('.').toInt() }
                .map { FoundCamera(it, rtsp[it], onvifHosts[it]) }
        }

    private fun open(
        host: String,
        port: Int,
    ): Boolean =
        runCatching {
            Socket().use { it.connect(InetSocketAddress(host, port), connectTimeoutMs) }
            true
        }.getOrDefault(false)

    /** Host → ONVIF device service URL, from WS-Discovery replies within [PROBE_WAIT_MS]. */
    private suspend fun wsDiscovery(): Map<String, String> =
        withContext(Dispatchers.IO) {
            val found = mutableMapOf<String, String>()
            runCatching {
                DatagramSocket().use { socket ->
                    socket.soTimeout = PROBE_WAIT_MS
                    val probe = probe().toByteArray()
                    socket.send(DatagramPacket(probe, probe.size, InetAddress.getByName(MULTICAST), DISCOVERY_PORT))
                    val buffer = ByteArray(REPLY_BYTES)
                    val end = System.currentTimeMillis() + PROBE_WAIT_MS
                    while (System.currentTimeMillis() < end) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        try {
                            socket.receive(packet)
                        } catch (_: SocketTimeoutException) {
                            break
                        }
                        val host = packet.address.hostAddress
                        val reply = String(packet.data, 0, packet.length)
                        xAddr(reply, host)?.let { found[host] = it }
                    }
                }
            }
            found
        }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 400
        private const val PARALLEL = 64
        private const val LAST_HOST = 254
        val RTSP_PORTS = listOf(554, 8554)
        private const val MULTICAST = "239.255.255.250" // WS-Discovery standard group, check-redaction:allow
        private const val DISCOVERY_PORT = 3702
        private const val PROBE_WAIT_MS = 2_500
        private const val REPLY_BYTES = 16 * 1024

        private fun probe() =
            """<?xml version="1.0" encoding="UTF-8"?><e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope" """ +
                """xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing" """ +
                """xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery" """ +
                """xmlns:dn="http://www.onvif.org/ver10/network/wsdl"><e:Header><w:MessageID>uuid:${UUID.randomUUID()}</w:MessageID>""" +
                """<w:To e:mustUnderstand="true">urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>""" +
                """<w:Action e:mustUnderstand="true">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action></e:Header>""" +
                """<e:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe></e:Body></e:Envelope>"""

        /** The device service URL from a ProbeMatch, preferring one on the replying host. */
        fun xAddr(
            reply: String,
            host: String,
        ): String? {
            val addrs =
                OnvifClient
                    .tag(reply, "XAddrs")
                    ?.split(' ')
                    ?.filter { it.startsWith("http") }
                    .orEmpty()
            return addrs.firstOrNull { "//$host" in it } ?: addrs.firstOrNull()
        }
    }
}
