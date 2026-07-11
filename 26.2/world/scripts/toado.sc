// ==============================================================================
//              ✦ TOẠ ĐỘ MINEPOINT SYSTEM ✦
//   Đánh dấu tọa độ và gửi lên Discord qua Webhook (Viết bằng Scarpet)
//   Dùng http_request() từ mod "scarpet-additions" (raw JSON POST)
//   Kênh: 🧭┃𝐌𝐢𝐧𝐞𝐩𝐨𝐢𝐧𝐭
// ==============================================================================

global_webhook_url = 'https://discord.com/api/webhooks/1470974798115901680/4gmg-np0yFvdrrCrZShs-1OtllaeQGSzevTRZHEMPc9CUjBMnV-NpReexOKUzvNAdhbh';

// saved/all lưu dạng list các map {uuid,name,x,y,z,dim,by} để tránh lỗi
// NBT khi khóa map chứa dấu cách hoặc dấu gạch ngang (uuid, tên địa điểm).
// Luôn khởi tạo một map SẠCH rồi mới nạp dữ liệu cũ vào. Nếu file app_data cũ
// bị hỏng (không phải map, saved/all không phải list) thì try() sẽ nuốt lỗi và
// ta giữ map rỗng -> tránh lỗi "filter should be a list" khi chạy lệnh.
global_toado = {'saved' -> [], 'all' -> []};
try(
  _loaded = load_app_data();
  if (_loaded != null && _loaded:'saved' != null, global_toado:'saved' = _loaded:'saved');
  if (_loaded != null && _loaded:'all'   != null, global_toado:'all'   = _loaded:'all');
,
  print('§e[MinePoint] Dữ liệu cũ không đọc được, đã khởi tạo lại danh sách trống.');
);

__config() -> {
  'scope' -> 'global',
  'commands' -> {
    ''              -> _()    -> cmd_help(),
    '<loc_text>'    -> _(loc) -> cmd_mark(loc),
    'list'          -> _()    -> cmd_list_self(),
    'list all'      -> _()    -> cmd_list_all(),
    'del <loc_text>'-> _(loc) -> cmd_del(loc),
    'clearall'      -> _()    -> cmd_clearall()
  },
  'arguments' -> {
    'loc_text' -> {'type' -> 'text'}
  },
  'allow_command_conflicts' -> true
};

print(format('d [MinePoint] ', 'w Hệ thống tọa độ Discord đã sẵn sàng!'));

// Gửi tin nhắn cho toàn bộ server
// (KHÔNG gọi print(message) không mục tiêu ở đây: khi hàm này chạy trong lúc
// xử lý lệnh của một người chơi, print() không mục tiêu sẽ gửi phản hồi lệnh
// về lại chính người chơi đó, gây ra tin nhắn bị lặp lại 2 lần cho họ.)
_broadcast(message) -> (
  for (player('all'), print(_, message));
);

_dim_name(dim) -> if(
  dim == 'minecraft:the_nether', 'Nether',
  dim == 'minecraft:the_end', 'The End',
  'Overworld'
);

// ── Avatar/nhân vật cho embed ─────────────────────────────────────────────────
// VẤN ĐỀ CŨ: dùng thẳng texture ely.by (http://skinsystem.ely.by/skins/<tên>.png)
// -> đó là "tấm da" 2D phẳng, Discord hiển thị thành mớ pixel lộn xộn chứ không
// phải nhân vật.
//
// GIẢI PHÁP: render NHÂN VẬT 3D. Chỉ Starlight Skins nhận được skin URL tuỳ ý
// (nên mới ghép được skin ely.by lậu), còn mc-heads/NMSR/Crafatar chỉ tra theo
// Mojang -> luôn ra Steve với account offline. Vì Starlight hay sập (502), ta
// làm cơ chế TỰ HỒI: thử ping Starlight, sống thì render skin ely.by thật của
// bạn thành nhân vật 3D; sập thì fallback sang nhân vật MẶC ĐỊNH của mc-heads
// (đẹp, luôn chạy) thay vì tấm da phẳng xấu xí.
_ely_skin(pname)  -> str('http://skinsystem.ely.by/skins/%s.png', pname);
_sl_url(pname, crop) -> str('https://starlightskins.lunareclipse.studio/render/default/%s/%s?skinUrl=%s', pname, crop, _ely_skin(pname));

// Trả về {face, body}. Ping Starlight (crop 'head') 1 lần; 200 -> dùng skin thật.
_render_urls(pname) -> (
  alive = false;
  try(
    resp = http_request({'uri' -> _sl_url(pname, 'head'), 'method' -> 'GET'});
    if (resp:'status_code' == 200, alive = true);
  , 'http_request_error',
    alive = false;
  );
  if (alive,
    {'face' -> _sl_url(pname, 'head'), 'body' -> _sl_url(pname, 'full')},
    // Fallback: nhân vật mặc định (mc-heads) — mặt cho icon nhỏ, cả người cho thumbnail.
    {'face' -> str('https://mc-heads.net/avatar/%s/64', pname),
     'body' -> str('https://mc-heads.net/body/%s', pname)}
  )
);

_dim_color(dimname) -> if(
  dimname == 'Nether', 16733525,
  dimname == 'The End', 11141290,
  5763719
);

// Chú thích bản đồ đổi theo từng thế giới (Overworld không hiện Nether/End...)
_legend(dimname) -> if(
  dimname == 'Nether',  '🟥 Nether/Crimson/Basalt · 🟪 Warped · 🔴 Bạn',
  dimname == 'The End', '🟪 The End · 🔴 Bạn',
  '🟩 Đồng bằng/Rừng · 🟨 Cát/Sa mạc · 🟦 Nước · ⬜ Tuyết · 🟫 Đầm lầy · 🔴 Bạn'
);

// ── Biome helpers (dùng cho bản đồ mini + hiển thị biome trong embed) ──────────
// Lấy khóa biome VIẾT HOA tại một toạ độ bất kỳ trong dimension của người chơi.
// VD: 'minecraft:snowy_plains' -> 'SNOWY_PLAINS'
_biome_short(p, x, y, z) -> (
  b = str(in_dimension(p, biome(x, y, z)));
  parts = split(':', b);
  upper(parts:(length(parts) - 1))
);

// Tên biome dễ đọc: 'SNOWY_PLAINS' -> 'snowy plains'
_biome_pretty(key) -> lower(replace(key, '_', ' '));

// Phân loại biome -> emoji ô màu (đồng đều 1 ô để bản đồ thẳng hàng trên Discord)
_biome_emoji(key) -> if(
  key ~ 'OCEAN|RIVER',                     '🟦',
  key ~ 'NETHER|CRIMSON|BASALT|SOUL',      '🟥',
  key ~ 'WARPED|END|MUSHROOM|CHERRY|VOID', '🟪',
  key ~ 'SNOW|FROZEN|ICE|GROVE',           '⬜',
  key ~ 'SWAMP|MANGROVE|DARK_FOREST',      '🟫',
  key ~ 'BEACH|DESERT|SAVANNA|BADLANDS',   '🟨',
  '🟩'
);

// Quét biome trong lưới quanh người chơi và dựng bản đồ top-down bằng emoji.
// radius 5, bước 12 khối -> lưới 11x11 phủ ±60 khối. Chỉ chạy khi gõ lệnh nên
// không ảnh hưởng hiệu năng tick. Muốn rộng/hẹp hơn thì chỉnh radius & step.
_build_minimap(p, px, py, pz) -> (
  radius = 5;
  step = 12;
  out = '';
  for (range(-radius, radius + 1),
    dz = _;
    row = '';
    for (range(-radius, radius + 1),
      dx = _;
      cell = if (dx == 0 && dz == 0,
        '🔴',
        _biome_emoji(_biome_short(p, px + dx * step, py, pz + dz * step))
      );
      row = row + cell;
    );
    out = out + row + '\n';
  );
  out
);

_build_embed(pname, locname, x, y, z, dimname, biome_emoji, biome_name, minimap, urls) -> {
  'username' -> 'AstolfoCuteo UwU',
  'embeds' -> [ {
    'author' -> {'name' -> pname, 'icon_url' -> urls:'face'},
    'title' -> '📍 ' + locname,
    'description' -> '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━',
    'color' -> _dim_color(dimname),
    'fields' -> [
      {'name' -> '📌 Tọa độ', 'value' -> str('```fix\nX: %d  |  Y: %d  |  Z: %d\n```', x, y, z), 'inline' -> false},
      {'name' -> '🌐 Thế giới', 'value' -> dimname, 'inline' -> true},
      {'name' -> '🌿 Biome', 'value' -> biome_emoji + ' ' + biome_name, 'inline' -> true},
      {'name' -> '🗺️ Bản đồ biome xung quanh', 'value' -> minimap, 'inline' -> false},
      {'name' -> '🧭 Chú thích', 'value' -> _legend(dimname), 'inline' -> false}
    ],
    'thumbnail' -> {'url' -> urls:'body'},
    'footer' -> {'text' -> '✦ Raumania SMP ✦'}
  } ]
};

_send_discord_embed(body) -> (
  try(
    resp = http_request({'uri' -> global_webhook_url, 'method' -> 'POST', 'headers' -> {'Content-Type' -> 'application/json'}, 'body' -> body});
    if (resp:'status_code' >= 300,
      print(str('§c[MinePoint] Discord webhook trả về mã lỗi %d', resp:'status_code'));
    );
  , 'http_request_error',
    print('§c[MinePoint] Lỗi gửi Discord: ' + _);
  );
);

cmd_help() -> (
  p = player();
  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');
  print(p, '  §d§l✦ MINEPOINT §8- §7Đánh dấu tọa độ');
  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');
  print(p, '  §7Sử dụng: §b/toado <tên địa điểm>');
  print(p, '');
  print(p, '  §7Ví dụ:');
  print(p, '    §8• §b/toado §fNhà của mình');
  print(p, '    §8• §b/toado §fEnd Portal');
  print(p, '');
  print(p, '  §7Xem danh sách: §b/toado list §7(hoặc §b/toado list all§7)');
  print(p, '  §7Xóa tọa độ: §b/toado del <tên>');
  print(p, '');
  print(p, '  §7Tọa độ sẽ được gửi lên §e🧭┃𝐌𝐢𝐧𝐞𝐩𝐨𝐢𝐧𝐭 §7trên Discord!');
  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');
  sound('minecraft:ui.button.click', pos(p), 0.5, 1.2);
);

cmd_mark(loc) -> (
  p = player();
  ppos = pos(p);
  x = floor(ppos:0);
  y = floor(ppos:1);
  z = floor(ppos:2);
  dim = p ~ 'dimension';
  dimname = _dim_name(dim);
  uuid = p ~ 'uuid';
  pname = str(p);

  bkey = _biome_short(p, x, y, z);
  bemoji = _biome_emoji(bkey);
  bname = _biome_pretty(bkey);
  minimap = _build_minimap(p, x, y, z);

  saved = filter(global_toado:'saved', !(_:'uuid' == uuid && _:'name' == loc));
  saved = put(saved, null, {'uuid' -> uuid, 'name' -> loc, 'x' -> x, 'y' -> y, 'z' -> z, 'dim' -> dimname});
  global_toado:'saved' = saved;

  all_list = filter(global_toado:'all', _:'name' != loc);
  all_list = put(all_list, null, {'name' -> loc, 'x' -> x, 'y' -> y, 'z' -> z, 'dim' -> dimname, 'by' -> pname});
  global_toado:'all' = all_list;

  store_app_data(global_toado);

  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');
  print(p, str('  §d§l✦ MINEPOINT §8│ §fĐã đánh dấu §e%s', loc));
  print(p, str('  §7Tọa độ: §a%d §7/ §e%d §7/ §b%d', x, y, z));
  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');
  sound('minecraft:entity.experience_orb.pickup', pos(p), 1.0, 1.5);

  _broadcast('');
  _broadcast('§8§m                                                  ');
  _broadcast('');
  _broadcast(str('  §d✦ §f%s §7đánh dấu §e%s', pname, loc));
  _broadcast(str('  §8[§a%d§7, §e%d§7, §b%d§8]', x, y, z));
  _broadcast('');
  _broadcast('§8§m                                                  ');
  _broadcast('');

  // Dựng embed + gửi trong task riêng: _render_urls() ping Starlight (có thể mất
  // vài giây / timeout khi sập) nên KHÔNG được chạy trên luồng chính -> tránh
  // giật server. Toàn bộ biến dùng để dựng embed được đóng gói qua outer().
  task( _(outer(pname), outer(loc), outer(x), outer(y), outer(z),
          outer(dimname), outer(bemoji), outer(bname), outer(minimap)) -> (
    urls = _render_urls(pname);
    body = encode_json(_build_embed(pname, loc, x, y, z, dimname, bemoji, bname, minimap, urls));
    _send_discord_embed(body);
  ));
  task( _(outer(p)) -> (
    sleep(1000);
    print(p, '  §d✦ §aĐã gửi lên Discord! ✦');
  ));
);

cmd_list_self() -> (
  p = player();
  uuid = p ~ 'uuid';
  mine = filter(global_toado:'saved', _:'uuid' == uuid);
  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');
  print(p, '  §d§l✦ MINEPOINTS §8- §7Tọa độ của bạn');
  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');
  if (length(mine) == 0,
    print(p, '  §7Bạn chưa có tọa độ. Dùng §b/toado <tên>§7!');
  ,
    for (mine,
      d = _;
      print(p, str('  §8• §e%s §8│ §a%d§7, §e%d§7, §b%d §8(%s)', d:'name', d:'x', d:'y', d:'z', d:'dim'));
    );
  );
  print(p, '');
  print(p, '  §7Dùng §b/toado list all §7để xem tất cả.');
  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');
  sound('minecraft:ui.button.click', pos(p), 0.5, 1.2);
);

cmd_list_all() -> (
  p = player();
  all_list = global_toado:'all';
  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');
  print(p, '  §d§l✦ MINEPOINTS §8- §7Tất cả tọa độ');
  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');
  if (length(all_list) == 0,
    print(p, '  §7Chưa có tọa độ nào.');
  ,
    for (all_list,
      d = _;
      print(p, str('  §8• §e%s §8│ §a%d§7, §e%d§7, §b%d §8- §f%s §8(%s)', d:'name', d:'x', d:'y', d:'z', d:'by', d:'dim'));
    );
  );
  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');
  sound('minecraft:ui.button.click', pos(p), 0.5, 1.2);
);

cmd_del(loc) -> (
  p = player();
  uuid = p ~ 'uuid';
  pname = str(p);
  existing = filter(global_toado:'saved', _:'uuid' == uuid && _:'name' == loc);
  if (length(existing) > 0,
    global_toado:'saved' = filter(global_toado:'saved', !(_:'uuid' == uuid && _:'name' == loc));
    global_toado:'all' = filter(global_toado:'all', !(_:'name' == loc && _:'by' == pname));
    store_app_data(global_toado);
    print(p, '');
    print(p, '§8§m                                                  ');
    print(p, '');
    print(p, str('  §d[MinePoint] §aĐã xóa §e%s§a!', loc));
    print(p, '');
    print(p, '§8§m                                                  ');
    print(p, '');
    sound('minecraft:entity.item.break', pos(p), 0.6, 1.4);
  ,
    print(p, '');
    print(p, '§8§m                                                  ');
    print(p, '');
    print(p, str('  §c[MinePoint] Không tìm thấy §e%s§c!', loc));
    print(p, '');
    print(p, '§8§m                                                  ');
    print(p, '');
    sound('minecraft:entity.villager.no', pos(p), 1.0, 1.0);
  );
);

cmd_clearall() -> (
  p = player();
  if (query(p, 'permission_level') < 2,
    print(p, '§cBạn không có quyền dùng lệnh này!');
    return();
  );
  global_toado = {'saved' -> [], 'all' -> []};
  store_app_data(global_toado);

  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');
  print(p, '  §d[MinePoint] §c§lĐã xóa TẤT CẢ tọa độ!');
  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');

  _broadcast('');
  _broadcast('§8§m                                                  ');
  _broadcast('');
  _broadcast(str('  §d✦ §c%s đã xóa toàn bộ danh sách MinePoint!', str(p)));
  _broadcast('');
  _broadcast('§8§m                                                  ');
  _broadcast('');
  for (player('all'), sound('minecraft:entity.item.break', pos(_), 0.8, 1.0));
);
