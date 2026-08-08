# PikaMC MCP Agent — setup cho Claude Code

Server này chạy sẵn 1 MCP (Model Context Protocol) agent qua HTTP, cho phép Claude Code kết nối và
thao tác trực tiếp với VPS (đọc trạng thái, sau này mở rộng thêm quản lý file/console tuỳ theo mức độ
tin cậy được cấp).

## Endpoint

```
http://ancient.pikamc.vn:25240/mcp
```

## Yêu cầu

- Đã cài [Claude Code](https://claude.com/claude-code)
- Repo này là **private** — token bên dưới chỉ nên còn nằm trong repo private này. Nếu ai đó fork/clone
  ra ngoài hoặc repo bị chuyển sang public, coi như token đã lộ và cần đổi ngay.

## Cài đặt

```bash
claude mcp add --transport http pikamc-agent http://ancient.pikamc.vn:25240/mcp --scope local -H "Authorization: Bearer toiyeufembi"
```

Dùng `--scope local` để MCP chỉ hiện trong project bạn đang mở, không áp dụng toàn máy.

Sau khi thêm, mở **session Claude Code mới** (MCP server chỉ được nạp lúc khởi động session) rồi thử
gọi tool `ping` để xác nhận kết nối thành công.

## Lưu ý bảo mật

- Không commit token vào bất kỳ repo/file nào (kể cả repo private).
- Không chia sẻ token qua kênh công khai (Discord public channel, forum, v.v.) — chỉ chat riêng 1-1.
- Nếu nghi ngờ token bị lộ, báo ngay để đổi token mới.
