# ⚡ PikaMC MCP Agent Setup

<p align="center">
  <img src="https://img.shields.io/badge/Protocol-Model%20Context%20Protocol-8A2BE2?style=for-the-badge&logo=anthropic" alt="MCP Protocol">
  <img src="https://img.shields.io/badge/Client-Claude%20Code%20%7C%20Antigravity-blue?style=for-the-badge" alt="Clients">
  <img src="https://img.shields.io/badge/Security-Private%20Bearer%20Auth-red?style=for-the-badge" alt="Security">
</p>

---

## 📌 Tổng Quan (Overview)

Server này chạy dịch vụ **MCP (Model Context Protocol) Agent** qua HTTP cho phép các AI Assistant & Dev tools (chẳng hạn như **Claude Code**, **Antigravity**) kết nối trực tiếp đến VPS **PikaMC** để truy vấn thông tin hệ thống, giám sát trạng thái và mở rộng điều khiển tự động.

### 🌐 Endpoint Dịch Vụ

| Thông số | Giá trị |
| :--- | :--- |
| **Endpoint URL** | `http://ancient.pikamc.vn:25240/mcp` |
| **Transport Mode** | HTTP (`StreamHTTP` / `HTTP SSE`) |
| **Authentication** | Bearer Token (`Authorization: Bearer <TOKEN>`) |

> [!IMPORTANT]
> **Yêu cầu bảo mật Repo**: Repo này chứa thông tin kết nối nội bộ. Nếu repo bị fork/clone ra bên ngoài hoặc chuyển sang Public, token truy cập phải được thu hồi và đổi ngay lập tức.

---

## 🛠️ Hướng Dẫn Cài Đặt (Quick Setup)

### 1️⃣ Cài đặt cho Claude Code

Yêu cầu: Đã cài đặt CLI [`Claude Code`](https://claude.com/claude-code).

Chạy lệnh bên dưới trong terminal tại thư mục dự án của bạn:

```bash
claude mcp add --transport http pikamc-agent http://ancient.pikamc.vn:25240/mcp \
  --scope local \
  -H "Authorization: Bearer toiyeufembi"
```

> [!TIP]
> - Cờ `--scope local` đảm bảo MCP server chỉ kích hoạt riêng cho project hiện tại, tránh ảnh hưởng đến các môi trường làm việc khác.
> - Sau khi thêm thành công, hãy **khởi động session mới** của Claude Code và gõ lệnh `ping` (hoặc kiểm tra bằng `claude mcp list`) để xác nhận kết nối.

---

### 2️⃣ Cài đặt cho Antigravity

Yêu cầu: Đã cài đặt [`Antigravity`](https://antigravity.google/).

Mở mục cấu hình MCP Servers trong Antigravity (**Settings** → **MCP Servers**, hoặc chỉnh sửa trực tiếp file cấu hình MCP) và chèn khối cấu hình sau:

```json
{
  "mcpServers": {
    "pikamc-agent": {
      "url": "http://ancient.pikamc.vn:25240/mcp",
      "headers": {
        "Authorization": "Bearer toiyeufembi"
      }
    }
  }
}
```

> [!NOTE]
> Sau khi lưu file cấu hình, thực hiện **Reload MCP Servers** hoặc khởi động lại Antigravity session. Gọi thử tool `ping` để xác nhận agent phản hồi bình thường.

---

## 🚀 Setup Tự Động Qua Startup Script (`setup.sh`)

Đối với các môi trường Sandbox, Cloud IDE, DevContainer, hoặc GitHub Codespaces có hỗ trợ chạy **Startup Command** (`postCreateCommand`), bạn có thể dùng script tự động để đăng ký MCP không cần gõ thủ công.

### 📋 Tính Năng Script:
- ✅ **Kiểm tra môi trường**: Tự động phát hiện CLI `claude`, nếu không có sẽ bỏ qua an toàn mà không làm hỏng tiến trình startup.
- 🔁 **Tính Idempotent**: Kiểm tra `pikamc-agent` đã tồn tại chưa trước khi thêm, tránh lặp lại hoặc ghi đè lỗi.
- 🔐 **Đăng ký tự động**: Thêm cấu hình MCP với scope local và token Bearer.

### 🖥️ Cách Sử Dụng:

Chỉ cần thêm lệnh sau vào **Startup Command** của môi trường:

```bash
bash mcp-agent-setup/setup.sh
```

> [!WARNING]
> Script `setup.sh` hiện tại tự động đăng ký cho **Claude Code CLI**. Đối với **Antigravity**, do sử dụng cấu hình định dạng JSON độc lập, bạn vẫn cần làm theo hướng dẫn thủ công ở trên.

---

## 🔒 Quy Tắc Bảo Mật (Security Guidelines)

> [!CAUTION]
> 1. **Tuyệt đối không push token công khai**: Không commit token vào bất kỳ repository hay forum/channel công khai nào.
> 2. **Phạm vi trao đổi**: Chỉ chia sẻ thông tin token qua kênh liên lạc nội bộ 1-1 khi thực sự cần thiết.
> 3. **Xử lý sự cố**: Khi phát hiện hoặc nghi ngờ token bị rò rỉ, phải thông báo cho quản trị viên ngay lập tức để revoke & cấp token mới.

