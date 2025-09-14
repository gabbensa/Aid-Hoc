# Aid-Hoc — MANET/Wi-Fi Direct Messaging for Rescue Teams (Android)

_A field-ready, peer-to-peer messenger designed for first responders. Works device-to-device without infrastructure, using Wi-Fi Direct (MANET-style) sockets for discovery, connection, and chat._

![Platform](https://img.shields.io/badge/Platform-Android-blue)
![Language](https://img.shields.io/badge/Language-Java-informational)
![Networking](https://img.shields.io/badge/Networking-Wi--Fi%20Direct%20%2F%20Sockets-lightgrey)
![Status](https://img.shields.io/badge/Status-Active-brightgreen)

## ✨ Why Aid-Hoc?
In disaster zones and off-grid areas, cellular and internet can fail. **Aid-Hoc** lets rescue units communicate **directly** over device-to-device links—auto-discovering nearby peers and exchanging messages without any base station.



## ✅ Key Features
- **Zero-infrastructure messaging** via Wi-Fi Direct sockets
- **Automatic peer discovery & auto-connect** (min-RTT preference when possible)
- **Reliable chat** (send/receive, UI with RecyclerView)
- **Username sync & presence** (device identity handshake)
- **Commander view (desktop)** via Firebase mirror (read-only ops view)
- **Crash-safe I/O** with background threads for network read/write
- **Offline-first mindset**; messages queue when link drops, then flush

> _This project originally started as a Bluetooth chat app and was carefully refactored to use Wi-Fi Direct/MANET-style transport while keeping the original logic intact._


**Data flow:**  
`Wi-Fi Direct discovery ➝ group formed (GO/client) ➝ sockets opened ➝ ReadWriteThread pumps bytes ➝ Controller parses ➝ UI updates`


## 🚀 Getting Started

### Requirements
- Android Studio (Hedgehog+ recommended)
- Android SDK + platform tools
- Two Android devices with Wi-Fi Direct support

### Build & Run
1. **Clone** the repo  
   `git clone https://github.com/gabbensa/Aid-Hoc.git && cd Aid-Hoc`
2. **Open** in Android Studio and let Gradle sync.
3. **Run** on two devices:
   - Create an account on the two devices as Field User then connect to your account you just created
   - On Device A: open the Discovery/Devices tab → Automatic discovery start
   - On Device B: do the same; wait for group formation (one becomes Group Owner)
   - Open **Chat** → exchange messages

> **Permissions:** The app requires Wi-Fi, network, and (on recent Android) nearby devices permissions for discovery/connection. Grant them on first launch.

## 🔧 Configuration
- **Auto-connect**: enabled by default; the app will attempt to connect as soon as a suitable peer is found.
- **Commander (Firebase) mirror** (optional): set your Firebase keys in `google-services.json` and enable the mirror flag in settings (if present in your build/branch).

## 🧪 Testing Tips
- Test both GO (server) and client sides.
- Toggle airplane mode (leave Wi-Fi on) to simulate lack of internet.
- Walk away + return to test socket teardown/reattach.

## 📍 Roadmap
- Multi-peer group chat (N>2 within a Wi-Fi Direct group)
- Delivery acks + resend window
- Basic file/attachment support
- Encrypted channels (TLS over local sockets or libsodium framing)


## 📄 License
MIT License

Copyright (c) 2025 Gabriel Bensamoun


## 🙏 Acknowledgments
- Android Wi-Fi Direct docs & community examples
- Mentors and classmates who reviewed early Bluetooth versions




