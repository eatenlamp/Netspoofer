// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package netspoofer.full

import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.color.DynamicColors
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    val prefs by lazy { getSharedPreferences("netspoofer", MODE_PRIVATE) }

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        updateLocale()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)

        setupTabs()
    }

    private fun setupTabs() {
        val adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 7

            override fun createFragment(position: Int): Fragment {
                val layoutId = when (position) {
                    0 -> R.layout.fragment_spoof
                    1 -> R.layout.fragment_scan
                    2 -> R.layout.fragment_profiles
                    3 -> R.layout.fragment_automation
                    4 -> R.layout.fragment_nonroot
                    5 -> R.layout.fragment_deviceinfo
                    6 -> R.layout.fragment_settings
                    else -> R.layout.fragment_spoof
                }
                return TabFragment.newInstance(layoutId)
            }
        }

        viewPager.adapter = adapter

        val tabTitles = arrayOf(
            getString(R.string.tab_spoofing),
            getString(R.string.tab_scan),
            getString(R.string.tab_profiles),
            getString(R.string.tab_automation),
            getString(R.string.tab_non_root),
            getString(R.string.tab_info),
            getString(R.string.tab_settings)
        )

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    fun runRootCommand(command: String): Boolean = runRootCommands(listOf(command))

    fun runRootCommands(commands: List<String>): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            for (command in commands) {
                outputStream.writeBytes("$command\n")
            }
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            outputStream.close()
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }

    fun getProp(name: String): String = executeWithOutput("getprop $name")

    fun executeWithOutput(command: String): String {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            reader.close()
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            ""
        } finally {
            process?.destroy()
        }
    }

    fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("NetSpoofer", text)
        clipboard.setPrimaryClip(clip)
        showToast("Copied to clipboard")
    }

    fun showNotification(title: String, text: String) {
        try {
            val channelId = "netspoofer_channel"
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            val channel = android.app.NotificationChannel(channelId, "NetSpoofer", android.app.NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
            
            val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setContentTitle(title).setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true).build()
            notificationManager.notify(1, notification)
        } catch (_: Exception) {}
    }

    fun exportSettings(data: String) {
        try {
            val file = File(getExternalFilesDir(null), "netspoofer_backup.txt")
            file.writeText(data)
            showToast("Exported to ${file.absolutePath}")
        } catch (e: Exception) {
            showToast("Export failed: ${e.message}")
        }
    }

    private fun updateLocale() {
        val lang = prefs.getString("language", "en") ?: "en"
        val currentLocale = resources.configuration.locales[0]
        if (currentLocale.language == lang) return

        val locale = java.util.Locale(lang)
        java.util.Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}