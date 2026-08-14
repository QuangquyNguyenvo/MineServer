# ✦ MINESERVER MOB BUFFS & MECHANICS SUMMARY ✦

Tài liệu này tổng hợp toàn bộ các cơ chế tăng chỉ số (buff), hiệu ứng đặc biệt và phần thưởng rơi ra (custom drops) của quái vật trên server Minecraft Fabric 26.2 (Chaos Cubed / 1.21+).

---

## 1. Buff theo Chiều Không Gian (Dimension Buffs)

Chỉ số máu, sát thương và hiệu ứng của quái vật thay đổi phụ thuộc vào thế giới người chơi đang đứng:

### 🌍 Overworld (Thế giới thường)
*   **Chỉ số cơ bản:** Mặc định của Minecraft vanilla.
*   **Hiệu ứng khi đánh trúng người chơi (On-Hit):**
    *   **Ban ngày:** **15% cơ hội** gây **Độc I (Poison I - 5s)** & **Làm chậm I (Slowness I - 3s)**.
    *   **Ban đêm:** **30% cơ hội** gây **Độc I (Poison I - 5s)** & **Làm chậm I (Slowness I - 3s)**.

### 🔥 Nether (Địa ngục)
*   **Chỉ số cơ bản (BuffMobs):** **Máu tối đa nhân 2.0 (x2.0 HP)** & **Sát thương tăng thêm 30% (x1.3 Damage)**.
*   **Hiệu ứng khi đánh trúng người chơi (On-Hit):**
    *   **30% cơ hội** gây đồng thời: **Độc I (Poison I - 5s)**, **Làm chậm I (Slowness I - 3s)**, **Héo úa I (Wither I - 3s)** và **Thiêu đốt (Burning - 5s)**.

### 🌌 The End (Kỷ cuối)
*   **Enderman:** Máu nhân 1.5 (**40 HP ➔ 60 HP**), sát thương cận chiến **tăng thêm 1.0 (0.5 tim)**.
*   **Shulker:** Máu nhân 1.5 (**30 HP ➔ 45 HP**).
*   **Đạn Shulker (`shulker_bullet`):** Gây thêm **1.0 sát thương** trực tiếp (trừ thẳng 1 HP khi trúng).
*   **Hiệu ứng khi đánh trúng người chơi (On-Hit):**
    *   **30% cơ hội** gây **Làm chậm II (Slowness II - 3s)**.

---

## 2. Chỉ số và Sát thương Boss

Các Boss được điều khiển bởi hệ thống Scarpet tùy chỉnh với lượng máu, kháng cự và bộ kỹ năng đặc thù:

| Tên Boss | Máu tối đa (Max HP) | Cơ chế sát thương & Kỹ năng đặc biệt |
| :--- | :--- | :--- |
| **Wither** | **600 HP** | Mọi đòn đánh trực tiếp và đạn sọ `wither_skull` gây thêm **+2.0 True Damage (1 tim)** trừ thẳng vào máu người chơi, xuyên qua giáp/enchantment. |
| **Ender Dragon** | **700 HP** | Ở The End/Nether, mọi đòn đánh gây thêm **+1.0 sát thương** trực tiếp (0.5 tim). |
| **Warden (Boss Tối Thượng)** | **1000 HP** | **Thông báo Global & Nhạc Nền:** Báo chat toàn server khi thức tỉnh; tự động phát nhạc Boss (`custom.warden_theme`) lặp vô tận trong 40m.<br>**Phase 1 (> 30% HP):** Kháng 30-50% vật lý, 80% Magic, miễn nhiễm ngạt nước, Anti-Flight (40m), Sculk Vacuum (kéo mục tiêu >16m), phá block 3x4x3, Sonic Boom gây 33% Max HP True Damage + giảm 50% hồi máu.<br>**Phase 2 (<= 30% HP):** Tăng **50% tốc độ**, Sonic Boom gây **45% Max HP**, **Kháng 100% sát thương tầm xa (cung, đinh ba, thuốc ném, đạn bắn...)** - buộc cận chiến.<br>**Huyết Tế Tối Thượng (< 10% HP):** Gây **Nausea II, Blindness, Poison II trong 10s** (bán kính 40m), **Bất tử (miễn nhiễm 100% sát thương)**, **chuyển sang Nhạc Huyết Tế Cuồng Nộ (`custom.warden_sacrifice`)** và **hồi phục dần từ 100 HP lên 400 HP (40% Max HP) trong 10s**. |

---

## 3. Sự kiện Đêm Trăng Máu (Blood Moon Event)

Một sự kiện đặc biệt diễn ra ngẫu nhiên ở **Overworld** làm quái vật phát cuồng và rơi ra phần thưởng hiếm:

*   **Tần suất:** Ngẫu nhiên cứ **8 đến 15 ngày chơi** (ngày tiếp theo được lên lịch và lưu trữ trong `world/scripts/bloodmoon.json`).
*   **Thời gian:** Chỉ kích hoạt vào **Ban đêm** (từ tick `12000` đến `23000`).
*   **Dấu hiệu nhận biết:**
    *   Lúc hoàng hôn (12000 tick): Tiếng sấm/tiếng gầm Wither vang lên toàn server, dòng chữ cảnh báo rực đỏ xuất hiện giữa màn hình: `ĐÊM TRĂNG MÁU BẮT ĐẦU` / `Quái vật bắt đầu cuồng nộ...`.
    *   Thông báo chat: `[Trăng Máu] Trăng máu đang lên... Bầu trời nhuộm sắc đỏ của sự cuồng nộ!`.
    *   Lúc bình minh: Phát âm thanh chiến thắng, thông báo yên bình và tự động lên lịch đêm tiếp theo.

### 🩸 Buff của Quái Overworld trong đêm Trăng Máu:
1.  **Máu tối đa:** Nhân **2.5 lần** (áp dụng cho quái thường spawn mới trong đêm, ngoại trừ Boss).
2.  **Tốc độ di chuyển:** Tăng **30%** (hệ số thuộc tính `generic.movement_speed` set thành `1.3`).
3.  **Sát thương:** Tăng thêm **2 điểm** (trừ thẳng 2 HP / 1 tim).
4.  **Hiệu ứng On-Hit:** Có **50% cơ hội** gây thêm **Mù II (Blindness II - 3s)**. Các hiệu ứng Độc/Chậm thông thường của Overworld vẫn hoạt động song song.
5.  **Kinh nghiệm:** Tăng **50% lượng XP** rơi ra khi bị người chơi tiêu diệt.

### 🎁 Vật phẩm rơi tùy chỉnh (Custom Drops) trong đêm Trăng Máu:

| Loài quái | Tỉ lệ rơi | Vật phẩm nhận được | Chi tiết / Phù phép |
| :--- | :--- | :--- | :--- |
| **Zombies** *(Zombie, Husk, Drowned, Zombie Villager)* | **10%** | **1 Kim cương** | `minecraft:diamond` |
| **Creeper** | **10%** | **1 TNT** | `minecraft:tnt` |
| **Enderman** | **10%** | **1 Mắt của Ender** | `minecraft:ender_eye` |
| **Skeleton & Stray** | **5%** | **Cây Cung đặc biệt** | Cung sẵn phù phép **Power V**, **Punch II**.<br>Trong đó có **10% cơ hội** có thêm phù phép **Mending**. |
| **Spiders** *(Spider, Cave Spider)* | **10%** | **1 Thuốc rơi chậm** | Potion có hiệu ứng `slow_falling` (1:30). |

---

## 4. Tệp tin Cấu hình & Script thực thi

*   **Buff Nether:** Được cấu hình qua mod BuffMobs tại file [`config/buffmobs.json`](file:///C:/Users/ADMIN/MineServer/config/buffmobs.json).
*   **Hiệu ứng On-Hit, The End, Boss & Trăng Máu:** Được viết hoàn toàn bằng Scarpet tại script [`world/scripts/custom_mob_effects.sc`](file:///C:/Users/ADMIN/MineServer/world/scripts/custom_mob_effects.sc).
