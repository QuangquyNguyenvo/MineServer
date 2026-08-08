# MineServer

Repo quản lý mod & config cho server Minecraft Fabric (MC 26.2). Có sẵn 2 script PowerShell để
đồng bộ mod giữa máy các thành viên và repo GitHub, khỏi phải gửi qua lại file jar thủ công.

## Cài mod vào máy (dành cho người chơi)

Chạy `UpdateMinecraftMods.ps1` (double-click hoặc `powershell -File UpdateMinecraftMods.ps1`).

- Script tự lấy danh sách mod mới nhất từ thư mục `mods/` trên GitHub (qua GitHub API, không cần
  clone repo) và đồng bộ vào thư mục mods Minecraft trên máy bạn.
- Có GUI chọn launcher (Legacy/TLauncher, bản quyền, hoặc đường dẫn tuỳ chỉnh); máy không hỗ trợ GUI
  thì tự fallback sang console, dùng cờ `-NoGui` để ép chạy console luôn.
- Tự tải mod thiếu/khác version, tự xoá mod cũ (chỉ xoá nếu đúng là bản cũ của 1 mod đang có trên
  server — không đụng tới mod riêng bạn tự thêm).
- Không cần quyền ghi vào repo, không cần token — chỉ đọc public API của GitHub.

## Cập nhật mod lên repo (dành cho người quản lý mod)

Sau khi thêm/bớt/đổi version mod trong thư mục `mods/` ở local:

```powershell
.\commit-mods.ps1 -CommitMessage "Mo ta thay doi mod"
```

Script sẽ `git add mods`, commit và push lên `origin main`. Ai cũng chạy được miễn có quyền push vào
repo (repo private, cần được add làm collaborator trên GitHub trước).

> Sau khi push, những người chạy `UpdateMinecraftMods.ps1` sẽ thấy mod mới ngay lần chạy tiếp theo
> (API GitHub không cache lâu, nhưng đôi khi cần chờ vài phút).

## Ghi chú cấu trúc

- `mods/` — mod jar hiện tại của server, đây là thứ duy nhất 2 script trên thao tác tới.
- `1.21.11/` — server Paper đời cũ, giữ lại trên git làm lưu trữ/kỷ niệm, **không** còn đồng bộ với
  máy chạy thật, đừng xoá.
- `world/`, `logs/`, `cache/`, các file trạng thái người chơi (`ops.json`, `whitelist.json`,...) —
  không track trên git (xem `.gitignore`), vì đổi liên tục và không cần thiết để cài mod.
