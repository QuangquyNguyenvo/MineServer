// ==============================================================================
//              ✦ TP COOLDOWN SYSTEM ✦
//   Thêm thời gian hồi (cooldown) cho các lệnh dịch chuyển của mod
//   "simple-warp-tpa-home-back" (mod đó chỉ có delay khởi động, KHÔNG có cooldown).
//
//   Cách hoạt động: bắt sự kiện player_command của Scarpet, nếu người chơi còn
//   trong thời gian hồi thì trả về 'cancel' -> lệnh bị huỷ trước khi chạy.
//
//   Chỉnh số giây ở global_cooldowns bên dưới rồi chạy: /script load tpcooldown
// ==============================================================================

__config() -> {
  'scope' -> 'global',
  'stay_loaded' -> true,
  'commands' -> {
    ''      -> _() -> cmd_status(),
    'reset' -> _() -> cmd_reset()
  },
  'allow_command_conflicts' -> true
};

// ── Bảng cooldown: 'tên lệnh' -> số giây ─────────────────────────────────────
// Đặt 0 để tắt cooldown cho lệnh đó. Xoá dòng đi cũng có tác dụng tương tự.
global_cooldowns = {
  'home'    -> 60,   // về nhà
  'warp'    -> 60,   // tới điểm warp công khai
  'tpa'     -> 45,   // xin dịch chuyển tới người chơi khác
  'back'    -> 120,  // quay lại vị trí cũ / điểm chết
  'sethome' -> 5,    // chống spam
  'delhome' -> 5,
  'setwarp' -> 5,
  'delwarp' -> 5
};

// Người chơi có permission_level >= mức này sẽ bỏ qua cooldown (2 = gamemaster/OP).
global_bypass_level = 2;

// 'uuid|lệnh' -> mốc thời gian (ms) được phép dùng lại
global_cd = {};

print(format('d [TP Cooldown] ', 'w Hệ thống thời gian hồi dịch chuyển đã sẵn sàng!'));

// ── Helper ───────────────────────────────────────────────────────────────────
_key(p, cmd) -> str(p ~ 'uuid') + '|' + cmd;

// Số giây còn lại của một lệnh, 0 nếu đã sẵn sàng.
_remaining(p, cmd) -> (
  until = get(global_cd, _key(p, cmd));
  if (type(until) != 'number', return(0));
  left = until - time();
  if (left <= 0, 0, ceil(left / 1000))
);

_fmt_time(secs) -> if(
  secs >= 60,
  str('%d phút %d giây', floor(secs / 60), secs % 60),
  str('%d giây', secs)
);

// ── Chặn lệnh khi còn cooldown ───────────────────────────────────────────────
__on_player_command(player, command) -> (
  cmd = lower(split(' ', command):0);
  limit = global_cooldowns:cmd;

  // Lệnh không nằm trong bảng -> cho qua.
  if (type(limit) != 'number' || limit <= 0, return());

  // OP / admin không bị giới hạn.
  if (query(player, 'permission_level') >= global_bypass_level, return());

  left = _remaining(player, cmd);
  if (left > 0,
    print(player, '');
    print(player, str('  §c✦ §fLệnh §e/%s §fđang hồi, còn §c%s§f.', cmd, _fmt_time(left)));
    print(player, '');
    sound('minecraft:entity.villager.no', pos(player), 0.8, 1.0);
    return('cancel');
  );

  // Cho phép chạy, bắt đầu tính giờ hồi.
  put(global_cd, _key(player, cmd), time() + limit * 1000);
  null
);

// Người chơi thoát game thì dọn dữ liệu để map không phình mãi.
__on_player_disconnects(player, reason) -> (
  uuid = str(player ~ 'uuid');
  for (keys(global_cd),
    if ((_ ~ uuid) == 0, delete(global_cd, _));
  );
);

// ── /tpcooldown : xem thời gian hồi của mình ─────────────────────────────────
cmd_status() -> (
  p = player();
  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');
  print(p, '  §d§l✦ TP COOLDOWN §8- §7Thời gian hồi của bạn');
  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');

  if (query(p, 'permission_level') >= global_bypass_level,
    print(p, '  §a✦ Bạn là admin, không bị giới hạn cooldown.');
  ,
    for (keys(global_cooldowns),
      cmd = _;
      limit = global_cooldowns:cmd;
      if (limit > 0,
        left = _remaining(p, cmd);
        if (left > 0,
          print(p, str('  §8• §e/%s §8│ §ccòn %s', cmd, _fmt_time(left)));
        ,
          print(p, str('  §8• §e/%s §8│ §asẵn sàng §8(hồi %s)', cmd, _fmt_time(limit)));
        );
      );
    );
  );

  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');
  sound('minecraft:ui.button.click', pos(p), 0.5, 1.2);
);

// ── /tpcooldown reset : admin xoá toàn bộ cooldown ───────────────────────────
cmd_reset() -> (
  p = player();
  if (query(p, 'permission_level') < global_bypass_level,
    print(p, '§cBạn không có quyền dùng lệnh này!');
    return();
  );
  global_cd = {};
  print(p, '');
  print(p, '  §d[TP Cooldown] §aĐã xoá toàn bộ thời gian hồi của mọi người chơi.');
  print(p, '');
  sound('minecraft:entity.experience_orb.pickup', pos(p), 1.0, 1.5);
);
