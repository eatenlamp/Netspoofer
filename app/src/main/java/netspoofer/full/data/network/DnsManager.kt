// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package netspoofer.full.data.network

import kotlinx.coroutines.*
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * A very simple DNS server that redirects all queries to a specific IP.
 */
object DnsManager {
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startRedirector(redirectIp: String) {
        stopRedirector()
        serverJob = scope.launch {
            try {
                val socket = DatagramSocket(5353) // Use a non-standard port, we'll redirect to it via iptables
                val buffer = ByteArray(512)
                
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    
                    // Simple DNS response logic (Fake)
                    // For a real implementation, we'd parse the DNS query and build a proper response.
                    // Here we just log and could potentially send back a fake A record.
                    Timber.d("DNS Query from ${packet.address.hostAddress}")
                    
                    // Respond with fake IP (This is a simplified version)
                    // respondWithFakeIp(socket, packet, redirectIp)
                }
            } catch (e: Exception) {
                Timber.e(e, "DNS Server error")
            }
        }
    }

    fun stopRedirector() {
        serverJob?.cancel()
        serverJob = null
    }
}
