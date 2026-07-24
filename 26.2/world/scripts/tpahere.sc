// ==============================================================================
//              ✦ TPA HERE SYSTEM ✦
//   Bổ sung lệnh "gọi người khác tới chỗ mình" mà mod
//   "simple-warp-tpa-home-back" không có (mod đó chỉ có /tpa = mình đi tới họ).
//
//   /tpahere <tên>  - mời một người tới chỗ bạn
//   /tpahere all    - mời tất cả người chơi online tới chỗ bạn
//   /tpahere yes    - chấp nhận lời mời đang chờ
//   /tpahere no     - từ chối lời mời đang chờ
//
//   Người được mời luôn phải tự bấm đồng ý, nên không ai bị kéo đi ngoài ý muốn.
//   Nạp bằng: /script load tpahere
// ==============================================================================

__config() -> {
  'scope' -> 'global',
  'stay_loaded' -> true,
  'commands' -> {
    ''         -> _()    -> cmd_help(),
    'yes'      -> _()    -> cmd_accept(),
    'no'       -> _()    -> cmd_deny(),
    'all'      -> _()    -> cmd_request_all(),
    '<target>' -> _(t)   -> cmd_request(t)
  },
  'arguments' -> {
    'target' -> {'type' -> 'text'}
  },
  'allow_command_conflicts' -> true
};

// ── Cấu hình ─────────────────────────────────────────────────────────────────
global_cd_single = 45;    // giây hồi cho /tpahere <tên>
global_cd_all    = 300;   // giây hồi cho /tpahere all
global_expiry    = 60;    // lời mời hết hạn sau bao nhiêu giây
global_bypass_level = 2;  // permission_level >= mức này thì bỏ qua cooldown

// uuid người được mời -> {'from' -> tên người mời, 'expires' -> ms}
global_pending = {};
// uuid người mời -> mốc thời gian (ms) được mời lại
global_cd = {};

print(format('d [TPA Here] ', 'w Hệ thống mời dịch chuyển đã sẵn sàng!'));

// ── Helper ───────────────────────────────────────────────────────────────────
_fmt_time(secs) -> if(
  secs >= 60,
  str('%d phút %d giây', floor(secs / 60), secs % 60),
  str('%d giây', secs)
);

// Số giây cooldown còn lại của người mời, 0 nếu sẵn sàng.
_cd_left(p) -> (
  if (query(p, 'permission_level') >= global_bypass_level, return(0));
  until = get(global_cd, str(p ~ 'uuid'));
  if (type(until) != 'number', return(0));
  left = until - time();
  if (left <= 0, 0, ceil(left / 1000))
);

_set_cd(p, secs) -> (
  if (query(p, 'permission_level') >= global_bypass_level, return());
  put(global_cd, str(p ~ 'uuid'), time() + secs * 1000);
);

// Gửi lời mời tới một người chơi.
_invite(sender, target) -> (
  sname = str(sender);
  put(global_pending, str(target ~ 'uuid'),
      {'from' -> sname, 'expires' -> time() + global_expiry * 1000});

  print(target, '');
  print(target, '§8§m                                                  ');
  print(target, '');
  print(target, str('  §d§l✦ TPA HERE §8│ §f%s §7mời bạn tới chỗ họ', sname));
  print(target, '');
  print(target, format(
    'w   ',
    'l [Đồng ý]', '!/tpahere yes', '^w Nhấn để dịch chuyển tới chỗ ' + sname,
    'w      ',
    'r [Từ chối]', '!/tpahere no', '^w Nhấn để từ chối lời mời'
  ));
  print(target, '');
  print(target, str('  §8Lời mời hết hạn sau %s.', _fmt_time(global_expiry)));
  print(target, '');
  print(target, '§8§m                                                  ');
  print(target, '');
  sound('minecraft:entity.experience_orb.pickup', pos(target), 0.8, 1.4);
);

// Dịch chuyển "who" tới chỗ "dest" (hỗ trợ khác thế giới).
_teleport_to(who, dest) -> (
  dpos = pos(dest);
  run(str('execute in %s run tp %s %f %f %f',
          dest ~ 'dimension', str(who), dpos:0, dpos:1, dpos:2));
  sound('minecraft:entity.enderman.teleport', pos(who), 1.0, 1.0);
);

// ── /tpahere <tên> ───────────────────────────────────────────────────────────
cmd_request(name) -> (
  p = player();
  target = player(name);

  if (target == null,
    print(p, str('  §c✦ Không tìm thấy người chơi §e%s§c.', name));
    return();
  );
  if (str(target) == str(p),
    print(p, '  §c✦ Bạn không thể tự mời chính mình.');
    return();
  );

  left = _cd_left(p);
  if (left > 0,
    print(p, str('  §c✦ §fLệnh §e/tpahere §fđang hồi, còn §c%s§f.', _fmt_time(left)));
    return();
  );

  _invite(p, target);
  _set_cd(p, global_cd_single);

  print(p, '');
  print(p, str('  §d✦ §fĐã gửi lời mời tới §e%s§f, đang chờ phản hồi...', str(target)));
  print(p, '');
  sound('minecraft:ui.button.click', pos(p), 0.5, 1.2);
);

// ── /tpahere all ─────────────────────────────────────────────────────────────
cmd_request_all() -> (
  p = player();

  left = _cd_left(p);
  if (left > 0,
    print(p, str('  §c✦ §fLệnh §e/tpahere all §fđang hồi, còn §c%s§f.', _fmt_time(left)));
    return();
  );

  count = 0;
  for (player('all'),
    if (str(_) != str(p),
      _invite(p, _);
      count += 1;
    );
  );

  if (count == 0,
    print(p, '  §c✦ Không có người chơi nào khác đang online.');
    return();
  );

  _set_cd(p, global_cd_all);
  print(p, '');
  print(p, str('  §d✦ §fĐã gửi lời mời tới §e%d §fngười chơi.', count));
  print(p, '');
  sound('minecraft:ui.button.click', pos(p), 0.5, 1.2);
);

// ── /tpahere yes ─────────────────────────────────────────────────────────────
cmd_accept() -> (
  p = player();
  req = get(global_pending, str(p ~ 'uuid'));

  if (type(req) != 'map',
    print(p, '  §c✦ Bạn không có lời mời nào đang chờ.');
    return();
  );
  if (time() > req:'expires',
    delete(global_pending, str(p ~ 'uuid'));
    print(p, '  §c✦ Lời mời đã hết hạn.');
    return();
  );

  sender = player(req:'from');
  delete(global_pending, str(p ~ 'uuid'));

  if (sender == null,
    print(p, str('  §c✦ §e%s §cđã offline, không thể dịch chuyển.', req:'from'));
    return();
  );

  _teleport_to(p, sender);
  print(p, str('  §a✦ Đã dịch chuyển tới chỗ §e%s§a.', str(sender)));
  print(sender, str('  §a✦ §e%s §ađã chấp nhận lời mời của bạn.', str(p)));
);

// ── /tpahere no ──────────────────────────────────────────────────────────────
cmd_deny() -> (
  p = player();
  req = get(global_pending, str(p ~ 'uuid'));

  if (type(req) != 'map',
    print(p, '  §c✦ Bạn không có lời mời nào đang chờ.');
    return();
  );

  delete(global_pending, str(p ~ 'uuid'));
  print(p, '  §7✦ Đã từ chối lời mời.');

  sender = player(req:'from');
  if (sender != null,
    print(sender, str('  §c✦ §e%s §cđã từ chối lời mời của bạn.', str(p)));
  );
);

// ── /tpahere ─────────────────────────────────────────────────────────────────
cmd_help() -> (
  p = player();
  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');
  print(p, '  §d§l✦ TPA HERE §8- §7Mời người khác tới chỗ bạn');
  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');
  print(p, '  §8• §b/tpahere <tên> §8│ §7mời một người');
  print(p, '  §8• §b/tpahere all §8│ §7mời tất cả người online');
  print(p, '  §8• §b/tpahere yes §8│ §7chấp nhận lời mời');
  print(p, '  §8• §b/tpahere no §8│ §7từ chối lời mời');
  print(p, '');
  print(p, str('  §7Thời gian hồi: §e%s §7(một người) §8/ §e%s §7(tất cả)',
               _fmt_time(global_cd_single), _fmt_time(global_cd_all)));
  print(p, '');
  print(p, '  §7Muốn tự đi tới chỗ người khác thì dùng §b/tpa <tên>§7.');
  print(p, '');
  print(p, '§8§m                                                  ');
  print(p, '');
  sound('minecraft:ui.button.click', pos(p), 0.5, 1.2);
);
