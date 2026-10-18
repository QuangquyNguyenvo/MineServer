# Bản vá tương thích 26.2 cho một số mod

Một số mod trong `mods/` còn dữ liệu kiểu cũ nên Minecraft 26.2 báo lỗi khi tải. Script ở đây sửa trực tiếp file JAR, chỉ đổi đúng những entry bị lỗi; class và asset khác giữ nguyên.

- `gptfix.py`: các quy tắc sửa (advancement predicate, mixin MagmaCube, recipe, trowel, tag, PTS...).
- `build.py`: áp các quy tắc đó lên đúng phiên bản JAR đang dùng, đồng thời sửa thêm hàm load của Farmhouse Decorations.
- `changes.json`: SHA256 của bản gốc và bản vá, cùng số entry đã đổi.

| Mod | Lỗi | Cách sửa |
|---|---|---|
| Alex's Mobs 1.2.0 | 42 advancement không tải được | Chuyển entity predicate sang schema 26.2 |
| Mob AI Tweaks 1.11.0-beta | Mixin MagmaCube lỗi, 12 advancement | Superclass `Slime` → `AbstractCubeMob`; sửa predicate |
| MoreVillagersRe 1.26.7.1 | 9 advancement, recipe `gilded_station` | Sửa predicate và ingredient |
| CubeAnimals 2.1.1 | Tên trade viết hoa, tag hunter | Đổi tên sang chữ thường |
| JobsPlus Tools 21.1.0 | 7 restriction trowel `use` | `use` → `use_item` + `interact_block` |
| Farming for Blockheads 26.2.0.5 | 2 recipe BOP | Thêm điều kiện registry |
| Farmhouse Decorations 26.9 | Hàm `cleanup_legacy_seats` lỗi cú pháp, làm hỏng **cả** tag `#minecraft:load` (hàm load của mọi mod/datapack khác không chạy) | Thay bằng hàm rỗng (tag chứa `:` nên lệnh không chọn được) |

Kết quả boot thử bằng đúng bộ mod này (sandbox, world copy): **85 ERROR → 0**. `/reload` sạch, `#minecraft:load` chạy đủ 10 hàm, Magma Cube summon và chết bình thường.

## Khi cập nhật một trong các mod trên

Bản mới có thể đã được tác giả sửa. Boot thử bản gốc trước. Nếu vẫn lỗi:

```
python -I build.py <thư mục chứa JAR gốc> <thư mục output trống>
```

Nếu tên file JAR đổi thì sửa danh sách `SPECS` trong `build.py`. Mod nào không còn khớp quy tắc sẽ bị `SKIP` thay vì vá sai.
