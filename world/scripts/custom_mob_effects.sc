// ==============================================================================
//              ✦ CUSTOM MOB EFFECTS & EVENTS SYSTEM ✦
//   Gây hiệu ứng bất lợi, buff quái Overworld/Nether/End và Đêm Trăng Máu
// ==============================================================================

// Cấu hình App: Quyền cấp 2 (OP & Console), nạp lệnh con, giữ app luôn chạy
__config() -> {
    'scope' -> 'global',
    'stay_loaded' -> true,
    'event_priority' -> 0,
    'command_permission' -> 2,
    'commands' -> {
        ''                      -> _() -> status(),
        'status'                -> _() -> status(),
        'trigger_blood_moon'    -> _() -> trigger_blood_moon(),
        'test_warden_phase_two' -> _() -> test_warden_p2(),
        'test_warden_heal'      -> _() -> test_warden_heal()
    },
    'allow_command_conflicts' -> true
};

global_blood_moon_day = null;
global_was_blood_moon_notified = false;

// Khai báo các biến toàn cục cho Warden
global_sonic_debuff = {};
global_player_last_health = {};
global_warden_pull_cooldown = {};
global_warden_phase2 = {};
global_warden_emergency_healed = {};
global_warden_healing_ticks = {};
global_warden_loot_dropped = {};
global_player_warden_music = {};
global_player_music_timer = {};
global_warden_melee_combo = {};
global_warden_sonic_charge_tick = {};
global_golem_hp = {};
global_warden_golem_slam_cd = {};
global_warden_last_health = {};
global_warden_last_player_dmg_tick = {};
global_warden_golem_heal_cd = {};

// Danh sách các hiệu ứng xấu để phản ngược lại người chơi
global_negative_effects = {
    'minecraft:slowness' -> true,
    'minecraft:mining_fatigue' -> true,
    'minecraft:nausea' -> true,
    'minecraft:blindness' -> true,
    'minecraft:hunger' -> true,
    'minecraft:weakness' -> true,
    'minecraft:poison' -> true,
    'minecraft:wither' -> true,
    'minecraft:levitation' -> true,
    'minecraft:unluck' -> true,
    'minecraft:darkness' -> true
};

// ── CÁC HÀM TIỆN ÍCH CỐT LÕI (100% NATIVE SCARPET 1.21+) ──

// Helper tính khoảng cách Euclid giữa 2 tọa độ
_distance(p1, p2) -> (
    if (p1 == null || p2 == null, return(999999));
    dx = p1:0 - p2:0;
    dy = p1:1 - p2:1;
    dz = p1:2 - p2:2;
    sqrt(dx*dx + dy*dy + dz*dz)
);

// Helper lấy tất cả Warden trên toàn bộ các Dimension
_get_all_wardens() -> (
    all_w = [];
    for(system_info('world_dimensions'),
        in_dimension(_,
            for(entity_list('warden'), all_w += _);
        );
    );
    all_w;
);

// Helper lấy chỉ số attribute an toàn (tương thích Minecraft 1.21+)
_get_attribute(e, attr_name, default_val) -> (
    if (e == null, return(default_val));
    clean_name = replace(attr_name, 'generic.', '');
    clean_name = replace(clean_name, 'minecraft:', '');
    
    // 1. Thử query với clean_name (max_health, movement_speed, attack_damage)
    try(
        val = query(e, 'attribute', clean_name);
        if (val != null, return(val));
    , null);
    
    // 2. Thử query với minecraft:<clean_name>
    try(
        val = query(e, 'attribute', 'minecraft:' + clean_name);
        if (val != null, return(val));
    , null);
    
    return(default_val);
);

// Helper lấy giới hạn máu tối đa cho phép của Warden (Phase 1: 1500 HP, Phase 2/Rage/Huyết Tế: Max 600 HP)
_get_warden_max_allowed_health(w) -> (
    if (w == null, return(1500.0));
    w_uuid = w ~ 'uuid';
    is_p2 = global_warden_phase2:w_uuid || (w ~ 'health') <= 450 || (global_warden_emergency_healed:w_uuid == true);
    if (is_p2, 600.0, _get_attribute(w, 'max_health', 1500.0))
);

// Hàm khởi tạo Bossbar cho Warden (Màu text xanh dark_aqua, màu thanh bossbar xanh blue)
_init_warden_bossbar() -> (
    run('bossbar add warden_boss {"text":"Warden","color":"dark_aqua","bold":true}');
    run('bossbar set minecraft:warden_boss max 1500');
    run('bossbar set minecraft:warden_boss color blue');
    run('bossbar set minecraft:warden_boss style progress');
    run('bossbar set minecraft:warden_boss visible false');
);

// Tải dữ liệu lưu trữ ngày Trăng Máu
_load_blood_moon_data() -> (
    current_day = floor(day_time() / 24000);
    data = read_file('bloodmoon', 'json');
    if (data != null && data:'next_day' != null && data:'next_day' >= current_day && data:'next_day' <= current_day + 30,
        global_blood_moon_day = data:'next_day';
    ,
        global_blood_moon_day = current_day + floor(rand(8)) + 8;
        _save_blood_moon_data();
    );
);

_save_blood_moon_data() -> (
    write_file('bloodmoon', 'json', {'next_day' -> global_blood_moon_day});
);

// Helper lấy lượng kinh nghiệm cộng thêm (50% của lượng gốc)
_get_additional_xp(mob_type) -> (
    xp_map = {
        'zombie' -> 2, 'creeper' -> 2, 'skeleton' -> 2, 'spider' -> 2,
        'enderman' -> 2, 'cave_spider' -> 2, 'drowned' -> 2, 'husk' -> 2,
        'stray' -> 2, 'zombie_villager' -> 2, 'witch' -> 2,
        'blaze' -> 2, 'ghast' -> 2, 'guardian' -> 5, 'elder_guardian' -> 50,
        'ravager' -> 10, 'evoker' -> 5, 'piglin_brute' -> 10,
        'wither' -> 25, 'ender_dragon' -> 250
    };
    val = xp_map:mob_type;
    if (val != null, val, 2)
);

// Helper gây chướng khí Huyết Tế Tối Thượng lên người chơi trong phạm vi 40m
_apply_warden_blood_sacrifice_debuffs(w_pos) -> (
    for(player('all'),
        p = _;
        if (_distance(pos(p), w_pos) <= 40,
            modify(p, 'effect', 'nausea', 200, 1);    // Nausea II trong 10s (200 ticks)
            modify(p, 'effect', 'blindness', 200, 0); // Blindness trong 10s (200 ticks)
            modify(p, 'effect', 'poison', 200, 1);   // Poison II trong 10s (200 ticks)
            run(str('title %s actionbar {"text":"§4§lChướng Khí Huyết Tế: Nausea II, Blindness & Poison II trong 10s!"}', p ~ 'name'));
        )
    );
);

// Helper bắn sóng âm Sonic Boom (Cận chiến hoặc theo dõi mục tiêu)
_fire_warden_melee_sonic_boom(w, target) -> (
    if (w == null || query(w, 'removed') || target == null || query(target, 'removed'), return());
    w_pos = pos(w);
    t_pos = pos(target);
    w_eye = [w_pos:0, w_pos:1 + 1.6, w_pos:2];
    t_eye = [t_pos:0, t_pos:1 + 1.0, t_pos:2];
    
    dx = t_eye:0 - w_eye:0;
    dy = t_eye:1 - w_eye:1;
    dz = t_eye:2 - w_eye:2;
    dist = sqrt(dx*dx + dy*dy + dz*dz);
    if (dist <= 0, return());
    
    // Bắn hạt Sonic Boom dọc theo tia
    step = 0.8;
    steps = max(1, floor(dist / step));
    for(range(0, steps + 1),
        prog = _ / steps;
        px = w_eye:0 + dx * prog;
        py = w_eye:1 + dy * prog;
        pz = w_eye:2 + dz * prog;
        run(str('particle minecraft:sonic_boom %f %f %f 0 0 0 0 1', px, py, pz));
    );
    
    sound('minecraft:entity.warden.sonic_boom', t_pos, 2.0, 1.0);
    
    w_uuid = w ~ 'uuid';
    is_p2 = global_warden_phase2:w_uuid;
    w_max = _get_warden_max_allowed_health(w);
    
    // Nếu mục tiêu là người chơi đứng xa (> 4.5m) -> Hồi +10 HP cho Warden
    if (dist > 4.5 && target ~ 'type' == 'player',
        modify(w, 'health', min(w_max, (w ~ 'health') + 10.0));
        sound('minecraft:entity.warden.heartbeat', w_pos, 1.5, 1.3);
        run(str('particle minecraft:sculk_soul %f %f %f 0.5 0.5 0.5 0.05 10', w_pos:0, w_pos:1 + 1.5, w_pos:2));
    );
    
    if (target ~ 'type' == 'player',
        t_max_hp = _get_attribute(target, 'max_health', 20.0);
        sonic_ratio = if(is_p2, 0.45, 0.33);
        true_dmg = t_max_hp * sonic_ratio;
        
        remaining_hp = (target ~ 'health') - true_dmg;
        if (remaining_hp <= 0,
            modify(target, 'health', 0.0);
        ,
            modify(target, 'health', remaining_hp);
        );
        p_name = target ~ 'name';
        global_sonic_debuff:p_name = 100;
        
        // Hất văng mục tiêu
        modify(target, 'motion', dx / dist * 0.8, 0.4, dz / dist * 0.8);
        
        if (is_p2,
            run(str('title %s actionbar {"text":"§4§lTrúng Sonic Boom Phase 2: Nhận 45%% Sát thương chuẩn & Giảm hồi máu 50%%!"}', p_name));
        ,
            run(str('title %s actionbar {"text":"§c§lBị trúng Sóng Âm: Nhận sát thương chuẩn & Giảm hồi máu 50%%!"}', p_name));
        );
    ,
        // Nếu mục tiêu là Iron Golem / Mob khác
        curr_thp = target ~ 'health';
        modify(target, 'health', max(0.0, curr_thp - 100.0));
        modify(target, 'motion', dx / dist * 1.0, 0.5, dz / dist * 1.0);
    );
);

// Helper kích hoạt trạng thái Cuồng Nộ (RAGE / Phase 2) cho Warden
_trigger_warden_rage(w, w_pos, w_uuid) -> (
    global_warden_phase2:w_uuid = true;
    run(str('attribute %s movement_speed base set 0.45', w_uuid));
    
    sound('minecraft:entity.warden.roar', w_pos, 2.5, 0.8);
    sound('minecraft:entity.ender_dragon.growl', w_pos, 2.0, 0.6);
    for(player('all'), sound('minecraft:entity.warden.roar', pos(_), 1.5, 0.8));
    
    run(str('particle minecraft:sculk_soul %f %f %f 1.0 1.0 1.0 0.2 60', w_pos:0, w_pos:1 + 1.5, w_pos:2));
    run(str('particle minecraft:soul_fire_flame %f %f %f 1.5 1.5 1.5 0.1 80', w_pos:0, w_pos:1 + 1.0, w_pos:2));
    
    run('title @a subtitle {"text":"Warden đã bùng nổ năng lượng Sculk!","color":"dark_red","italic":true}');
    run('title @a title {"text":"CUỒNG NỘ (RAGE)!","color":"red","bold":true}');
    
    run('tellraw @a ["",{"text":"[WARNING] ","color":"dark_red","bold":true},{"text":"Warden ","color":"dark_aqua","bold":true},{"text":"đã rơi vào trạng thái ","color":"gray"},{"text":"CUỒNG NỘ (RAGE)!","color":"red","bold":true,"italic":true},{"text":"\\nSức mạnh Sculk bùng nổ, mọi đòn đánh giờ đây bỏ qua giáp!","color":"dark_purple"}]');
);

// Helper tạo rơi vật phẩm thần thoại và kho báu khi Warden bị tiêu diệt
_drop_warden_loot(w_pos, killer) -> (
    bx = w_pos:0;
    by = w_pos:1 + 0.5;
    bz = w_pos:2;
    
    // ── NHÓM 1: GUARANTEED 100% DROPS ──
    run(str('summon item %f %f %f {Item:{id:"minecraft:heavy_core",count:1}}', bx, by, bz));
    
    star_count = if(rand(1.0) < 0.5, 1, 2);
    run(str('summon item %f %f %f {Item:{id:"minecraft:nether_star",count:%d}}', bx, by, bz, star_count));
    
    template_count = if(rand(1.0) < 0.5, 1, 2);
    run(str('summon item %f %f %f {Item:{id:"minecraft:netherite_upgrade_smithing_template",count:%d}}', bx, by, bz, template_count));
    
    run(str('summon experience_orb %f %f %f {value:500}', bx, by, bz));
    run(str('summon experience_orb %f %f %f {value:500}', bx, by, bz));
    run(str('summon experience_orb %f %f %f {value:500}', bx, by, bz));
    run(str('summon experience_orb %f %f %f {value:500}', bx, by, bz));
    
    // ── NHÓM 2: TRANG BỊ THẦN THOẠI GOD GEAR (TỈ LỆ 10% RƠI 1 TRONG 3 MÓN THẦN KHÍ) ──
    if (rand(1.0) < 0.10,
        god_gear_roll = floor(rand(3));
        if (god_gear_roll == 0,
            run(str('summon item %f %f %f {Item:{id:"weaponsexpanded:netherite_scythe",count:1,components:{"minecraft:custom_name":{text:"Lưỡi Đao Hư Không (Void Reaper)",color:"dark_purple",bold:1b,italic:0b},"minecraft:lore":[{text:"Được rèn từ mảnh vỡ xương sọ và linh hồn Sculk của Warden.",color:"gray",italic:1b},{text:"★ Trang Bị Thần Thoại (Mythic Tier) ★",color:"gold",bold:1b}],"minecraft:enchantments":{"minecraft:sharpness":7,"minecraft:looting":4,"minecraft:sweeping_edge":4,"minecraft:unbreaking":5,"minecraft:mending":1},"minecraft:rarity":"epic"}}}', bx, by, bz));
            run('tellraw @a ["",{"text":"[THẦN KHÍ XUẤT THẾ] ","color":"gold","bold":true},{"text":"Warden đã rơi ra bảo khí Thần Thoại: ","color":"yellow"},{"text":"Lưỡi Đao Hư Không (Void Reaper)!","color":"dark_purple","bold":true}]');
        , god_gear_roll == 1,
            run(str('summon item %f %f %f {Item:{id:"minecraft:netherite_chestplate",count:1,components:{"minecraft:custom_name":{text:"Giáp Ngực Hư Vô (Sculk Carapace)",color:"dark_aqua",bold:1b,italic:0b},"minecraft:lore":[{text:"Lớp mai giáp hắc ám hấp thụ chấn động từ cõi chết.",color:"gray",italic:1b},{text:"★ Trang Bị Thần Thoại (Mythic Tier) ★",color:"gold",bold:1b}],"minecraft:enchantments":{"minecraft:protection":6,"minecraft:thorns":4,"minecraft:unbreaking":5,"minecraft:mending":1},"minecraft:rarity":"epic"}}}', bx, by, bz));
            run('tellraw @a ["",{"text":"[THẦN KHÍ XUẤT THẾ] ","color":"gold","bold":true},{"text":"Warden đã rơi ra bảo khí Thần Thoại: ","color":"yellow"},{"text":"Giáp Ngực Hư Vô (Sculk Carapace)!","color":"dark_aqua","bold":true}]');
        ,
            run(str('summon item %f %f %f {Item:{id:"minecraft:netherite_boots",count:1,components:{"minecraft:custom_name":{text:"Ủng Bóng Ma (Ghost Walker Boots)",color:"dark_aqua",bold:1b,italic:0b},"minecraft:lore":[{text:"Bước đi êm ái như bóng ma, lướt qua mọi cạm bẫy Deep Dark.",color:"gray",italic:1b},{text:"★ Trang Bị Thần Thoại (Mythic Tier) ★",color:"gold",bold:1b}],"minecraft:enchantments":{"minecraft:protection":6,"minecraft:feather_falling":5,"minecraft:depth_strider":4,"minecraft:soul_speed":3,"minecraft:mending":1},"minecraft:rarity":"epic"}}}', bx, by, bz));
            run('tellraw @a ["",{"text":"[THẦN KHÍ XUẤT THẾ] ","color":"gold","bold":true},{"text":"Warden đã rơi ra bảo khí Thần Thoại: ","color":"yellow"},{"text":"Ủng Bóng Ma (Ghost Walker Boots)!","color":"dark_aqua","bold":true}]');
        );
    );
    
    // ── NHÓM 3: KHO BÁU & TÀI NGUYÊN (TỈ LỆ 50% MỖI MÓN) ──
    if (rand(1.0) < 0.50,
        gap_count = floor(rand(3)) + 2;
        run(str('summon item %f %f %f {Item:{id:"minecraft:enchanted_golden_apple",count:%d}}', bx, by, bz, gap_count));
    );
    
    if (rand(1.0) < 0.50,
        run(str('summon item %f %f %f {Item:{id:"minecraft:netherite_ingot",count:2}}', bx, by, bz));
    );
    
    if (rand(1.0) < 0.50,
        run(str('summon item %f %f %f {Item:{id:"minecraft:totem_of_undying",count:2}}', bx, by, bz));
    );
    
    if (rand(1.0) < 0.50,
        run(str('summon item %f %f %f {Item:{id:"minecraft:silence_armor_trim_smithing_template",count:1}}', bx, by, bz));
    );
    
    sound('minecraft:ui.toast.challenge_complete', w_pos, 2.0, 1.0);
    sound('minecraft:entity.player.levelup', w_pos, 2.0, 0.5);
    run(str('summon firework_rocket %f %f %f {LifeTime:20,FireworksItem:{id:"firework_rocket",count:1,components:{"minecraft:fireworks":{explosions:[{Shape:"large_ball",Colors:[16711680,65280,255],FadeColors:[16776960]}]}}}}', bx, by + 1, bz));
    
    killer_name = if(killer != null, killer ~ 'name', 'Dũng sĩ vô danh');
    run(str('tellraw @a ["",{"text":"[CHIẾN TÍCH] ","color":"green","bold":true},{"text":"%s ","color":"gold","bold":true},{"text":"đã tiêu diệt thành công ","color":"yellow"},{"text":"Chúa Tể Bóng Tối Warden!","color":"dark_aqua","bold":true}]', killer_name));
);

// ── HANDLERS KHI MOB SPAWN (100% NATIVE 1.21+) ──

_on_enderman_load(e, new) -> (
    if (new && (e ~ 'dimension') ~ 'the_end',
        schedule(0, _(outer(e)) -> (
            if (e && !query(e, 'removed'),
                base_hp = _get_attribute(e, 'max_health', 40.0);
                new_hp = base_hp * 1.5;
                run(str('attribute %s max_health base set %f', e ~ 'uuid', new_hp));
                modify(e, 'health', new_hp);
                
                base_dmg = _get_attribute(e, 'attack_damage', 7.0);
                run(str('attribute %s attack_damage base set %f', e ~ 'uuid', base_dmg + 1.0));
            )
        ));
    )
);

_on_shulker_load(e, new) -> (
    if (new && (e ~ 'dimension') ~ 'the_end',
        schedule(0, _(outer(e)) -> (
            if (e && !query(e, 'removed'),
                base_hp = _get_attribute(e, 'max_health', 30.0);
                new_hp = base_hp * 1.5;
                run(str('attribute %s max_health base set %f', e ~ 'uuid', new_hp));
                modify(e, 'health', new_hp);
            )
        ));
    )
);

_on_wither_load(e, new) -> (
    if (new,
        schedule(0, _(outer(e)) -> (
            if (e && !query(e, 'removed'),
                run(str('attribute %s max_health base set 600', e ~ 'uuid'));
                modify(e, 'health', 600.0);
            )
        ));
    )
);

_on_dragon_load(e, new) -> (
    if (new,
        schedule(0, _(outer(e)) -> (
            if (e && !query(e, 'removed'),
                run(str('attribute %s max_health base set 700', e ~ 'uuid'));
                modify(e, 'health', 700.0);
            )
        ));
    )
);

_on_warden_load(e, new) -> (
    if (new,
        schedule(0, _(outer(e)) -> (
            if (e && !query(e, 'removed'),
                run(str('attribute %s max_health base set 1500', e ~ 'uuid'));
                run(str('attribute %s movement_speed base set 0.30', e ~ 'uuid'));
                modify(e, 'health', 1500.0);
                
                // Thông báo global toàn server
                w_pos = pos(e);
                dim = str(e ~ 'dimension');
                dim_name = if(dim ~ 'overworld', 'Overworld', if(dim ~ 'nether', 'Nether', if(dim ~ 'end', 'The End', 'Overworld')));
                bx = floor(w_pos:0);
                by = floor(w_pos:1);
                bz = floor(w_pos:2);
                
                for(player('all'),
                    p = _;
                    sound('minecraft:entity.warden.emerge', pos(p), 1.2, 0.7);
                    sound('minecraft:ambient.cave', pos(p), 1.0, 0.5);
                    print(p, str('§4§l[CẢNH BÁO TOÀN SERVER] §cChúa Tể Bóng Tối §4§lWarden §cđã thức tỉnh tại §e%s [%d, %d, %d]§c! Hãy cẩn trọng!', dim_name, bx, by, bz));
                    run(str('title %s subtitle {"text":"§cTại: %s [%d, %d, %d]","italic":true}', p ~ 'name', dim_name, bx, by, bz));
                    run(str('title %s title {"text":"§4§lWARDEN ĐÃ XUẤT HIỆN!"}', p ~ 'name'));
                );
            )
        ));
    )
);

_on_overworld_mob_load(e, new) -> (
    if (new && (e ~ 'dimension') ~ 'overworld',
        day = floor(day_time() / 24000);
        daytime = day_time() % 24000;
        is_night = (daytime >= 12000 && daytime < 23000);
        
        if (day == global_blood_moon_day && is_night,
            schedule(0, _(outer(e)) -> (
                if (e && !query(e, 'removed'),
                    base_hp = _get_attribute(e, 'max_health', 20.0);
                    new_hp = base_hp * 2.5;
                    run(str('attribute %s max_health base set %f', e ~ 'uuid', new_hp));
                    modify(e, 'health', new_hp);
                    
                    base_speed = _get_attribute(e, 'movement_speed', 0.25);
                    new_speed = base_speed * 1.3;
                    run(str('attribute %s movement_speed base set %f', e ~ 'uuid', new_speed));
                )
            ));
        )
    )
);

// Khởi chạy khi App được nạp
_setup_app() -> (
    _init_warden_bossbar();
    _load_blood_moon_data();
    
    entity_load_handler('enderman', '_on_enderman_load');
    entity_load_handler('shulker', '_on_shulker_load');
    entity_load_handler('wither', '_on_wither_load');
    entity_load_handler('ender_dragon', '_on_dragon_load');
    entity_load_handler('warden', '_on_warden_load');
    
    for(['zombie', 'skeleton', 'creeper', 'spider', 'cave_spider', 'witch', 'drowned', 'husk', 'stray', 'phantom', 'slime', 'silverfish', 'bogged'],
        entity_load_handler(_, '_on_overworld_mob_load')
    );
);

__on_start() -> _setup_app();
_setup_app();

// Hàm phá hủy block xung quanh Warden để tránh bị nhốt
_warden_break_blocks(w) -> (
    w_pos = pos(w);
    bx = floor(w_pos:0);
    by = floor(w_pos:1);
    bz = floor(w_pos:2);
    
    for(range(-1, 2), dx = _;
        for(range(0, 4), dy = _;
            for(range(-1, 2), dz = _;
                bpos = [bx + dx, by + dy, bz + dz];
                b = block(bpos);
                if (b != 'air' && b != 'water' && b != 'lava' && b != 'cave_air' && b != 'void_air' && b != 'bedrock' && b != 'barrier',
                    run(str('fill %d %d %d %d %d %d air destroy', bpos:0, bpos:1, bpos:2, bpos:0, bpos:1, bpos:2));
                )
            )
        )
    )
);

// ── SỰ KIỆN KHI NGƯỜI CHƠI NHẬN SÁT THƯƠNG ──
__on_player_takes_damage(player, amount, source, source_entity) -> (
    if (player == null || (player ~ 'health') <= 0, return());
    p_name = player ~ 'name';
    
    // ── XỬ LÝ SÁT THƯƠNG SONIC BOOM CỦA WARDEN ──
    if (source == 'sonic_boom' || (source_entity != null && source_entity ~ 'type' == 'warden' && source ~ 'sonic'),
        hp = player ~ 'health';
        max_hp = _get_attribute(player, 'max_health', 20.0);
        
        is_phase2 = false;
        if (source_entity != null && source_entity ~ 'type' == 'warden',
            w_uuid = source_entity ~ 'uuid';
            is_phase2 = (global_warden_phase2:w_uuid || (source_entity ~ 'health') <= 450);
        ,
            p_pos = pos(player);
            for(_get_all_wardens(),
                w = _;
                if (_distance(p_pos, pos(w)) <= 40,
                    w_uuid = w ~ 'uuid';
                    if (global_warden_phase2:w_uuid || (w ~ 'health') <= 450,
                        is_phase2 = true;
                        break();
                    )
                )
            );
        );
        
        sonic_ratio = if(is_phase2, 0.45, 0.33);
        true_damage = max_hp * sonic_ratio;
        target_hp = hp - true_damage;
        
        global_sonic_debuff:p_name = 100;
        if (is_phase2,
            run(str('title %s actionbar {"text":"§4§lTrúng Sonic Boom Phase 2: Nhận 45%% Sát thương chuẩn & Giảm hồi máu 50%%!"}', p_name));
        ,
            run(str('title %s actionbar {"text":"§c§lBị trúng Sóng Âm: Nhận sát thương chuẩn & Giảm hồi máu 50%%!"}', p_name));
        );
        sound('minecraft:entity.warden.sonic_boom', pos(player), 1.0, if(is_phase2, 1.2, 1.0));
        
        if (target_hp <= 0,
            modify(player, 'health', 0.0);
            return();
        );
        
        if (amount >= hp,
            temp_health = hp + amount;
            run(str('attribute %s max_health base set %f', player ~ 'uuid', max(max_hp, temp_health)));
            modify(player, 'health', temp_health);
            schedule(0, _(outer(player), outer(target_hp), outer(max_hp)) -> (
                if (player && !query(player, 'removed'),
                    run(str('attribute %s max_health base set %f', player ~ 'uuid', max_hp));
                    modify(player, 'health', target_hp);
                )
            ));
        ,
            schedule(0, _(outer(player), outer(target_hp)) -> (
                if (player && !query(player, 'removed'),
                    modify(player, 'health', target_hp);
                )
            ));
        );
        return();
    );
    
    if (source_entity == null, return());
    type = source_entity ~ 'type';
    
    // ── CƠ CHẾ PHẢN HIỆU ỨNG XẤU, HỒI MÁU TẦM XA & TÍCH LŨY COMBO CỦA WARDEN ──
    if (type == 'warden',
        effects = query(source_entity, 'effect');
        if (effects != null,
            for(effects,
                effect_info = _;
                effect_name = effect_info:0;
                amp = effect_info:1;
                dur = effect_info:2;
                
                if (global_negative_effects:effect_name,
                    apply_dur = min(100, if(dur == -1, 100, dur));
                    modify(player, 'effect', effect_name, apply_dur, amp);
                    if (rand(1.0) < 0.3,
                        run(str('title %s actionbar {"text":"§c§lWarden phản lại hiệu ứng xấu: %s!"}', p_name, effect_name));
                    );
                )
            )
        );
        
        w_uuid = source_entity ~ 'uuid';
        w_pos = pos(source_entity);
        p_dist = _distance(pos(player), w_pos);
        w_max = _get_warden_max_allowed_health(source_entity);
        
        // Nếu đòn đánh ở cự ly xa (> 4.5m) -> Hồi +10 HP cho Warden
        if (p_dist > 4.5,
            modify(source_entity, 'health', min(w_max, (source_entity ~ 'health') + 10.0));
            sound('minecraft:entity.warden.heartbeat', w_pos, 1.5, 1.3);
            run(str('particle minecraft:sculk_soul %f %f %f 0.5 0.5 0.5 0.05 10', w_pos:0, w_pos:1 + 1.5, w_pos:2));
        ,
            // Đòn đánh cận chiến (<= 4.5m) -> Tích lũy điểm Combo (3+1 trước Huyết Tế, 2+1 sau Huyết Tế)
            is_post_sacrifice = (global_warden_emergency_healed:w_uuid && (global_warden_healing_ticks:w_uuid == null || global_warden_healing_ticks:w_uuid <= 0));
            max_combo = if(is_post_sacrifice, 2, 3);
            combo = (global_warden_melee_combo:w_uuid || 0) + 1;
            if (combo >= max_combo,
                global_warden_melee_combo:w_uuid = 0;
                global_warden_sonic_charge_tick:w_uuid = 15; // Nạp 15 ticks (0.75s) rồi bắn
                sound('minecraft:entity.warden.sonic_charge', w_pos, 2.0, 1.0);
                run(str('particle minecraft:sculk_charge_pop %f %f %f 0.5 0.5 0.5 0.1 20', w_pos:0, w_pos:1 + 1.5, w_pos:2));
            ,
                global_warden_melee_combo:w_uuid = combo;
            );
        );
    );
    
    is_mob = query(source_entity, 'category') == 'hostile' || type == 'wither' || type == 'ender_dragon';
    is_bullet = (type == 'shulker_bullet');
    is_boss_attack = (type == 'wither' || type == 'wither_skull' || type == 'ender_dragon' || type == 'dragon_fireball');
    is_wither_attack = (type == 'wither' || type == 'wither_skull');
    
    if (!is_mob && !is_bullet && !is_boss_attack, return());
    dim = str(player ~ 'dimension');
    is_overworld = (dim ~ 'overworld');
    is_nether = (dim ~ 'nether');
    is_end = (dim ~ 'end');
    
    if (is_boss_attack && !is_wither_attack && (is_nether || is_end),
        modify(player, 'health', max(0.5, (player ~ 'health') - 1.0));
    );
    
    if (is_wither_attack,
        modify(player, 'health', max(0.5, (player ~ 'health') - 4.0));
        run(str('title %s actionbar {"text":"§4§lBị tấn công bởi Wither: Nhận 4 sát thương chuẩn! (Bỏ qua giáp)"}', p_name));
    );
    
    if (is_overworld && is_mob && !is_boss_attack,
        day = floor(day_time() / 24000);
        daytime = day_time() % 24000;
        is_night = (daytime >= 12000 && daytime < 23000);
        is_blood_moon = (day == global_blood_moon_day && is_night);
        
        if (is_blood_moon,
            modify(player, 'health', max(0.5, (player ~ 'health') - 2.0));
            if (rand(1.0) < 0.50,
                modify(player, 'effect', 'blindness', 60, 1);
                run(str('title %s actionbar {"text":"§4§lTrăng Máu: Bạn bị mù quáng (Blindness II)!"}', p_name));
            )
        );
        
        chance = if(is_night, 0.30, 0.15);
        if (rand(1.0) < chance,
            modify(player, 'effect', 'poison', 100, 0);
            modify(player, 'effect', 'slowness', 60, 0);
            if (!is_blood_moon,
                run(str('title %s actionbar {"text":"§cBạn bị nhiễm độc và làm chậm!"}', p_name));
            )
        )
    );
    
    if (is_nether && is_mob && !is_boss_attack,
        if (rand(1.0) < 0.30,
            modify(player, 'effect', 'poison', 100, 0);
            modify(player, 'effect', 'slowness', 60, 0);
            modify(player, 'effect', 'wither', 60, 0);
            modify(player, 'fire', 100);
            run(str('title %s actionbar {"text":"§4§lCảnh báo: Bạn bị thiêu đốt và nguyền rủa bởi quái Nether!"}', p_name));
        )
    );
    
    if (is_end,
        if (is_mob && !is_boss_attack && rand(1.0) < 0.30,
            modify(player, 'effect', 'slowness', 60, 1);
            run(str('title %s actionbar {"text":"§5Bạn bị làm chậm cực độ (Slowness II)!"}', p_name));
        );
        if (is_bullet,
            modify(player, 'health', max(0.5, (player ~ 'health') - 1.0));
        )
    );
);

// ── SỰ KIỆN KHI NGƯỜI CHƠI TẤN CÔNG / GÂY SÁT THƯƠNG CHO QUÁI ──
__on_player_deals_damage(player, amount, entity) -> (
    if (entity == null, return());
    type = entity ~ 'type';
    
    // ── XỬ LÝ TOÀN DIỆN CƠ CHẾ BOSS WARDEN KHI BỊ NGƯỜI CHƠI TẤN CÔNG ──
    if (type == 'warden',
        hp = entity ~ 'health';
        max_hp = _get_attribute(entity, 'max_health', 1500.0);
        w_uuid = entity ~ 'uuid';
        w_pos = pos(entity);
        p_pos = pos(player);
        dist = _distance(p_pos, w_pos);
        global_warden_last_player_dmg_tick:w_uuid = global_tick_count;
        
        // 1. Kiểm tra trạng thái Huyết Tế Bất Tử (10s)
        is_channeling_heal = (global_warden_healing_ticks:w_uuid != null && global_warden_healing_ticks:w_uuid > 0);
        if (is_channeling_heal,
            modify(entity, 'nbt_merge', '{Invulnerable:1b}');
            sound('minecraft:item.shield.block', w_pos, 1.2, 0.8);
            run(str('particle minecraft:enchanted_hit %f %f %f 0.5 0.8 0.5 0.2 20', w_pos:0, w_pos:1 + 1.5, w_pos:2));
            run(str('title %s actionbar {"text":"§4§l[Bất Tử] Warden đang Huyết Tế (150-600 HP), miễn nhiễm mọi sát thương!"}', player ~ 'name'));
            return('cancel');
        );
        
        // 2. Kiểm tra kích hoạt Phase 2 (<= 30% Max HP / <= 450 HP)
        is_phase2 = global_warden_phase2:w_uuid || (hp / max_hp <= 0.30);
        if (is_phase2 && !global_warden_phase2:w_uuid,
            _trigger_warden_rage(entity, w_pos, w_uuid);
            is_phase2 = true;
        );
        
        // 3. Kiểm tra sát thương từ vật thể bắn / cung tên trong Phase 2
        holds = query(player, 'holds');
        held_item = if(holds != null && holds:0 != null, str(holds:0), '');
        is_ranged_item = (held_item ~ 'bow' || held_item ~ 'crossbow' || held_item ~ 'trident' || held_item ~ 'potion' || held_item ~ 'wind_charge');
        is_projectile = (is_ranged_item || dist > 6.5);
        
        resistance = 0.0;
        if (is_phase2 && is_projectile,
            // Phase 2: Kháng 100% sát thương từ vật thể bắn
            schedule(0, _(outer(entity), outer(hp)) -> (
                if (entity && !query(entity, 'removed'),
                    modify(entity, 'health', min(_get_warden_max_allowed_health(entity), hp));
                )
            ));
            sound('minecraft:item.shield.block', w_pos, 1.2, 0.8);
            run(str('particle minecraft:crit %f %f %f 0.5 0.5 0.5 0.2 15', w_pos:0, w_pos:1 + 1.5, w_pos:2));
            run(str('title %s actionbar {"text":"§c§l[Phase 2] Warden miễn nhiễm 100%% với vật thể bắn! Hãy cận chiến!"}', player ~ 'name'));
            return('cancel');
        , held_item ~ 'potion',
            resistance = 0.80; // Kháng 80% phép thuật / thuốc
        );
        
        actual_damage = amount * (1.0 - resistance);
        remaining_hp = hp - actual_damage;
        
        // 4. Kích hoạt Huyết Tế Tối Thượng (< 10% Max HP / < 150 HP trong Phase 2)
        if (is_phase2 && remaining_hp < (max_hp * 0.10) && !global_warden_emergency_healed:w_uuid,
            global_warden_emergency_healed:w_uuid = true;
            global_warden_healing_ticks:w_uuid = 200; // Hồi máu dần trong 10 giây (200 ticks)
            start_hp = max(150.0, remaining_hp);
            
            // KÍCH HOẠT BẤT TỬ TUYỆT ĐỐI NGAY LẬP TỨC TẠI ENGINE LEVEL
            modify(entity, 'nbt_merge', '{Invulnerable:1b}');
            
            temp_health = hp + amount + start_hp;
            run(str('attribute %s max_health base set %f', w_uuid, temp_health));
            modify(entity, 'health', temp_health);
            
            schedule(0, _(outer(entity), outer(max_hp), outer(start_hp), outer(w_uuid)) -> (
                if (entity && !query(entity, 'removed'),
                    run(str('attribute %s max_health base set %f', w_uuid, max_hp));
                    modify(entity, 'health', start_hp);
                    modify(entity, 'nbt_merge', '{Invulnerable:1b}');
                    
                    p_pos = pos(entity);
                    sound('minecraft:item.totem.use', p_pos, 2.0, 0.8);
                    sound('minecraft:entity.warden.heartbeat', p_pos, 2.0, 1.2);
                    sound('minecraft:entity.wither.spawn', p_pos, 1.5, 1.2);
                    run(str('particle minecraft:totem_of_undying %f %f %f 1.0 1.5 1.0 0.5 150', p_pos:0, p_pos:1 + 1.5, p_pos:2));
                    
                    run(str('title @a[x=%f,y=%f,z=%f,distance=..40] title {"text":"§4§l[HUYẾT TẾ TỐI THƯỢNG]","bold":true}', p_pos:0, p_pos:1, p_pos:2));
                    run(str('title @a[x=%f,y=%f,z=%f,distance=..40] subtitle {"text":"§cWarden giải phóng chướng khí (10s) & Hồi phục về 600 HP (40%% Máu)!"}', p_pos:0, p_pos:1, p_pos:2));
                    
                    _apply_warden_blood_sacrifice_debuffs(p_pos);
                    
                    for(player('all'),
                        p = _;
                        p_name = p ~ 'name';
                        if (_distance(pos(p), p_pos) <= 40,
                            run(str('execute as %s at @s run stopsound @s record', p_name));
                            run(str('execute as %s at @s run playsound minecraft:custom.warden_sacrifice record @s ~ ~ ~ 1000.0 1.0', p_name));
                            global_player_warden_music:p_name = 'sacrifice';
                            global_player_music_timer:p_name = 3500;
                            print(p, '§4§l[Warden] Kích hoạt Huyết Tế Tối Thượng! Chướng khí độc lan tỏa 40m và bắt đầu hấp thụ sinh lực về 600 HP (40% Máu)!');
                        )
                    );
                )
            ));
            return('cancel');
        );
        
        // 5. Kiểm tra rơi phần thưởng khi Warden bị tiêu diệt
        if (remaining_hp <= 0 && global_warden_emergency_healed:w_uuid && !global_warden_loot_dropped:w_uuid,
            global_warden_loot_dropped:w_uuid = true;
            _drop_warden_loot(w_pos, player);
        );
        
        // 6. Áp dụng giảm trừ sát thương thông thường
        if (resistance > 0.0,
            if (amount >= hp,
                if (actual_damage < hp,
                    temp_health = hp + amount;
                    run(str('attribute %s max_health base set %f', w_uuid, max(max_hp, temp_health)));
                    modify(entity, 'health', temp_health);
                    
                    schedule(0, _(outer(entity), outer(hp), outer(actual_damage), outer(max_hp), outer(w_uuid)) -> (
                        if (entity && !query(entity, 'removed'),
                            run(str('attribute %s max_health base set %f', w_uuid, max_hp));
                            modify(entity, 'health', max(0.5, hp - actual_damage));
                        )
                    ));
                )
            ,
                heal_back = amount * resistance;
                schedule(0, _(outer(entity), outer(heal_back)) -> (
                    if (entity && !query(entity, 'removed'),
                        curr_hp = entity ~ 'health';
                        modify(entity, 'health', min(_get_warden_max_allowed_health(entity), curr_hp + heal_back));
                    )
                ));
            )
        );
        return();
    );
    
    // ── XỬ LÝ DROP CHO CÁC QUÁI VẬT KHÁC (TRĂNG MÁU) ──
    if (entity ~ 'health' <= amount,
        is_hostile = query(entity, 'category') == 'hostile';
        
        if (is_hostile,
            day = floor(day_time() / 24000);
            daytime = day_time() % 24000;
            is_night = (daytime >= 12000 && daytime < 23000);
            is_blood_moon = (day == global_blood_moon_day && is_night);
            
            if (is_blood_moon,
                pos = pos(entity);
                
                extra_xp = _get_additional_xp(type);
                run(str('summon experience_orb %f %f %f {value:%d}', pos:0, pos:1, pos:2, extra_xp));
                
                r = rand(1.0);
                
                if (type ~ 'zombie' || type == 'husk' || type == 'drowned' || type == 'zombie_villager',
                    if (r < 0.10,
                        run(str('summon item %f %f %f {Item:{id:"minecraft:diamond",count:1}}', pos:0, pos:1, pos:2))
                    )
                );
                
                if (type == 'creeper',
                    if (r < 0.10,
                        run(str('summon item %f %f %f {Item:{id:"minecraft:tnt",count:1}}', pos:0, pos:1, pos:2))
                    )
                );
                
                if (type == 'enderman',
                    if (r < 0.10,
                        run(str('summon item %f %f %f {Item:{id:"minecraft:ender_eye",count:1}}', pos:0, pos:1, pos:2))
                    )
                );
                
                if (type == 'skeleton' || type == 'stray',
                    if (r < 0.05,
                        has_mending = rand(1.0) < 0.10;
                        if (has_mending,
                            run(str('summon item %f %f %f {Item:{id:"minecraft:bow",count:1,components:{"minecraft:enchantments":{"minecraft:power":5,"minecraft:punch":2,"minecraft:mending":1}}}}', pos:0, pos:1, pos:2))
                        ,
                            run(str('summon item %f %f %f {Item:{id:"minecraft:bow",count:1,components:{"minecraft:enchantments":{"minecraft:power":5,"minecraft:punch":2}}}}', pos:0, pos:1, pos:2))
                        )
                    )
                );
                
                if (type == 'spider' || type == 'cave_spider',
                    if (r < 0.10,
                        run(str('summon item %f %f %f {Item:{id:"minecraft:potion",count:1,components:{"minecraft:potion_contents":{potion:"minecraft:slow_falling"}}}}', pos:0, pos:1, pos:2))
                    )
                );
            )
        )
    );
);

// ── XỬ LÝ HẤP THỤ SINH LỰC KHI NGƯỜI CHƠI TỬ VONG GẦN WARDEN (+50 HP SIPHON, KHÔNG THÔNG BÁO) ──
__on_player_dies(player) -> (
    if (player == null, return());
    p_pos = pos(player);
    p_dim = player ~ 'dimension';
    
    // Tìm các Warden trong phạm vi 30 blocks
    nearby_wardens = filter(_get_all_wardens(), (_ ~ 'dimension') == p_dim && _distance(pos(_), p_pos) <= 30.0);
    for(nearby_wardens,
        w = _;
        w_uuid = w ~ 'uuid';
        w_pos = pos(w);
        w_max = _get_warden_max_allowed_health(w);
        curr_hp = w ~ 'health';
        
        // Hồi phục 50 HP (giới hạn cứng theo trần max_allowed: 600 HP trong Phase 2 / Rage / Sacrifice)
        new_hp = min(w_max, curr_hp + 50.0);
        modify(w, 'health', new_hp);
        
        // Hiệu ứng âm thanh và hạt linh hồn (Không gửi tin nhắn chat/actionbar)
        sound('minecraft:entity.warden.heartbeat', w_pos, 2.0, 1.2);
        sound('minecraft:entity.warden.roar', w_pos, 1.5, 1.0);
        run(str('particle minecraft:sculk_soul %f %f %f 0.8 1.2 0.8 0.1 30', w_pos:0, w_pos:1 + 1.5, w_pos:2));
        run(str('particle minecraft:totem_of_undying %f %f %f 0.6 1.0 0.6 0.2 40', w_pos:0, w_pos:1 + 1.5, w_pos:2));
    );
);

// Quét định kỳ mỗi tick / định kỳ để quản lý Warden, Nhạc nền BGM và Trăng Máu
global_tick_count = 0;

__on_tick() -> (
    global_tick_count = global_tick_count + 1;
    
    // 1. Quản lý đếm ngược cooldown kéo mục tiêu của Warden
    for(keys(global_warden_pull_cooldown),
        cd = global_warden_pull_cooldown:_;
        if (cd > 0,
            global_warden_pull_cooldown:_ = cd - 1;
        ,
            delete(global_warden_pull_cooldown:_);
        )
    );

    // 2. Quản lý đếm ngược thời lượng nhạc nền BGM của từng người chơi
    for(keys(global_player_music_timer),
        p_name = _;
        tmr = global_player_music_timer:p_name;
        if (tmr != null && tmr > 0,
            global_player_music_timer:p_name = tmr - 1;
        )
    );

    // 3. Lấy danh sách người chơi online & Warden
    players = player('all');
    wardens = _get_all_wardens();
    
    // Miễn nhiễm ngạt nước 100% cho Warden & Giữ trần máu 600 HP trong Phase 2/Rage/Huyết Tế
    for(wardens,
        modify(_, 'air', 300);
        w_allowed = _get_warden_max_allowed_health(_);
        if ((_ ~ 'health') > w_allowed,
            modify(_, 'health', w_allowed);
        );
    );
    
    // Dọn dẹp bộ nhớ UUID của Warden đã biến mất
    if (global_tick_count % 100 == 0,
        living_uuids = {};
        for(wardens, living_uuids:( _ ~ 'uuid' ) = true);
        for(keys(global_warden_phase2),
            if (!living_uuids:_, delete(global_warden_phase2:_));
        );
        for(keys(global_warden_emergency_healed),
            if (!living_uuids:_, delete(global_warden_emergency_healed:_));
        );
        for(keys(global_warden_healing_ticks),
            if (!living_uuids:_, delete(global_warden_healing_ticks:_));
        );
        for(keys(global_warden_loot_dropped),
            if (!living_uuids:_, delete(global_warden_loot_dropped:_));
        );
        for(keys(global_warden_melee_combo),
            if (!living_uuids:_, delete(global_warden_melee_combo:_));
        );
        for(keys(global_warden_sonic_charge_tick),
            if (!living_uuids:_, delete(global_warden_sonic_charge_tick:_));
        );
        for(keys(global_warden_golem_slam_cd),
            if (!living_uuids:_, delete(global_warden_golem_slam_cd:_));
        );
        for(keys(global_warden_last_health),
            if (!living_uuids:_, delete(global_warden_last_health:_));
        );
        for(keys(global_warden_last_player_dmg_tick),
            if (!living_uuids:_, delete(global_warden_last_player_dmg_tick:_));
        );
        for(keys(global_warden_golem_heal_cd),
            if (!living_uuids:_, delete(global_warden_golem_heal_cd:_));
        );
    );
    
    // 3.5. Giảm 90% sát thương từ sinh vật không phải người chơi trong trạng thái Cuồng Nộ (Rage / Phase 2)
    for(wardens,
        w = _;
        w_uuid = w ~ 'uuid';
        curr_hp = w ~ 'health';
        last_hp = global_warden_last_health:w_uuid;
        w_max = _get_warden_max_allowed_health(w);
        
        if (last_hp != null && curr_hp < last_hp,
            delta_dmg = last_hp - curr_hp;
            last_p_tick = global_warden_last_player_dmg_tick:w_uuid;
            is_player_dmg = (last_p_tick != null && (global_tick_count - last_p_tick <= 3));
            is_p2 = global_warden_phase2:w_uuid || (curr_hp / w_max <= 0.30) || (global_warden_emergency_healed:w_uuid == true);
            
            // Nếu trong Phase 2 / Rage và sát thương KHÔNG PHẢI do người chơi gây ra (Mob / Golem / Non-player)
            if (is_p2 && !is_player_dmg,
                // Giảm 90% sát thương (hoàn lại 90% lượng máu bị mất)
                refund_hp = delta_dmg * 0.90;
                new_hp = min(w_max, curr_hp + refund_hp);
                modify(w, 'health', new_hp);
                curr_hp = new_hp;
                
                w_pos = pos(w);
                sound('minecraft:item.shield.block', w_pos, 0.8, 1.2);
                run(str('particle minecraft:enchanted_hit %f %f %f 0.4 0.8 0.4 0.1 6', w_pos:0, w_pos:1 + 1.2, w_pos:2));
            );
        );
        global_warden_last_health:w_uuid = curr_hp;
    );
    
    // 4. Tiến trình hồi máu dần (10s = 200 ticks) khi Warden kích hoạt Huyết Tế Tối Thượng (150 -> 600 HP)
    for(wardens,
        w = _;
        w_uuid = w ~ 'uuid';
        heal_ticks = global_warden_healing_ticks:w_uuid;
        if (heal_ticks != null && heal_ticks > 0,
            global_warden_healing_ticks:w_uuid = heal_ticks - 1;
            curr_w_hp = w ~ 'health';
            w_max = _get_attribute(w, 'max_health', 1500.0);
            target_cap = w_max * 0.40; // 600.0 HP (40% Max HP)
            
            // Duy trì Bất Tử tuyệt đối (kháng 100% mọi nguồn sát thương)
            modify(w, 'nbt_merge', '{Invulnerable:1b}');
            
            if (curr_w_hp < target_cap,
                modify(w, 'health', min(target_cap, curr_w_hp + 2.25));
            );
            
            w_p = pos(w);
            if (heal_ticks % 4 == 0,
                run(str('particle minecraft:sculk_soul %f %f %f 0.6 0.8 0.6 0.05 10', w_p:0, w_p:1 + 1.2, w_p:2));
                run(str('particle minecraft:totem_of_undying %f %f %f 0.5 0.8 0.5 0.1 6', w_p:0, w_p:1 + 1.5, w_p:2));
            );
            // ── VÒNG XOÁY HUYẾT TẾ (SCULK SINGULARITY VORTEX) ──
            if (heal_ticks % 5 == 0,
                w_dim = w ~ 'dimension';
                for(players,
                    p = _;
                    if ((p ~ 'dimension') == w_dim && query(p, 'gamemode') == 'survival',
                        p_p = pos(p);
                        p_dist = _distance(p_p, w_p);
                        if (p_dist <= 40 && p_dist > 2.5,
                            p_dx = p_p:0 - w_p:0;
                            p_dy = p_p:1 - w_p:1;
                            p_dz = p_p:2 - w_p:2;
                            pull_force = min(1.2, max(0.4, 0.5 + (p_dist / 40.0) * 0.5));
                            modify(p, 'motion', -p_dx / p_dist * pull_force, -0.2, -p_dz / p_dist * pull_force);
                            run(str('particle minecraft:sculk_soul %f %f %f %f %f %f 0.1 8', p_p:0, p_p:1 + 1.0, p_p:2, -p_dx / p_dist, -p_dy / p_dist, -p_dz / p_dist));
                        );
                    );
                );
            );
            
            if (heal_ticks % 30 == 0,
                sound('minecraft:block.sculk_shrieker.shriek', w_p, 1.2, 0.8);
            );
            
            if (heal_ticks == 1,
                delete(global_warden_healing_ticks:w_uuid);
                // Kết thúc 10s Huyết Tế: Tắt Bất Tử, cho phép nhận sát thương trở lại
                modify(w, 'nbt_merge', '{Invulnerable:0b}');
                modify(w, 'health', target_cap);
                sound('minecraft:entity.warden.roar', w_p, 2.0, 1.0);
                run(str('title @a[x=%f,y=%f,z=%f,distance=..40] actionbar {"text":"§a§lQuá trình Huyết Tế hoàn tất! Warden đã phục hồi 600 HP (40%% Máu)!"}', w_p:0, w_p:1, w_p:2));
            );
        )
    );
    
    // 5. Phá block xung quanh Warden mỗi 5 ticks để tránh bị nhốt
    if (global_tick_count % 5 == 0,
        for(wardens,
            _warden_break_blocks(_);
        )
    );
    
    // 6. Quản lý Boss Health Bar, Phase 2 và Hệ Thống Nhạc Nền BGM Looping
    if (global_tick_count % 5 == 0,
        if (length(wardens) > 0,
            w = wardens:0;
            w_uuid = w ~ 'uuid';
            w_hp = w ~ 'health';
            w_pos = pos(w);
            w_max = _get_attribute(w, 'max_health', 1500.0);
            
            if (w_hp <= (w_max * 0.30) && !global_warden_phase2:w_uuid,
                _trigger_warden_rage(w, w_pos, w_uuid);
            );
            
            is_p2 = global_warden_phase2:w_uuid;
            
            if (is_p2 && w_hp < (w_max * 0.10) && w_hp > 0 && !global_warden_emergency_healed:w_uuid,
                global_warden_emergency_healed:w_uuid = true;
                global_warden_healing_ticks:w_uuid = 200; // 10 giây
                
                // KÍCH HOẠT BẤT TỬ TUYỆT ĐỐI NGAY LẬP TỨC
                modify(w, 'nbt_merge', '{Invulnerable:1b}');
                
                sound('minecraft:item.totem.use', w_pos, 2.0, 0.8);
                sound('minecraft:entity.warden.heartbeat', w_pos, 2.0, 1.2);
                sound('minecraft:entity.wither.spawn', w_pos, 1.5, 1.2);
                run(str('particle minecraft:totem_of_undying %f %f %f 1.0 1.5 1.0 0.5 150', w_pos:0, w_pos:1 + 1.5, w_pos:2));
                
                run(str('title @a[x=%f,y=%f,z=%f,distance=..40] title {"text":"§4§l[HUYẾT TẾ TỐI THƯỢNG]","bold":true}', w_pos:0, w_pos:1, w_pos:2));
                run(str('title @a[x=%f,y=%f,z=%f,distance=..40] subtitle {"text":"§cWarden giải phóng chướng khí (10s) & Hồi phục về 600 HP (40%% Máu)!"}', w_pos:0, w_pos:1, w_pos:2));
                
                _apply_warden_blood_sacrifice_debuffs(w_pos);
                
                for(players,
                    p = _;
                    p_name = p ~ 'name';
                    if (_distance(pos(p), w_pos) <= 40,
                        run(str('execute as %s at @s run stopsound @s record', p_name));
                        run(str('execute as %s at @s run playsound minecraft:custom.warden_sacrifice record @s ~ ~ ~ 1000.0 1.0', p_name));
                        global_player_warden_music:p_name = 'sacrifice';
                        global_player_music_timer:p_name = 3500;
                        print(p, '§4§l[Warden] Kích hoạt Huyết Tế Tối Thượng! Chướng khí độc lan tỏa 40m và bắt đầu hấp thụ sinh lực về 600 HP (40% Máu)!');
                    )
                );
            );
            
            // Cập nhật Bossbar
            if (is_p2,
                run('bossbar set minecraft:warden_boss name {"text":"Warden (Phase 2 - Cuồng Nộ)","color":"red","bold":true}');
                run('bossbar set minecraft:warden_boss color red');
            ,
                run('bossbar set minecraft:warden_boss name {"text":"Warden","color":"dark_aqua","bold":true}');
                run('bossbar set minecraft:warden_boss color blue');
            );
            
            run(str('bossbar set minecraft:warden_boss value %d', floor(w_hp)));
            run('bossbar set minecraft:warden_boss visible true');
            run(str('bossbar set minecraft:warden_boss players @a[x=%f,y=%f,z=%f,distance=..40]', w_pos:0, w_pos:1, w_pos:2));
            
            // ── QUẢN LÝ NHẠC NỀN BGM LOOPING CHO NGƯỜI CHƠI TRONG VÙNG 40M ──
            has_sacrificed = global_warden_emergency_healed:w_uuid;
            req_track = if(has_sacrificed, 'sacrifice', 'theme');
            req_sound = if(has_sacrificed, 'minecraft:custom.warden_sacrifice', 'minecraft:custom.warden_theme');
            track_dur = if(has_sacrificed, 3500, 4180);
            
            for(players,
                p = _;
                p_name = p ~ 'name';
                p_dist = _distance(pos(p), w_pos);
                curr_track = global_player_warden_music:p_name;
                tmr = global_player_music_timer:p_name;
                
                if (p_dist <= 40,
                    if (curr_track != req_track || tmr == null || tmr <= 0,
                        if (curr_track != null,
                            run(str('execute as %s at @s run stopsound @s record', p_name));
                        );
                        run(str('execute as %s at @s run playsound %s record @s ~ ~ ~ 1000.0 1.0', p_name, req_sound));
                        global_player_warden_music:p_name = req_track;
                        global_player_music_timer:p_name = track_dur;
                    );
                ,
                    if (curr_track != null,
                        run(str('execute as %s at @s run stopsound @s record minecraft:custom.warden_theme', p_name));
                        run(str('execute as %s at @s run stopsound @s record minecraft:custom.warden_sacrifice', p_name));
                        delete(global_player_warden_music:p_name);
                        delete(global_player_music_timer:p_name);
                    );
                );
            );
        ,
            run('bossbar set minecraft:warden_boss visible false');
            run('bossbar set minecraft:warden_boss players');
            
            for(keys(global_player_warden_music),
                p_name = _;
                run(str('execute as %s at @s run stopsound @s record minecraft:custom.warden_theme', p_name));
                run(str('execute as %s at @s run stopsound @s record minecraft:custom.warden_sacrifice', p_name));
                delete(global_player_warden_music:p_name);
                delete(global_player_music_timer:p_name);
            );
        )
    );
    
    for(players,
        p = _;
        p_name = p ~ 'name';
        p_pos = pos(p);
        
        // --- CƠ CHẾ ANTI-FLIGHT (TRỌNG LỰC CỰC ĐẠI) ---
        nearby_warden = false;
        for(wardens,
            w = _;
            if (_distance(p_pos, pos(w)) <= 40,
                nearby_warden = true;
                break();
            )
        );
        
        if (nearby_warden,
            is_flying = query(p, 'flying');
            is_gliding = (query(p, 'pose') == 'fall_flying');
            if (is_flying || is_gliding,
                if (is_flying, modify(p, 'flying', false));
                mot = query(p, 'motion');
                modify(p, 'motion', mot:0, -0.8, mot:2);
                
                run(str('title %s actionbar {"text":"§c§lTrọng Lực Cực Đại: Cấm Bay!"}', p_name));
            )
        );

        // --- CƠ CHẾ DEBUFF GIẢM HỒI MÁU & Countdown ---
        ticks = global_sonic_debuff:p_name;
        if (ticks != null,
            if (ticks > 1,
                global_sonic_debuff:p_name = ticks - 1;
            ,
                delete(global_sonic_debuff:p_name);
                print(p, '§a§l[Kháng hiệu ứng] Khả năng hồi máu đã trở lại bình thường.');
                sound('minecraft:entity.player.levelup', pos(p), 0.5, 1.5);
            )
        );
        
        curr_hp = p ~ 'health';
        prev_hp = global_player_last_health:p_name;
        
        if (prev_hp != null && prev_hp > 0 && curr_hp > prev_hp,
            if (global_sonic_debuff:p_name != null,
                heal_delta = curr_hp - prev_hp;
                new_hp = curr_hp - (heal_delta * 0.5);
                modify(p, 'health', new_hp);
                curr_hp = new_hp;
                
                if (rand(1.0) < 0.2,
                    run(str('title %s actionbar {"text":"§c§lGiảm Hồi Máu: -50%% Khả Năng Hồi Phục!"}', p_name));
                );
            )
        );
        
        global_player_last_health:p_name = curr_hp;
    );
    
    // --- CƠ CHẾ SCULK VACUUM (KÉO MỤC TIÊU) ---
    for(wardens,
        w = _;
        w_uuid = w ~ 'uuid';
        w_pos = pos(w);
        target = query(w, 'target');
        
        if (target && target ~ 'type' == 'player',
            p_pos = pos(target);
            dx = p_pos:0 - w_pos:0;
            dy = p_pos:1 - w_pos:1;
            dz = p_pos:2 - w_pos:2;
            dist_h = sqrt(dx*dx + dz*dz);
            
            if (dist_h > 16 || dy > 6,
                cd = global_warden_pull_cooldown:w_uuid;
                if (cd == null,
                    total_dist = sqrt(dx*dx + dy*dy + dz*dz);
                    if (total_dist > 0,
                        global_warden_pull_cooldown:w_uuid = 120;
                        
                        pull_strength = 1.2;
                        v_pull_x = -dx / total_dist * pull_strength;
                        v_pull_y = -dy / total_dist * pull_strength + 0.2;
                        v_pull_z = -dz / total_dist * pull_strength;
                        
                        modify(target, 'motion', v_pull_x, v_pull_y, v_pull_z);
                        
                        run(str('particle minecraft:sculk_soul %f %f %f 0.5 0.5 0.5 0.1 25', p_pos:0, p_pos:1, p_pos:2));
                        sound('minecraft:block.sculk_shrieker.shriek', p_pos, 1.5, 1.0);
                        run(str('title %s actionbar {"text":"§d§lSculk Vacuum: Bạn bị kéo về phía Warden!"}', target ~ 'name'));
                    )
                )
            )
        )
    );

    // --- CƠ CHẾ KHẮC CHẾ IRON GOLEM & MELEE SONIC BOOM (COMBO 3+1 / 2+1) ---
    for(wardens,
        w = _;
        w_uuid = w ~ 'uuid';
        w_pos = pos(w);
        w_dim = w ~ 'dimension';
        w_max = _get_warden_max_allowed_health(w);
        is_post_sacrifice = (global_warden_emergency_healed:w_uuid && (global_warden_healing_ticks:w_uuid == null || global_warden_healing_ticks:w_uuid <= 0));
        max_combo = if(is_post_sacrifice, 2, 3);
        
        // Giảm Cooldown hồi máu từ Iron Golem
        heal_cd = global_warden_golem_heal_cd:w_uuid;
        if (heal_cd != null && heal_cd > 0,
            global_warden_golem_heal_cd:w_uuid = heal_cd - 1;
        );
        
        // 1. Cú Đập Địa Chấn Khắc Chế Iron Golem (50 Sát Thương trong 8m, CD: 4s)
        slam_cd = global_warden_golem_slam_cd:w_uuid;
        if (slam_cd != null && slam_cd > 0,
            global_warden_golem_slam_cd:w_uuid = slam_cd - 1;
        );
        
        nearby_slam_golems = entity_area('iron_golem', w_pos, [8, 4, 8]);
        if (length(nearby_slam_golems) > 0 && (global_warden_golem_slam_cd:w_uuid == null || global_warden_golem_slam_cd:w_uuid <= 0),
            global_warden_golem_slam_cd:w_uuid = 80; // 4 giây hồi chiêu
            
            sound('minecraft:entity.warden.attack_impact', w_pos, 2.5, 0.7);
            sound('minecraft:entity.generic.explode', w_pos, 1.5, 1.2);
            run(str('particle minecraft:explosion %f %f %f 0 0 0 0 1', w_pos:0, w_pos:1 + 0.5, w_pos:2));
            run(str('particle minecraft:sculk_charge_pop %f %f %f 1.5 0.5 1.5 0.2 40', w_pos:0, w_pos:1 + 0.2, w_pos:2));
            
            for(nearby_slam_golems,
                g = _;
                g_pos = pos(g);
                gdx = g_pos:0 - w_pos:0;
                gdz = g_pos:2 - w_pos:2;
                gdist = max(0.1, sqrt(gdx*gdx + gdz*gdz));
                
                // Hất tung Iron Golem lên trời
                modify(g, 'motion', gdx / gdist * 0.6, 0.9, gdz / gdist * 0.6);
                
                // Gây 50 sát thương lên Iron Golem
                curr_ghp = g ~ 'health';
                new_ghp = max(0.0, curr_ghp - 50.0);
                modify(g, 'health', new_ghp);
                
                // Xử lý hồi máu cho Warden (5 HP, có Cooldown 1s / 20 ticks)
                if (global_warden_golem_heal_cd:w_uuid == null || global_warden_golem_heal_cd:w_uuid <= 0,
                    global_warden_golem_heal_cd:w_uuid = 20; // 1 giây Cooldown
                    modify(w, 'health', min(w_max, (w ~ 'health') + 5.0));
                    sound('minecraft:entity.warden.heartbeat', w_pos, 1.5, 1.3);
                    run(str('particle minecraft:sculk_soul %f %f %f 0.5 0.5 0.5 0.05 10', w_pos:0, w_pos:1 + 1.5, w_pos:2));
                );
                
                if (new_ghp <= 0 || query(g, 'removed'),
                    sound('minecraft:entity.warden.roar', w_pos, 2.0, 1.0);
                    run(str('particle minecraft:totem_of_undying %f %f %f 0.6 1.0 0.6 0.1 25', w_pos:0, w_pos:1 + 1.5, w_pos:2));
                );
                
                // Tích lũy điểm Combo đòn đánh
                combo = (global_warden_melee_combo:w_uuid || 0) + 1;
                if (combo >= max_combo,
                    global_warden_melee_combo:w_uuid = 0;
                    global_warden_sonic_charge_tick:w_uuid = 15;
                    sound('minecraft:entity.warden.sonic_charge', w_pos, 2.0, 1.0);
                ,
                    global_warden_melee_combo:w_uuid = combo;
                );
                
                global_golem_hp:(g ~ 'uuid') = new_ghp;
            );
        );
        
        // 2. Theo dõi và hấp thụ máu từ đòn cận chiến thường lên Iron Golem gần Warden (12m)
        golems = entity_area('iron_golem', w_pos, [12, 6, 12]);
        for(golems,
            g = _;
            g_uuid = g ~ 'uuid';
            curr_ghp = g ~ 'health';
            last_ghp = global_golem_hp:g_uuid;
            if (last_ghp == null, last_ghp = _get_attribute(g, 'max_health', 100.0));
            
            if (curr_ghp < last_ghp,
                // Xử lý hồi máu cho Warden (5 HP, có Cooldown 1s / 20 ticks)
                if (global_warden_golem_heal_cd:w_uuid == null || global_warden_golem_heal_cd:w_uuid <= 0,
                    global_warden_golem_heal_cd:w_uuid = 20; // 1 giây Cooldown
                    modify(w, 'health', min(w_max, (w ~ 'health') + 5.0));
                    sound('minecraft:entity.warden.heartbeat', w_pos, 1.5, 1.3);
                    run(str('particle minecraft:sculk_soul %f %f %f 0.5 0.5 0.5 0.05 10', w_pos:0, w_pos:1 + 1.5, w_pos:2));
                );
                
                if (curr_ghp <= 0 || query(g, 'removed'),
                    sound('minecraft:entity.warden.roar', w_pos, 2.0, 1.0);
                    run(str('particle minecraft:totem_of_undying %f %f %f 0.6 1.0 0.6 0.1 25', w_pos:0, w_pos:1 + 1.5, w_pos:2));
                );
                
                combo = (global_warden_melee_combo:w_uuid || 0) + 1;
                if (combo >= max_combo,
                    global_warden_melee_combo:w_uuid = 0;
                    global_warden_sonic_charge_tick:w_uuid = 15;
                    sound('minecraft:entity.warden.sonic_charge', w_pos, 2.0, 1.0);
                ,
                    global_warden_melee_combo:w_uuid = combo;
                );
            );
            global_golem_hp:g_uuid = curr_ghp;
        );
        
        // 3. Tiến trình nạp và bắn Sonic Boom cận chiến
        charge_ticks = global_warden_sonic_charge_tick:w_uuid;
        if (charge_ticks != null && charge_ticks > 0,
            global_warden_sonic_charge_tick:w_uuid = charge_ticks - 1;
            if (charge_ticks % 3 == 0,
                run(str('particle minecraft:sculk_charge_pop %f %f %f 0.3 0.3 0.3 0.05 5', w_pos:0, w_pos:1 + 1.6, w_pos:2));
            );
            
            if (charge_ticks == 1,
                delete(global_warden_sonic_charge_tick:w_uuid);
                tgt = query(w, 'target');
                if (tgt == null,
                    nearby_p = filter(players, _distance(pos(_), w_pos) <= 20 && (_ ~ 'dimension') == w_dim && query(_, 'gamemode') == 'survival');
                    tgt = if(length(nearby_p) > 0, nearby_p:0, if(length(golems) > 0, golems:0, null));
                );
                if (tgt != null,
                    _fire_warden_melee_sonic_boom(w, tgt);
                );
            );
        );
    );

    // 7. Quản lý Đêm Trăng Máu
    if (global_tick_count % 20 == 0,
        day = floor(day_time() / 24000);
        daytime = day_time() % 24000;
        is_night = (daytime >= 12000 && daytime < 23000);
        
        // Bắt đầu đêm Trăng Máu (Hoàng hôn đến đêm)
        if (day == global_blood_moon_day && is_night,
            if (!global_was_blood_moon_notified,
                for(players,
                    p = _;
                    sound('minecraft:entity.wither.spawn', pos(p), 1.0, 0.7);
                    sound('minecraft:ambient.cave', pos(p), 1.0, 0.5);
                    run(str('title %s subtitle {"text":"§oQuái vật bắt đầu cuồng nộ..."}', p ~ 'name'));
                    run(str('title %s title {"text":"§4§lĐÊM TRĂNG MÁU BẮT ĐẦU"}', p ~ 'name'));
                    print(p, '§4§l[Trăng Máu] Trăng máu đang lên... Bầu trời nhuộm sắc đỏ của sự cuồng nộ!');
                );
                global_was_blood_moon_notified = true;
            )
        );
        
        // Kết thúc đêm Trăng Máu khi trời sáng (hết đêm hoặc sang ngày mới)
        if (global_was_blood_moon_notified && !is_night,
            for(players,
                p = _;
                sound('minecraft:ui.toast.challenge_complete', pos(p), 0.8, 1.0);
                print(p, '§a§l[Yên bình] Đêm trăng máu đã qua. Sức mạnh quái vật trở lại bình thường.');
            );
            global_blood_moon_day = day + floor(rand(8)) + 8;
            _save_blood_moon_data();
            global_was_blood_moon_notified = false;
        );
        
        // Tự động lên lịch lại nếu ngày Trăng Máu đã bị trôi qua trong quá khứ
        if (day > global_blood_moon_day,
            global_blood_moon_day = day + floor(rand(8)) + 8;
            _save_blood_moon_data();
            global_was_blood_moon_notified = false;
        );
    )
);

// ── LỆNH KIỂM TRA DÀNH CHO ADMIN OP & SERVER CONSOLE ──

trigger_blood_moon() -> (
    run('time set 12000');
    current_day = floor(day_time() / 24000);
    global_blood_moon_day = current_day;
    global_was_blood_moon_notified = false;
    _save_blood_moon_data();
    msg = 'Đã kích hoạt Trăng Máu cho hôm nay và chuyển giờ về Hoàng Hôn (12000 ticks)!';
    for(player('all'), print(_, '§4§l[Trăng Máu] ' + msg));
    print('[Server Console] ' + msg);
);

test_warden_p2() -> (
    wardens = _get_all_wardens();
    if (length(wardens) == 0,
        msg = 'Không tìm thấy Warden nào trong toàn bộ thế giới!';
        for(player('all'), print(_, '§c' + msg));
        print('[Server Console] ' + msg);
        return();
    );
    w = wardens:0;
    modify(w, 'health', 460.0);
    msg = str('Đã đặt máu Warden (%s) về 460 HP (chuẩn bị kích hoạt Phase 2 khi xuống <= 450 HP / 30%% Máu)!', w ~ 'uuid');
    for(player('all'), print(_, '§a' + msg));
    print('[Server Console] ' + msg);
);

test_warden_heal() -> (
    wardens = _get_all_wardens();
    if (length(wardens) == 0,
        msg = 'Không tìm thấy Warden nào trong toàn bộ thế giới!';
        for(player('all'), print(_, '§c' + msg));
        print('[Server Console] ' + msg);
        return();
    );
    w = wardens:0;
    global_warden_phase2:(w ~ 'uuid') = true;
    delete(global_warden_emergency_healed:(w ~ 'uuid'));
    delete(global_warden_healing_ticks:(w ~ 'uuid'));
    modify(w, 'nbt_merge', '{Invulnerable:0b}');
    modify(w, 'health', 140.0);
    msg = str('Đã đặt Warden (%s) vào Phase 2 với 140 HP để kiểm tra cơ chế Huyết Tế (< 10%% / < 150 HP) hồi lên 600 HP trong 10s và nhạc Sacrifice!', w ~ 'uuid');
    for(player('all'), print(_, '§a' + msg));
    print('[Server Console] ' + msg);
);

status() -> (
    current_day = floor(day_time() / 24000);
    daytime = day_time() % 24000;
    is_night = (daytime >= 12000 && daytime < 23000);
    is_bm_now = (current_day == global_blood_moon_day && is_night);
    days_left = max(0, global_blood_moon_day - current_day);
    lines = [
        '--- Trạng thái Custom Mob Effects ---',
        str('Ngày hiện tại: %d (Thời gian ngày: %d)', current_day, daytime),
        str('Ngày Trăng Máu tiếp theo: %d (Còn %d ngày nữa)', global_blood_moon_day, days_left),
        str('Trăng Máu đang hoạt động: %s', if(is_bm_now, '§c§lĐANG CHẠY', '§7Không'))
    ];
    for(lines,
        line = _;
        for(player('all'), print(_, '§7' + line));
        print('[Server Console] ' + line);
    );
);
