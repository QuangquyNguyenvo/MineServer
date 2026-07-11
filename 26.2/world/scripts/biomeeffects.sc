// ==============================================================================
//              ✦ BIOME EFFECTS SYSTEM v2 ✦
//   Hiệu ứng theo biome — cân bằng + sáng tạo (Scarpet)
//
//   Triết lý cân bằng:
//     • Biome NGUY HIỂM  -> buff sinh tồn (Nether: chống lửa, End/núi: rơi chậm,
//                           dưới nước: thở nước).
//     • Biome YÊN BÌNH   -> buff nhẹ thuần tuý (tốc độ, hồi máu).
//     • Biome KHẮC NGHIỆT -> đánh đổi (1 buff + 1 debuff NHẸ, cấp I, ngắn).
//   Không còn hiệu ứng cấp II tàn nhẫn hay poison tra tấn.
//
//   Mỗi biome có: tên + lời thoại + 1-2 hiệu ứng + âm thanh + particle riêng.
//   Phân loại bằng regex nên phủ được MỌI biến thể biome (mọi ocean, mọi End...).
//   Cooldown riêng theo từng biome/người chơi để tránh spam khi qua lại ranh giới.
// ==============================================================================

// CÂN BẰNG chống lạm dụng — 2 tầng cooldown cho hiệu ứng biome:
//   • Global gate: sau MỖI lần hiệu ứng biome kích hoạt, phải chờ tối thiểu
//     global_biome_gate_ms trước khi bất kỳ biome nào khác được kích hoạt ->
//     đi men ranh giới nhiều biome không bị "dội" liên tục.
//   • Per-biome cooldown: mỗi biome có cooldown RIÊNG (3-10 phút, tuỳ độ khó,
//     xem _biome_cd_ms) -> vào lại cùng một biome không kích hoạt lại quá sớm.
global_biome_gate_ms = 45000;     // 45s giãn cách tối thiểu giữa 2 lần bất kỳ

global_current_biome = {};        // uuid -> biome_key hiện tại
global_biome_last_received = {};  // uuid -> {biome_key -> thời điểm nhận cuối (ms)}
global_biome_gate = {};           // uuid -> thời điểm (ms) mới được kích hoạt biome tiếp
global_tick_count = 0;

// ── Hệ thống KINH DỊ cho biome tối tăm ────────────────────────────────────────
// Khi ở trong biome có 'horror' -> true, thỉnh thoảng game sẽ "thì thầm": phát
// một âm thanh rùng rợn của Minecraft + (đôi khi) một dòng chữ ma quái trên
// actionbar. Tất cả âm thanh đều là ÂM THANH CÓ SẴN trong Minecraft (gọi theo
// ID), KHÔNG cần tải resource pack -> chạy được ngay trên mọi client.
global_horror_active = {};   // uuid -> đang ở biome kinh dị hay không
global_horror_next = {};     // uuid -> thời điểm (ms) được phép "thì thầm" tiếp
global_jumpscare_next = {};  // uuid -> thời điểm (ms) được phép JUMPSCARE tiếp (rất thưa)
global_horror_cat = {};      // uuid -> nhóm biome kinh dị hiện tại (để chọn text riêng)

// Âm thanh RÙNG RỢN có sẵn trong Minecraft (tiếng hang, warden, sculk, enderman...)
// Phát ở âm lượng LỚN (xem global_horror_volume) cho đã tai, ám ảnh hơn.
global_horror_volume = 1.0;  // âm lượng thì thầm nền (max tại chỗ người chơi)
global_horror_sounds = [
  'minecraft:ambient.cave',
  'minecraft:entity.warden.nearby_close',
  'minecraft:entity.warden.heartbeat',
  'minecraft:entity.warden.listening',
  'minecraft:entity.enderman.stare',
  'minecraft:block.sculk_shrieker.shriek',
  'minecraft:block.sculk_sensor.clicking',
  'minecraft:ambient.soul_sand_valley.mood',
  'minecraft:ambient.basalt_deltas.mood',
  'minecraft:ambient.crimson_forest.mood',
  'minecraft:entity.skeleton.ambient',
  'minecraft:entity.zombie.ambient',
  'minecraft:entity.vex.charge'
];

// JUMPSCARE — âm thanh SỐC, cực lớn, dội thẳng vào mặt (hiếm khi phát).
global_jumpscare_sounds = [
  'minecraft:entity.warden.roar',
  'minecraft:entity.warden.sonic_boom',
  'minecraft:entity.ghast.scream',
  'minecraft:entity.enderman.scream',
  'minecraft:entity.wither.spawn',
  'minecraft:entity.elder_guardian.curse'
];

// Chữ JUMPSCARE cỡ lớn nhá giữa màn hình. Dùng §k (obfuscated) = chữ nhiễu tự
// nhảy loạn như tín hiệu hỏng -> luôn render trên mọi client, tạo cảm giác glitch
// kinh dị. §k được kẹp 2 bên câu chính cho "nhiễu sóng".
global_jumpscare_titles = [
  '§4§l§kXX§r §4§lĐẰNG SAU LƯNG MÀY §r§4§l§kXX',
  '§4§l§k##§r §4§lTAO THẤY MÀY RỒI §r§4§l§k##',
  '§0§l§kll§r §4§lĐỪNG. QUAY. LẠI. §r§0§l§kll',
  '§4§l§kMM§r §4§lMÀY LÀ CỦA TAO §r§4§l§kMM',
  '§4§l§kZZ§r §0§lKHÔNG AI CỨU MÀY §r§4§l§kZZ',
  '§4§l§kVV§r §4§lNHÌN. KÌA. §r§4§l§kVV',
  '§0§l§k██§r §4§lMÀY KHÔNG NÊN ĐÀO SÂU §r§0§l§k██'
];

// Lời BÁO HIỆU trước cú hù (giai đoạn căng thẳng) — thầm, ngắn, dồn nén.
global_dread_lines = [
  '§8§ocó gì đó không ổn...',
  '§8§obỗng nhiên... im lặng.',
  '§7§ohơi thở sau gáy bạn.',
  '§8§ođừng cử động.',
  '§8§onín thở đi...',
  '§7§obóng tối đang siết lại.',
  '§8§onó ngay kia thôi...',
  '§8§ođừng phát ra tiếng.'
];

// Lời thì thầm ma quái DÙNG CHUNG cho mọi biome kinh dị (ngắn, chọn ngẫu nhiên).
global_horror_whispers = [
  '§8§o...nghe thấy chưa?',
  '§8§ochạy đi.',
  '§4§oNÓ thấy bạn rồi.',
  '§8§ođừng quay lại.',
  '§8§ophía sau bạn kìa.',
  '§5§obạn không đơn độc.',
  '§8§oai đó gọi tên bạn.',
  '§4§ođừng chạy.',
  '§8§ohãy nhắm mắt.',
  '§5§obạn không thuộc về đây.',
  '§8§otại sao còn ở đây?',
  '§4§oquá muộn rồi.',
  '§8§ohít thở khẽ thôi.',
  '§8§ocó tiếng bước chân...',
  '§4§omày là của tao.',
  '§8§ođừng nhìn ra sau.',
  '§5§obóng tối đang đói.',
  '§8§ođếm ngược đi...',
  '§4§okhông ai cứu bạn đâu.',
  '§8§ođứng yên.',
  // ── biến thể NHIỄU (§k) — chữ nhảy loạn xen giữa, như tín hiệu chập chờn ──
  '§8§oh§kắ§8§on đang ở §kđâ§8§oy...',
  '§4§ot§ka§4§oi nghe th§kấy§4§o mày',
  '§8§ođừng §ktin§8§o vào bóng của mình',
  '§8§k▓▒░§r §8§omáu trên tường... của ai?',
  '§5§ocơ thể này §ksắp§5§o không là của bạn',
  '§8§ochúng đếm §ktừng§8§o hơi thở bạn',
  '§4§k!!§r §c§oĐỪNG NGOẢNH LẠI §4§k!!',
  '§8§otường đang §kthở§8§o cùng bạn',
  '§8§ocái gì đó §kướt§8§o vừa chạm gáy bạn',
  '§0§k████████████'
];

// Thì thầm RIÊNG theo từng nhóm biome — mỗi biome một chất kinh dị khác nhau.
global_horror_whispers_by_cat = {
  'DEEP_DARK' -> [
    '§0§oim lặng... nó đang nghe.',
    '§8§ođừng gây tiếng động.',
    '§0§onó nghe được nhịp tim bạn.',
    '§4§omột shrieker vừa hét.',
    '§8§osculk đang bò tới.',
    '§0§onhắm mắt cũng vô ích.',
    '§8§ođừng bước nữa.',
    '§5§obóng đêm nuốt ánh sáng.',
    '§0§otim bạn đập to quá.',
    '§4§oNÓ đang lần theo tiếng.'
  ],
  'NETHER' -> [
    '§4§olửa dưới chân đang cười.',
    '§c§omùi lưu huỳnh và máu.',
    '§4§oghast khóc vì bạn.',
    '§c§ođịa ngục không lối ra.',
    '§4§oxương ai đó dưới tro.',
    '§6§ođừng nhìn dung nham.',
    '§c§obạn cháy được đấy.',
    '§4§otro tàn nhớ tên bạn.',
    '§6§oquỷ dữ đang đói.'
  ],
  'SOUL' -> [
    '§8§olinh hồn gọi tên bạn.',
    '§7§ochân bạn lún vào cõi chết.',
    '§8§ochúng muốn thân xác bạn.',
    '§5§olửa xanh nuốt hi vọng.',
    '§7§ođừng thở, chúng biết.',
    '§8§ohàng ngàn mắt nhìn bạn.',
    '§5§obạn thuộc về nơi này rồi.',
    '§7§ohãy ở lại với chúng tôi.'
  ],
  'DRIPSTONE' -> [
    '§8§ogiọt nước... hay máu?',
    '§7§ocó gì rơi từ trần hang.',
    '§8§otiếng nhỏ giọt gần lại.',
    '§7§ođừng đứng dưới nhũ đá.',
    '§8§ohang này không đáy.',
    '§7§ođá đang thở.',
    '§8§ocẩn thận phía trên.',
    '§7§ovọng lại tiếng của bạn.'
  ],
  'DARK_FOREST' -> [
    '§2§ocây dịch chuyển sau lưng.',
    '§8§oánh mắt trong bụi rậm.',
    '§5§ođừng theo tiếng gọi.',
    '§8§orừng nuốt ánh trăng.',
    '§2§onấm... hay khuôn mặt?',
    '§8§olá cây thì thầm tên bạn.',
    '§5§olối ra biến mất rồi.',
    '§2§ocó thứ đu trên cành.'
  ],
  'CAVE' -> [
    '§8§obạn ở quá sâu rồi.',
    '§8§otiếng vọng không phải của bạn.',
    '§7§ođường lên biến mất rồi.',
    '§8§ocó thứ đang đào phía sau.',
    '§8§ođừng để đuốc tắt.',
    '§7§ovách đá đang khép lại.',
    '§8§olòng đất nuốt chửng bạn.',
    '§8§ohang này không có đáy.',
    '§7§ohít thở nhẹ thôi.',
    '§8§ocái gì vừa chạm chân bạn?'
  ]
};

__config() -> {
  'scope' -> 'global',
  'commands' -> {
    ''          -> _() -> cmd_reset_biome(),
    'jumpscare' -> _() -> cmd_test_jumpscare()
  }
};

print(format('a [BiomeEffects] ', 'w Hệ thống hiệu ứng biome v2 (cân bằng) đã sẵn sàng!'));

// Lấy khóa biome VIẾT HOA (VD: 'minecraft:snowy_plains' -> 'SNOWY_PLAINS')
_biome_key_at(p) -> (
  b = str(in_dimension(p, biome(pos(p))));
  parts = split(':', b);
  upper(parts:(length(parts) - 1))
);

// ── Bảng chủ đề biome ─────────────────────────────────────────────────────────
// Mỗi chủ đề: {title, sub, fx:[[effect, giây, cấp-1], ...], sound, particle}
// THỨ TỰ QUAN TRỌNG: mục cụ thể phải đứng trước mục chung
// (VD CRIMSON/WARPED trước FOREST, OCEAN trước SNOW để frozen_ocean thành nước).
_biome_theme(key) -> if(

  key ~ 'NETHER|CRIMSON|BASALT',
    {'title' -> '§4§lĐỊA NGỤC MÁU', 'sub' -> '§cLửa quỷ giữ bạn sống, nhưng địa ngục rút cạn sức bạn.',
     'fx' -> [['fire_resistance', 30, 0], ['weakness', 18, 0], ['hunger', 14, 0]],
     'sound' -> 'minecraft:ambient.nether_wastes.mood', 'particle' -> 'minecraft:flame', 'horror' -> true},

  key ~ 'DEEP_DARK',
    {'title' -> '§0§lVỰC THẲM U MINH', 'sub' -> '§8Bóng tối nuốt chửng ánh sáng... và cả bạn.',
     'fx' -> [['darkness', 20, 0], ['blindness', 6, 0], ['slowness', 16, 0], ['mining_fatigue', 18, 0]],
     'sound' -> 'minecraft:entity.warden.heartbeat', 'particle' -> 'minecraft:sculk_soul', 'horror' -> true},

  // Biome tổng hợp khi ở SÂU DƯỚI LÒNG ĐẤT (hang động) — ghi đè biome bề mặt
  // (rừng/đồng cỏ...) để không hiện "rừng xanh hồi phục" khi bạn đang trong hang.
  key ~ 'UNDERGROUND',
    {'title' -> '§8§lLÒNG ĐẤT SÂU THẲM', 'sub' -> '§7Bóng tối vây quanh, chỉ có tiếng vọng bầu bạn.',
     'fx' -> [['darkness', 8, 0]],
     'sound' -> 'minecraft:ambient.cave', 'particle' -> 'minecraft:smoke', 'horror' -> true},

  key ~ 'SOUL',
    {'title' -> '§8§lTHUNG LŨNG LINH HỒN', 'sub' -> '§7Oan hồn níu chân và bóp nghẹt hơi thở bạn.',
     'fx' -> [['fire_resistance', 30, 0], ['slowness', 16, 0], ['blindness', 5, 0]],
     'sound' -> 'minecraft:ambient.soul_sand_valley.mood', 'particle' -> 'minecraft:soul', 'horror' -> true},

  key ~ 'WARPED',
    {'title' -> '§3§lRỪNG MÉO MÓ', 'sub' -> '§bBào tử kỳ lạ tiếp sức cho bạn.',
     'fx' -> [['fire_resistance', 20, 0], ['speed', 10, 0]],
     'sound' -> 'minecraft:block.nether_sprouts.break', 'particle' -> 'minecraft:warped_spore'},

  key ~ 'END',
    {'title' -> '§5§lVÙNG ĐẤT CUỐI', 'sub' -> '§dHư không nâng đỡ bước chân bạn.',
     'fx' -> [['slow_falling', 12, 0], ['resistance', 8, 0]],
     'sound' -> 'minecraft:block.enderchest.open', 'particle' -> 'minecraft:portal'},

  key ~ 'OCEAN',
    {'title' -> '§1§lĐẠI DƯƠNG', 'sub' -> '§3Lòng biển ban cho bạn hơi thở và tốc độ.',
     'fx' -> [['water_breathing', 25, 0], ['dolphins_grace', 15, 0]],
     'sound' -> 'minecraft:ambient.underwater.enter', 'particle' -> 'minecraft:bubble'},

  key ~ 'RIVER',
    {'title' -> '§b§lDÒNG SÔNG', 'sub' -> '§3Dòng nước mát cuốn bạn đi.',
     'fx' -> [['water_breathing', 15, 0], ['dolphins_grace', 10, 0]],
     'sound' -> 'minecraft:entity.player.splash', 'particle' -> 'minecraft:splash'},

  key ~ 'SWAMP|MANGROVE',
    {'title' -> '§2§lĐẦM LẦY', 'sub' -> '§7Bùn lầy bám vào nhưng bạn thở được dưới nước.',
     'fx' -> [['water_breathing', 12, 0], ['slowness', 6, 0]],
     'sound' -> 'minecraft:block.mud.step', 'particle' -> 'minecraft:mycelium'},

  key ~ 'CHERRY',
    {'title' -> '§d§lRỪNG ANH ĐÀO', 'sub' -> '§dNhững cánh hoa xoa dịu tâm hồn bạn.',
     'fx' -> [['regeneration', 6, 0], ['speed', 8, 0]],
     'sound' -> 'minecraft:block.amethyst_block.chime', 'particle' -> 'minecraft:cherry_leaves'},

  key ~ 'MUSHROOM',
    {'title' -> '§d§lĐẢO NẤM', 'sub' -> '§7Vùng đất an lành ban phước cho bạn.',
     'fx' -> [['regeneration', 6, 0], ['resistance', 10, 0], ['saturation', 2, 0]],
     'sound' -> 'minecraft:block.amethyst_block.chime', 'particle' -> 'minecraft:spore_blossom_air'},

  key ~ 'SNOW|FROZEN|ICE|GROVE',
    {'title' -> '§b§lVÙNG BĂNG GIÁ', 'sub' -> '§7Cái lạnh làm bạn chậm lại nhưng cứng cỏi hơn.',
     'fx' -> [['slowness', 6, 0], ['resistance', 8, 0]],
     'sound' -> 'minecraft:block.powder_snow.step', 'particle' -> 'minecraft:snowflake'},

  key ~ 'DESERT',
    {'title' -> '§e§lSA MẠC', 'sub' -> '§6Cái nóng bào mòn nhưng bạn không sợ lửa.',
     'fx' -> [['fire_resistance', 12, 0], ['hunger', 8, 0]],
     'sound' -> 'minecraft:block.sand.step', 'particle' -> 'minecraft:poof'},

  key ~ 'SAVANNA',
    {'title' -> '§6§lTHẢO NGUYÊN', 'sub' -> '§eBạn phi nhanh qua đồng cỏ khô, nhưng mau đói.',
     'fx' -> [['speed', 10, 0], ['hunger', 6, 0]],
     'sound' -> 'minecraft:block.grass.step', 'particle' -> 'minecraft:poof'},

  key ~ 'BADLANDS',
    {'title' -> '§c§lVÙNG ĐẤT ĐỎ', 'sub' -> '§6Đất khoáng giúp bạn đào nhanh dù cơ thể yếu đi.',
     'fx' -> [['haste', 12, 0], ['weakness', 8, 0]],
     'sound' -> 'minecraft:block.gravel.step', 'particle' -> 'minecraft:crimson_spore'},

  key ~ 'BEACH|SHORE',
    {'title' -> '§e§lBỜ BIỂN', 'sub' -> '§bCát ấm và sóng vỗ khiến bạn thư thái.',
     'fx' -> [['speed', 10, 0], ['regeneration', 4, 0]],
     'sound' -> 'minecraft:entity.player.splash', 'particle' -> 'minecraft:splash'},

  key ~ 'JUNGLE|BAMBOO',
    {'title' -> '§2§lRỪNG RẬM', 'sub' -> '§aBạn nhanh nhẹn đu qua tán cây.',
     'fx' -> [['jump_boost', 12, 0], ['speed', 8, 0]],
     'sound' -> 'minecraft:entity.parrot.ambient', 'particle' -> 'minecraft:composter'},

  key ~ 'DRIPSTONE',
    {'title' -> '§8§lHANG THẠCH NHŨ', 'sub' -> '§7Từng giọt nước rơi trong bóng tối... hay là máu?',
     'fx' -> [['darkness', 14, 0], ['mining_fatigue', 12, 0], ['nausea', 6, 0]],
     'sound' -> 'minecraft:ambient.cave', 'particle' -> 'minecraft:dripping_dripstone_water', 'horror' -> true},

  key ~ 'DARK_FOREST',
    {'title' -> '§8§lRỪNG U TỐI', 'sub' -> '§5Tán cây che khuất bầu trời, bóng tối lẽo đẽo theo bạn.',
     'fx' -> [['darkness', 14, 0], ['nausea', 8, 0], ['weakness', 14, 0]],
     'sound' -> 'minecraft:ambient.cave', 'particle' -> 'minecraft:smoke', 'horror' -> true},

  key ~ 'TAIGA',
    {'title' -> '§2§lRỪNG TAIGA', 'sub' -> '§7Rừng thông lạnh lẽo tôi luyện bạn.',
     'fx' -> [['resistance', 10, 0], ['slowness', 6, 0]],
     'sound' -> 'minecraft:block.amethyst_block.chime', 'particle' -> 'minecraft:composter'},

  key ~ 'FOREST|BIRCH',
    {'title' -> '§a§lRỪNG XANH', 'sub' -> '§2Không khí trong lành hồi phục bạn.',
     'fx' -> [['regeneration', 6, 0], ['speed', 8, 0]],
     'sound' -> 'minecraft:block.amethyst_block.chime', 'particle' -> 'minecraft:happy_villager'},

  key ~ 'WINDSWEPT|PEAK|SLOPE|HILL|MOUNTAIN|STONY|GRAVELLY',
    {'title' -> '§7§lVÙNG CAO NGUYÊN', 'sub' -> '§fKhông khí loãng khiến bước chân bạn nhẹ tênh.',
     'fx' -> [['slow_falling', 10, 0], ['jump_boost', 10, 0]],
     'sound' -> 'minecraft:item.elytra.flying', 'particle' -> 'minecraft:cloud'},

  key ~ 'PLAINS|MEADOW|SUNFLOWER|FLOWER',
    {'title' -> '§a§lĐỒNG CỎ', 'sub' -> '§2Gió đồng nội tiếp thêm sinh lực cho bạn.',
     'fx' -> [['speed', 8, 0], ['regeneration', 4, 0]],
     'sound' -> 'minecraft:block.amethyst_block.chime', 'particle' -> 'minecraft:happy_villager'},

  // Mặc định: mọi biome khác vẫn có chút cảm giác "vùng đất mới".
  {'title' -> '§f§lVÙNG ĐẤT MỚI', 'sub' -> '§7Bạn tiến vào một vùng đất chưa quen.',
   'fx' -> [['speed', 6, 0]],
   'sound' -> 'minecraft:block.amethyst_block.chime', 'particle' -> 'minecraft:crit'}
);

_apply_biome_theme(p, theme) -> (
  // Áp dụng tất cả hiệu ứng (ẩn hạt vanilla để dùng particle chủ đề cho gọn)
  for (theme:'fx',
    e = _;
    modify(p, 'effect', e:0, e:1 * 20, e:2, false, true);
  );

  ppos = pos(p);
  // Actionbar: tên biome » lời thoại (encode_json để escape an toàn dấu ngoặc)
  line = theme:'title' + ' §8» ' + theme:'sub';
  run(str('title %s actionbar %s', str(p), encode_json({'text' -> line})));
  if (theme:'horror' == true,
    // Biome kinh dị: vừa bước vào là DỘI âm thanh lớn + tiếng tim đập trầm nền.
    sound(theme:'sound', ppos, 1.0, 1.0);
    sound('minecraft:entity.warden.heartbeat', ppos, 1.0, 0.7);
    particle(theme:'particle', [ppos:0, ppos:1 + 1, ppos:2], 40, 0.8, 1.1, 0.8, 0.02);
  ,
    sound(theme:'sound', ppos, 0.7, 1.0);
    particle(theme:'particle', [ppos:0, ppos:1 + 1, ppos:2], 24, 0.6, 0.9, 0.6, 0.02);
  );
);

// Nháy đỏ màn hình kiểu "BỊ TẤN CÔNG" nhưng KHÔNG mất máu thật: gây 1 sát
// thương nhỏ (để client hiện hiệu ứng đỏ + giật máu) rồi hồi lại y nguyên máu
// ngay trong cùng tick. Chỉ làm khi máu đủ cao để chắc chắn không chết.
_fake_hurt(p) -> (
  hp = query(p, 'health');
  if (hp > 2,
    run(str('damage %s 1 minecraft:magic', str(p)));  // magic: xuyên giáp, không knockback
    modify(p, 'health', hp);                            // trả lại máu -> net = 0 dame
  );
);

// Chọn một dòng báo hiệu (giai đoạn căng thẳng trước cú hù).
_pick_dread() -> global_dread_lines:(floor(rand(length(global_dread_lines))));

// GIAI ĐOẠN 1 — CĂNG THẲNG: không hù ngay. Mọi thứ như "nín lại": tối sầm dần,
// tim đập chậm dồn dập, chân khựng, một dòng báo hiệu mờ ảo. Rồi SAU 1.5-3.5s
// NGẪU NHIÊN (để bất ngờ, không đoán được) mới nổ ra cú hù thật ở giai đoạn 2.
_jumpscare_buildup(p) -> (
  ppos = pos(p);
  sound('minecraft:entity.warden.heartbeat', ppos, 1.0, 0.4);      // tim đập chậm, trầm
  sound('minecraft:ambient.cave', ppos, 0.8, 0.6);
  modify(p, 'effect', 'darkness', 120, 0, false, false);           // tối dần trong lúc chờ
  modify(p, 'effect', 'slowness', 100, 0, false, false);           // ghì chân lại
  run(str('title %s times 6 40 8', str(p)));
  run(str('title %s subtitle %s', str(p), encode_json({'text' -> _pick_dread()})));
  run(str('title %s title %s', str(p), encode_json({'text' -> '§8§l. . .'})));
  // Đồng hồ đập giữa giai đoạn cho căng thêm.
  schedule(18, _(outer(p)) -> sound('minecraft:entity.warden.heartbeat', pos(p), 1.0, 0.5));
  // Cú hù thật sau 30-70 tick (1.5-3.5s) ngẫu nhiên -> không đoán được thời điểm.
  d = 30 + floor(rand(40));
  schedule(d, _(outer(p)) -> _jumpscare(p, pos(p)));
);

// GIAI ĐOẠN 2 — CÚ HÙ: chồng nhiều âm thanh sốc cực lớn + nhá chữ đỏ nhiễu +
// nháy đỏ "bị tấn công" (không mất máu) + rung lắc mạnh, KÉO DÀI để còn "thấm".
_jumpscare(p, ppos) -> (
  // Chồng 3 tiếng cùng lúc cho dày, nặng và "đầy mặt" hơn hẳn 1 tiếng đơn.
  js = global_jumpscare_sounds:(floor(rand(length(global_jumpscare_sounds))));
  sound(js, ppos, 6.0, if(rand(1) < 0.5, 0.7, 1.0));
  sound('minecraft:entity.warden.sonic_boom', ppos, 4.0, 0.8);
  sound('minecraft:entity.warden.heartbeat', ppos, 4.0, 0.5);

  jt = global_jumpscare_titles:(floor(rand(length(global_jumpscare_titles))));
  run(str('title %s times 2 50 16', str(p)));  // giữ chữ ~2.5s cho thấm
  run(str('title %s subtitle %s', str(p), encode_json({'text' -> '§8§obạn không thể trốn thoát'})));
  run(str('title %s title %s', str(p), encode_json({'text' -> jt})));

  _fake_hurt(p);  // nháy đỏ như bị đánh, nhưng không mất máu
  // Dư chấn KÉO DÀI để trải nghiệm (không tắt cái rụp).
  modify(p, 'effect', 'blindness',  40, 0, false, false);   // 2s tối sầm hẳn
  modify(p, 'effect', 'darkness',  180, 0, false, false);   // 9s bóng tối lởn vởn
  modify(p, 'effect', 'nausea',    220, 0, false, false);   // 11s méo mó, xoay đảo
  modify(p, 'effect', 'slowness',   80, 1, false, false);   // 4s chân khựng
  particle('minecraft:sculk_soul', [ppos:0, ppos:1 + 1, ppos:2], 80, 0.7, 1.1, 0.7, 0.05);
  // Một nhịp tim tàn dư ~1s sau cho cảm giác "chưa hết".
  schedule(20, _(outer(p)) -> sound('minecraft:entity.warden.heartbeat', pos(p), 2.0, 0.5));
);

// Cooldown RIÊNG cho từng biome (giây) — biome càng khó/nguy hiểm cooldown càng
// dài; biome buff/chill (tăng tốc, hồi máu...) tối thiểu 3 phút. Chống lạm dụng.
_biome_cd_ms(key) -> 1000 * if(
  key ~ 'DEEP_DARK',                                          600,  // 10 phút — brutal nhất
  key ~ 'NETHER|CRIMSON|BASALT|SOUL',                         480,  // 8 phút — địa ngục
  key ~ 'UNDERGROUND|DRIPSTONE',                              420,  // 7 phút — hang động
  key ~ 'DARK_FOREST',                                        360,  // 6 phút — rừng u tối
  key ~ 'END|SWAMP|MANGROVE|SNOW|FROZEN|ICE|GROVE|DESERT|BADLANDS|TAIGA', 240,  // 4 phút — khắc nghiệt
  180  // 3 phút — mặc định cho biome buff/chill
);

_check_player_biome(p) -> (
  raw = _biome_key_at(p);
  // Trong hang sâu -> ghi đè thành 'UNDERGROUND', TRỪ khi vốn đã là biome hang
  // chuyên biệt (deep dark / dripstone) thì giữ nguyên cho đúng chủ đề riêng.
  key = if (_is_underground(p) && !(raw ~ 'DEEP_DARK|DRIPSTONE'), 'UNDERGROUND', raw);
  uuid = p ~ 'uuid';
  prev = global_current_biome:uuid;

  if (prev == key, return());          // chỉ xử lý khi ĐỔI biome
  global_current_biome:uuid = key;
  now_ms = time();

  theme = _biome_theme(key);

  // Cập nhật trạng thái kinh dị mỗi lần đổi biome (kể cả khi hiệu ứng bị gate)
  // để "thì thầm"/jumpscare luôn bám theo người chơi trong vùng tối.
  is_horror = theme:'horror' == true;
  global_horror_active:uuid = is_horror;
  if (is_horror,
    global_horror_cat:uuid = _horror_cat(key);
    // Chỉ đặt hẹn giờ LẦN ĐẦU (null) để không reset mỗi khi băng qua biome khác.
    if (global_horror_next:uuid == null,
      global_horror_next:uuid = now_ms + 30000 + floor(rand(60000)));       // whisper đầu: 30-90s
    if (global_jumpscare_next:uuid == null,
      global_jumpscare_next:uuid = now_ms + 600000 + floor(rand(600000)));  // jumpscare đầu: 10-20 phút
  ,
    delete(global_horror_next, uuid);
    delete(global_horror_cat, uuid);
    // KHÔNG xoá global_jumpscare_next: giữ đồng hồ chạy tiếp cho lần vào vùng tối sau.
  );

  // (a) Global gate — vừa mới kích hoạt biome nào đó xong thì khoan đã.
  gate = global_biome_gate:uuid;
  if (gate != null && now_ms < gate, return());

  // (b) Per-biome cooldown dài (3-10 phút tuỳ biome).
  last_map = global_biome_last_received:uuid;
  if (last_map == null,
    last_map = {};
    global_biome_last_received:uuid = last_map;
  );
  last_time = last_map:key;
  if (last_time != null && (now_ms - last_time) < _biome_cd_ms(key), return());

  last_map:key = now_ms;
  global_biome_gate:uuid = now_ms + global_biome_gate_ms;
  _apply_biome_theme(p, theme);
);

// Không khí kinh dị khi ở biome tối. Gọi mỗi giây trong __on_tick.
// Có 2 đồng hồ RIÊNG BIỆT để dễ cân bằng, không lạm dụng:
//   • JUMPSCARE: cực thưa (10-25 phút/lần) -> đi mine 30p-1h chỉ dính 2-3 lần.
//   • THÌ THẦM : thưa (1.5-3.5 phút/lần)  -> đủ tạo không khí, không spam.
_horror_ambience(p) -> (
  uuid = p ~ 'uuid';
  if (global_horror_active:uuid != true, return());
  now_ms = time();
  ppos = pos(p);

  // ── JUMPSCARE (rất hiếm) ──
  jnxt = global_jumpscare_next:uuid;
  if (jnxt != null && now_ms >= jnxt,
    global_jumpscare_next:uuid = now_ms + 780000 + floor(rand(720000));  // 13-25 phút tới lần sau
    _jumpscare_buildup(p);  // căng thẳng trước -> hù sau (không hù ngay)
    return();  // đã vào chuỗi hù thì thôi thì thầm ở nhịp này
  );

  // ── THÌ THẦM (thưa) ──
  wnxt = global_horror_next:uuid;
  if (wnxt == null || now_ms < wnxt, return());
  global_horror_next:uuid = now_ms + 90000 + floor(rand(120000));  // 90-210s tới lần sau

  wsound = global_horror_sounds:(floor(rand(length(global_horror_sounds))));
  // Thỉnh thoảng hạ pitch cho tiếng trầm, méo mó, rợn người hơn.
  wpitch = if (rand(1) < 0.4, 0.6, 1.0);
  sound(wsound, ppos, global_horror_volume, wpitch);

  // 60% kèm một dòng thì thầm; nếu không thì chỉ có âm thanh (đáng sợ hơn).
  if (rand(1) < 0.6,
    run(str('title %s actionbar %s', str(p), encode_json({'text' -> _pick_whisper(uuid)})));
  );
);

// Nhóm biome kinh dị -> để chọn bộ thì thầm riêng cho đúng chất từng nơi.
_horror_cat(key) -> if(
  key ~ 'UNDERGROUND',           'CAVE',
  key ~ 'DEEP_DARK',             'DEEP_DARK',
  key ~ 'NETHER|CRIMSON|BASALT', 'NETHER',
  key ~ 'SOUL',                  'SOUL',
  key ~ 'DRIPSTONE',             'DRIPSTONE',
  key ~ 'DARK_FOREST',           'DARK_FOREST',
  'GENERIC'
);

// Có đang ở SÂU dưới lòng đất (trong hang) không? So Y của người chơi với đỉnh
// địa hình (heightmap) tại cùng x,z: thấp hơn >=6 khối -> coi như trong hang.
// LÝ DO: Minecraft 1.18+ dùng biome 3D nhưng hang thường VẪN mang biome bề mặt
// (rừng/đồng cỏ), nên phải tự phát hiện "ở dưới lòng đất" thay vì tin vào biome.
// Chỉ áp dụng cho Overworld (Nether/End không có khái niệm "bề mặt" như vậy).
_is_underground(p) -> (
  if (p ~ 'dimension' != 'minecraft:overworld', return(false));
  pp = pos(p);
  py = pp:1;
  if (py > 62, return(false));   // trên mực nước biển -> không phải hang (tránh nhầm khi đứng dưới tán cây cao)
  surf = top('motion_blocking', pp:0, pp:2);
  py < surf - 6                  // có >=6 khối che phía trên -> đang trong hang/dưới lòng đất
);

// Chọn một câu thì thầm: 60% lấy câu RIÊNG của biome (nếu có), 40% lấy câu CHUNG.
_pick_whisper(uuid) -> (
  cat = global_horror_cat:uuid;
  own = if(cat == null, null, global_horror_whispers_by_cat:cat);
  pool = if(own != null && rand(1) < 0.6, own, global_horror_whispers);
  pool:(floor(rand(length(pool))))
);

__on_tick() -> (
  global_tick_count = global_tick_count + 1;
  if (global_tick_count % 20 == 0,
    for (player('all'),
      _check_player_biome(_);
      _horror_ambience(_);
    );
  );
);

cmd_reset_biome() -> (
  p = player();
  if (query(p, 'permission_level') < 2,
    print(p, '§cBạn không có quyền sử dụng lệnh này!');
    return();
  );
  uuid = p ~ 'uuid';
  delete(global_current_biome, uuid);
  delete(global_biome_last_received, uuid);
  delete(global_biome_gate, uuid);
  delete(global_horror_active, uuid);
  delete(global_horror_next, uuid);
  delete(global_jumpscare_next, uuid);
  delete(global_horror_cat, uuid);
  print(p, '§aĐã reset biome cache và cooldown của bạn!');
  sound('minecraft:entity.item.break', pos(p), 0.8, 1.0);
);

// Lệnh TEST jumpscare: /biomeeffects jumpscare — tự dội một cú hù vào chính mình.
cmd_test_jumpscare() -> (
  p = player();
  // Chạy đúng CHUỖI thật (căng thẳng -> hù bất ngờ) để bạn trải nghiệm y hệt
  // lúc gặp trong hang. Không in chữ "chuẩn bị" để không lộ bài, giữ bất ngờ.
  _jumpscare_buildup(p);
);
