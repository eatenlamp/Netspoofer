# NetSpoofer 🛡️

Android network toolkit: scan, spoof, analyze.

A tool for understanding what's happening on your Wi-Fi. Combines network scanning, traffic analysis, and device identity spoofing. Works with or without root.

# WARNING: THIS IS A BEGINNER PROJECT!
---

## 🌠 Screenshots 

<table>
  <tr>
    <td><img width="150" alt="Screenshot 1" src="https://github.com/user-attachments/assets/d128885a-0bb4-4a6b-b045-e6ddc4e6a36e" /></td>
    <td><img width="150" alt="Screenshot 2" src="https://github.com/user-attachments/assets/d2fb3635-82ca-4972-9ac9-748adee22cfc" /></td>
    <td><img width="150" alt="Screenshot 3" src="https://github.com/user-attachments/assets/984bdebd-52d0-426e-8be4-ab4d0859779b" /></td>
    <td><img width="150" alt="Screenshot 4" src="https://github.com/user-attachments/assets/34e1fb7b-3d33-4c19-8316-47ba0be56372" /></td>
  </tr>
</table>

---

## 📦 Features

### 📱 Device Spoofing (root)
- Change model, manufacturer, build fingerprint with resetprop
- Change Wi-Fi MAC address
- Generates valid MAC addresses from database

### 🔍 Network Scanning
- Fast parallel ping sweep
- Hostname and vendor resolution
- TTL-based OS detection (Windows / Linux / Android / iOS)
- Nmap support (if installed)

### ⚔️ ARP & MITM (root)
- ARP spoofing with traffic redirection
- HTTP/DNS capture via tcpdump
- Global DNS redirect via iptables
- One-tap device blocking

### 🔒 Non-root
- App/address blocking via VpnService
- Detailed hardware and system info

---

## 🛠 Tech Stack

- Kotlin + coroutines
- MVVM + Clean Architecture
- Material 3 with Dynamic Colors
- Room for history and profiles
- OkHttp, custom sockets
- Timber for logging

---

## 📥 Build

git clone https://github.com/eatenlamp/Netspoofer.git

cd Netspoofer

# Open in Android Studio
# Build :app

### Requirements

- Android 10+ (API 29+)
- Root for spoofing, ARP, iptables
- BusyBox — recommended

---

## ⚖️ Disclaimer

For educational and authorized testing only.

Unauthorized use in networks you don't own is illegal.
The developer is not responsible for how this tool is used.

---

## 🌐 Languages

- English
- Русский

---

## 📄 License

GPLv3
