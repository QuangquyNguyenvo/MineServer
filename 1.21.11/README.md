# ★ Homieee Mine Server ★

> 🎮 **Tết 2026 - 1 Tháng Quẩy Cùng AE**

Server Minecraft Survival chill cho nhóm bạn chơi chung dịp Tết.

## 📋 Yêu cầu

- **Java 21+**: [Tải tại Adoptium](https://adoptium.net/temurin/releases/?version=21)
- **Minecraft 1.21.11** (hoặc 1.21.x)

## 🚀 Cách chạy Server

```bash
# Chạy file start.bat
# Chọn chế độ:
#   [1] Port 25565 (mặc định)
#   [2] Port 25566 (test, tránh đè port)
#   [3] Port tùy chọn
```

## 📦 Plugins đã cài

| Plugin | Chức năng | Lệnh chính |
|--------|-----------|------------|
| **MiniMOTD** | MOTD đẹp với RGB gradient | `/minimotd reload` |
| **TAB** | Tablist hiện TPS, RAM, Ping | `/tab reload` |
| **SkinsRestorer** | Đổi skin (offline mode) | `/skin <tên>` |
| **SimpleTPA** | Teleport request | `/tpa <player>` |
| **SimpleVoiceChat** | Voice chat proximity | Nhấn `V` ingame |
| **Skript** | Custom scripts | `/sk reload all` |

## 🎤 Voice Chat Setup

**Server:** Cần mở port **24454 UDP** (ngoài port game 25565 TCP)

**Client:** Người chơi cần cài mod:
- [Simple Voice Chat (Fabric)](https://modrinth.com/mod/simple-voice-chat/versions?l=fabric)
- [Simple Voice Chat (Forge)](https://modrinth.com/mod/simple-voice-chat/versions?l=forge)

## 📁 Cấu trúc thư mục

```
MinecraftServer/
├── plugins/
│   ├── MiniMOTD/          # Config MOTD (tracked)
│   ├── TAB/               # Config Tablist (tracked)
│   ├── SimpleTPA/         # Config TPA việt hóa (tracked)
│   ├── SimpleVoiceChat/   # Config voice chat (tracked)
│   ├── Skript/scripts/    # Custom scripts (tracked)
│   └── *.jar              # Plugin files (ignored)
├── world/                 # World data (ignored)
├── start.bat              # Launcher với chọn port
└── server.properties      # Config server
```

## 🔧 Sau khi clone repo

1. Tải các plugin JAR (xem `plugins/.gitkeep`)
2. Chạy `start.bat`
3. Chọn port phù hợp
4. Enjoy! 🎉

## 🌐 Kết nối

- **Local:** `localhost` hoặc `localhost:PORT`
- **LAN:** IP nội bộ (hiện khi chạy start.bat)
- **Internet:** Cần port forward router + IP public
