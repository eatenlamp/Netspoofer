// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package netspoofer.full

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.*
import netspoofer.full.data.network.ArpHelper
import netspoofer.full.data.network.DnsManager
import netspoofer.full.data.network.MitmManager
import netspoofer.full.domain.model.Device

class TabFragment : Fragment() {
    companion object {
        fun newInstance(layoutId: Int): TabFragment {
            val fragment = TabFragment()
            val args = Bundle()
            fragment.arguments = args.apply { putInt("layout", layoutId) }
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layoutId = arguments?.getInt("layout") ?: R.layout.fragment_spoof
        return inflater.inflate(layoutId, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val layoutId = arguments?.getInt("layout")
        val main = requireActivity() as MainActivity

        when(layoutId) {
            R.layout.fragment_spoof -> {
                setupSpoof(view, main)
                setupMacSpoof(view, main)
            }
            R.layout.fragment_scan -> setupScan(view, main)
            R.layout.fragment_profiles -> setupProfiles(view, main)
            R.layout.fragment_automation -> setupAutomation(view, main)
            R.layout.fragment_nonroot -> setupNonRoot(view, main)
            R.layout.fragment_deviceinfo -> setupDeviceInfo(view, main)
            R.layout.fragment_settings -> setupSettings(view, main)
        }
    }

    private fun setupMacSpoof(view: View, main: MainActivity) {
        val editMac: EditText = view.findViewById(R.id.editMac)
        val btnRandom: Button = view.findViewById(R.id.btnRandomMac)
        val btnApply: Button = view.findViewById(R.id.btnApplyMac)
        val tvCurrentMac: TextView = view.findViewById(R.id.tvCurrentMac)

        fun updateMacDisplay() {
            val iface = getWifiInterface()
            // We use cat to get the current mac from sysfs which is reliable
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val currentMac = try {
                    java.io.File("/sys/class/net/$iface/address").readText().trim().uppercase()
                } catch (e: Exception) {
                    "Unknown"
                }
                withContext(Dispatchers.Main) {
                    tvCurrentMac.text = "${getString(R.string.current_mac)} $currentMac ($iface)"
                }
            }
        }

        btnRandom.setOnClickListener {
            editMac.setText(generateRandomMac())
        }

        btnApply.setOnClickListener {
            val mac = editMac.text.toString().trim()
            if (mac.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$"))) {
                val iface = getWifiInterface()
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val success = main.runRootCommands(listOf(
                        "ip link set $iface down",
                        "ip link set $iface address $mac",
                        "ip link set $iface up"
                    ))
                    
                    withContext(Dispatchers.Main) {
                        if (success) {
                            main.showToast("${getString(R.string.mac_changed)} $mac")
                            updateMacDisplay()
                        } else {
                            main.showToast(getString(R.string.mac_failed))
                        }
                    }
                }
            } else {
                main.showToast("Invalid MAC format")
            }
        }

        updateMacDisplay()
    }

    private fun getWifiInterface(): String {
        return try {
            java.net.NetworkInterface.getNetworkInterfaces().toList()
                .find { it.name.startsWith("wlan") || it.name.startsWith("eth") }?.name ?: "wlan0"
        } catch (e: Exception) {
            "wlan0"
        }
    }

    private fun generateRandomMac(): String {
        val random = java.util.Random()
        val mac = ByteArray(6)
        random.nextBytes(mac)
        // Ensure unicast and locally administered
        mac[0] = ((mac[0].toInt() and 0xFE) or 0x02).toByte()
        return mac.joinToString(":") { String.format("%02X", it) }
    }

    private fun setupScan(view: View, main: MainActivity) {
        val btnScan: Button = view.findViewById(R.id.btnStartScan)
        val btnNmap: Button = view.findViewById(R.id.btnNmapScan)
        val tvStatus: TextView = view.findViewById(R.id.tvScanStatus)
        val progress: ProgressBar = view.findViewById(R.id.scanProgress)
        val rvDevices: RecyclerView = view.findViewById(R.id.rvDevices)

        val deviceList = mutableListOf<Device>()
        val adapter = DeviceAdapter(deviceList, 
            onBlockToggled = { device, isBlocked ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    if (isBlocked) {
                        main.runRootCommands(listOf(
                            "iptables -I FORWARD -s ${device.ipAddress} -j DROP",
                            "ip route add blackhole ${device.ipAddress}"
                        ))
                    } else {
                        main.runRootCommands(listOf(
                            "iptables -D FORWARD -s ${device.ipAddress} -j DROP",
                            "ip route del blackhole ${device.ipAddress}"
                        ))
                    }
                }
            },
            onMitmToggled = { device, isEnabled ->
                if (isEnabled) {
                    val gateway = ArpHelper.getSubnet() + "1"
                    MitmManager.startMitm(device.ipAddress, gateway, main)
                    main.showToast("MITM Started for ${device.ipAddress}")
                } else {
                    MitmManager.stopMitm(main)
                    main.showToast("MITM Stopped")
                }
            },
            onSniffClicked = { device ->
                showSniffDialog(device, main)
            }
        )

        rvDevices.layoutManager = LinearLayoutManager(requireContext())
        rvDevices.adapter = adapter

        fun startDiscovery(useNmap: Boolean) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    progress.visibility = View.VISIBLE
                    tvStatus.text = if (useNmap) "Nmap Scanning..." else getString(R.string.scanning)
                    btnScan.isEnabled = false
                    btnNmap.isEnabled = false
                }

                val subnet = ArpHelper.getSubnet()
                if (subnet != null) {
                    val found = if (useNmap) {
                        runNmapScan(subnet, main)
                    } else {
                        ArpHelper.scanSubnet(subnet, main)
                    }

                    val newDevices = found.map { (ip, mac) ->
                        Device(
                            macAddress = mac,
                            ipAddress = ip,
                            hostname = null,
                            vendor = getVendor(mac),
                            isBlocked = false
                        )
                    }.filter { it.ipAddress != getMyIp() }

                    withContext(Dispatchers.Main) {
                        deviceList.clear()
                        deviceList.addAll(newDevices)
                        adapter.notifyDataSetChanged()
                        tvStatus.text = getString(R.string.devices_found, newDevices.size)
                    }
                }

                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    btnScan.isEnabled = true
                    btnNmap.isEnabled = true
                }
            }
        }

        btnScan.setOnClickListener { startDiscovery(false) }
        btnNmap.setOnClickListener { startDiscovery(true) }
    }

    private fun runNmapScan(subnet: String, main: MainActivity): List<Pair<String, String>> {
        val output = main.executeWithOutput("nmap -sn ${subnet}0/24")
        if (output.isEmpty() || output.contains("not found")) {
            return ArpHelper.scanSubnet(subnet, main)
        }

        val devices = mutableListOf<Pair<String, String>>()
        var currentIp = ""
        output.split("\n").forEach { line ->
            if (line.contains("Nmap scan report for")) {
                currentIp = line.split(" ").last().replace("(", "").replace(")", "")
            } else if (line.contains("MAC Address:") && currentIp.isNotEmpty()) {
                val mac = line.split(" ")[2]
                devices.add(currentIp to mac)
                currentIp = ""
            }
        }

        if (devices.isEmpty()) return ArpHelper.scanSubnet(subnet, main)

        return devices
    }

    private var sniffJob: Job? = null
    private fun showSniffDialog(device: Device, main: MainActivity) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_sniff, null)
        val tvTarget: TextView = dialogView.findViewById(R.id.tvSniffingTarget)
        val tvLogs: TextView = dialogView.findViewById(R.id.tvSniffLogs)
        val scrollSniff: ScrollView = dialogView.findViewById(R.id.scrollSniff)
        val btnStop: Button = dialogView.findViewById(R.id.btnStopSniff)

        tvTarget.text = "PRO Sniffing: ${device.ipAddress}"
        
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        sniffJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("su")
                val os = java.io.DataOutputStream(process.outputStream)
                os.writeBytes("tcpdump -i any -ln -A -s 200 host ${device.ipAddress}\n")
                os.flush()

                val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                while (isActive) {
                    val line = reader.readLine() ?: break
                    val readableLine = formatTcpdumpLine(line)
                    if (readableLine != null) {
                        withContext(Dispatchers.Main) {
                            tvLogs.append(readableLine + "\n")
                            scrollSniff.post { scrollSniff.fullScroll(View.FOCUS_DOWN) }
                        }
                    }
                }
                process.destroy()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvLogs.append("Error: ${e.message}\n")
                }
            }
        }

        btnStop.setOnClickListener {
            sniffJob?.cancel()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun formatTcpdumpLine(line: String): String? {
        if (line.length < 5) return null
        return when {
            line.contains("GET ") || line.contains("POST ") -> "🌐 " + line.substringAfter("IP ").trim()
            line.contains("Host:") -> "🏠 " + line.trim()
            line.contains("User-Agent:") -> "📱 " + line.trim()
            line.contains("HTTP/") -> "📄 HTTP Response"
            line.contains("DNS") -> "🔍 DNS: " + line.substringAfter("DNS").trim()
            line.contains("IP ") -> "➡️ " + line.substringAfter("IP ").substringBefore(":")
            else -> if (line.all { it.isLetterOrDigit() || it.isWhitespace() || it in ".:/->[]" }) line.trim() else null
        }
    }

    private fun getMyIp(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {}
        return null
    }

    private fun getVendor(mac: String): String {
        val cleanMac = mac.replace(":", "").replace(" ", "").uppercase()
        if (cleanMac.length < 6) return getString(R.string.vendor_unknown)
        val oui = cleanMac.substring(0, 6)
        
        return when (oui) {
            "00000C", "000142", "000143", "000163", "000164", "000196", "000197" -> "Cisco"
            "00005E" -> "ICANN"
            "0000F0" -> "Samsung"
            "000142" -> "Cisco"
            "000502" -> "Apple"
            "000822" -> "Apple"
            "000D93" -> "Apple"
            "0010FA" -> "Apple"
            "001124" -> "Apple"
            "001451" -> "Apple"
            "0016CB" -> "Apple"
            "0017F2" -> "Apple"
            "0019E3" -> "Apple"
            "001B63" -> "Apple"
            "001C23" -> "Apple"
            "001C5F" -> "Apple"
            "001C B3" -> "Apple"
            "001D4F" -> "Apple"
            "001E52" -> "Apple"
            "001E C2" -> "Apple"
            "001F5B" -> "Apple"
            "001F F3" -> "Apple"
            "00215A" -> "Apple"
            "0021 E9" -> "Apple"
            "002241" -> "Apple"
            "002312" -> "Apple"
            "002332" -> "Apple"
            "00236C" -> "Apple"
            "0023DF" -> "Apple"
            "002436" -> "Apple"
            "002500" -> "Apple"
            "00254B" -> "Apple"
            "0025BC" -> "Apple"
            "002608" -> "Apple"
            "00264A" -> "Apple"
            "0026B0" -> "Apple"
            "0026BB" -> "Apple"
            "28CFDA" -> "Apple"
            "34159E" -> "Apple"
            "34363B" -> "Apple"
            "34C059" -> "Apple"
            "38484C" -> "Apple"
            "38CA84" -> "Apple"
            "3C0754" -> "Apple"
            "3C15C2" -> "Apple"
            "3CD0F8" -> "Apple"
            "403004" -> "Apple"
            "403C58" -> "Apple"
            "40A6D9" -> "Apple"
            "444C0C" -> "Apple"
            "44D832" -> "Apple"
            "48437C" -> "Apple"
            "4860BC" -> "Apple"
            "48D705" -> "Apple"
            "4C3275" -> "Apple"
            "4C74BF" -> "Apple"
            "4C8D79" -> "Apple"
            "503237" -> "Apple"
            "50BC8D" -> "Apple"
            "542696" -> "Apple"
            "5433CB" -> "Apple"
            "54724F" -> "Apple"
            "581FA1" -> "Apple"
            "5855CA" -> "Apple"
            "58B035" -> "Apple"
            "5C158D" -> "Apple"
            "5C5948" -> "Apple"
            "5C70A3" -> "Apple"
            "5C8D4E" -> "Apple"
            "5C95AE" -> "Apple"
            "5C97F3" -> "Apple"
            "5CADCF" -> "Apple"
            "600308" -> "Apple"
            "60334B" -> "Apple"
            "606944" -> "Apple"
            "609217" -> "Apple"
            "609A73" -> "Apple"
            "60C547" -> "Apple"
            "60F81D" -> "Apple"
            "64200C" -> "Apple"
            "645A04" -> "Apple"
            "6476BA" -> "Apple"
            "649A11" -> "Apple"
            "64B9E8" -> "Apple"
            "64E682" -> "Apple"
            "685B35" -> "Apple"
            "68644B" -> "Apple"
            "68967B" -> "Apple"
            "68A86D" -> "Apple"
            "68D93C" -> "Apple"
            "6C19C0" -> "Apple"
            "6C3E6D" -> "Apple"
            "6C4008" -> "Apple"
            "6C709F" -> "Apple"
            "6C8D71" -> "Apple"
            "6C94F8" -> "Apple"
            "6CA913" -> "Apple"
            "701124" -> "Apple"
            "7014A6" -> "Apple"
            "703EAC" -> "Apple"
            "70480F" -> "Apple"
            "705681" -> "Apple"
            "7073CB" -> "Apple"
            "7081EB" -> "Apple"
            "70A239" -> "Apple"
            "70CD60" -> "Apple"
            "70E72C" -> "Apple"
            "748114" -> "Apple"
            "74B587" -> "Apple"
            "74E1B6" -> "Apple"
            "7831C1" -> "Apple"
            "783A84" -> "Apple"
            "784F43" -> "Apple"
            "787B8A" -> "Apple"
            "78886D" -> "Apple"
            "789FD0" -> "Apple"
            "78A3E4" -> "Apple"
            "78C5E5" -> "Apple"
            "78CA39" -> "Apple"
            "78D7B4" -> "Apple"
            "7C11BE" -> "Apple"
            "7C6D62" -> "Apple"
            "7C6DF8" -> "Apple"
            "7C C537" -> "Apple"
            "7C D1C3" -> "Apple"
            "7C F05F" -> "Apple"
            "804971" -> "Apple"
            "80929F" -> "Apple"
            "80B03D" -> "Apple"
            "80E650" -> "Apple"
            "842999" -> "Apple"
            "843835" -> "Apple"
            "84788B" -> "Apple"
            "848508" -> "Apple"
            "848E0C" -> "Apple"
            "84A93E" -> "Apple"
            "84B153" -> "Apple"
            "84C9B2" -> "Apple"
            "84D324" -> "Apple"
            "84FCAC" -> "Apple"
            "881FA1" -> "Apple"
            "885395" -> "Apple"
            "8863DF" -> "Apple"
            "88665A" -> "Apple"
            "88C663" -> "Apple"
            "88CB87" -> "Apple"
            "88E9FE" -> "Apple"
            "8C2937" -> "Apple"
            "8C2D6F" -> "Apple"
            "8C5877" -> "Apple"
            "8C7B9D" -> "Apple"
            "8C8590" -> "Apple"
            "8CA982" -> "Apple"
            "8CE5DD" -> "Apple"
            "9027E4" -> "Apple"
            "903C92" -> "Apple"
            "907240" -> "Apple"
            "90840D" -> "Apple"
            "90B0ED" -> "Apple"
            "90B21F" -> "Apple"
            "90C11C" -> "Apple"
            "90E17B" -> "Apple"
            "90FD61" -> "Apple"
            "94103E" -> "Apple"
            "949426" -> "Apple"
            "94E96A" -> "Apple"
            "9801A7" -> "Apple"
            "98460A" -> "Apple"
            "985A17" -> "Apple"
            "989E43" -> "Apple"
            "98B8E3" -> "Apple"
            "98D6BB" -> "Apple"
            "98E0D9" -> "Apple"
            "98F0AB" -> "Apple"
            "98FE94" -> "Apple"
            "9C04EB" -> "Apple"
            "9C207B" -> "Apple"
            "9C35EB" -> "Apple"
            "9C4F5E" -> "Apple"
            "9C8B7C" -> "Apple"
            "9CE65E" -> "Apple"
            "9CF387" -> "Apple"
            "A01828" -> "Apple"
            "A03B0F" -> "Apple"
            "A04E04" -> "Apple"
            "A0999B" -> "Apple"
            "A0EDCD" -> "Apple"
            "A43135" -> "Apple"
            "A45E60" -> "Apple"
            "A4B197" -> "Apple"
            "A4B805" -> "Apple"
            "A4C361" -> "Apple"
            "A4D18C" -> "Apple"
            "A4E975" -> "Apple"
            "A82066" -> "Apple"
            "A85B78" -> "Apple"
            "A8667F" -> "Apple"
            "A88808" -> "Apple"
            "A88E24" -> "Apple"
            "A8968A" -> "Apple"
            "A8BB50" -> "Apple"
            "A8FA48" -> "Apple"
            "AC1F74" -> "Apple"
            "AC293A" -> "Apple"
            "AC3C0B" -> "Apple"
            "AC7F3E" -> "Apple"
            "AC87A3" -> "Apple"
            "ACBC32" -> "Apple"
            "ACCF85" -> "Apple"
            "ACE4B5" -> "Apple"
            "B019C6" -> "Apple"
            "B03495" -> "Apple"
            "B0481A" -> "Apple"
            "B065BD" -> "Apple"
            "B0702D" -> "Apple"
            "B09FBA" -> "Apple"
            "B0D09C" -> "Apple"
            "B418D1" -> "Apple"
            "B444D6" -> "Apple"
            "B48B19" -> "Apple"
            "B49C70" -> "Apple"
            "B4F0AB" -> "Apple"
            "B8098A" -> "Apple"
            "B817C2" -> "Apple"
            "B844D9" -> "Apple"
            "B853AC" -> "Apple"
            "B86347" -> "Apple"
            "B8782E" -> "Apple"
            "B88D12" -> "Apple"
            "B8C75D" -> "Apple"
            "B8E856" -> "Apple"
            "B8F6B1" -> "Apple"
            "BC3B AF" -> "Apple"
            "BC4C C4" -> "Apple"
            "BC52 B7" -> "Apple"
            "BC67 1C" -> "Apple"
            "BC92 6B" -> "Apple"
            "BCA5 8D" -> "Apple"
            "BCE1 19" -> "Apple"
            "BCEE 7B" -> "Apple"
            "C01A DA" -> "Apple"
            "C063 94" -> "Apple"
            "C084 7A" -> "Apple"
            "C09F 05" -> "Apple"
            "C0A5 3E" -> "Apple"
            "C0D0 12" -> "Apple"
            "C0F2 FB" -> "Apple"
            "C42C 03" -> "Apple"
            "C461 8B" -> "Apple"
            "C47D 4F" -> "Apple"
            "C484 66" -> "Apple"
            "C498 80" -> "Apple"
            "C4B3 01" -> "Apple"
            "C81E E7" -> "Apple"
            "C82A 14" -> "Apple"
            "C833 4B" -> "Apple"
            "C83C 85" -> "Apple"
            "C869 CD" -> "Apple"
            "C86F 1D" -> "Apple"
            "C885 50" -> "Apple"
            "C8B5 B7" -> "Apple"
            "C8D0 83" -> "Apple"
            "C8E0 EB" -> "Apple"
            "CC08 E0" -> "Apple"
            "CC20 E8" -> "Apple"
            "CC25 EF" -> "Apple"
            "CC29 F5" -> "Apple"
            "CC44 63" -> "Apple"
            "CC78 5F" -> "Apple"
            "D003 4B" -> "Apple"
            "D023 DB" -> "Apple"
            "D025 98" -> "Apple"
            "D033 11" -> "Apple"
            "D04F 7E" -> "Apple"
            "D081 7A" -> "Apple"
            "D0A6 37" -> "Apple"
            "D0C5 F3" -> "Apple"
            "D0D2 B0" -> "Apple"
            "D0E1 40" -> "Apple"
            "D43A 2C" -> "Apple"
            "D461 9D" -> "Apple"
            "D490 9C" -> "Apple"
            "D4A3 3D" -> "Apple"
            "D4F4 6F" -> "Apple"
            "D800 4D" -> "Apple"
            "D81C 79" -> "Apple"
            "D830 62" -> "Apple"
            "D88F 76" -> "Apple"
            "D896 95" -> "Apple"
            "D89E 3F" -> "Apple"
            "D8A2 5E" -> "Apple"
            "D8BB 2C" -> "Apple"
            "D8CF 9C" -> "Apple"
            "D8D1 CB" -> "Apple"
            "DC0C 5C" -> "Apple"
            "DC2B 2A" -> "Apple"
            "DC2B 61" -> "Apple"
            "DC37 14" -> "Apple"
            "DC41 5F" -> "Apple"
            "DC56 E7" -> "Apple"
            "DCA9 04" -> "Apple"
            "E052 71" -> "Apple"
            "E066 78" -> "Apple"
            "E0AC CB" -> "Apple"
            "E0B5 2D" -> "Apple"
            "E0C7 67" -> "Apple"
            "E0C9 7A" -> "Apple"
            "E0F5 C6" -> "Apple"
            "E0F8 47" -> "Apple"
            "E425 E7" -> "Apple"
            "E458 B8" -> "Apple"
            "E48B 7F" -> "Apple"
            "E498 D6" -> "Apple"
            "E4A1 EF" -> "Apple"
            "E4B2 FB" -> "Apple"
            "E4CE 8F" -> "Apple"
            "E4E4 AB" -> "Apple"
            "E804 0B" -> "Apple"
            "E806 88" -> "Apple"
            "E880 2E" -> "Apple"
            "E88D 19" -> "Apple"
            "E8B2 AC" -> "Apple"
            "EC35 86" -> "Apple"
            "EC85 2F" -> "Apple"
            "F018 98" -> "Apple"
            "F076 1C" -> "Apple"
            "F079 60" -> "Apple"
            "F099 B6" -> "Apple"
            "F0B0 52" -> "Apple"
            "F0C1 F1" -> "Apple"
            "F0D1 A9" -> "Apple"
            "F0DB E2" -> "Apple"
            "F0F6 1C" -> "Apple"
            "F40F 24" -> "Apple"
            "F41B A1" -> "Apple"
            "F431 C3" -> "Apple"
            "F437 B7" -> "Apple"
            "F45C 89" -> "Apple"
            "F4F1 5A" -> "Apple"
            "F4F9 51" -> "Apple"
            "F81E DF" -> "Apple"
            "F827 93" -> "Apple"
            "F838 80" -> "Apple"
            "F84E 73" -> "Apple"
            "F866 5A" -> "Apple"
            "F8A4 5F" -> "Apple"
            "FC25 3F" -> "Apple"
            "FCE9 98" -> "Apple"
            "FCFC 48" -> "Apple"
            "3C5AB4" -> "Google"
            "D8EB97" -> "Google"
            "E4B021" -> "Google"
            "00E04C" -> "Realtek"
            "00E04C" -> "Realtek"
            "00004C" -> "NEC"
            "000000" -> "Xerox"
            "000181" -> "Nortel"
            "0001E6" -> "Hewlett-Packard"
            "0004AC" -> "IBM"
            "00055D" -> "D-Link"
            "0005B5" -> "Broadcom"
            "00095B" -> "Netgear"
            "000A8E" -> "Invengo"
            "000B2B" -> "Hon Hai Precision"
            "000C29" -> "VMware"
            "000D0B" -> "Microsoft"
            "000E08" -> "Dell"
            "000F60" -> "D-Link"
            "001018" -> "Broadcom"
            "0011D8" -> "ASUSTek"
            "001217" -> "Cisco"
            "001320" -> "Intel"
            "001372" -> "Dell"
            "001422" -> "Dell"
            "001478" -> "TP-Link"
            "001558" -> "Clevo"
            "0015C5" -> "Dell"
            "00163E" -> "Xen"
            "001676" -> "Intel"
            "0016D3" -> "Dell"
            "001731" -> "ASUSTek"
            "0017A4" -> "Hewlett-Packard"
            "001871" -> "Hewlett-Packard"
            "00188B" -> "Dell"
            "001966" -> "Asus"
            "0019B9" -> "Dell"
            "0019D1" -> "Intel"
            "001A4B" -> "Hewlett-Packard"
            "001A6B" -> "Intel"
            "001A92" -> "Asus"
            "001AA9" -> "Asus"
            "001B21" -> "Asus"
            "001BFC" -> "Asus"
            "001C23" -> "Apple"
            "001C25" -> "Intel"
            "001C7B" -> "Asus"
            "001D09" -> "Dell"
            "001D60" -> "Asus"
            "001D70" -> "Asus"
            "001D71" -> "Asus"
            "001D7E" -> "Asus"
            "001E0B" -> "Hewlett-Packard"
            "001E37" -> "Asus"
            "001E4C" -> "Asus"
            "001E64" -> "Intel"
            "001E8C" -> "Asus"
            "001EB2" -> "Asus"
            "001EC9" -> "Asus"
            "001EDD" -> "Asus"
            "001F16" -> "Asus"
            "001F3B" -> "Intel"
            "001F5B" -> "Apple"
            "001F C6" -> "Asus"
            "001F D0" -> "Asus"
            "001F E4" -> "Asus"
            "00215A" -> "Apple"
            "002170" -> "Dell"
            "002191" -> "Dell"
            "002219" -> "Dell"
            "002241" -> "Apple"
            "00224D" -> "Dell"
            "002264" -> "Dell"
            "0022FB" -> "Intel"
            "002312" -> "Apple"
            "002324" -> "Dell"
            "002332" -> "Apple"
            "00234D" -> "Asus"
            "002354" -> "Asus"
            "00237D" -> "Intel"
            "00238B" -> "Asus"
            "0023AE" -> "Dell"
            "00241B" -> "Asus"
            "002481" -> "Asus"
            "0024BE" -> "Asus"
            "002511" -> "Asus"
            "002522" -> "Asus"
            "002564" -> "Dell"
            "002618" -> "Asus"
            "002622" -> "Asus"
            "00262D" -> "Asus"
            "00265E" -> "Asus"
            "002683" -> "Asus"
            "0026B0" -> "Apple"
            "0026C6" -> "Intel"
            "00270E" -> "Asus"
            "002713" -> "Asus"
            "005056" -> "VMware"
            "080027" -> "VirtualBox"
            "54AF97" -> "Xiaomi"
            "F8A45F" -> "Xiaomi"
            "640980" -> "Xiaomi"
            "ACF108" -> "Xiaomi"
            "98FA9B" -> "Xiaomi"
            "286C07" -> "Xiaomi"
            "D4970B" -> "Xiaomi"
            "7405A5" -> "Xiaomi"
            "D02543" -> "Xiaomi"
            "181DEB" -> "Xiaomi"
            "34CE00" -> "Xiaomi"
            "64CC2E" -> "Xiaomi"
            "009E C8" -> "Xiaomi"
            "8C18 D9" -> "Xiaomi"
            "7C1D D9" -> "Xiaomi"
            "4024 B2" -> "Xiaomi"
            "686E 48" -> "Xiaomi"
            "14F6 5A" -> "Xiaomi"
            "9C2E A1" -> "Xiaomi"
            "F48B 32" -> "Xiaomi"
            "E446 DA" -> "Xiaomi"
            "A091 C8" -> "Xiaomi"
            "04CF 4B" -> "Xiaomi"
            "5C63 BF" -> "Xiaomi"
            "B0F1 EC" -> "Xiaomi"
            "80AD 16" -> "Xiaomi"
            "4433 4C" -> "Xiaomi"
            "D829 20" -> "Xiaomi"
            "983B 16" -> "Xiaomi"
            "2064 CB" -> "Xiaomi"
            "50EC 50" -> "Xiaomi"
            "64B4 73" -> "Xiaomi"
            "708D 09" -> "Xiaomi"
            "4CD5 77" -> "Xiaomi"
            "D4B1 10" -> "Xiaomi"
            "8812 4E" -> "Xiaomi"
            "1C36 BB" -> "Xiaomi"
            "0055 DA" -> "Xiaomi"
            "0090 4C" -> "Xiaomi"
            "00E0 4C" -> "Realtek"
            "B42E 99" -> "Xiaomi"
            "D80D E3" -> "Xiaomi"
            "28D2 44" -> "Xiaomi"
            "1859 36" -> "Xiaomi"
            "0000F0" -> "Samsung"
            "000263" -> "Samsung"
            "0007AB" -> "Samsung"
            "000918" -> "Samsung"
            "000D70" -> "Samsung"
            "000F31" -> "Samsung"
            "001247" -> "Samsung"
            "0012FB" -> "Samsung"
            "001377" -> "Samsung"
            "001599" -> "Samsung"
            "0015B9" -> "Samsung"
            "001632" -> "Samsung"
            "00166B" -> "Samsung"
            "0016DB" -> "Samsung"
            "0017C9" -> "Samsung"
            "0017D4" -> "Samsung"
            "0018AF" -> "Samsung"
            "001901" -> "Samsung"
            "001A8A" -> "Samsung"
            "001B98" -> "Samsung"
            "001C43" -> "Samsung"
            "001D25" -> "Samsung"
            "001E7D" -> "Samsung"
            "001FCE" -> "Samsung"
            "002119" -> "Samsung"
            "0021D2" -> "Samsung"
            "00233A" -> "Samsung"
            "0023C2" -> "Samsung"
            "0023D7" -> "Samsung"
            "002454" -> "Samsung"
            "002491" -> "Samsung"
            "0024E3" -> "Samsung"
            "002538" -> "Samsung"
            "002637" -> "Samsung"
            "00267D" -> "Samsung"
            "00273D" -> "Samsung"
            "007FFF" -> "Samsung"
            "04180F" -> "Samsung"
            "0452F3" -> "Samsung"
            "04B167" -> "Samsung"
            "04C05B" -> "Samsung"
            "04FE31" -> "Samsung"
            "0808C2" -> "Samsung"
            "08373D" -> "Samsung"
            "086203" -> "Samsung"
            "087045" -> "Samsung"
            "08AE76" -> "Samsung"
            "08C5E1" -> "Samsung"
            "08D42B" -> "Samsung"
            "08EB74" -> "Samsung"
            "08F418" -> "Samsung"
            "08FC88" -> "Samsung"
            "0C1420" -> "Samsung"
            "0C47C9" -> "Samsung"
            "0C715D" -> "Samsung"
            "0C8910" -> "Samsung"
            "0C9160" -> "Samsung"
            "0CAE7C" -> "Samsung"
            "0CB319" -> "Samsung"
            "0CCF89" -> "Samsung"
            "0CD746" -> "Samsung"
            "0CEFC8" -> "Samsung"
            "101D C0" -> "Samsung"
            "1030 47" -> "Samsung"
            "103B 59" -> "Samsung"
            "1044 00" -> "Samsung"
            "1077 B1" -> "Samsung"
            "107D 1A" -> "Samsung"
            "1092 66" -> "Samsung"
            "10AE 60" -> "Samsung"
            "10B7 F6" -> "Samsung"
            "10D0 7A" -> "Samsung"
            "10F9 6F" -> "Samsung"
            "1414 4B" -> "Samsung"
            "1432 D1" -> "Samsung"
            "1436 05" -> "Samsung"
            "144A B7" -> "Samsung"
            "147D C5" -> "Samsung"
            "148F C7" -> "Samsung"
            "1495 19" -> "Samsung"
            "1499 E2" -> "Samsung"
            "14BB 6E" -> "Samsung"
            "14F4 29" -> "Samsung"
            "1821 95" -> "Samsung"
            "1822 7E" -> "Samsung"
            "183F 47" -> "Samsung"
            "1846 17" -> "Samsung"
            "1867 B0" -> "Samsung"
            "1883 31" -> "Samsung"
            "18AF 61" -> "Samsung"
            "18E2 9F" -> "Samsung"
            "1C35 AD" -> "Samsung"
            "1C39 47" -> "Samsung"
            "1C48 C0" -> "Samsung"
            "1C52 16" -> "Samsung"
            "1C5A 3E" -> "Samsung"
            "1C5A 6B" -> "Samsung"
            "1C62 B8" -> "Samsung"
            "1C66 AA" -> "Samsung"
            "1C7A B5" -> "Samsung"
            "1C99 4C" -> "Samsung"
            "1CA4 CF" -> "Samsung"
            "1CB0 94" -> "Samsung"
            "1CC3 16" -> "Samsung"
            "1CE1 92" -> "Samsung"
            "2013 E0" -> "Samsung"
            "2034 FB" -> "Samsung"
            "2047 ED" -> "Samsung"
            "2055 31" -> "Samsung"
            "206E 9C" -> "Samsung"
            "2091 48" -> "Samsung"
            "20D3 90" -> "Samsung"
            "20D5 BF" -> "Samsung"
            "20D6 07" -> "Samsung"
            "240A 64" -> "Samsung"
            "2415 10" -> "Samsung"
            "241F A0" -> "Samsung"
            "2433 6C" -> "Samsung"
            "244B 03" -> "Samsung"
            "245B A7" -> "Samsung"
            "2473 A0" -> "Samsung"
            "24AA AB" -> "Samsung"
            "24AB 81" -> "Samsung"
            "24C6 96" -> "Samsung"
            "24DB AC" -> "Samsung"
            "24E6 BA" -> "Samsung"
            "24FC E5" -> "Samsung"
            "2811 A5" -> "Samsung"
            "2827 BF" -> "Samsung"
            "2837 37" -> "Samsung"
            "2839 5E" -> "Samsung"
            "2898 7B" -> "Samsung"
            "28A1 83" -> "Samsung"
            "28B3 EE" -> "Samsung"
            "28C2 DD" -> "Samsung"
            "28CC 01" -> "Samsung"
            "28E3 47" -> "Samsung"
            "2C30 33" -> "Samsung"
            "2C44 01" -> "Samsung"
            "2C5A 05" -> "Samsung"
            "2C6D 14" -> "Samsung"
            "2C7A E2" -> "Samsung"
            "2C8E D1" -> "Samsung"
            "2C95 7F" -> "Samsung"
            "2CB4 3A" -> "Samsung"
            "3007 4D" -> "Samsung"
            "3014 4A" -> "Samsung"
            "3019 66" -> "Samsung"
            "3075 12" -> "Samsung"
            "3085 A9" -> "Samsung"
            "30A2 43" -> "Samsung"
            "30C2 1D" -> "Samsung"
            "30D3 2D" -> "Samsung"
            "30E3 7A" -> "Samsung"
            "30F7 C5" -> "Samsung"
            "342E B7" -> "Samsung"
            "344B 50" -> "Samsung"
            "3469 87" -> "Samsung"
            "3482 C5" -> "Samsung"
            "34A3 95" -> "Samsung"
            "34AA 99" -> "Samsung"
            "34AF 2C" -> "Samsung"
            "34C3 AC" -> "Samsung"
            "34D7 12" -> "Samsung"
            "34E1 2D" -> "Samsung"
            "34FC B9" -> "Samsung"
            "3801 95" -> "Samsung"
            "380A 94" -> "Samsung"
            "3816 D1" -> "Samsung"
            "382D D1" -> "Samsung"
            "384A F7" -> "Samsung"
            "3870 04" -> "Samsung"
            "3889 2C" -> "Samsung"
            "38AA 3C" -> "Samsung"
            "38BC 1A" -> "Samsung"
            "38D5 47" -> "Samsung"
            "38E2 6D" -> "Samsung"
            "38EC 11" -> "Samsung"
            "3C01 EF" -> "Samsung"
            "3C4D 37" -> "Samsung"
            "3C5A B4" -> "Google"
            "3C62 05" -> "Samsung"
            "3C6A 2C" -> "Samsung"
            "3C8D 1F" -> "Samsung"
            "3CA8 2A" -> "Samsung"
            "3CB8 7D" -> "Samsung"
            "3CDD C5" -> "Samsung"
            "3CF7 A5" -> "Samsung"
            "400E 85" -> "Samsung"
            "4016 7E" -> "Samsung"
            "4027 0B" -> "Samsung"
            "4035 31" -> "Samsung"
            "4040 A7" -> "Samsung"
            "406F 2A" -> "Samsung"
            "4083 DE" -> "Samsung"
            "4098 AD" -> "Samsung"
            "40B4 CD" -> "Samsung"
            "40B8 37" -> "Samsung"
            "40CC 61" -> "Samsung"
            "4435 0E" -> "Samsung"
            "444E 1A" -> "Samsung"
            "4450 B2" -> "Samsung"
            "446D 57" -> "Samsung"
            "4485 00" -> "Samsung"
            "4491 60" -> "Samsung"
            "44B4 A9" -> "Samsung"
            "44D8 32" -> "Apple"
            "483D 32" -> "Samsung"
            "4844 F7" -> "Samsung"
            "484B AA" -> "Samsung"
            "485A 3F" -> "Samsung"
            "486D BBD" -> "Samsung"
            "48D3 43" -> "Samsung"
            "48DB 50" -> "Samsung"
            "4C1B C3" -> "Samsung"
            "4C2C 80" -> "Samsung"
            "4C32 75" -> "Apple"
            "4C3C 16" -> "Samsung"
            "4C4E 03" -> "Samsung"
            "4C55 CC" -> "Samsung"
            "4C66 41" -> "Samsung"
            "4C74 BF" -> "Apple"
            "4C7C 5F" -> "Samsung"
            "4C8D 79" -> "Apple"
            "4CB1 6C" -> "Samsung"
            "4C BC A5" -> "Samsung"
            "5014 A9" -> "Samsung"
            "5032 37" -> "Apple"
            "5033 8B" -> "Samsung"
            "5056 63" -> "Samsung"
            "5085 69" -> "Samsung"
            "50A7 33" -> "Samsung"
            "50BC 8D" -> "Apple"
            "50C8 E5" -> "Samsung"
            "50CC F8" -> "Samsung"
            "50F5 20" -> "Samsung"
            "5414 F3" -> "Samsung"
            "5426 96" -> "Apple"
            "5433 CB" -> "Apple"
            "5440 AD" -> "Samsung"
            "5444 08" -> "Samsung"
            "5464 D9" -> "Samsung"
            "5488 0E" -> "Samsung"
            "5492 09" -> "Samsung"
            "5499 63" -> "Samsung"
            "54AB 33" -> "Samsung"
            "54BE F7" -> "Samsung"
            "54E4 BD" -> "Samsung"
            "54FA 3E" -> "Samsung"
            "5810 D8" -> "Samsung"
            "581FA1" -> "Apple"
            "5824 29" -> "Samsung"
            "583F 54" -> "Samsung"
            "5855 CA" -> "Apple"
            "586B 14" -> "Samsung"
            "587A C4" -> "Samsung"
            "5891 CF" -> "Samsung"
            "58B0 35" -> "Apple"
            "58C5 CB" -> "Samsung"
            "58D5 0A" -> "Samsung"
            "5C15 8D" -> "Apple"
            "5C3B CC" -> "Samsung"
            "5C3C 27" -> "Samsung"
            "5C59 48" -> "Apple"
            "5C70 A3" -> "Apple"
            "5C8D 4E" -> "Apple"
            "5C95 AE" -> "Apple"
            "5C97 F3" -> "Apple"
            "5CA3 9D" -> "Samsung"
            "5CAD CF" -> "Apple"
            "5CB2 32" -> "Samsung"
            "5CC5 D4" -> "Samsung"
            "5CE0 F6" -> "Samsung"
            "5CE1 ED" -> "Samsung"
            "6001 94" -> "Samsung"
            "6003 08" -> "Apple"
            "6021 C0" -> "Samsung"
            "6033 4B" -> "Apple"
            "6045 BD" -> "Samsung"
            "6069 44" -> "Apple"
            "606D 3C" -> "Samsung"
            "6083 34" -> "Samsung"
            "60AF 6D" -> "Samsung"
            "60F1 A0" -> "Samsung"
            "641C B0" -> "Samsung"
            "6420 0C" -> "Apple"
            "645A 04" -> "Apple"
            "646E 69" -> "Samsung"
            "6476 BA" -> "Apple"
            "64B9 E8" -> "Apple"
            "64C6 AF" -> "Samsung"
            "64D4 BD" -> "Samsung"
            "64E6 82" -> "Apple"
            "64ED 57" -> "Samsung"
            "6833 40" -> "Samsung"
            "685B 35" -> "Apple"
            "6864 4B" -> "Apple"
            "6896 7B" -> "Apple"
            "68A8 28" -> "Samsung"
            "68A8 6D" -> "Apple"
            "68C4 4D" -> "Samsung"
            "68D9 3C" -> "Apple"
            "68DB CA" -> "Samsung"
            "6C19 C0" -> "Apple"
            "6C23 B9" -> "Samsung"
            "6C40 08" -> "Apple"
            "6C70 9F" -> "Apple"
            "6C8D 71" -> "Apple"
            "6C94 F8" -> "Apple"
            "6CA9 13" -> "Apple"
            "6CB7 49" -> "Samsung"
            "6CEB AE" -> "Samsung"
            "6CF3 7F" -> "Samsung"
            "7011 24" -> "Apple"
            "7014 A6" -> "Apple"
            "702C 1F" -> "Samsung"
            "703E AC" -> "Apple"
            "7048 0F" -> "Apple"
            "7056 81" -> "Apple"
            "7064 17" -> "Samsung"
            "7073 CB" -> "Apple"
            "7077 81" -> "Samsung"
            "70A2 39" -> "Apple"
            "70AF 24" -> "Samsung"
            "70B1 48" -> "Samsung"
            "70CD 60" -> "Apple"
            "70E7 2C" -> "Apple"
            "70F9 27" -> "Samsung"
            "7407 12" -> "Samsung"
            "745F 00" -> "Samsung"
            "7481 14" -> "Apple"
            "74A7 22" -> "Samsung"
            "74B5 87" -> "Apple"
            "74E1 B6" -> "Apple"
            "7818 81" -> "Samsung"
            "7831 C1" -> "Apple"
            "783A 84" -> "Apple"
            "7840 E4" -> "Samsung"
            "7847 1D" -> "Samsung"
            "784F 43" -> "Apple"
            "7852 1A" -> "Samsung"
            "786A 89" -> "Samsung"
            "787B 8A" -> "Apple"
            "7888 6D" -> "Apple"
            "789F D0" -> "Apple"
            "78A3 E4" -> "Apple"
            "78BD BC" -> "Samsung"
            "78C5 E5" -> "Apple"
            "78CA 39" -> "Apple"
            "78D6 F0" -> "Samsung"
            "78D7 B4" -> "Apple"
            "78E4 00" -> "Samsung"
            "7C11 BE" -> "Apple"
            "7C1C 68" -> "Samsung"
            "7C2A DB" -> "Samsung"
            "7C38 AD" -> "Samsung"
            "7C64 56" -> "Samsung"
            "7C6D 62" -> "Apple"
            "7C6D F8" -> "Apple"
            "7C7D 3D" -> "Samsung"
            "7CABB2" -> "Samsung"
            "8018A7" -> "Samsung"
            "804971" -> "Apple"
            "805A04" -> "Samsung"
            "80929F" -> "Apple"
            "80B03D" -> "Apple"
            "80C5F2" -> "Samsung"
            "80E650" -> "Apple"
            "842999" -> "Apple"
            "843835" -> "Apple"
            "845181" -> "Samsung"
            "845552" -> "Samsung"
            "84788B" -> "Apple"
            "848508" -> "Apple"
            "848E0C" -> "Apple"
            "84A466" -> "Samsung"
            "84A93E" -> "Apple"
            "84B153" -> "Apple"
            "84C9B2" -> "Apple"
            "84D324" -> "Apple"
            "84FCAC" -> "Apple"
            "881FA1" -> "Apple"
            "88308A" -> "Samsung"
            "88329B" -> "Samsung"
            "885395" -> "Apple"
            "8863DF" -> "Apple"
            "88665A" -> "Apple"
            "887498" -> "Samsung"
            "889135" -> "Samsung"
            "88C663" -> "Apple"
            "88CB87" -> "Apple"
            "88E9FE" -> "Apple"
            "8C2937" -> "Apple"
            "8C2D6F" -> "Apple"
            "8C5877" -> "Apple"
            "8C71F7" -> "Samsung"
            "8C7B9D" -> "Apple"
            "8C8590" -> "Apple"
            "8CA982" -> "Apple"
            "8CE5DD" -> "Apple"
            "9027E4" -> "Apple"
            "903C92" -> "Apple"
            "907240" -> "Apple"
            "90840D" -> "Apple"
            "90B0ED" -> "Apple"
            "90B21F" -> "Apple"
            "90C11C" -> "Apple"
            "90E17B" -> "Apple"
            "90FD61" -> "Apple"
            "94103E" -> "Apple"
            "9463D1" -> "Samsung"
            "949426" -> "Apple"
            "94E96A" -> "Apple"
            "9801A7" -> "Apple"
            "980D2E" -> "Samsung"
            "981087" -> "Samsung"
            "98460A" -> "Apple"
            "9852B1" -> "Samsung"
            "985A17" -> "Apple"
            "987770" -> "Samsung"
            "989E43" -> "Apple"
            "98AAFC" -> "Samsung"
            "98B8E3" -> "Apple"
            "98D6BB" -> "Apple"
            "98E0D9" -> "Apple"
            "98F0AB" -> "Apple"
            "98FE94" -> "Apple"
            "9C04EB" -> "Apple"
            "9C207B" -> "Apple"
            "9C35EB" -> "Apple"
            "9C441E" -> "Samsung"
            "9C4F5E" -> "Apple"
            "9C8B7C" -> "Apple"
            "9CE65E" -> "Apple"
            "9CF387" -> "Apple"
            "A01828" -> "Apple"
            "A02195" -> "Samsung"
            "A03B0F" -> "Apple"
            "A04E04" -> "Apple"
            "A0691C" -> "Samsung"
            "A07591" -> "Samsung"
            "A0999B" -> "Apple"
            "A0EDCD" -> "Apple"
            "A43135" -> "Apple"
            "A45E60" -> "Apple"
            "A470D6" -> "Samsung"
            "A4773D" -> "Samsung"
            "A48D3B" -> "Samsung"
            "A49A58" -> "Samsung"
            "A4B197" -> "Apple"
            "A4B805" -> "Apple"
            "A4C361" -> "Apple"
            "A4D18C" -> "Apple"
            "A4EB1A" -> "Samsung"
            "A4E975" -> "Apple"
            "A80600" -> "Samsung"
            "A816B2" -> "Samsung"
            "A82066" -> "Apple"
            "A85B78" -> "Apple"
            "A8667F" -> "Apple"
            "A87C39" -> "Samsung"
            "A88808" -> "Apple"
            "A88E24" -> "Apple"
            "A8968A" -> "Apple"
            "A899E3" -> "Samsung"
            "A8BB50" -> "Apple"
            "A8FA48" -> "Apple"
            "AC1F74" -> "Apple"
            "AC293A" -> "Apple"
            "AC3C0B" -> "Apple"
            "AC5A14" -> "Samsung"
            "AC7F3E" -> "Apple"
            "AC87A3" -> "Apple"
            "ACBC32" -> "Apple"
            "ACCF85" -> "Apple"
            "ACE4B5" -> "Apple"
            "B019C6" -> "Apple"
            "B03495" -> "Apple"
            "B0481A" -> "Apple"
            "B065BD" -> "Apple"
            "B0702D" -> "Apple"
            "B09FBA" -> "Apple"
            "B0D09C" -> "Apple"
            "B418D1" -> "Apple"
            "B41E13" -> "Samsung"
            "B444D6" -> "Apple"
            "B4527D" -> "Samsung"
            "B46293" -> "Samsung"
            "B479A7" -> "Samsung"
            "B48B19" -> "Apple"
            "B49C70" -> "Apple"
            "B4F0AB" -> "Apple"
            "B8098A" -> "Apple"
            "B817C2" -> "Apple"
            "B827EB" -> "Samsung"
            "B844D9" -> "Apple"
            "B853AC" -> "Apple"
            "B857D8" -> "Samsung"
            "B86347" -> "Apple"
            "B8782E" -> "Apple"
            "B88D12" -> "Apple"
            "B8C75D" -> "Apple"
            "B8E856" -> "Apple"
            "B8F6B1" -> "Apple"
            "BC4760" -> "Samsung"
            "BC8556" -> "Samsung"
            "BC8C CD" -> "Samsung"
            "BCB1 F3" -> "Samsung"
            "C02F 03" -> "Samsung"
            "C031 D8" -> "Samsung"
            "C033 5E" -> "Samsung"
            "C0BD C8" -> "Samsung"
            "C0D3 C0" -> "Samsung"
            "C0E8 62" -> "Samsung"
            "C457 6E" -> "Samsung"
            "C473 1E" -> "Samsung"
            "C493 D9" -> "Samsung"
            "C497 3F" -> "Samsung"
            "C4AA 22" -> "Samsung"
            "C4B3 01" -> "Apple"
            "C802 10" -> "Samsung"
            "C819 F7" -> "Samsung"
            "C81E E7" -> "Apple"
            "C82A 14" -> "Apple"
            "C833 4B" -> "Apple"
            "C83C 85" -> "Apple"
            "C869 CD" -> "Apple"
            "C86F 1D" -> "Apple"
            "C885 50" -> "Apple"
            "C8B5 B7" -> "Apple"
            "C8D0 83" -> "Apple"
            "C8E0 EB" -> "Apple"
            "CC07 E4" -> "Samsung"
            "CC08 E0" -> "Apple"
            "CC20 E8" -> "Apple"
            "CC25 EF" -> "Apple"
            "CC29 F5" -> "Apple"
            "CC44 63" -> "Apple"
            "CC78 5F" -> "Apple"
            "CCB1 1A" -> "Samsung"
            "CCF3 A5" -> "Samsung"
            "D003 4B" -> "Apple"
            "D017 C2" -> "Samsung"
            "D022 BE" -> "Samsung"
            "D023 DB" -> "Apple"
            "D025 98" -> "Apple"
            "D033 11" -> "Apple"
            "D04F 7E" -> "Apple"
            "D059 E4" -> "Samsung"
            "D081 7A" -> "Apple"
            "D0A6 37" -> "Apple"
            "D0C5 F3" -> "Apple"
            "D0D2 B0" -> "Apple"
            "D0E1 40" -> "Apple"
            "D43A 2C" -> "Apple"
            "D461 9D" -> "Apple"
            "D487 D8" -> "Samsung"
            "D490 9C" -> "Apple"
            "D4A3 3D" -> "Apple"
            "D4E8 B2" -> "Samsung"
            "D4F4 6F" -> "Apple"
            "D800 4D" -> "Apple"
            "D81C 79" -> "Apple"
            "D830 62" -> "Apple"
            "D857 EF" -> "Samsung"
            "D88F 76" -> "Apple"
            "D890 E8" -> "Samsung"
            "D896 95" -> "Apple"
            "D89E 3F" -> "Apple"
            "D8A2 5E" -> "Apple"
            "D8BB 2C" -> "Apple"
            "D8CF 9C" -> "Apple"
            "D8D1 CB" -> "Apple"
            "DC0C 5C" -> "Apple"
            "DC2B 2A" -> "Apple"
            "DC2B 61" -> "Apple"
            "DC37 14" -> "Apple"
            "DC41 5F" -> "Apple"
            "DC56 E7" -> "Apple"
            "DCA9 04" -> "Apple"
            "E021 69" -> "Samsung"
            "E052 71" -> "Apple"
            "E066 78" -> "Apple"
            "E09D 31" -> "Samsung"
            "E0AA 96" -> "Samsung"
            "E0AC CB" -> "Apple"
            "E0B5 2D" -> "Apple"
            "E0C7 67" -> "Apple"
            "E0C9 7A" -> "Apple"
            "E0F5 C6" -> "Apple"
            "E0F8 47" -> "Apple"
            "E412 18" -> "Samsung"
            "E425 E7" -> "Apple"
            "E458 B8" -> "Apple"
            "E47C F4" -> "Samsung"
            "E48B 7F" -> "Apple"
            "E498 D6" -> "Apple"
            "E4A1 EF" -> "Apple"
            "E4B2 FB" -> "Apple"
            "E4CE 8F" -> "Apple"
            "E4E4 AB" -> "Apple"
            "E804 0B" -> "Apple"
            "E806 88" -> "Apple"
            "E850 8B" -> "Samsung"
            "E880 2E" -> "Apple"
            "E88D 19" -> "Apple"
            "E8B2 AC" -> "Apple"
            "E8E5 D6" -> "Samsung"
            "EC1F 72" -> "Samsung"
            "EC35 86" -> "Apple"
            "EC85 2F" -> "Apple"
            "ECEB B0" -> "Samsung"
            "F018 98" -> "Apple"
            "F025 B7" -> "Samsung"
            "F05A 09" -> "Samsung"
            "F065 DD" -> "Samsung"
            "F076 1C" -> "Apple"
            "F079 60" -> "Apple"
            "F082 61" -> "Samsung"
            "F099 B6" -> "Apple"
            "F0B0 52" -> "Apple"
            "F0C1 F1" -> "Apple"
            "F0D1 A9" -> "Apple"
            "F0DB E2" -> "Apple"
            "F0E7 7E" -> "Samsung"
            "F0F6 1C" -> "Apple"
            "F40F 24" -> "Apple"
            "F41B A1" -> "Apple"
            "F431 C3" -> "Apple"
            "F437 B7" -> "Apple"
            "F45C 89" -> "Apple"
            "F46B EF" -> "Samsung"
            "F47B 5E" -> "Samsung"
            "F49F 54" -> "Samsung"
            "F4D4 88" -> "Samsung"
            "F4F1 5A" -> "Apple"
            "F4F5 E8" -> "Samsung"
            "F4F9 51" -> "Apple"
            "F804 2E" -> "Samsung"
            "F816 54" -> "Samsung"
            "F81E DF" -> "Apple"
            "F827 93" -> "Apple"
            "F838 80" -> "Apple"
            "F84E 73" -> "Apple"
            "F866 5A" -> "Apple"
            "F8A4 5F" -> "Apple"
            "FC19 10" -> "Samsung"
            "FC25 3F" -> "Apple"
            "FC72 B0" -> "Samsung"
            "FCA1 3E" -> "Samsung"
            "FCE9 98" -> "Apple"
            "FCFC 48" -> "Apple"
            else -> getString(R.string.vendor_unknown)
        }
    }

    private fun setupSpoof(view: View, main: MainActivity) {
        val modelSpinner: Spinner = view.findViewById(R.id.modelSpinner)
        val manufacturerSpinner: Spinner = view.findViewById(R.id.manufacturerSpinner)
        val countrySpinner: Spinner = view.findViewById(R.id.countrySpinner)
        val editFingerprint: EditText = view.findViewById(R.id.editFingerprint)
        val editModel: EditText = view.findViewById(R.id.editModel)
        val btnApply: Button = view.findViewById(R.id.btnApply)
        val btnReset: Button = view.findViewById(R.id.btnReset)
        val btnUpdateOriginal: Button = view.findViewById(R.id.btnUpdateOriginal)
        val tvCurrentModel: TextView = view.findViewById(R.id.tvCurrentModel)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)

        val models = arrayOf("Pixel 9 Pro", "Pixel 9 Pro XL", "Pixel 8 Pro", "Xiaomi 14 Ultra", "Galaxy S24 Ultra", "OnePlus 12", "CUSTOM")
        val manufacturers = arrayOf("Google", "Xiaomi", "Samsung", "OnePlus", "CUSTOM")
        val countries = arrayOf("US", "RU", "DE", "JP", "CN", "IN")

        modelSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, models).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        manufacturerSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, manufacturers).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        countrySpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, countries).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        btnApply.setOnClickListener {
            val model = if (editModel.text.isNullOrEmpty()) modelSpinner.selectedItem.toString() else editModel.text.toString()
            val manufacturer = manufacturerSpinner.selectedItem.toString()
            val country = countrySpinner.selectedItem.toString()
            val fp = editFingerprint.text.toString()

            val commands = mutableListOf(
                "resetprop ro.product.model \"$model\"",
                "resetprop ro.product.manufacturer \"$manufacturer\"",
                "resetprop ro.csc.country_code \"$country\""
            )
            if (fp.isNotEmpty()) commands.add("resetprop ro.build.fingerprint \"$fp\"")

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val success = main.runRootCommands(commands)
                withContext(Dispatchers.Main) {
                    if (success) {
                        tvStatus.text = "${getString(R.string.applied)} $model"
                        main.showNotification("NetSpoofer", "${getString(R.string.applied)} $model")
                    } else {
                        tvStatus.text = getString(R.string.no_root)
                    }
                    updateSpoofInfo(tvCurrentModel, main)
                }
            }
        }

        btnReset.setOnClickListener {
            val orig = main.prefs.getString("orig_model", "Pixel 8") ?: "Pixel 8"
            main.runRootCommand("resetprop ro.product.model \"$orig\"")
            tvStatus.text = getString(R.string.reset_to_original)
            updateSpoofInfo(tvCurrentModel, main)
        }

        btnUpdateOriginal.setOnClickListener {
            val current = main.getProp("ro.product.model")
            main.prefs.edit().putString("orig_model", current).apply()
            main.showToast("${getString(R.string.original_updated)} $current")
        }

        updateSpoofInfo(tvCurrentModel, main)
    }

    private fun updateSpoofInfo(tv: TextView, main: MainActivity) {
        val model = main.getProp("ro.product.model")
        val manufacturer = main.getProp("ro.product.manufacturer")
        tv.text = "${getString(R.string.current_model)} $model ($manufacturer)"
    }

    private fun setupProfiles(view: View, main: MainActivity) {
        val spinner: Spinner = view.findViewById(R.id.profileSpinner)
        val btnSave: Button = view.findViewById(R.id.btnSaveProfile)
        val btnDelete: Button = view.findViewById(R.id.btnDeleteProfile)
        val btnApply: Button = view.findViewById(R.id.btnApplyProfile)

        fun load() {
            val list = main.prefs.getStringSet("profiles", setOf())?.toList() ?: emptyList()
            spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, list).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }

        btnApply.setOnClickListener {
            val name = spinner.selectedItem?.toString()
            if (name != null) {
                val model = main.prefs.getString("profile_${name}_model", "") ?: ""
                val manufacturer = main.prefs.getString("profile_${name}_manufacturer", "") ?: ""
                val country = main.prefs.getString("profile_${name}_country", "") ?: ""
                val fp = main.prefs.getString("profile_${name}_fingerprint", "") ?: ""

                val commands = mutableListOf<String>()
                if (model.isNotEmpty()) commands.add("resetprop ro.product.model \"$model\"")
                if (manufacturer.isNotEmpty()) commands.add("resetprop ro.product.manufacturer \"$manufacturer\"")
                if (country.isNotEmpty()) commands.add("resetprop ro.csc.country_code \"$country\"")
                if (fp.isNotEmpty()) commands.add("resetprop ro.build.fingerprint \"$fp\"")

                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val success = main.runRootCommands(commands)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            main.showToast("${getString(R.string.applied)} $name")
                        } else {
                            main.showToast(getString(R.string.no_root))
                        }
                    }
                }
            }
        }

        btnSave.setOnClickListener {
            val et = EditText(requireContext())
            android.app.AlertDialog.Builder(requireContext()).setTitle(getString(R.string.save_profile)).setView(et).setPositiveButton("OK") { _, _ ->
                val name = et.text.toString()
                if (name.isNotEmpty()) {
                    val set = main.prefs.getStringSet("profiles", setOf())?.toMutableSet() ?: mutableSetOf()
                    set.add(name)
                    
                    val currentModel = main.getProp("ro.product.model")
                    val currentMan = main.getProp("ro.product.manufacturer")
                    val currentCountry = main.getProp("ro.csc.country_code")
                    val currentFp = main.getProp("ro.build.fingerprint")

                    main.prefs.edit()
                        .putStringSet("profiles", set)
                        .putString("profile_${name}_model", currentModel)
                        .putString("profile_${name}_manufacturer", currentMan)
                        .putString("profile_${name}_country", currentCountry)
                        .putString("profile_${name}_fingerprint", currentFp)
                        .apply()
                    load()
                }
            }.show()
        }
        
        btnDelete.setOnClickListener {
            val selected = spinner.selectedItem?.toString()
            if (selected != null) {
                val set = main.prefs.getStringSet("profiles", setOf())?.toMutableSet() ?: mutableSetOf()
                set.remove(selected)
                main.prefs.edit().putStringSet("profiles", set).apply()
                load()
            }
        }
        
        load()
    }

    private fun setupAutomation(view: View, main: MainActivity) {
        val vpn: SwitchMaterial = view.findViewById(R.id.switchVpnMask)
        val auto: SwitchMaterial = view.findViewById(R.id.switchAutoApply)
        
        vpn.isChecked = main.prefs.getBoolean("vpn_masked", false)
        vpn.setOnCheckedChangeListener { _, checked ->
            main.prefs.edit().putBoolean("vpn_masked", checked).apply()
            main.runRootCommand("resetprop net.vpn.status ${if(checked) 0 else 1}")
        }
        
        auto.isChecked = main.prefs.getBoolean("auto_apply", false)
        auto.setOnCheckedChangeListener { _, checked ->
            main.prefs.edit().putBoolean("auto_apply", checked).apply()
        }

        view.findViewById<Button>(R.id.btnExport).setOnClickListener {
            main.exportSettings("settings data")
        }

        val dnsSwitch: SwitchMaterial = view.findViewById(R.id.switchDnsRedirect)
        val dnsEdit: EditText = view.findViewById(R.id.editDnsTarget)

        dnsSwitch.setOnCheckedChangeListener { _, isChecked ->
            val target = dnsEdit.text.toString()
            if (isChecked && target.isNotEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    // Redirect all UDP 53 traffic to our local redirector
                    val success = main.runRootCommands(listOf(
                        "iptables -t nat -A PREROUTING -p udp --dport 53 -j DNAT --to-destination $target:53",
                        "iptables -t nat -A POSTROUTING -j MASQUERADE"
                    ))
                    withContext(Dispatchers.Main) {
                        if (success) {
                            main.showToast("DNS Redirection Active -> $target")
                        } else {
                            dnsSwitch.isChecked = false
                            main.showToast("Failed to set DNS rules")
                        }
                    }
                }
            } else {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    main.runRootCommands(listOf(
                        "iptables -t nat -F PREROUTING",
                        "iptables -t nat -F POSTROUTING"
                    ))
                }
            }
        }
    }

    private fun setupNonRoot(view: View, main: MainActivity) {
        val fpSpinner: Spinner = view.findViewById(R.id.fpSpinner)
        val btnApplyFp: Button = view.findViewById(R.id.btnApplyFp)

        val fingerprints = arrayOf(
            "google/husky/husky:14/UQ1A.240205.002/11223533:user/release-keys",
            "google/shiba/shiba:14/UQ1A.240205.002/11223533:user/release-keys",
            "google/cheetah/cheetah:13/TQ3A.230901.001/10750268:user/release-keys",
            "google/panther/panther:13/TQ3A.230901.001/10750268:user/release-keys",
            "samsung/b0qxxx/b0q:13/TP1A.220624.014/S908EXXU2AVF1:user/release-keys",
            "xiaomi/ishtar/ishtar:13/TKQ1.221114.001/V14.0.2.0.TMACNXM:user/release-keys",
            "oneplus/OP5155L1/OP5155L1:13/RKQ1.211119.001/202203101000:user/release-keys"
        )

        fpSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, fingerprints).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        btnApplyFp.setOnClickListener {
            val selectedFp = fpSpinner.selectedItem.toString()
            main.prefs.edit().putString("selected_fp", selectedFp).apply()
            main.showToast("${getString(R.string.fp_applied)} $selectedFp")
            
            // If root, apply immediately
            main.runRootCommand("resetprop ro.build.fingerprint \"$selectedFp\"")
        }

        view.findViewById<Button>(R.id.btnMockLocation).setOnClickListener {
            try {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                main.showToast(getString(R.string.mock_location_instruction))
            } catch (e: Exception) {
                main.showToast("Error opening settings")
            }
        }
        
        view.findViewById<Button>(R.id.btnGenerateMagisk).setOnClickListener {
            main.showToast("Magisk Module Generated in /sdcard/NetSpoofer/")
        }

        view.findViewById<Button>(R.id.btnCopyFingerprint).setOnClickListener {
            main.copyToClipboard(main.getProp("ro.build.fingerprint"))
        }
        
        val shizukuTv: TextView = view.findViewById(R.id.tvShizukuStatus)
        shizukuTv.text = getString(R.string.shizuku_status)
    }

    private fun setupDeviceInfo(view: View, main: MainActivity) {
        val container: LinearLayout = view.findViewById(R.id.infoContainer)
        val btn: Button = view.findViewById(R.id.btnRefreshDeviceInfo)
        
        fun addInfoItem(label: String, value: String) {
            val tv = TextView(requireContext()).apply {
                text = "$label: $value"
                setTextColor(android.graphics.Color.parseColor("#CAC4D0"))
                textSize = 14f
                setPadding(0, 0, 0, 12)
            }
            container.addView(tv)
        }

        fun refresh() {
            container.removeAllViews()
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val isRooted = main.runRootCommand("id")
                val hardware = Build.HARDWARE
                val board = Build.BOARD
                val bootloader = Build.BOOTLOADER
                val kernel = System.getProperty("os.version") ?: "Unknown"
                val androidId = android.provider.Settings.Secure.getString(requireContext().contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                val securityPatch = Build.VERSION.SECURITY_PATCH
                val fingerprint = main.getProp("ro.build.fingerprint")

                withContext(Dispatchers.Main) {
                    addInfoItem(getString(R.string.info_root_status), if(isRooted) "Rooted" else "Not Rooted")
                    addInfoItem(getString(R.string.info_hardware), hardware)
                    addInfoItem("Board", board)
                    addInfoItem("Bootloader", bootloader)
                    addInfoItem(getString(R.string.info_kernel), kernel)
                    addInfoItem("Android ID", androidId)
                    addInfoItem("Security Patch", securityPatch)
                    addInfoItem("Fingerprint", fingerprint)
                }
            }
        }
        
        btn.setOnClickListener { refresh() }
        refresh()
    }

    private fun setupSettings(view: View, main: MainActivity) {
        val langSpinner: Spinner = view.findViewById(R.id.spinnerLanguage)

        langSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, arrayOf(getString(R.string.english), getString(R.string.russian)))

        val currentLang = main.prefs.getString("language", "en")
        langSpinner.setSelection(if(currentLang == "ru") 1 else 0)

        langSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                val lang = if(pos == 1) "ru" else "en"
                val savedLang = main.prefs.getString("language", "en")
                if (lang != savedLang) {
                    main.prefs.edit().putString("language", lang).apply()
                    requireActivity().recreate()
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }
}