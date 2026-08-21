# ✦ AGENT CONTEXT HANDOVER & SYSTEM MASTER GUIDE ✦
> **Dành cho AI Agent tiếp theo tiếp quản quản trị hệ thống Minecraft Server Chaos Cubed & Client Test Instance.**

---

## 1. 🌐 Tổng Quan Kiến Trúc & Thông Tin Kết Nối

### 1.1. Server Production (VPS Dedicated Server)
* **Loader & Phiên bản:** Fabric 26.2 (Minecraft 1.21+).
* **Kết nối MCP Server:** `pikamc-agent`
  * `ping`: Kiểm tra agent heartbeat trên VPS (`PIKAMC_AGENT_OK`).
  * `minecraft_command`: Thực thi lệnh Console/RCON trực tiếp (Không dùng dấu `/` ở đầu, ví dụ: `script unload custom_mob_effects`, `script load custom_mob_effects`, `tick query`).
* **Kết nối SFTP Quản Trị:**
  * **Host:** `ancient.pikamc.vn` | **Port:** `2022`
  * **User:** `ehuiw3vt.770f2c1b` | **Password:** `0ZrcXJHS7J5OuGpV`
* **Kho lưu trữ mã nguồn Local:** [`C:\Users\ADMIN\MineServer`](file:///C:/Users/ADMIN/MineServer)
* **GitHub Repository:** `https://github.com/QuangquyNguyenvo/MineServer.git` (nhánh `main`).

---

### 1.2. Client Test Instance (CurseForge Singleplayer)
* **Đường dẫn thư mục:** [`C:\Users\ADMIN\curseforge\minecraft\Instances\test`](file:///C:/Users/ADMIN/curseforge/minecraft/Instances/test)
* **Mục đích:** Dùng để người dùng vào game Singleplayer test trực tiếp các cơ chế Boss, sát thương, hiệu ứng và nhạc nền trước khi triển khai hoặc kiểm tra tính năng.
* **Danh sách 10 mod cốt lõi tối giản (đã dọn dẹp các mod thừa):**
  1. `fabric-carpet-26.2+v260616.jar` (Engine chạy script Scarpet).
  2. `scarpet-additions-26.1-1.1.4.jar` (Thư viện mở rộng Carpet).
  3. `weaponsexpanded_26.2_1.9.2_fabric.jar` (Vũ khí Lưỡi Hái Netherite `Void Reaper`).
  4. `buffmobs-3.2.0+mc26.2-fabric.jar` (Quản lý chỉ số quái).
  5. `mob-ai-tweaks-1.11.0-beta.jar` (AI quái vật nâng cao).
  6. `advancednetherite-fabric-2.4.2-26.2.jar` (Netherite mở rộng).
  7. `moretotems-fabric-26.2-2.25.0.jar` (Totem mở rộng).
  8. `fabric-api-0.156.0+26.2.jar` (Thư viện nền tảng).
  9. `sodium-fabric-0.9.1+mc26.2.jar` (Tối ưu FPS đồ họa Client).
  10. `modmenu-20.0.1.jar` (Giao diện menu mod).
* **Vị trí script test:** [`config/carpet/scripts/custom_mob_effects.sc`](file:///C:/Users/ADMIN/curseforge/minecraft/Instances/test/config/carpet/scripts/custom_mob_effects.sc) và [`saves/New World/scripts/custom_mob_effects.sc`](file:///C:/Users/ADMIN/curseforge/minecraft/Instances/test/saves/New%20World/scripts/custom_mob_effects.sc).

---

## 2. 👾 Boss Tối Thượng Warden (Đại Tu Toàn Diện & Phase 2)

Toàn bộ logic được lập trình bằng Scarpet độc lập tại file [`world/scripts/custom_mob_effects.sc`](file:///C:/Users/ADMIN/MineServer/world/scripts/custom_mob_effects.sc):

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                    ✦ THÔNG SỐ CHIẾN ĐẤU BOSS WARDEN ✦                                    │
├──────────────────────────┬───────────────────────────────────────────────────────────────────────────────┤
│ Máu tối đa (Max HP)      │ 1,500 HP (generic.max_health base set 1500, thanh Bossbar max 1500)          │
│ Tốc độ ban đầu           │ 0.30 (nhanh hơn vanilla)                                                     │
│ Kháng vật lý động        │ Kháng 30% khi máu > 1,050 HP (> 70%); Kháng 50% khi máu < 750 HP (< 50%)      │
│ Kháng phép thuật         │ Kháng 80% Instant Damage (Harming I/II, Magic, Indirect Magic)               │
│ Kháng môi trường         │ Miễn nhiễm 100% sát thương ngạt nước (Drown Immunity)                        │
│ Phản hiệu ứng xấu        │ Phản ngược Slowness, Poison, Weakness, Blindness... lại người chơi trong 5s   │
│ Trọng lực cực đại        │ Anti-Flight trong 40m (kéo người chơi rơi tự do motion Y = -0.8 nếu bay)     │
│ Kéo mục tiêu (Vacuum)    │ Kéo giật người chơi cách xa > 16m hoặc cao > 6m về chân Warden (CD: 6s/120t)  │
│ Phá hủy địa hình         │ Quét phá hủy block rắn vùng 3x4x3 mỗi 5 ticks để chống bị nhốt               │
│ Thông báo Spawn Global   │ Broadcast chat đỏ toàn server kèm tọa độ X, Y, Z và âm thanh gầm emerge      │
└──────────────────────────┴───────────────────────────────────────────────────────────────────────────────┘
```

---

### 🔥 2.1. Cơ Chế Phase 2 - Cuồng Nộ / RAGE (Kích hoạt khi $\le 30\%$ Máu / $\le 450$ HP)
* **Tăng tốc độ:** Tăng **50% tốc độ di chuyển** (`movement_speed` base set `0.45`).
* **Kháng 100% Vật Thể Bắn (Projectile Immunity):** Miễn nhiễm hoàn toàn với cung tên, đinh ba, thuốc ném, cầu lửa, sọ Wither, Wind Charge, pháo hoa... **bắt buộc người chơi phải cận chiến bằng kiếm/vũ khí**.
* **Sonic Boom Thăng Hoa:** Gây sát thương chuẩn **45% Max HP** của người chơi (Phase 1 là 33% Max HP) kèm **Debuff giảm 50% hồi máu trong 5s (100 ticks)**.
* **Âm thanh & Hiệu ứng:** Gầm rú toàn server (`entity.warden.roar` + `entity.ender_dragon.growl`), bùng nổ hạt linh hồn Sculk và lửa xanh.
* **Thông báo Title & Tellraw Chat:**
  * **Subtitle:** `Warden đã bùng nổ năng lượng Sculk!` (màu `dark_red`, in nghiêng).
  * **Tellraw Chat:** `[WARNING] Warden đã rơi vào trạng thái CUỒNG NỘ (RAGE)!\nSức mạnh Sculk bùng nổ, mọi đòn đánh giờ đây bỏ qua giáp!`.
* **Thanh Bossbar:** Chuyển tên thành `Warden (Phase 2 - Cuồng Nộ)` màu đỏ và thanh máu chuyển sang màu đỏ (`red`).

---

### 🩸 2.2. Cơ Chế Huyết Tế Tối Thượng (Emergency Heal khi $< 10\%$ Máu / $< 150$ HP trong Phase 2)
* **Kích hoạt duy nhất 1 lần trong trận đấu** khi máu tụt dưới 150 HP.
* **Chướng Khí Độc (40m Radius):** Áp đặt **Buồn nôn II (Nausea II)**, **Mù quáng (Blindness)** và **Trúng độc II (Poison II)** lên toàn bộ người chơi trong 40m trong **10 giây (200 ticks)**.
* **Bất Tử 100% (10 Giây):** Warden được miễn nhiễm 100% mọi nguồn sát thương trong suốt 10 giây vận khí hồi phục; liên tục bung hạt Totem, linh hồn Sculk và nhịp tim dồn dập.
* **Tốc Độ Hồi Phục:** Hồi từ 150 HP lên **600 HP (40% Max HP)** với tốc độ **+2.25 HP/tick** (+45 HP/giây).

---

### 🎵 2.3. Hệ Thống Nhạc Nền BGM Looping & Resource Pack
* **Giai đoạn 1 (Spawn / Phase 1):** Phát bài `minecraft:custom.warden_theme` (BGM chiến đấu loop vô tận trong bán kính 40m).
* **Giai đoạn 2 (Huyết Tế / Phase 2):** Khi Huyết Tế kích hoạt, nhạc lập tức dừng bài cũ và chuyển sang `minecraft:custom.warden_sacrifice` (cắt từ giây thứ 14 của bản nhạc cuồng nộ, loop vô tận cho đến khi Warden chết).
* **Smart Stopsound:** Tự động ngắt nhạc sạch sẽ khi người chơi rời khỏi bán kính 40m hoặc Warden bị tiêu diệt.
* **Resource Pack Server:** [`ChaosCubed_Warden_BGM.zip`](file:///C:/Users/ADMIN/MineServer/ChaosCubed_Warden_BGM.zip) (5.92 MB, SHA1: `67b5bce83e97785b6b1ca59d7122485d06b87e8d`).
* **Cấu hình `server.properties` trên VPS:**
  * `resource-pack=https://raw.githubusercontent.com/QuangquyNguyenvo/MineServer/main/ChaosCubed_Warden_BGM.zip`
  * `resource-pack-sha1=67b5bce83e97785b6b1ca59d7122485d06b87e8d`

---

### 🎁 2.4. Phần Thưởng Rơi Ra Khi Hạ Gục Warden (Mythic Loot Drops)

| Nhóm phần thưởng | Tỉ lệ rơi | Chi tiết vật phẩm |
| :--- | :---: | :--- |
| **Nhóm 1: Đảm bảo (Guaranteed)** | **100%** | • **1x Lõi Nặng (Heavy Core - `minecraft:heavy_core`)**<br>• **1 - 2x Sao Địa Ngục (Nether Star)**<br>• **1 - 2x Phôi Nâng Cấp Netherite (`netherite_upgrade_smithing_template`)**<br>• **~2,000 XP Orbs (4 cụm 500 XP)** |
| **Nhóm 2: Bảo Khí Thần Thoại (Mythic God Gear)** | **10%** *(Chọn 1 trong 3)* | 1. 🗡️ **Lưỡi Đao Hư Không (`Void Reaper`):** Lưỡi Hái Netherite (`weaponsexpanded:netherite_scythe`) phù phép **Sharpness VII, Looting IV, Sweeping Edge IV, Unbreaking V, Mending**.<br>2. 🛡️ **Giáp Ngực Hư Vô (`Sculk Carapace`):** Giáp ngực Netherite phù phép **Protection VI, Thorns IV, Unbreaking V, Mending**.<br>3. 👢 **Ủng Bóng Ma (`Ghost Walker Boots`):** Giày Netherite phù phép **Protection VI, Feather Falling V, Depth Strider IV, Soul Speed III, Mending**. |
| **Nhóm 3: Kho Báu Jackpot** | **50%** *(Độc lập)* | • **2 - 4x Táo Vàng Notch (Enchanted Golden Apple)**<br>• **2x Thỏi Netherite (Netherite Ingot)**<br>• **2x Totem Bất Tử (Totem of Undying)**<br>• **1x Bản Mẫu Trang Trí Áo Giáp Silence (`silence_armor_trim_smithing_template`)** |
| **Hiệu ứng chiến tích** | **100%** | Bắn pháo hoa ăn mừng, âm thanh `ui.toast.challenge_complete` và vinh danh `[CHIẾN TÍCH] <Tên người chơi> đã tiêu diệt thành công Chúa Tể Bóng Tối Warden!` trên khung chat toàn server. |

---

### ⚔️ 2.5. Khắc Chế Iron Golem, Cú Đập Địa Chấn & Sonic Boom
* **Cú Đập Địa Chấn Khắc Chế Iron Golem (Anti-Golem Earth Slam):**
  * Quét Iron Golem trong bán kính **8 blocks** (CD: 4 giây / 80 ticks).
  * Tung cú đập địa chấn hất tung Iron Golem lên trời (Motion Y = `+0.9`) và gây **50 Sát Thương** (Chỉ tác dụng lên Iron Golem, an toàn cho Người chơi).
  * Đánh trúng hoặc tiêu diệt Iron Golem $\rightarrow$ Hồi **+5 HP** cho Warden (Kèm thời gian hồi **Cooldown 1 giây / 20 ticks** giữa các lần hút máu để chống hồi máu dồn dập).
* **Kháng 90% Sát Thương Không Phải Người Chơi (Rage Phase):**
  * Khi Warden ở trạng thái Cuồng Nộ (Phase 2 / Rage, $\le 30\%$ HP), Warden được **miễn nhiễm 90% sát thương** từ tất cả các sinh vật không phải người chơi (Iron Golem, Mob, Wither, v.v., chỉ nhận 10% sát thương). Đòn đánh từ Người chơi vẫn gây 100% sát thương đầy đủ.
* **Trần Máu Tối Đa Khóa Cứng (Hard Cap 600 HP):**
  * Khi Warden đã bước vào Phase 2 (RAGE) hoặc kích hoạt Huyết Tế, lượng máu của Warden cho dù được hồi phục từ bất kỳ nguồn nào (Huyết Tế, hút máu Iron Golem, Sonic Boom tầm xa, hồi phục tự nhiên) đều **không bao giờ được vượt quá 600 HP**.
* **Tiến Hóa Combo Sonic Boom (Combo 3+1 / 2+1):**
  * **Trước khi Huyết Tế:** Cứ sau **3 đòn đánh thường** $\rightarrow$ đòn tiếp theo (đòn 4) chắc chắn là **Sonic Boom**.
  * **Sau khi Huyết Tế hoàn tất:** Cứ sau **2 đòn đánh thường** $\rightarrow$ đòn tiếp theo (đòn 3) chắc chắn là **Sonic Boom**.
  * Áp dụng khi đánh cả Người chơi lẫn Iron Golem.
* **Sonic Boom Tầm Xa Hồi Máu (+10 HP Siphon):**
  * Khi mục tiêu là Người chơi đứng ở cự ly xa ($> 4.5\text{m}$), mỗi đòn Sonic Boom trúng đích sẽ **hồi phục `+10 HP` cho Warden** (không kích hoạt lặp lên Iron Golem).
* **Hút Sinh Lực Khi Tiêu Diệt Người Chơi (+50 HP Siphon on Kill - Chống Spawn-Camp):**
  * Khi có bất kỳ người chơi nào tử vong trong bán kính **30 blocks** quanh Warden $\rightarrow$ Warden hấp thụ linh hồn và được **hồi phục `+50.0 HP`** (vẫn tuân thủ giới hạn trần máu 600 HP trong Phase 2).
  * Chỉ phát hiệu ứng âm thanh gầm/nhịp tim và hạt linh hồn, hoàn toàn **không gửi thông báo chữ** lên chat/màn hình người chơi.
* **Sát Thương Chuẩn Kết Liễu:**
  * Sát thương chuẩn Sonic Boom (33% / 45% Max HP) có thể kết liễu người chơi trực tiếp (khiến máu về $\le 0$ sẽ tử vong ngay hoặc nổ Totem of Undying, không còn bị giữ bất tử ở 0.5 HP).

---

## 3. 🌕 Đêm Trăng Máu & Các Cơ Chế Mob Khác

1. **Đêm Trăng Máu (Blood Moon):**
   * Chu kỳ xuất hiện: **8 đến 15 ngày một lần** tại Overworld.
   * Quái vật Overworld: **x2.5 Max HP**, **+30% tốc độ di chuyển**, đánh gây thêm 2 True Damage (1 tim) và **50% cơ hội gây mù Blindness II trong 3s**.
   * Phần thưởng rơi thêm: Zombie (10% Diamond), Creeper (10% TNT), Enderman (10% Eye of Ender), Skeleton (5% Bow Power V / Punch II / Mending), Spider (10% Slow Falling Potion).
2. **Boss Wither:** 600 Max HP, mọi đòn đánh và đạn sọ `wither_skull` gây thêm **+4.0 True Damage (2 tim)** trừ thẳng vào máu người chơi, xuyên qua giáp.
3. **Boss Ender Dragon:** 700 Max HP, gây thêm +1.0 sát thương trực tiếp ở The End.
4. **Enderman / Shulker (The End):** Nhân 1.5x Max HP (Enderman 60 HP, Shulker 45 HP), Enderman tăng +1.0 sát thương.
5. **Nether Mobs:** Cấu hình qua [`config/buffmobs.json`](file:///C:/Users/ADMIN/MineServer/config/buffmobs.json).

---

## 4. 🛠️ Quy Trình Thao Tác & Deploy Chuẩn (SOP)

Khi cần sửa đổi code script Scarpet hoặc file config:

1. **Chỉnh sửa tại local:** [`C:\Users\ADMIN\MineServer\world\scripts\custom_mob_effects.sc`](file:///C:/Users/ADMIN/MineServer/world/scripts/custom_mob_effects.sc).
2. **Upload lên VPS qua SFTP:**
   ```python
   import paramiko
   transport = paramiko.Transport(('ancient.pikamc.vn', 2022))
   transport.connect(username='ehuiw3vt.770f2c1b', password='0ZrcXJHS7J5OuGpV')
   sftp = paramiko.SFTPClient.from_transport(transport)
   sftp.put(r'C:\Users\ADMIN\MineServer\world\scripts\custom_mob_effects.sc', 'world/scripts/custom_mob_effects.sc')
   sftp.close(); transport.close()
   ```
3. **Reload trên VPS Runtime (qua tool `minecraft_command`):**
   * Bước 1: `script unload custom_mob_effects`
   * Bước 2: `script load custom_mob_effects`
   *(Lưu ý: Không dùng `script reload` vì Carpet phiên bản này không hỗ trợ reload trực tiếp).*
4. **Đồng bộ sang Instance Test CurseForge:**
   * Copy sang `C:\Users\ADMIN\curseforge\minecraft\Instances\test\config\carpet\scripts\custom_mob_effects.sc`
   * Copy sang `C:\Users\ADMIN\curseforge\minecraft\Instances\test\saves\New World\scripts\custom_mob_effects.sc`
5. **Commit Git:** `git add ...`, `git commit -m "..."`, `git push origin main`.

---

## 5. ⚠️ Các Bài Học Kinh Nghiệm Kỹ Thuật (Crucial Gotchas & Rules)

1. **Không dùng các hàm add-on không thuộc chuẩn Vanilla Scarpet:**
   * Không dùng `distance(p1, p2)` hoặc `attribute(e, name)` vì chúng phụ thuộc vào mod `scarpet-additions`.
   * **Luôn dùng hàm helper nội tại:**
     * `_distance(p1, p2)`: Tự tính khoảng cách Euclid 3D $\sqrt{dx^2 + dy^2 + dz^2}$.
     * `_get_attribute(e, name, default)`: Sử dụng `query(e, 'max_health')` hoặc `query(e, 'attribute', name)`.
2. **Quyền hạn lệnh test (`_check_permission`):**
   * File script cấu hình `__config() -> {'scope' -> 'global', 'command_permission' -> 4}`.
   * Hàm `_check_permission()` cho phép **Server Console / RCON** (`player() == null`) và người chơi có quyền **Admin OP cấp $\ge 2$ trong Singleplayer/Server** sử dụng lệnh test, đồng thời **chặn 100% người chơi bình thường**.
3. **Xung đột AI Illusioner trong `mob-ai-tweaks`:**
   * Trong Minecraft 1.21+, AI bắn cung tùy chỉnh của `mob-ai-tweaks` gây lỗi `IllegalArgumentException: Invalid weapon firing an arrow` làm crash server.
   * **Quy tắc bắt buộc:** File [`config/mob-ai-tweaks/general_config.txt`](file:///C:/Users/ADMIN/MineServer/config/mob-ai-tweaks/general_config.txt) phải luôn đặt `illusioner_rework=false` và `wardens_have_health_bars=false`.
4. **Tránh lỗi trả về `'cancel'` trong Event Hook sát thương:**
   * Carpet không thể hủy hoàn toàn đòn đánh bằng `'cancel'`. Luôn sử dụng cơ chế buff máu đệm và hồi phục trong `schedule(0, ...)`.
5. **Thực thể Player không có thuộc tính `~ 'dead'`:**
   * Luôn kiểm tra trạng thái sống của người chơi bằng `p ~ 'health' > 0`.

---

## 6. 🎮 Danh Sách Lệnh Kiểm Tra Nhanh (Cheat Sheet)

| Lệnh Minecraft Console / OP | Mô tả chức năng |
| :--- | :--- |
| `/custom_mob_effects test_warden_phase_two` | Đặt máu Warden về 460 HP (sẵn sàng kích hoạt Phase 2 RAGE khi tụt $\le 450$ HP) |
| `/custom_mob_effects test_warden_heal` | Đặt Warden vào Phase 2 với 140 HP (kích hoạt ngay Huyết Tế 600 HP, Chướng khí & Nhạc Cuồng Nộ) |
| `/custom_mob_effects trigger_blood_moon` | Kích hoạt Đêm Trăng Máu ngay lập tức và chuyển giờ về Hoàng Hôn |
| `/custom_mob_effects status` | Xem trạng thái Trăng Máu và chu kỳ ngày hiện tại |
| `tick query` | Kiểm tra MSPT và TPS thực tế của Server |

---

## 7. 🌐 Hướng Dẫn Tích Hợp Web (Minecraft Web Integration)

Toàn bộ hướng dẫn tích hợp hiển thị trạng thái server (`ancient.pikamc.vn:25238`), số người online và avatar danh sách người chơi lên trang web (HTML/JS, React, Next.js, Node.js) đã được lưu chi tiết tại:
👉 [`MINECRAFT_WEB_INTEGRATION_GUIDE.md`](file:///C:/Users/ADMIN/MineServer/MINECRAFT_WEB_INTEGRATION_GUIDE.md)
* **REST API Endpoint:** `https://api.mcsrvstat.us/3/ancient.pikamc.vn:25238`
* **Avatar API:** `https://mc-heads.net/avatar/{username}/32`

---

## 8. 📜 Tài Liệu Changelog Boss Wither & Warden (Boss Mechanics Changelog)

Bản tổng hợp chi tiết toàn bộ các cơ chế thay đổi sức mạnh, chỉ số cân bằng, combo đòn đánh, các phase chiến đấu, hiệu ứng âm thanh/hình ảnh và gói phần thưởng thần thoại của Boss **Wither** và **Warden** được lưu trữ đầy đủ tại:
👉 [`CHANGELOG_BOSS_MECHANICS.md`](file:///C:/Users/ADMIN/MineServer/CHANGELOG_BOSS_MECHANICS.md)


