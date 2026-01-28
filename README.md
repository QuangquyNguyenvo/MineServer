# Minecraft Server (Local Test)

Dự án này là môi trường local để test plugin và viết Skript.

## Yêu cầu
- **Java 21**: Cần thiết để chạy phiên bản Minecraft 1.21.1.
- Tải Java 21 tại: [Adoptium](https://adoptium.net/temurin/releases/?version=21)

## Cách chạy Server
1. Chạy file `start.bat`.
2. Đợi server load lần đầu.
3. Vào game Minecraft phiên bản 1.21.1, địa chỉ server là `localhost`.

## Cấu trúc thư mục quan trọng
- `plugins/`: Nơi chứa file plugin `.jar`.
- `plugins/Skript/scripts/`: Nơi bạn viết code Skript (`.sk`).
- `server.properties`: Cấu hình server (đã được bỏ qua trong git để tránh xung đột).

## Skript Sample
Đã có sẵn file `plugins/Skript/scripts/test.sk`. Bạn có thể dùng lệnh `/test` trong game để kiểm tra.
