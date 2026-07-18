# Hướng dẫn cập nhật Mod cho MineServer v26.2

Tài liệu này hướng dẫn người chơi tự động cập nhật và đồng bộ hóa danh sách Mod từ máy chủ (repository này) về máy cá nhân bằng công cụ cập nhật 1-Click.

## 🚀 Tính năng của công cụ:
* Tự động kiểm tra và so sánh danh sách mod hiện có trên máy cá nhân với máy chủ.
* Chỉ tải về các mod bị thiếu hoặc có phiên bản mới hơn (dung lượng thay đổi).
* Tự động xóa các mod cũ hoặc không còn sử dụng trên máy chủ để tránh xung đột game.
* Hỗ trợ các Launcher thông dụng: Legacy Launcher, Official Minecraft Launcher (Bản quyền), TLauncher.
* Hỗ trợ giao diện đồ họa tối (Dark Mode) hoặc tự động chạy ở chế độ dòng lệnh (Console) nếu máy không hỗ trợ GUI.

---

## 🛠️ Hướng dẫn cài đặt và sử dụng

### Bước 1: Tải xuống tệp kịch bản
Tải tệp tin cập nhật `UpdateMinecraftMods.ps1` từ repository này và lưu vào một thư mục trên máy tính của bạn (tốt nhất là lưu chung trong thư mục Minecraft hoặc trên Desktop để tiện mở).

### Bước 2: Tạo tệp chạy nhanh (1-Click)
Do Windows mặc định chặn chạy trực tiếp tệp kịch bản PowerShell (`.ps1`), bạn cần tạo một tệp tin batch để chạy nhanh:

1. Tạo một tệp văn bản mới trong cùng thư mục với tệp `UpdateMinecraftMods.ps1`.
2. Đổi tên tệp văn bản đó thành `UpdateMod.bat` (chú ý phần đuôi mở rộng phải là `.bat` chứ không phải `.txt`).
3. Nhấp chuột phải vào tệp `UpdateMod.bat`, chọn **Edit** (hoặc mở bằng Notepad) và dán nội dung sau vào:

```cmd
@echo off
title MineServer Mod Updater
powershell -ExecutionPolicy Bypass -WindowStyle Normal -File "%~dp0UpdateMinecraftMods.ps1"
pause
```

4. Lưu tệp tin lại và đóng Notepad.

### Bước 3: Chạy cập nhật
1. Nhấp đúp chuột vào tệp `UpdateMod.bat` vừa tạo.
2. Giao diện đồ họa của công cụ **MineServer Mod Updater** sẽ xuất hiện.
3. Chọn Launcher bạn đang sử dụng (hoặc chọn **Đường dẫn tự chọn** nếu bạn cài đặt Minecraft ở thư mục khác).
4. Nhấn nút **Kiểm tra & Cập nhật** và đợi tiến trình tải hoàn thành.
5. Khi có thông báo thành công hiện lên, bạn có thể khởi động game và trải nghiệm!

---

## ⚠️ Giải quyết sự cố thường gặp

### 1. Lỗi không thể tải danh sách mod từ GitHub
* **Nguyên nhân**: Kết nối mạng bị gián đoạn hoặc bị tường lửa chặn truy cập GitHub.
* **Cách khắc phục**: Kiểm tra lại kết nối Internet của bạn, thử tắt VPN hoặc thử lại sau vài phút.

### 2. Giao diện GUI không hiển thị
* **Nguyên nhân**: Máy tính của bạn đang chạy trong môi trường hạn chế hoặc thiếu thư viện PresentationFramework của Windows.
* **Cách khắc phục**: Công cụ sẽ tự động chuyển sang chế độ Console (dòng lệnh) để tiếp tục tải mod. Bạn chỉ cần theo dõi quá trình tải trực tiếp trên cửa sổ màu đen và đợi thông báo thành công.
