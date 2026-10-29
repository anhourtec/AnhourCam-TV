package com.anhourtec.camgrid.tv.data

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/** The TV's own address on the home network (Wi-Fi or Ethernet), for the pairing server. */
object LanAddress {
    fun find(): InetAddress? =
        runCatching {
            NetworkInterface
                .getNetworkInterfaces()
                .toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        }.getOrNull()
}
