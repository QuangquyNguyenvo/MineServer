# PikaMC MCP Agent — setup

Server này chạy sẵn 1 MCP (Model Context Protocol) agent qua HTTP, cho phép các tool hỗ trợ MCP
(Claude Code, Antigravity, ...) kết nối và thao tác trực tiếp với VPS (đọc trạng thái, sau này mở
rộng thêm quản lý file/console tuỳ theo mức độ tin cậy được cấp).

## Endpoint

```
http://ancient.pikamc.vn:25240/mcp
```

## Yêu cầu chung

- Repo này là **private** — token bên dưới chỉ nên còn nằm trong repo private này. Nếu ai đó fork/clone
  ra ngoài hoặc repo bị chuyển sang public, coi như token đã lộ và cần đổi ngay.

---

## Cài đặt cho Claude Code

- Đã cài [Claude Code](https://claude.com/claude-code)

```bash
claude mcp add --transport http pikamc-agent http://ancient.pikamc.vn:25240/mcp --scope local -H "Authorization: Bearer toiyeufembi"
```

Dùng `--scope local` để MCP chỉ hiện trong project bạn đang mở, không áp dụng toàn máy.

Sau khi thêm, mở **session Claude Code mới** (MCP server chỉ được nạp lúc khởi động session) rồi thử
gọi tool `ping` để xác nhận kết nối thành công.

---

## Cài đặt cho Antigravity

- Đã cài [Antigravity](https://antigravity.google/)

Mở phần cấu hình MCP servers của Antigravity (Settings → MCP Servers, hoặc file cấu hình MCP tương ứng)
và thêm entry sau:

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

Lưu lại rồi khởi động lại session/reload MCP servers trong Antigravity, sau đó thử gọi tool `ping`
để xác nhận kết nối thành công.

---

## Setup tự động qua startup command (.sh)

Nếu môi trường dev/cloud sandbox của bạn có ô cấu hình **startup command** (chạy mỗi lần khởi động
môi trường, ví dụ: Claude Code cloud sandbox, devcontainer `postCreateCommand`, GitHub Codespaces...),
dùng script có sẵn để MCP tự đăng ký mà không cần add tay:

```
mcp-agent-setup/setup.sh
```

Script này:

- Kiểm tra lệnh `claude` có tồn tại không, không có thì bỏ qua (không làm fail startup).
- Kiểm tra MCP `pikamc-agent` đã được add chưa (idempotent — chạy lại nhiều lần không lỗi).
- Nếu chưa có thì chạy `claude mcp add` với endpoint + token y hệt phần "Cài đặt cho Claude Code" ở trên.

Set làm startup command:

```bash
bash mcp-agent-setup/setup.sh
```

> Script chỉ đăng ký cho Claude Code (dùng lệnh CLI `claude mcp add`). Antigravity cấu hình qua file
> JSON, không có bước CLI tương ứng — làm theo mục "Cài đặt cho Antigravity" ở trên.

---

## Lưu ý bảo mật

- Không commit token vào bất kỳ repo/file nào (kể cả repo private).
- Không chia sẻ token qua kênh công khai (Discord public channel, forum, v.v.) — chỉ chat riêng 1-1.
- Nếu nghi ngờ token bị lộ, báo ngay để đổi token mới.
