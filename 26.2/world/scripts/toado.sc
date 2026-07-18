// ==============================================================================
//              ✦ TOẠ ĐỘ MINEPOINT SYSTEM ✦  (viết lại v2)
//   Đánh dấu tọa độ và gửi lên Discord qua Webhook (Scarpet)
//   Dùng http_request() từ mod "scarpet-additions" (raw JSON POST)
//   Kênh: 🧭┃𝐌𝐢𝐧𝐞𝐩𝐨𝐢𝐧𝐭
//
//   FIX LỖI filter(): app_data lưu bằng NBT. Một LIST RỖNG khi lưu rồi nạp lại
//   có thể MẤT KIỂU (trả về null / compound rỗng) -> filter() nhận đối số không
//   phải list -> văng lỗi "filter should be applied to a list". Bản này ÉP KIỂU
//   mọi giá trị về list qua _L() ngay trước khi filter, và khi nạp dữ liệu chỉ
//   nhận giá trị nếu type() == 'list'. Nhờ vậy không bao giờ filter trên non-list.
// ==============================================================================

global_webhook_url = 'https://discord.com/api/webhooks/1470974798115901680/4gmg-np0yFvdrrCrZShs-1OtllaeQGSzevTRZHEMPc9CUjBMnV-NpReexOKUzvNAdhbh';

// ── Helper an toàn kiểu ───────────────────────────────────────────────────────
// _L(v): trả về v nếu là list, ngược lại trả list rỗng. Dùng trước MỌI filter().
_L(v) -> if(type(v) == 'list', v, []);
// _saved()/_all(): luôn đọc ra list sạch từ global_toado.
_saved() -> _L(global_toado:'saved');
_all()   -> _L(global_toado:'all');

// Khởi tạo map SẠCH rồi mới nạp dữ liệu cũ. Chỉ nhận khi đúng kiểu 'list'.
global_toado = {'saved' -> [], 'all' -> []};
try(
  _loaded = load_app_data();
  if (type(_loaded) == 'map',
    if (type(_loaded:'saved') == 'list', global_toado:'saved' = _loaded:'saved');
    if (type(_loaded:'all')   == 'list', global_toado:'all'   = _loaded:'all');
  );
,
  print('§e[MinePoint] Dữ liệu cũ không đọc được, đã khởi tạo lại danh sách trống.');
);

__config() -> {
  'scope' -> 'global',
  'commands' -> {
    ''               -> _()    -> cmd_help(),
    '<loc_text>'     -> _(loc) -> cmd_mark(loc),
    'list'           -> _()    -> cmd_list_self(),
    'list all'       -> _()    -> cmd_list_all(),
    'del <loc_text>' -> _(loc) -> cmd_del(loc),
    'clearall'       -> _()    -> cmd_clearall()
  },
  'arguments' -> {
    'loc_text' -> {'type' -> 'text'}
  },
  'allow_command_conflicts' -> true
};

print(format('d [MinePoint] ', 'w Hệ thống tọa độ Discord đã sẵn sàng!'));

// ── Gửi tin cho toàn server (không dùng print() không mục tiêu để tránh lặp) ──
_broadcast(message) -> (
  for (player('all'), print(_, message));
);

_dim_name(dim) -> if(
  dim == 'minecraft:the_nether', 'Nether',
  dim == 'minecraft:the_end', 'The End',
  'Overworld'
);

// ── Avatar/nhân vật 3D cho embed (Starlight + skin ely.by, fallback mc-heads) ──
_ely_skin(pname)     -> str('http://skinsystem.ely.by/skins/%s.png', pname);
_sl_url(pname, crop) -> str('https://starlightskins.lunareclipse.studio/render/default/%s/%s?skinUrl=%s', pname, crop, _ely_skin(pname));

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
    {'face' -> str('https://mc-heads.net/avatar/%s/64', pname),
     'body' -> str('https://mc-heads.net/body/%s', pname)}
  )
);

_dim_color(dimname) -> if(
  dimname == 'Nether', 16733525,
  dimname == 'The End', 11141290,
  5763719
);

_legend(dimname) -> if(
  dimname == 'Nether',  '🟥 Nether/Crimson/Basalt · 🟪 Warped · 🔴 Bạn',
  dimname == 'The End', '🟪 The End · 🔴 Bạn',
  '🟩 Đồng bằng/Rừng · 🟨 Cát/Sa mạc · 🟦 Nước · ⬜ Tuyết · 🟫 Đầm lầy · 🔴 Bạn'
);

// ── Biome helpers ─────────────────────────────────────────────────────────────
_biome_short(p, x, y, z) -> (
  b = str(in_dimension(p, biome(x, y, z)));
  parts = split(':', b);
  upper(parts:(length(parts) - 1))
);
_biome_pretty(key) -> lower(replace(key, '_', ' '));
_biome_emoji(key) -> if(
  key ~ 'OCEAN|RIVER',                     '🟦',
  key ~ 'NETHER|CRIMSON|BASALT|SOUL',      '🟥',
  key ~ 'WARPED|END|MUSHROOM|CHERRY|VOID', '🟪',
  key ~ 'SNOW|FROZEN|ICE|GROVE',           '⬜',
  key ~ 'SWAMP|MANGROVE|DARK_FOREST',      '🟫',
  key ~ 'BEACH|DESERT|SAVANNA|BADLANDS',   '🟨',
  '🟩'
);

// Bản đồ biome top-down 11x11 (±60 khối). Chỉ chạy khi gõ lệnh.
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

  // Ép kiểu list rồi mới filter -> không bao giờ filter trên non-list.
  saved = filter(_saved(), !(_:'uuid' == uuid && _:'name' == loc));
  saved = put(saved, null, {'uuid' -> uuid, 'name' -> loc, 'x' -> x, 'y' -> y, 'z' -> z, 'dim' -> dimname});
  global_toado:'saved' = saved;

  all_list = filter(_all(), _:'name' != loc);
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

  // Dựng embed + gửi trong task riêng (ping Starlight có thể chậm -> không chặn tick).
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
  mine = filter(_saved(), _:'uuid' == uuid);
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
  all_list = _all();
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
  existing = filter(_saved(), _:'uuid' == uuid && _:'name' == loc);
  if (length(existing) > 0,
    global_toado:'saved' = filter(_saved(), !(_:'uuid' == uuid && _:'name' == loc));
    global_toado:'all'   = filter(_all(),   !(_:'name' == loc && _:'by' == pname));
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
