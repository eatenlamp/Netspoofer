// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package netspoofer.full.data.network

import timber.log.Timber
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*

/**
 * Helper for working with ARP and network scanning.
 */
object ArpHelper {

    fun getSubnet(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress
                        if (ip != null && ip.startsWith("192.168.") || ip?.startsWith("10.") == true) {
                            return ip.substring(0, ip.lastIndexOf(".") + 1)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting subnet")
        }
        return null
    }

    /**
     * Scans the subnet by pinging all IPs.
     */
    fun scanSubnet(subnet: String, main: netspoofer.full.MainActivity): List<Pair<String, String>> {
        // Group pings into batches to avoid overwhelming the system
        val batches = (1..254).chunked(50)
        for (batch in batches) {
            val commands = batch.map { i -> "ping -c 1 -W 1 $subnet$i > /dev/null 2>&1 &" }
            main.runRootCommands(commands)
        }
        
        // Wait for pings and ARP resolution
        Thread.sleep(3000)
        
        return getLocalDevices(main)
    }

    fun getLocalDevices(main: netspoofer.full.MainActivity): List<Pair<String, String>> {
        val devices = mutableMapOf<String, String>()
        
        // 1. Try reading /proc/net/arp (Works on older Android or with specific perms)
        try {
            val arpFile = java.io.File("/proc/net/arp")
            if (arpFile.exists()) {
                arpFile.readLines().drop(1).forEach { line ->
                    val parts = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
                    if (parts.size >= 4) {
                        val ip = parts[0]
                        val mac = parts[3]
                        if (isValidMac(mac)) {
                            devices[ip] = mac
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d("Proc ARP failed: ${e.message}")
        }

        // 2. Try 'ip neighbor' via root (More reliable on Android 10+)
        try {
            val output = main.executeWithOutput("ip neighbor show")
            output.split("\n").forEach { line ->
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 5) {
                    val ip = parts[0]
                    val mac = parts[4]
                    if (isValidMac(mac)) {
                        devices[ip] = mac
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d("IP neighbor failed: ${e.message}")
        }

        return devices.toList()
    }

    private fun isValidMac(mac: String): Boolean {
        return mac.isNotEmpty() && mac != "00:00:00:00:00:00" && mac.contains(":")
    }

    fun getMacFromIp(ip: String, main: netspoofer.full.MainActivity): String? {
        return getLocalDevices(main).find { it.first == ip }?.second
    }
}
