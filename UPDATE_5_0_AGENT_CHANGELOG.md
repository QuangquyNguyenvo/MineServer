# ✦ UPDATE 5.0 TECHNICAL CHANGELOG & AGENT SPECIFICATION ✦
> **Tài liệu kỹ thuật chi tiết dành cho AI Agent tiếp quản: Toàn bộ thay đổi, kiến trúc hệ thống, danh sách mod, cơ chế Boss và quy tắc vận hành trong Update 5.0.**

---

## 📌 1. Metadata & Thông Số Môi Trường Hệ Thống
* **Tên bản cập nhật:** Update 5.0 (v5.0.0) — *Huyết Tế Warden Cuồng Nộ, Muôn Loài Kỳ Thú & Rạp Chiếu Phim YouTube*.
* **Ngày phát hành:** `21/08/2026`.
* **Minecraft Target:** Fabric `26.2` (Minecraft `1.21.1`).
* **Fabric Loader:** `0.19.3` | **Fabric API:** `0.156.0+26.2`.
* **VPS Server IP:** `ancient.pikamc.vn:25238` (Game) | `ancient.pikamc.vn:2022` (SFTP).
* **MCP Agent Tool:** `pikamc-agent` (`http://ancient.pikamc.vn:25240/mcp`).
* **Web Changelog Repo:** [`C:\Users\ADMIN\OlongBell-Changelogs`](file:///C:/Users/ADMIN/OlongBell-Changelogs) (Deploy trên Cloudflare Pages & Workers).
* **MineServer Repo:** [`C:\Users\ADMIN\MineServer`](file:///C:/Users/ADMIN/MineServer) (Deploy trên GitHub `QuangquyNguyenvo/MineServer`).

---

## 💀 2. Đại Tu Toàn Diện Boss Tối Thượng Warden (Chaos Cubed Engine)
Toàn bộ logic được lập trình bằng Scarpet độc lập tại: [`world/scripts/custom_mob_effects.sc`](file:///C:/Users/ADMIN/MineServer/world/scripts/custom_mob_effects.sc).

### 2.1. Chỉ Số Cơ Bản & Kháng Hiệu Ứng
* **Max HP:** `1,500 HP` (Cập nhật trực tiếp `generic.max_health` và Bossbar).
* **Tốc độ cơ bản:** `0.30` (Mặc định vanilla là `0.25`).
* **Kháng sát thương vật lý động:**
  * Kháng `30%` khi HP $> 1,050$ ($> 70\%$).
  * Kháng `50%` khi HP $< 750$ ($< 50\%$).
* **Kháng phép thuật:** Kháng `80%` sát thương Instant Damage (Harming I/II, Magic).
* **Kháng môi trường:** Miễn nhiễm `100%` sát thương ngạt nước (Drown).
* **Phản đòn hiệu ứng xấu:** Khi bị dính Slowness, Poison, Weakness, Blindness... Warden sẽ phản ngược hiệu ứng đó lên người chơi trong `5 giây`.
* **Anti-Flight (Trọng lực cực đại):** Trong bán kính `40m`, nếu người chơi bay hoặc dùng Elytra, tự động kéo giật rơi tự do (Motion Y = `-0.8`).
* **Vacuum Pull (Kéo mục tiêu):** Kéo giật người chơi ở xa $> 16\text{m}$ hoặc trên cao $> 6\text{m}$ về chân Warden (Cooldown `6s / 120 ticks`).
* **Phá hủy địa hình:** Quét và phá hủy block rắn vùng `3x4x3` mỗi `5 ticks` quanh người để chống bị nhốt.

---

### 2.2. Phase 2 — Trạng Thái Cuồng Nộ (RAGE khi $\le 30\%$ HP / $\le 450$ HP)
* **Tăng tốc độ di chuyển:** `+50%` (`movement_speed` = `0.45`).
* **Miễn nhiễm 100% Vật thể bắn (Projectile Immunity):** Chặn toàn bộ Cung tên, Đinh ba, Thuốc ném, Cầu lửa, Sọ Wither, Wind Charge, Pháo hoa... **Bắt buộc người chơi phải cận chiến**.
* **Sonic Boom Thăng Hoa:** Gây sát thương chuẩn **`45% Max HP`** của người chơi (Phase 1 là `33% Max HP`), có khả năng kết liễu trực tiếp và kèm **Debuff giảm 50% hiệu quả hồi máu trong 5s (100 ticks)**.
* **Giao diện & Cảnh báo:** Bossbar đổi tên thành `Warden (Phase 2 - Cuồng Nộ)` màu đỏ; gửi Title & Chat tellraw đỏ cảnh báo toàn server.

---

### 2.3. Huyết Tế Tối Thượng (Emergency Heal khi $< 10\%$ HP / $< 150$ HP)
* **Kích hoạt:** Đúng `1 lần duy nhất` trong toàn bộ trận đấu khi máu tụt dưới 150 HP trong Phase 2.
* **Bất tử 10 giây:** Miễn nhiễm `100%` mọi nguồn sát thương trong 10 giây (200 ticks) vận khí.
* **Chướng khí độc (40m):** Áp đặt **Buồn nôn II (Nausea II)**, **Mù quáng (Blindness)** và **Trúng độc II (Poison II)** lên toàn bộ người chơi trong phạm vi `40m` trong `10 giây`.
* **Tốc độ hồi phục:** Hồi từ 150 HP lên **`600 HP (40% Max HP)`** với tốc độ **`+2.25 HP/tick`** (`+45 HP/giây`).

---

### 2.4. Hệ Thống Nhạc Nền BGM Looping (Dynamic Boss Music)
* **Phase 1:** Phát bài `minecraft:custom.warden_theme` loop liên tục trong bán kính `40m`.
* **Phase 2 (Huyết Tế):** Ngắt bài cũ và chuyển sang `minecraft:custom.warden_sacrifice` loop liên tục đến khi Warden chết.
* **Resource Pack Server:** [`ChaosCubed_Warden_BGM.zip`](file:///C:/Users/ADMIN/MineServer/ChaosCubed_Warden_BGM.zip) (`5.92 MB`, SHA1: `67b5bce83e97785b6b1ca59d7122485d06b87e8d`).
* **Cấu hình `server.properties`:**
  ```properties
  resource-pack=https://raw.githubusercontent.com/QuangquyNguyenvo/MineServer/main/ChaosCubed_Warden_BGM.zip
  resource-pack-sha1=67b5bce83e97785b6b1ca59d7122485d06b87e8d
  ```

---

### 2.5. Cơ Chế Khắc Chế & Phần Thưởng Rơi Ra
* **Anti-Iron Golem:** Quét Golem trong `8m` (CD: `4s`), hất tung lên trời (Motion Y = `+0.9`), gây `50 Sát thương` và hồi `+5 HP` cho Warden (CD hút máu: `1s`).
* **Kháng 90% Non-Player Damage:** Trong Phase 2, Warden chỉ nhận `10%` sát thương từ mob/golem/wither.
* **Trần Máu Khóa Cứng (Hard Cap):** Trong Phase 2, máu Warden không bao giờ được vượt quá `600 HP`.
* **Combo Sonic Boom:** Trước Huyết Tế: combo `3 đòn đánh thường + 1 Sonic Boom`. Sau Huyết Tế: combo `2 đòn đánh thường + 1 Sonic Boom`.
* **Sonic Boom Tầm Xa Hút Máu:** Đánh trúng người chơi cách $> 4.5\text{m}$ $\rightarrow$ Hồi `+10 HP`.
* **Hút Sinh Lực Kẻ Tử Nạn:** Người chơi chết trong `30m` $\rightarrow$ Warden hồi `+50 HP`.
* **Mythic Loot Drops:**
  * **100% Chắc chắn:** `1x Heavy Core`, `1-2x Nether Star`, `1-2x Netherite Upgrade Template`, `~2,000 XP`.
  * **10% Bảo Khí Thần Thoại:**
    1. 🗡️ `Void Reaper` (Lưỡi Hái Netherite: Sharpness VII, Looting IV, Sweeping Edge IV, Mending).
    2. 🛡️ `Sculk Carapace` (Giáp Ngực Netherite: Protection VI, Thorns IV, Mending).
    3. 👢 `Ghost Walker Boots` (Ủng Netherite: Protection VI, Feather Falling V, Soul Speed III, Mending).
  * **50% Kho Báu:** `2-4x Enchanted Golden Apple`, `2x Netherite Ingot`, `2x Totem of Undying`, `1x Silence Armor Trim Template`.

---

## 📦 3. Chi Tiết Danh Sách Mod Mới & Cập Nhật (Update 5.0)

| Tên Tệp Mod (`.jar`) | Mod ID | Phiên bản | Môi trường | Dung lượng | Mô tả chức năng & Kỹ thuật |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `alexsmobsfabric-26.2-1.1.0.jar` | `alexsmobs` | `26.2-1.1.0` | `*` | 25.91 MB | Bổ sung 80+ sinh vật hoang dã, hoạt ảnh 3D, thuần hóa & vật phẩm chế tạo. |
| `citadelfabric-26.2-1.1.0.jar` | `citadel` | `26.2-1.1.0` | `*` | 0.75 MB | Thư viện API bắt buộc cho Alex's Mobs hoạt động trên Fabric. |
| `edm-26.8-fabric-26.2.jar` | `electronic_device_mod` | `26.8` | `*` | 0.88 MB | Thiết bị điện tử: TV, màn hình chiếu, máy tính, phát YouTube đồng bộ. |
| `watermedia-3.0.0.23.jar` | `watermedia` | `3.0.0.23` | `*` | 3.46 MB | Multimedia Engine giải mã video/audio trực tiếp trong không gian 3D. |
| `watermedia_binaries-3.0.0.6.jar` | `watermedia_binaries` | `3.0.0.6` | `*` | 137.07 MB | Binary giải mã VLC/FFmpeg (*Bị loại khỏi Git vì >100MB, lưu cục bộ/VPS*). |
| `Carry Cats 2.4 26.2.jar` | `shouldercats` | `2.4+mc26.2` | `*` | 0.10 MB | Bế mèo thuần hóa đặt lên vai khi di chuyển. |
| `cutecats-fabric-26.2-.jar` | `kingdomcats` | `1.2.2-fabric.1` | `Client` | 0.15 MB | Nâng cấp hoạt ảnh và mô hình 3D cho 7 giống mèo. |
| `gianttorii-1.0.0 Fabric 26.2.jar` | `gianttorii` | `1.0.0` | `*` | 0.02 MB | Sinh cấu trúc Cổng Torii Nhật Bản khổng lồ ở Cherry Grove. |
| `ipla-mc26.2-fabric-6.4.3beta.jar` | `ipla` | `6.4.3beta` | `*` | 0.31 MB | Cho phép đặt bất kỳ vật phẩm nào lên mọi bề mặt khối. |
| `pozitification-0.1-26.2.jar` | `pozitification` | `0.1` | `*` | 0.09 MB | Thêm phím tắt ngồi (Sit) và bò trườn (Crawl) đồng bộ đa người chơi. |
| `slabbed-0.5.0-alpha.1+26.2.jar` | `slabbed` | `0.5.0-alpha.1` | `*` | 0.84 MB | Mở rộng khối bậc thang và phiến nửa cho mọi loại vật liệu. |
| `Clumps-fabric-26.2-26.2.1.jar` | `clumps` | `26.2.1` | `*` | 0.02 MB | Gộp các hạt XP Orbs rơi thành 1 cụm lớn chống drop FPS. |
| `AdvancementPlaques-26.2-fabric-1.7.2.jar` | `advancementplaques` | `1.7.2` | `Client` | 0.19 MB | Hiển thị bảng vinh danh thành tựu dạng Banner nổi. |
| `Iceberg-26.2-fabric-1.4.2.1.jar` | `iceberg` | `1.4.2.1` | `*` | 0.34 MB | Thư viện API nền tảng cho AdvancementPlaques. |
| `JustEnoughProfessions-fabric-26.2-12.0.0.jar` | `justenoughprofessions`| `12.0.0` | `*` | 0.02 MB | Tra cứu nghề nghiệp dân làng và khối bàn làm việc tương ứng. |
| `JustEnoughResources-Fabric-26.2-1.11.0.43.jar` | `justenoughresources`| `1.11.0.43` | `*` | 0.25 MB | Tra cứu tỉ lệ rơi đồ và phân bố tài nguyên khoáng sản. |
| `MoreVillagersRe-26.1.x-fabric-1.26.7.1.jar` | `morevillagersre` | `1.26.7.1` | `*` | 1.32 MB | Bổ sung thêm nhiều ngành nghề dân làng đặc hữu. |
| `skinlayers3d-fabric-1.11.2-mc26.2.jar` | `skinlayers3d` | `1.11.2` | `Client` | 1.88 MB | Hiển thị lớp da/áo khoác 3D nổi khối chân thực. |
| `EnchantmentDescriptions-fabric-MC26.2-26.2.0.1.jar` | `enchdesc` | `26.2.0.1` | `Client` | 0.08 MB | Hiển thị tooltip mô tả công năng của từng loại phù phép. |
| `fabric-api-0.156.0+26.2.jar` | `fabric-api` | `0.156.0+26.2`| `*` | 2.41 MB | **Bản Fabric API ổn định chuẩn của Server** (*Không dùng bản 0.158*). |

---

## 🛠️ 4. Quy Tắc Vận Hành Dành Cho AI Agent Tiếp Quản

### 4.1. Cảnh Báo Xung Đột & Tương Thích Mod (Gotchas)
1. **Fabric API Version:**
   * **Bắt buộc dùng:** `fabric-api-0.156.0+26.2.jar`.
   * **Không dùng:** `fabric-api-0.158.0+26.2.jar` vì bản 0.158 yêu cầu Fabric Loader phiên bản mới hơn và vô hiệu hóa các module server khiến server crash `FormattedException: fabric-api missing`.
2. **File Lớn Hơn 100MB (`watermedia_binaries`):**
   * File `mods/watermedia_binaries-3.0.0.6.jar` nặng `137 MB` vượt quá giới hạn 100MB của GitHub.
   * File này đã được thêm vào `.gitignore`. **Tuyệt đối không commit file này vào Git** mà chỉ upload trực tiếp lên VPS qua SFTP.
3. **Cấu hình `mob-ai-tweaks`:**
   * File `config/mob-ai-tweaks/general_config.txt` phải luôn giữ:
     ```
     illusioner_rework=false
     wardens_have_health_bars=false
     ```
   * *Lý do:* AI Illusioner bắn cung của mod này bị lỗi crash game trên Fabric 26.2.

---

### 4.2. Quy Trình Nạp Script & Khởi Động Server
1. **Chỉnh sửa Scarpet:** Chỉnh sửa [`world/scripts/custom_mob_effects.sc`](file:///C:/Users/ADMIN/MineServer/world/scripts/custom_mob_effects.sc).
2. **Upload SFTP:** Đẩy file lên VPS đường dẫn `world/scripts/custom_mob_effects.sc`.
3. **Reload trên Server Runtime:**
   * Gửi qua MCP `minecraft_command`:
     ```
     script unload custom_mob_effects
     script load custom_mob_effects
     ```
4. **Lệnh Test Boss Warden (Dành cho OP / Console):**
   * `/custom_mob_effects test_warden_phase_two`: Đặt máu Warden về 460 HP để test Phase 2 RAGE.
   * `/custom_mob_effects test_warden_heal`: Đặt máu Warden về 140 HP để test Huyết Tế 600 HP và nhạc BGM.
   * `/custom_mob_effects trigger_blood_moon`: Kích hoạt Đêm Trăng Máu ngay lập tức.
   * `/custom_mob_effects status`: Xem thông tin trạng thái ngày và chu kỳ Trăng Máu.

---

## 🌐 5. Cập Nhật Website Changelog & Triển Khai
* **Mã nguồn Website:** [`C:\Users\ADMIN\OlongBell-Changelogs`](file:///C:/Users/ADMIN/OlongBell-Changelogs)
* **Dữ liệu Changelog:** [`scripts/changelogs-data.js`](file:///C:/Users/ADMIN/OlongBell-Changelogs/scripts/changelogs-data.js)
* **Bản sao lưu Backup:** [`backup_state/`](file:///C:/Users/ADMIN/OlongBell-Changelogs/backup_state/)
* **Lệnh deploy Cloudflare Backend:**
  ```powershell
  npm --prefix "C:\Users\ADMIN\OlongBell-Changelogs\backend-worker" run deploy
  ```
* **Lệnh push Frontend:**
  ```powershell
  git -C "C:\Users\ADMIN\OlongBell-Changelogs" push origin main
  ```
