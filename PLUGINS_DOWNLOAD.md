# Hướng dẫn tải Plugin - Homieee Mine / Raumania Land

## ✅ Plugins đã cài sẵn (tự động tải)

| Plugin | Chức năng |
|--------|-----------|
| MiniMOTD | MOTD đẹp với gradient |
| TAB | Tablist với TPS/RAM/Ping |
| SkinsRestorer | Đổi skin (việt hóa) |
| SimpleTPA | Teleport request (việt hóa) |
| SimpleVoiceChat | Voice chat (port 24454 UDP) |
| GSit | Ngồi, nằm, crawl |
| Skript | Custom scripts |

## 📦 Plugins cần tải thủ công (MIỄN PHÍ)

### Furniture & Custom Items (Tùy chọn)

**Lựa chọn 1: MyFurniture (Miễn phí, có furniture sẵn)**
> ⚠️ Yêu cầu: MC 1.21.4+ | Cần 2 plugin phụ thuộc

1. **SCore** (Thư viện bắt buộc)
   - Link: https://www.spigotmc.org/resources/score.87015/
   - Tải và đổi tên: `SCore.jar`

2. **ExecutableItems Free** (Phụ thuộc)
   - Link: https://www.spigotmc.org/resources/executable-items.77578/
   - Tải và đổi tên: `ExecutableItems.jar`

3. **MyFurniture** (Plugin chính)
   - Link: https://www.spigotmc.org/resources/myfurniture.79024/
   - Tải và đổi tên: `MyFurniture.jar`

**Lựa chọn 2: Chỉ dùng GSit (đã cài)**
- Đủ cho survival server nhỏ
- Hỗ trợ ngồi trên ghế/cầu thang, nằm, bò

## 🎨 Resource Pack với Logo

### Cách 1: Tự host (Thủ công)

1. Chạy script build:
   ```powershell
   .\build_resourcepack.ps1
   ```

2. Upload `server-resourcepack.zip` lên host:
   - GitHub Releases
   - Dropbox (thay `?dl=0` → `?dl=1`)
   - Google Drive (dùng direct link)

3. Cập nhật `server.properties`:
   ```properties
   require-resource-pack=true
   resource-pack=https://your-url/server-resourcepack.zip
   resource-pack-sha1=<sha1 từ script>
   ```

### Cách 2: MyFurniture tự host
- MyFurniture + ExecutableItems sẽ tự tạo và host resourcepack
- Chỉ cần thêm textures vào folder plugin

## ⚠️ Lưu ý quan trọng

**Về Oraxen/ItemsAdder/Nexo:**
- Đây là các plugin **TRẢ PHÍ** ($15-25 USD)
- Nếu muốn dùng, mua từ Spigot/Polymart

**Về Voice Chat:**
- Cần mở port **24454 UDP**
- Người chơi cần cài mod client (Fabric/Forge)

## 🔗 Links chính thức

- [Hangar (Paper plugins)](https://hangar.papermc.io)
- [Modrinth](https://modrinth.com)
- [SpigotMC](https://www.spigotmc.org/resources)
- [SCore GitHub](https://github.com/Ssomar-Developement/SCore)
