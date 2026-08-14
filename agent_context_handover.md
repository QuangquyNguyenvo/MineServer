# ✦ AGENT CONTEXT HANDOVER & WORKFLOW GUIDE (CẬP NHẬT MỚI NHẤT) ✦

Tài liệu này ghi lại toàn bộ bối cảnh dự án, trạng thái hệ thống, cấu hình hiện tại, các cơ chế game vừa được nâng cấp và nguyên tắc bảo mật quan trọng dành cho các AI Agent tiếp theo tiếp quản việc quản trị Minecraft Server **Chaos Cubed (Fabric 1.21+ / 26.2)**.

---

## 1. Thông Tin Chung & Kết Nối Server

* **Server Loader:** Fabric 26.2 (Minecraft 1.21+).
* **MCP Server kết nối VPS:** `pikamc-agent`
  * **Công cụ có sẵn:**
    * `ping`: Kiểm tra trạng thái Agent trên VPS (`PIKAMC_AGENT_OK`).
    * `minecraft_command`: Chạy các lệnh console qua RCON (Lưu ý: Không dùng dấu `/` ở đầu, ví dụ: `script load custom_mob_effects`, `servercore reload`, `bossbar list`, `tick query`).
* **Thông tin kết nối SFTP:**
  * **Host:** `ancient.pikamc.vn` | **Port:** `2022`
  * **Username:** `ehuiw3vt.770f2c1b` | **Password:** `0ZrcXJHS7J5OuGpV`
* **Kho lưu trữ mã nguồn Local:** [`C:\Users\ADMIN\MineServer`](file:///C:/Users/ADMIN/MineServer)
  * **GitHub Remote:** `https://github.com/QuangquyNguyenvo/MineServer.git` (nhánh `main`).
* **Trạng thái thư mục World (Data Thế Giới):**
  * Đã tải toàn bộ thư mục `world` (dung lượng **9.55 GB**, **8,989 files**) về máy cục bộ tại [`C:\Users\ADMIN\MineServer\world`](file:///C:/Users/ADMIN/MineServer/world).
  * Đã có sẵn script tải đa luồng và hỗ trợ resume khi đứt mạng tại `download_world.py`.

---

## 2. Các Cơ Chế Game & Cấu Hình Đã Thiết Lập

### ⚡ 1. Tối Ưu Hóa Hiệu Năng (ServerCore & Carpet)
* File cấu hình: [`config/servercore/config.yml`](file:///C:/Users/ADMIN/MineServer/config/servercore/config.yml)
* **View Distance:** `20` chunk.
* **Simulation Distance:** `7` chunk (Scale động xuống tối thiểu 5 khi quá tải).
* **Chunk-tick Distance:** `7` chunk (Scale động xuống tối thiểu 5).
* **Dynamic MSPT Target:** `35ms` (duy trì ổn định 20.0 TPS).
* **Tối ưu phụ:** `lobotomize-villagers: true` và `prevent-moving-into-unloaded-chunks: true`.

---

### 🩸 2. Boss Wither (Buffed)
* **Máu tối đa:** Tăng từ 300 lên **600 HP** khi spawn.
* **Sát thương chuẩn (True Damage):** Mọi đòn đánh trực tiếp từ Wither và đạn sọ `wither_skull` luôn gây thêm **2 True Damage (1 tim)** trừ thẳng vào máu người chơi, hoàn toàn bỏ qua mọi lớp giáp/enchantment.

---

### 👾 3. Boss Tối Thượng Warden (Đại Tu Toàn Diện & Phase 2)
Toàn bộ logic được lập trình bằng Scarpet tại file [`world/scripts/custom_mob_effects.sc`](file:///C:/Users/ADMIN/MineServer/world/scripts/custom_mob_effects.sc):

1. **Máu tối đa:** Tăng lên **1000 HP** khi spawn.
2. **Kháng sát thương vật lý động theo ngưỡng máu (Phase 1):**
   * Kháng **30%** khi máu $> 70\%$ (> 700 HP).
   * Kháng **50%** khi máu $< 50\%$ (< 500 HP).
3. **Kháng 80% Instant Damage / Phép thuật:**
   * Sát thương từ `magic` và `indirect_magic` (thuốc Instant Damage Harming I & II) bị giảm 80%.
4. **Miễn nhiễm ngạt nước (Drown Immunity):**
   * Hoàn toàn triệt tiêu sát thương ngạt nước (`drown`), không thể bị dìm chết.
5. **Sonic Boom True Damage:**
   * **Phase 1 (> 30% HP):** Gây sát thương chuẩn bằng **33% máu tối đa** của người chơi.
   * **Phase 2 (<= 30% HP):** Gây sát thương chuẩn tăng vọt lên **45% máu tối đa** của người chơi.
6. **Giảm hồi máu 50% (Healing Debuff):**
   * Trúng Sonic Boom sẽ dính debuff giảm 50% khả năng hồi máu từ mọi nguồn trong **5 giây (100 ticks)**.
7. **Trọng Lực Cực Đại (Anti-Flight Zone):**
   * Trong bán kính **40 blocks** quanh Warden, cấm bay hoàn toàn (Creative fly, Modded fly, Elytra) và kéo người chơi rơi thẳng xuống đất (`motion Y = -0.8`).
8. **Kéo Mục Tiêu (Sculk Vacuum / Pull):**
   * Nếu mục tiêu của Warden đứng xa $> 16$ block hoặc cao $> 6$ block, Warden kéo giật người chơi về chân nó kèm hiệu ứng hạt `sculk_soul` và âm thanh shriek (Cooldown **6 giây / 120 ticks**).
9. **Khả năng phá Block (Anti-Trapping):**
   * Quét vùng 3x4x3 quanh Warden mỗi 5 ticks và phá hủy các block rắn (kể cả Obsidian) bằng `/fill air destroy` để chống bị người chơi nhốt.
10. **Phản hiệu ứng xấu (Effect Reflection):**
    * Khi đánh trúng người chơi, Warden quét các hiệu ứng tiêu cực nó đang dính (Slowness, Poison, Weakness, Blindness...) và phản ngược lại người chơi trong tối đa 5 giây.
11. **🔥 Cơ chế Phase 2 - Cuồng Nộ (Kích hoạt khi <= 30% Máu / <= 300 HP):**
    * **Tăng tốc độ:** Tăng **50% tốc độ di chuyển** (`movement_speed` base set 0.45).
    * **Kháng 100% Sát thương tầm xa (Projectile Immunity):** Kháng tuyệt đối 100% mọi vật thể bắn (Cung tên, Đinh ba, Bình thuốc ném/kéo dài, Cầu lửa, Sọ Wither, Wind Charge, Pháo hoa...), buộc người chơi phải cận chiến bằng kiếm.
    * **Sonic Boom Thăng Hoa:** Sát thương chuẩn tăng lên **45% Max HP** của người chơi.
    * **Hiệu ứng & Âm thanh:** Gầm rú chuyển dạng (`warden.roar` + `ender_dragon.growl`), bung hạt linh hồn Sculk và Lửa linh hồn; thông báo Title cảnh báo toàn khu vực 40m.
12. **🩸 Cơ chế Huyết Tế Tối Thượng (Emergency Heal khi < 10% Máu trong Phase 2):**
    * Khi máu Warden tụt xuống dưới **10%** (< 100 HP) trong Phase 2, Warden kích hoạt cơ chế hồi sinh khẩn cấp 1 lần duy nhất: hấp thụ linh hồn Sculk và **hồi phục ngay lập tức về 30% Máu tối đa (300 HP)**.
    * Kèm hiệu ứng bảo hiểm chống chết sốc trong tick, nổ hạt Totem, tiếng đập tim dồn dập và thông báo Title `[HUYẾT TẾ TỐI THƯỢNG]`.
13. **Thanh máu Boss (Boss Health Bar):**
    * **Phase 1:** Tên **Warden** màu `dark_aqua` (`§3`), thanh màu `blue`.
    * **Phase 2:** Tên **Warden (Phase 2 - Cuồng Nộ)** màu `red` (`§c`), thanh chuyển sang màu `red`.
    * Tự động hiển thị/ẩn trong bán kính 40m quanh Warden.

---

### 🌕 4. Các Cơ Chế Khác
* **Đêm Trăng Máu (Blood Moon):** Xuất hiện chu kỳ 8-15 ngày; quái Overworld nhân 2.5x máu, tăng 30% tốc độ, 50% mù Blindness II và drop thêm vật phẩm hiếm (Diamond, TNT, Eye of Ender, Bow Power V...).
* **Nether Buffs:** Cấu hình qua BuffMobs tại [`config/buffmobs.json`](file:///C:/Users/ADMIN/MineServer/config/buffmobs.json) (Warden, Wither, Ender Dragon nằm trong blacklist của BuffMobs để tránh xung đột với Scarpet).

---

## 3. Quy Trình Đồng Bộ & Deploy Lên Server VPS

Khi cần sửa đổi file script hoặc cấu hình:
1. **Chỉnh sửa file tại local:** [`C:\Users\ADMIN\MineServer`](file:///C:/Users/ADMIN/MineServer).
2. **Upload trực tiếp lên VPS qua SFTP:**
   * Sử dụng thư viện paramiko để upload file tương ứng vào `/world/scripts/` hoặc `/config/`.
3. **Reload trên Server Runtime (qua MCP RCON):**
   * Để reload Scarpet: Chạy `script unload <tên_app>` rồi `script load <tên_app>` (ví dụ: `script unload custom_mob_effects` -> `script load custom_mob_effects`). *Lưu ý: Không có lệnh `script reload` trong bản Carpet hiện tại.*
   * Để reload ServerCore: Chạy `servercore reload`.
4. **Kiểm tra Logs & Gỡ lỗi:**
   * Tải và đọc đuôi file `logs/latest.log` từ VPS để kiểm tra lỗi runtime.
5. **Đồng bộ Git:**
   * `git add <file>`, `git commit -m "..."`, `git push origin main`.
   * *Lưu ý:* Không commit các tệp dữ liệu thế giới (dimensions, playerdata, camerapture...) lên Git.

---

## 4. ⚠️ Các Bài Học Kinh Nghiệm & Lưu Ý Lập Trình (Crucial Bugfixes)

1. **Lỗi `Unknown entity feature: dead` trong Scarpet:**
   * Trong Scarpet, thực thể người chơi **không** có thuộc tính `~ 'dead'`. Để kiểm tra người chơi còn sống hay không, chỉ cần kiểm tra máu `p ~ 'health' > 0` và biến lưu máu cũ `prev_hp > 0`.
2. **Lỗi trả về `'cancel'` trong Event Sát thương của Carpet:**
   * Việc trả về `'cancel'` trong `__on_damaged` hoặc `__on_player_takes_damage` không thể chặn sát thương hoàn toàn do bug của Carpet.
   * **Giải pháp chuẩn:** Sử dụng cơ chế hồi máu / điều chỉnh máu tại `schedule(0, ...)` (tick tiếp theo). Nếu sát thương gốc có nguy cơ giết chết thực thể trước khi được giảm trừ, hãy buff máu tạm thời cho thực thể ngay trong event để vượt qua hit đánh, rồi đưa về lượng máu chuẩn ở `schedule(0, ...)`.
3. **Giới hạn màu của Vanilla Bossbar:**
   * Lệnh `bossbar set color` của Minecraft chỉ nhận các màu: `pink`, `blue`, `red`, `green`, `yellow`, `purple`, `white`.
   * Để có màu `dark_aqua` (`§3`), ta định dạng màu này vào phần text của bossbar: `bossbar add warden_boss {"text":"Warden","color":"dark_aqua","bold":true}` và đặt thanh màu `blue`.

---

## 5. Danh Mục File Quan Trọng

| Đường dẫn File | Mô tả |
| :--- | :--- |
| [`world/scripts/custom_mob_effects.sc`](file:///C:/Users/ADMIN/MineServer/world/scripts/custom_mob_effects.sc) | Toàn bộ script Scarpet điều khiển Boss Warden, Wither, Bossbar, Trăng Máu và hiệu ứng On-Hit |
| [`config/servercore/config.yml`](file:///C:/Users/ADMIN/MineServer/config/servercore/config.yml) | Cấu hình tối ưu hóa hiệu năng ServerCore (View/Simulation distance, Dynamic MSPT) |
| [`config/buffmobs.json`](file:///C:/Users/ADMIN/MineServer/config/buffmobs.json) | Cấu hình tăng chỉ số quái thường Nether/Overworld của mod BuffMobs |
| [`mob_buffs_summary.md`](file:///C:/Users/ADMIN/MineServer/mob_buffs_summary.md) | Tài liệu tóm tắt các cơ chế Buff quái và Trăng Máu |
| [`walkthrough.md`](file:///C:/Users/ADMIN/.gemini/antigravity-cli/brain/8a36bc8b-0faa-4bb4-b9de-7b6de53e1149/walkthrough.md) | Nhật ký thay đổi và kiểm tra thực tế |
