# Hướng dẫn cập nhật Mod cho MineServer v26.2

Tài liệu này hướng dẫn người chơi tự động cập nhật và đồng bộ hóa danh sách Mod từ máy chủ (repository này) về máy cá nhân bằng công cụ cập nhật nhanh 1 dòng lệnh.

## 🚀 Tính năng của công cụ:
* Tự động kiểm tra và so sánh danh sách mod hiện có trên máy cá nhân với máy chủ.
* Chỉ tải về các mod bị thiếu hoặc có phiên bản mới hơn (dung lượng thay đổi).
* Tự động xóa các mod cũ hoặc không còn sử dụng trên máy chủ để tránh xung đột game.
* Hỗ trợ các Launcher thông dụng: Legacy Launcher, Official Minecraft Launcher (Bản quyền), TLauncher.
* Hỗ trợ giao diện đồ họa tối (Dark Mode) hoặc tự động chạy ở chế độ dòng lệnh (Console) nếu máy không hỗ trợ GUI.

---

## 🛠️ Hướng dẫn chạy nhanh (1 dòng lệnh - Không cần tải file)

Đây là cách nhanh nhất và an toàn nhất, không lo bị Windows Defender hay các phần mềm diệt virus chặn file.

### Bước 1: Mở PowerShell
Nhấn phím **Windows** trên bàn phím, gõ `powershell` và nhấn **Enter** để mở cửa sổ dòng lệnh màu xanh của Windows.

### Bước 2: Chép và dán lệnh sau vào cửa sổ PowerShell:

```powershell
irm https://raw.githubusercontent.com/QuangquyNguyenvo/MineServer/main/26.2/UpdateMinecraftMods.ps1 | iex
```

### Bước 3: Thực hiện cập nhật
1. Nhấn **Enter** để chạy lệnh.
2. Giao diện đồ họa của công cụ **MineServer Mod Updater** sẽ xuất hiện.
3. Chọn Launcher bạn đang sử dụng (hoặc chọn **Duong dan tu chon** nếu cài đặt ở vị trí khác).
4. Nhấn nút **Kiem tra & Cap nhat** và đợi tiến trình hoàn tất.
5. Khi thấy thông báo thành công, bạn có thể tắt công cụ và vào game trải nghiệm!

---

## ⚠️ Giải quyết sự cố thường gặp

### 1. Lỗi không thể tải danh sách mod từ GitHub
* **Nguyên nhân**: Kết nối mạng bị gián đoạn hoặc bị tường lửa chặn truy cập GitHub.
* **Cách khắc phục**: Kiểm tra lại kết nối Internet, tắt VPN nếu đang bật, và chạy lại lệnh trên.

### 2. Giao diện đồ họa (GUI) không hiển thị
* **Nguyên nhân**: Máy tính thiếu thư viện đồ họa hoặc chạy trong môi trường hạn chế.
* **Cách khắc phục**: Công cụ sẽ tự động nhận diện và chuyển hướng sang chế độ dòng lệnh (Console) màu đen để tiếp tục tải mod. Bạn chỉ cần theo dõi màn hình dòng lệnh và đợi thông báo hoàn thành.
