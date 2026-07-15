# NetSpoofer 🛡️

Android network toolkit: scan, spoof, analyze.

A hands-on tool for understanding what's happening on your Wi-Fi. Combines network scanning, traffic analysis, and device identity spoofing. Works with or without root.

# WARNING: THIS IS A BEGINNER PROJECT!

---

## 📦 Features

### 📱 Device Spoofing (root)
- Change model, manufacturer, build fingerprint via resetprop
- Change Wi-Fi MAC address
- OUI database for valid vendor MAC generation

### 🔍 Network Scanning
- Fast parallel ping sweep
- Hostname and vendor resolution
- TTL-based OS detection (Windows / Linux / Android / iOS)
- Nmap support (if installed)

### ⚔️ ARP & MITM (root)
- ARP spoofing with traffic redirction
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

MIT
