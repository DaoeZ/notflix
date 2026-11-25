package com.nuvio.tv.core.server

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

object DeviceIpAddress {

    fun get(context: Context): String? {
        // Try WifiManager first
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiIp = runCatching { wifiManager?.connectionInfo?.ipAddress ?: 0 }.getOrDefault(0)
        if (wifiIp != 0) {
            val formatted = formatIp(wifiIp)
            if (!formatted.startsWith("169.254.") && !formatted.startsWith("127.")) {
                return formatted
            }
        }

        // Fallback: iterate active physical NetworkInterfaces (Ethernet / Wi-Fi) preferring RFC 1918 site-local IPv4
        return try {
            val candidates = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { iface ->
                    runCatching { iface.isUp && !iface.isLoopback && !iface.isVirtual }.getOrDefault(false) &&
                        !iface.name.startsWith("tun", ignoreCase = true) &&
                        !iface.name.startsWith("ppp", ignoreCase = true) &&
                        !iface.name.startsWith("rmnet", ignoreCase = true)
                }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            (candidates.firstOrNull { it.isSiteLocalAddress } ?: candidates.firstOrNull())?.hostAddress
        } catch (e: Exception) {
            null
        }
    }

    private fun formatIp(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }
}
