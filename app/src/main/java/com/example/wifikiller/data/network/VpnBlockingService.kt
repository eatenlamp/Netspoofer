// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package netspoofer.full.data.network

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import timber.log.Timber
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Сервис для блокировки интернет-трафика через VpnService (без root).
 * Перехватывает IP-пакеты и дропает те, которые принадлежат заблокированным адресам.
 */
class VpnBlockingService : VpnService(), Runnable {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    
    // Список заблокированных IP (IPv4)
    private val blockedIps = ConcurrentHashMap.newKeySet<String>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
            ACTION_BLOCK -> {
                intent.getStringExtra(EXTRA_IP)?.let { blockedIps.add(it) }
            }
            ACTION_UNBLOCK -> {
                intent.getStringExtra(EXTRA_IP)?.let { blockedIps.remove(it) }
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnThread != null) return
        
        val builder = Builder()
            .setSession("WifiKillerBlocking")
            .addAddress("10.0.0.1", 24)
            .addRoute("0.0.0.0", 0) // Перехватываем весь IPv4 трафик
            
        vpnInterface = builder.establish()
        vpnThread = Thread(this, "VpnBlockingThread").apply { start() }
        Timber.i("VPN Blocking Service started")
    }

    private fun stopVpn() {
        vpnThread?.interrupt()
        vpnThread = null
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
        Timber.i("VPN Blocking Service stopped")
    }

    override fun run() {
        try {
            val input = FileInputStream(vpnInterface?.fileDescriptor)
            val output = FileOutputStream(vpnInterface?.fileDescriptor)
            val buffer = ByteBuffer.allocate(32767)

            while (!Thread.interrupted()) {
                buffer.clear()
                val length = input.read(buffer.array())
                
                if (length > 0) {
                    buffer.limit(length)
                    if (shouldForwardPacket(buffer)) {
                        output.write(buffer.array(), 0, length)
                    } else {
                        Timber.d("Packet dropped for blocked IP")
                    }
                }
                Thread.sleep(10) // Небольшая задержка для предотвращения перегрузки CPU
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in VPN loop")
        } finally {
            stopVpn()
        }
    }

    /**
     * Анализирует IP-заголовок пакета и решает, нужно ли его пропускать.
     */
    private fun shouldForwardPacket(packet: ByteBuffer): Boolean {
        if (packet.remaining() < 20) return true // Слишком короткий для IP заголовка
        
        val version = (packet.get(0).toInt() shr 4) and 0x0F
        if (version != 4) return true // Пока блокируем только IPv4
        
        // Извлекаем Source IP (байты 12-15) или Destination IP (байты 16-19)
        // В зависимости от того, блокируем мы входящий или исходящий трафик.
        // Для "Wi-Fi Killer" на самом устройстве мы блокируем исходящие пакеты к заблокированным целям
        // или имитируем поведение шлюза.
        
        val destIp = getIpAddress(packet, 16)
        return !blockedIps.contains(destIp)
    }

    private fun getIpAddress(packet: ByteBuffer, offset: Int): String {
        return (packet.get(offset).toInt() and 0xFF).toString() + "." +
               (packet.get(offset + 1).toInt() and 0xFF).toString() + "." +
               (packet.get(offset + 2).toInt() and 0xFF).toString() + "." +
               (packet.get(offset + 3).toInt() and 0xFF).toString()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "netspoofer.full.START"
        const val ACTION_STOP = "netspoofer.full.STOP"
        const val ACTION_BLOCK = "netspoofer.full.BLOCK"
        const val ACTION_UNBLOCK = "netspoofer.full.UNBLOCK"
        const val EXTRA_IP = "extra_ip"
    }
}
