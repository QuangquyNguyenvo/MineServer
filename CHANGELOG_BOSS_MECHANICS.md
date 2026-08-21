# 📜 CHANGELOG: HỆ THỐNG CÂN BẰNG BOSS WITHER & WARDEN (FABRIC 1.21.1)

> **Dành cho:** AI Coding Agent & Nhà phát triển Máy chủ.  
> **Tệp thực thi mã nguồn:** [world/scripts/custom_mob_effects.sc](file:///C:/Users/ADMIN/MineServer/world/scripts/custom_mob_effects.sc)  
> **Bản cập nhật:** Minecraft 1.21.1 Fabric (Carpet Mod & Scarpet Engine)

---

## 📑 MỤC LỤC
1. [Boss 1: Wither (Chúa Tể Hư Vô)](#1--boss-wither-chúa-tể-hư-vô)
2. [Boss 2: Warden (Chúa Tể Bóng Tối) - Tổng Quan Chỉ Số](#2--boss-warden-chúa-tể-bóng-tối---tổng-quan-chỉ-số)
3. [Warden: Các Phase Chiến Đấu & Cơ Chế Huyết Tế](#3--warden-các-phase-chiến-đấu--cơ-chế-huyết-tế)
4. [Warden: Combo Đòn Đánh & Sát Thương Chuẩn Sonic Boom](#4--warden-combo-đòn-đánh--sát-thương-chuẩn-sonic-boom)
5. [Warden: Cơ Chế Chống Cheese & Chống Spawn-Camp](#5--warden-cơ-chế-chống-cheese--chống-spawn-camp)
6. [Warden: Hệ Thống BGM, Bossbar Đa Boss & Phần Thưởng Thần Thoại](#6--warden-hệ-thống-bgm-bossbar-đa-boss--phần-thưởng-thần-thoại)
7. [Tóm Tắt Bảng Thông Số Kỹ Thuật So Sánh](#7--tóm-tắt-bảng-thông-số-kỹ-thuật-so-sánh)

---

## 1. 💀 Boss: Wither (Chúa Tể Hư Vô)

### 🔹 Chỉ Số & Thuộc Tính:
- **Máu Tối Đa (Max Health):** **`600.0 HP`** *(Gấp đôi Vanilla: 300 HP)*.
- **Sát Thương Chuẩn Xuyên Giáp (True Armor-Piercing Damage):**
  - Tất cả đòn tấn công cận chiến và toàn bộ đạn sọ `wither_skull` trúng người chơi gây thêm **`+4.0 True Damage` (2 trái tim đỏ)** trừ trực tiếp vào thanh máu.
  - Sát thương này **bỏ qua hoàn toàn** điểm giáp Netherite, bùa bảo vệ Protection IV và hiệu ứng kháng cự Resistance.
- **Hiệu ứng đi kèm:** Wither II duy trì liên tục kết hợp sát thương chuẩn.

---

## 2. 👹 Boss: Warden (Chúa Tể Bóng Tối) - Tổng Quan Chỉ Số

### 🔹 Chỉ Số Cơ Bản:
- **Máu Tối Đa Khởi Đầu (Phase 1 Max Health):** **`1500.0 HP`** *(Gấp 3 lần Vanilla: 500 HP)*.
- **Tốc Độ Di Chuyển (Movement Speed):** **`0.30`** *(+20% so với Vanilla `0.25`)*.
- **Kháng Nước Tuyệt Đối (Water Immunity):** Đặt `air = 300` liên tục mỗi tick, miễn nhiễm 100% ngạt thở trong nước/dung nham.
- **Chống Bẫy / Nhốt (Anti-Trapping):** Tự động phá hủy toàn bộ các khối cứng xung quanh trong bán kính 2 blocks mỗi 5 ticks nếu bị người chơi cô lập.
- **Chống Bay (Anti-Flight Gravity):** Khi người chơi bay (Elytra/Creative) trong phạm vi 30m quanh Warden, tự động áp dụng lực hút trọng lực kéo người chơi rơi xuống đất.

---

## 3. ⚔️ Warden: Các Phase Chiến Đấu & Cơ Chế Huyết Tế

### 🔴 Phase 2: Cuồng Nộ (RAGE State - Máu <= 30% / 450 HP):
1. **Kích hoạt:** Khi máu của Warden giảm xuống <= 450.0 HP.
2. **Cơ chế Giảm Sát Thương Chọn Lọc (Selective Damage Reduction):**
   - **Người chơi (Player):** Gây **`100% sát thương đầy đủ`** (cận chiến, cung tên, mace đều không bị giảm).
   - **Sinh vật Không Phải Người Chơi (Non-Player: Iron Golem, Mob, Wither,...):** Warden được **giảm 90% sát thương** (chỉ nhận 10% damage, hoàn lại 90% máu bị mất). *Mục đích: Chặn hoàn toàn chiến thuật farm Boss bằng Iron Golem.*
3. **Trần Máu Tối Đa Khóa Cứng (Hard Cap 600 HP):**
   - Một khi đã bước vào Phase 2, lượng máu tối đa cho phép của Warden bị **khóa cứng ở mức trần `600.0 HP` (40% Max HP)**. Mọi cơ chế hồi phục (Huyết Tế, hút máu Golem, Sonic boom, v.v.) không bao giờ được vượt quá 600 HP.

### 🩸 Huyết Tế Tối Thượng (Ultimate Blood Sacrifice - Máu < 10% / 150 HP):
1. **Điều kiện:** Kích hoạt **duy nhất 1 lần trong đời Boss** khi ở Phase 2 và máu tụt xuống < 150.0 HP.
2. **Thời gian thi triển:** Kéo dài đúng **10 giây (200 ticks)**.
3. **Bất Tử Tuyệt Đối (Invulnerability):** Gán tag `{Invulnerable:1b}`, miễn nhiễm 100% sát thương từ mọi nguồn trong 10 giây.
4. **Hồi Máu Đều Đặn:** Tăng +2.25 HP mỗi tick cho đến khi đạt chính xác **`600.0 HP`**.
5. **Vòng Xoáy Hắc Ám (Sculk Singularity Vortex):** Mỗi 5 ticks, tác dụng lực kéo liên tục hút toàn bộ người chơi trong phạm vi **40m** về phía chân Warden.
6. **Chướng Khí Độc Tố (Miasma Debuffs 40m):**
   - `Darkness` (12 giây)
   - `Slowness IV` (10 giây)
   - `Weakness III` (10 giây)
   - `Nausea III` (8 giây)
   - `Wither II` (10 giây)

---

## 4. ⚡ Warden: Combo Đòn Đánh & Sát Thương Chuẩn Sonic Boom

### 🥊 Tiến Hóa Combo Cận Chiến:
- **Giai đoạn Trước Huyết Tế (Pre-Sacrifice): Combo 3+1**
  - Cứ sau **3 đòn đánh cận chiến thường** -> Đòn thứ 4 tự động nạp và phóng **Sonic Boom Cận Chiến**.
- **Giai đoạn Sau Huyết Tế (Post-Sacrifice Evolution): Combo 2+1**
  - Cứ sau **2 đòn đánh cận chiến thường** -> Đòn thứ 3 tự động phóng **Sonic Boom Cận Chiến**.

### 💥 Sát Thương Chuẩn & Khả Năng Kết Liễu:
- **Tỷ lệ sát thương Sonic Boom:**
  - **Trước Huyết Tế:** Gây sát thương chuẩn = **`33% Max HP`** của mục tiêu.
  - **Sau Huyết Tế:** Gây sát thương chuẩn = **`45% Max HP`** của mục tiêu.
- **Kết Liễu Trực Tiếp (Lethal Execution):** Sát thương chuẩn Sonic Boom có khả năng kết liễu người chơi trực tiếp (khiến máu về <= 0 sẽ tử vong hoặc nổ Totem of Undying, không còn bị kẹt giữ mạng ở 0.5 HP).

### 💚 Đòn Đánh Lên Iron Golem & Hút Máu:
- Mỗi đòn đánh thường lên Iron Golem gây **25% Max HP của Golem** (25 sát thương chuẩn).
- Mỗi đòn đánh trúng Iron Golem **hồi phục `+5.0 HP` cho Warden** (kèm cooldown 1 giây / 20 ticks giữa các lần hút máu).
- Đòn Sonic Boom tầm xa (> 4.5m) trúng Người chơi **hồi phục `+10.0 HP` cho Warden**.

---

## 5. 🛡️ Warden: Cơ Chế Chống Cheese & Chống Spawn-Camp

### 🩸 Hút Sinh Lực Khi Tiêu Diệt Người Chơi (+50 HP Siphon on Player Kill):
- **Phạm vi kích hoạt:** Bán kính **30 blocks** xung quanh Warden.
- **Cơ chế:** Bất kể khi nào có người chơi tử vong trong phạm vi 30m (do Warden đánh, dính chướng khí, té ngã,...), Warden lập tức hấp thụ linh hồn người chơi và **được hồi phục `+50.0 HP`** (tối đa không vượt trần 600 HP).
- **Thiết kế Im Lặng (Silent Mode):** Hoàn toàn **không gửi bất kỳ thông báo chữ nào** lên chat hay màn hình; chỉ phát âm thanh nhịp tim `heartbeat` và bùng nổ hiệu ứng hạt linh hồn `sculk_soul`.
- 👉 **Tác dụng:** Chặn đứng hoàn toàn chiến thuật "đặt giường cạnh Boss để liên tục hồi sinh chém bào máu (naked rush)", vì mỗi mạng chết sẽ nuôi máu lại cho Boss nhiều hơn lượng sát thương gây ra.

---

## 6. 🎵 Warden: Hệ Thống BGM, Bossbar Đa Boss & Phần Thưởng Thần Thoại

### 🎶 Nhạc Nền BGM Looping Tùy Biến (Custom Sound Engine):
- **Âm thanh Chiến đấu thường / Phase 1:** Phát file nhạc `minecraft:custom.warden_theme` (Looping 4180 ticks / ~3m29s).
- **Âm thanh Huyết Tế / Phase 2 Evolved:** Phát file nhạc `minecraft:custom.warden_sacrifice` (Looping 3500 ticks / ~2m55s).
- **Cơ chế Đa Boss Động (Per-Player Dynamic Tracking):** Tự động phát hiện Warden gần nhất với từng người chơi trong bán kính 50m. Khi rời xa khỏi 50m, nhạc tự động `stopsound` dừng ngay lập tức.

### 📊 Thanh Máu Bossbar Đa Boss:
- **Hiển thị:**
  - Phase 1: `Warden` (Màu xanh dương / `blue` / `dark_aqua`).
  - Phase 2: `Warden (Phase 2 - Cuồng Nộ)` (Màu đỏ / `red`).
- **Mở khóa giới hạn máu hiển thị:** Đã tích hợp mod `AttributeFix` trên cả Server & Client để hiển thị chuẩn xác toàn bộ `1500 / 1500 HP` mà không bị chặn ở mức mặc định `1024 HP`.

### 🎁 Gói Phần Thưởng Thần Thoại (Mythic Drops):
Chỉ rơi khi tiêu diệt thành công Warden sau khi đã trải qua giai đoạn Huyết Tế:
1. **Giáp Ngực Hư Vô (Void Chestplate):**
   - Giáp Netherite: Protection V, Unbreaking IV, Mending, Thorns III.
   - Thuộc tính cộng thêm: `+4 Armor Toughness`, `+2 Knockback Resistance`, `+4.0 Max Health` (2 tim đỏ).
2. **Ủng Bóng Ma (Phantom Boots):**
   - Ủng Netherite: Feather Falling V, Soul Speed III, Depth Strider III.
   - Thuộc tính cộng thêm: `+15% Tốc độ di chuyển`, `+2 Armor Toughness`.
3. **Ngọc Điêu Khắc Hư Không (Sculk Core Artifact):** Cổ vật thần thoại tùy biến.
4. **Tài nguyên quý:** 2-4 Thỏi Netherite, 16-32 Kim Cương, 32-64 Mảnh Vỡ Tiếng Vọng (Echo Shard), 3000 Điểm Kinh Nghiệm (XP).

---

## 7. 📊 Tóm Tắt Bảng Thông Số Kỹ Thuật So Sánh

| Chỉ số / Cơ chế | Minecraft Vanilla | MineServer Custom Boss |
| :--- | :--- | :--- |
| **Wither Max HP** | 300 HP | **600 HP** |
| **Wither Sát thương** | Sát thương vật lý + Wither effect | **+4.0 True Damage (2 tim)** xuyên giáp mỗi đòn/đạn sọ |
| **Warden Max HP (Phase 1)** | 500 HP | **1500 HP** |
| **Warden Tốc độ di chuyển** | 0.25 | **0.30** (+20%) |
| **Warden Kháng ngạt nước** | Không (Bị ngạt khi ngâm nước) | **Miễn nhiễm 100% ngạt thở** |
| **Warden Kháng Non-Player (Golem)** | 0% (Nhận đủ sát thương) | **Giảm 90% sát thương** trong Phase 2 |
| **Warden Kháng Sát Thương Người Chơi**| 0% | **0% (Người chơi gây 100% full sát thương)** |
| **Warden Hồi máu từ Iron Golem** | Không có | **+5.0 HP / đòn** (Cooldown 1 giây) |
| **Warden Hồi máu từ Người chơi chết** | Không có | **+50.0 HP / mạng hạ gục** (Chống Spawn-camp) |
| **Warden Combo Sonic Boom** | Cooldown ngẫu nhiên | **Combo 3+1** (Pre-Sacrifice) -> **Combo 2+1** (Post-Sacrifice) |
| **Sonic Boom Sát Thương** | 10.0 - 15.0 Sát thương ma thuật | **33% Max HP** (Phase 1) -> **45% Max HP** (Phase 2), có thể kết liễu |
| **Warden Huyết Tế (Máu < 10%)** | Không có | **Bất tử 10s + Hồi phục về 600 HP (Hard Cap) + Vòng xoáy hút 40m** |
| **Nhạc Nền BGM Chiến Đấu** | Âm thanh mặc định | **`warden_theme` (Phase 1) & `warden_sacrifice` (Phase 2)** |
| **Phần thưởng rơi khi chết** | 1 Sculk Catalyst, 5 XP | **Bộ Giáp Ngực Hư Vô, Ủng Bóng Ma Thần Thoại, Netherite, 3000 XP** |
