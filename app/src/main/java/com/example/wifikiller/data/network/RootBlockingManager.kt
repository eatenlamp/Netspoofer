package netspoofer.full.data.network

import timber.log.Timber
import java.io.DataOutputStream

/**
 * Менеджер для блокировки трафика через root (iptables/ip route).
 */
object RootBlockingManager {

    fun blockIp(ip: String): Boolean {
        return try {
            // Используем iptables для блокировки пересылки пакетов
            executeRootCommand("iptables -I FORWARD -s $ip -j DROP")
            // Дополнительно можно добавить в blackhole для локального трафика
            executeRootCommand("ip route add blackhole $ip")
            Timber.i("IP $ip blocked via root")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to block IP $ip")
            false
        }
    }

    fun unblockIp(ip: String): Boolean {
        return try {
            executeRootCommand("iptables -D FORWARD -s $ip -j DROP")
            executeRootCommand("ip route del blackhole $ip")
            Timber.i("IP $ip unblocked via root")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to unblock IP $ip")
            false
        }
    }

    private fun executeRootCommand(command: String): Int {
        val process = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(process.outputStream)
        os.writeBytes("$command\n")
        os.writeBytes("exit\n")
        os.flush()
        return process.waitFor()
    }
}
