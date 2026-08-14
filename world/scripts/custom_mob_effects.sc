// ==============================================================================
//              ✦ CUSTOM MOB EFFECTS & EVENTS SYSTEM ✦
//   Gây hiệu ứng bất lợi, buff quái Overworld/Nether/End và Đêm Trăng Máu
// ==============================================================================

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

// Hàm khởi tạo Bossbar cho Warden (Màu text xanh dark_aqua, màu thanh bossbar xanh blue)
_init_warden_bossbar() -> (
    run('bossbar add warden_boss {"text":"Warden","color":"dark_aqua","bold":true}');
    run('bossbar set minecraft:warden_boss max 1000');
    run('bossbar set minecraft:warden_boss color blue');
    run('bossbar set minecraft:warden_boss style progress');
    run('bossbar set minecraft:warden_boss visible false');
);

// Khởi chạy khi nạp script
_init_warden_bossbar();

// Tải dữ liệu lưu trữ ngày Trăng Máu
_load_blood_moon_data() -> (
    data = read_file('bloodmoon', 'json');
    if (data != null,
        global_blood_moon_day = data:'next_day';
    ,
        // Nếu chưa có file, lên lịch ngẫu nhiên từ 8 đến 15 ngày tới
        current_day = floor(time() / 24000);
        global_blood_moon_day = current_day + floor(rand(8)) + 8;
        _save_blood_moon_data();
    );
);

_save_blood_moon_data() -> (
    write_file('bloodmoon', 'json', {'next_day' -> global_blood_moon_day});
);

// Khởi chạy khi nạp script
_load_blood_moon_data();

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
    if (val != null, val, 2) // Mặc định cộng 2 XP cho các quái khác
);

// Helper gây chướng khí Huyết Tế Tối Thượng lên người chơi trong phạm vi 40m
_apply_warden_blood_sacrifice_debuffs(w_pos) -> (
    for(player('all'),
        p = _;
        if (distance(pos(p), w_pos) <= 40,
            modify(p, 'effect', 'nausea', 200, 1);    // Nausea II trong 10s (200 ticks)
            modify(p, 'effect', 'blindness', 200, 0); // Blindness trong 10s (200 ticks)
            modify(p, 'effect', 'poison', 200, 1);   // Poison II trong 10s (200 ticks)
            run(str('title %s actionbar {"text":"§4§lChướng Khí Huyết Tế: Nausea II, Blindness & Poison II trong 10s!"}', p ~ 'name'));
        )
    );
);

// Helper kích hoạt trạng thái Cuồng Nộ (RAGE / Phase 2) cho Warden
_trigger_warden_rage(w, w_pos, w_uuid) -> (
    global_warden_phase2:w_uuid = true;
    run(str('attribute %s minecraft:generic.movement_speed base set 0.45', w_uuid));
    
    // Hiệu ứng âm thanh gầm rú & hạt
    sound('minecraft:entity.warden.roar', w_pos, 2.5, 0.8);
    sound('minecraft:entity.ender_dragon.growl', w_pos, 2.0, 0.6);
    for(player('all'), sound('minecraft:entity.warden.roar', pos(_), 1.5, 0.8));
    
    run(str('particle minecraft:sculk_soul %f %f %f 1.0 1.0 1.0 0.2 60', w_pos:0, w_pos:1 + 1.5, w_pos:2));
    run(str('particle minecraft:soul_fire_flame %f %f %f 1.5 1.5 1.5 0.1 80', w_pos:0, w_pos:1 + 1.0, w_pos:2));
    
    // Thông báo màn hình Title & Subtitle theo đúng định dạng
    run('title @a subtitle {"text":"Warden đã bùng nổ năng lượng Sculk!","color":"dark_red","italic":true}');
    run('title @a title {"text":"CUỒNG NỘ (RAGE)!","color":"red","bold":true}');
    
    // Thông báo khung chat bằng tellraw theo đúng định dạng
    run('tellraw @a ["",{"text":"[WARNING] ","color":"dark_red","bold":true},{"text":"Warden ","color":"dark_aqua","bold":true},{"text":"đã rơi vào trạng thái ","color":"gray"},{"text":"CUỒNG NỘ (RAGE)!","color":"red","bold":true,"italic":true},{"text":"\\nSức mạnh Sculk bùng nổ, mọi đòn đánh giờ đây bỏ qua giáp!","color":"dark_purple"}]');
);

// Helper tạo rơi vật phẩm thần thoại và kho báu khi Warden bị tiêu diệt
_drop_warden_loot(w_pos, killer) -> (
    bx = w_pos:0;
    by = w_pos:1 + 0.5;
    bz = w_pos:2;
    
    // ── NHÓM 1: GUARANTEED 100% DROPS ──
    // 1. 1x Heavy Core (Lõi Nặng)
    run(str('summon item %f %f %f {Item:{id:"minecraft:heavy_core",count:1}}', bx, by, bz));
    
    // 2. 1 - 2x Nether Star (Sao Địa Ngục)
    star_count = if(rand(1.0) < 0.5, 1, 2);
    run(str('summon item %f %f %f {Item:{id:"minecraft:nether_star",count:%d}}', bx, by, bz, star_count));
    
    // 3. 1 - 2x Netherite Upgrade Template (Phôi Nâng Cấp Netherite)
    template_count = if(rand(1.0) < 0.5, 1, 2);
    run(str('summon item %f %f %f {Item:{id:"minecraft:netherite_upgrade_smithing_template",count:%d}}', bx, by, bz, template_count));
    
    // 4. Cơn mưa kinh nghiệm ~2000 XP (4 x 500 XP orbs)
    run(str('summon experience_orb %f %f %f {value:500}', bx, by, bz));
    run(str('summon experience_orb %f %f %f {value:500}', bx, by, bz));
    run(str('summon experience_orb %f %f %f {value:500}', bx, by, bz));
    run(str('summon experience_orb %f %f %f {value:500}', bx, by, bz));
    
    // ── NHÓM 2: TRANG BỊ THẦN THOẠI GOD GEAR (TỈ LỆ 20% RƠI 1 TRONG 3 MÓN) ──
    if (rand(1.0) < 0.20,
        god_gear_roll = floor(rand(3));
        if (god_gear_roll == 0,
            // 1. Lưỡi Đao Hư Không (Void Reaper - Netherite Scythe)
            run(str('summon item %f %f %f {Item:{id:"weaponsexpanded:netherite_scythe",count:1,components:{"minecraft:custom_name":\'{"text":"Lưỡi Đao Hư Không (Void Reaper)","color":"dark_purple","bold":true,"italic":false}\',"minecraft:lore":[\'{"text":"Được rèn từ mảnh vỡ xương sọ và linh hồn Sculk của Warden.","color":"gray","italic":true}\',\'{"text":"★ Trang Bị Thần Thoại (Mythic Tier) ★","color":"gold","bold":true}\'],"minecraft:enchantments":{levels:{"minecraft:sharpness":7,"minecraft:looting":4,"minecraft:sweeping_edge":4,"minecraft:unbreaking":5,"minecraft:mending":1}},"minecraft:rarity":"epic"}}}', bx, by, bz));
            run('tellraw @a ["",{"text":"[THẦN KHÍ XUẤT THẾ] ","color":"gold","bold":true},{"text":"Warden đã rơi ra bảo khí Thần Thoại: ","color":"yellow"},{"text":"Lưỡi Đao Hư Không (Void Reaper)!","color":"dark_purple","bold":true}]');
        , god_gear_roll == 1,
            // 2. Giáp Ngực Hư Vô (Sculk Carapace - Netherite Chestplate)
            run(str('summon item %f %f %f {Item:{id:"minecraft:netherite_chestplate",count:1,components:{"minecraft:custom_name":\'{"text":"Giáp Ngực Hư Vô (Sculk Carapace)","color":"dark_aqua","bold":true,"italic":false}\',"minecraft:lore":[\'{"text":"Lớp mai giáp hắc ám hấp thụ chấn động từ cõi chết.","color":"gray","italic":true}\',\'{"text":"★ Trang Bị Thần Thoại (Mythic Tier) ★","color":"gold","bold":true}\'],"minecraft:enchantments":{levels:{"minecraft:protection":6,"minecraft:thorns":4,"minecraft:unbreaking":5,"minecraft:mending":1}},"minecraft:rarity":"epic"}}}', bx, by, bz));
            run('tellraw @a ["",{"text":"[THẦN KHÍ XUẤT THẾ] ","color":"gold","bold":true},{"text":"Warden đã rơi ra bảo khí Thần Thoại: ","color":"yellow"},{"text":"Giáp Ngực Hư Vô (Sculk Carapace)!","color":"dark_aqua","bold":true}]');
        ,
            // 3. Ủng Bóng Ma (Ghost Walker Boots - Netherite Boots)
            run(str('summon item %f %f %f {Item:{id:"minecraft:netherite_boots",count:1,components:{"minecraft:custom_name":\'{"text":"Ủng Bóng Ma (Ghost Walker Boots)","color":"dark_aqua","bold":true,"italic":false}\',"minecraft:lore":[\'{"text":"Bước đi êm ái như bóng ma, lướt qua mọi cạm bẫy Deep Dark.","color":"gray","italic":true}\',\'{"text":"★ Trang Bị Thần Thoại (Mythic Tier) ★","color":"gold","bold":true}\'],"minecraft:enchantments":{levels:{"minecraft:protection":5,"minecraft:feather_falling":5,"minecraft:swift_sneak":5,"minecraft:soul_speed":3,"minecraft:depth_strider":3,"minecraft:mending":1}},"minecraft:rarity":"epic"}}}', bx, by, bz));
            run('tellraw @a ["",{"text":"[THẦN KHÍ XUẤT THẾ] ","color":"gold","bold":true},{"text":"Warden đã rơi ra bảo khí Thần Thoại: ","color":"yellow"},{"text":"Ủng Bóng Ma (Ghost Walker Boots)!","color":"dark_aqua","bold":true}]');
        );
    );
    
    // ── NHÓM 3: KHO BÁU & TÀI NGUYÊN (TỈ LỆ 50% MỖI MÓN) ──
    // 1. Táo Vàng Phù Phép (2 - 4 quả)
    if (rand(1.0) < 0.50,
        gap_count = floor(rand(3)) + 2; // 2, 3, hoặc 4
        run(str('summon item %f %f %f {Item:{id:"minecraft:enchanted_golden_apple",count:%d}}', bx, by, bz, gap_count));
    );
    
    // 2. 2x Netherite Ingot
    if (rand(1.0) < 0.50,
        run(str('summon item %f %f %f {Item:{id:"minecraft:netherite_ingot",count:2}}', bx, by, bz));
    );
    
    // 3. 2x Totem of Undying
    if (rand(1.0) < 0.50,
        run(str('summon item %f %f %f {Item:{id:"minecraft:totem_of_undying",count:2}}', bx, by, bz));
    );
    
    // 4. 1x Silence Armor Trim Smithing Template
    if (rand(1.0) < 0.50,
        run(str('summon item %f %f %f {Item:{id:"minecraft:silence_armor_trim_smithing_template",count:1}}', bx, by, bz));
    );
    
    // Hiệu ứng ăn mừng & âm thanh chiến thắng
    sound('minecraft:ui.toast.challenge_complete', w_pos, 2.0, 1.0);
    sound('minecraft:entity.player.levelup', w_pos, 2.0, 0.5);
    run(str('summon firework_rocket %f %f %f {LifeTime:20,FireworksItem:{id:"firework_rocket",count:1,components:{"minecraft:fireworks":{explosions:[{Shape:"large_ball",Colors:[16711680,65280,255],FadeColors:[16776960]}]}}}}', bx, by + 1, bz));
    
    // Thông báo toàn server đã hạ gục Warden
    killer_name = if(killer != null, killer ~ 'name', 'Dũng sĩ vô danh');
    run(str('tellraw @a ["",{"text":"[CHIẾN TÍCH] ","color":"green","bold":true},{"text":"%s ","color":"gold","bold":true},{"text":"đã tiêu diệt thành công ","color":"yellow"},{"text":"Chúa Tể Bóng Tối Warden!","color":"dark_aqua","bold":true}]', killer_name));
);

// Register handler cho Enderman spawn ở The End
entity_load_handler('enderman', _(e, new) -> (
    if (new && e ~ 'dimension' == 'minecraft:the_end',
        // Tăng max health gấp 1.5 lần (Base 40 -> 60)
        base_hp = attribute(e, 'generic.max_health');
        if (base_hp != null,
            new_hp = base_hp * 1.5;
            run(str('attribute %s minecraft:generic.max_health base set %f', e ~ 'uuid', new_hp));
            modify(e, 'health', new_hp);
        );
        
        // Tăng damage thêm 1.0 (Base 7 -> 8)
        base_dmg = attribute(e, 'generic.attack_damage');
        if (base_dmg != null,
            run(str('attribute %s minecraft:generic.attack_damage base set %f', e ~ 'uuid', base_dmg + 1.0));
        )
    )
));

// Register handler cho Shulker spawn ở The End
entity_load_handler('shulker', _(e, new) -> (
    if (new && e ~ 'dimension' == 'minecraft:the_end',
        // Tăng max health gấp 1.5 lần (Base 30 -> 45)
        base_hp = attribute(e, 'generic.max_health');
        if (base_hp != null,
            new_hp = base_hp * 1.5;
            run(str('attribute %s minecraft:generic.max_health base set %f', e ~ 'uuid', new_hp));
            modify(e, 'health', new_hp);
        )
    )
));

// Register handler cho Wither spawn (bất kể đâu)
entity_load_handler('wither', _(e, new) -> (
    if (new,
        run(str('attribute %s minecraft:generic.max_health base set 600', e ~ 'uuid'));
        modify(e, 'health', 600.0);
    )
));

// Register handler cho Ender Dragon spawn (bất kể đâu)
entity_load_handler('ender_dragon', _(e, new) -> (
    if (new,
        run(str('attribute %s minecraft:generic.max_health base set 700', e ~ 'uuid'));
        modify(e, 'health', 700.0);
    )
));

// Register handler cho Warden spawn (bất kể đâu)
entity_load_handler('warden', _(e, new) -> (
    if (new,
        run(str('attribute %s minecraft:generic.max_health base set 1000', e ~ 'uuid'));
        run(str('attribute %s minecraft:generic.movement_speed base set 0.30', e ~ 'uuid'));
        modify(e, 'health', 1000.0);
        
        // ── THÔNG BÁO GLOBAL TOÀN SERVER TRÊN KHUNG CHAT KHI WARDEN SPAWN ──
        w_pos = pos(e);
        dim = e ~ 'dimension';
        dim_name = if(dim == 'minecraft:overworld', 'Overworld', if(dim == 'minecraft:the_nether', 'Nether', 'The End'));
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

// Hook xử lý sát thương (Kháng sát thương của Warden, Phase 2 & Sát thương chuẩn Sonic Boom)
__on_damaged(entity, amount, source, attacking_entity) -> (
    // 1. Xử lý kháng sát thương, miễn ngạt và Phase 2 của Warden
    if (entity ~ 'type' == 'warden',
        if (source == 'drown',
            schedule(0, _(outer(entity), outer(amount)) -> (
                if (entity && !query(entity, 'removed'),
                    modify(entity, 'health', min(1000.0, (entity ~ 'health') + amount));
                )
            ));
            return();
        );

        hp = entity ~ 'health';
        max_hp = attribute(entity, 'generic.max_health');
        if (max_hp == null, max_hp = 1000.0);
        w_uuid = entity ~ 'uuid';
        w_pos = pos(entity);
        
        // ── MIỄN NHIỄM SÁT THƯƠNG TRONG QUÁ TRÌNH HUYẾT TẾ HỒI MÁU (100 -> 400 HP) ──
        is_channeling_heal = (global_warden_healing_ticks:w_uuid != null && global_warden_healing_ticks:w_uuid > 0);
        if (is_channeling_heal,
            if (amount >= hp,
                temp_health = hp + amount;
                run(str('attribute %s minecraft:generic.max_health base set %f', w_uuid, max(max_hp, temp_health)));
                modify(entity, 'health', temp_health);
                schedule(0, _(outer(entity), outer(hp), outer(max_hp), outer(w_uuid)) -> (
                    if (entity && !query(entity, 'removed'),
                        run(str('attribute %s minecraft:generic.max_health base set %f', w_uuid, max_hp));
                        modify(entity, 'health', hp);
                    )
                ));
            ,
                schedule(0, _(outer(entity), outer(amount), outer(max_hp)) -> (
                    if (entity && !query(entity, 'removed'),
                        curr_hp = entity ~ 'health';
                        modify(entity, 'health', min(max_hp, curr_hp + amount));
                    )
                ));
            );
            sound('minecraft:item.shield.block', w_pos, 1.2, 0.8);
            run(str('particle minecraft:enchanted_hit %f %f %f 0.5 0.8 0.5 0.2 20', w_pos:0, w_pos:1 + 1.5, w_pos:2));
            run(str('title @a[x=%f,y=%f,z=%f,distance=..35] actionbar {"text":"§4§l[Bất Tử] Warden đang Huyết Tế (100-400 HP), miễn nhiễm mọi sát thương!"}', w_pos:0, w_pos:1, w_pos:2));
            return();
        );
        
        // Kiểm tra trạng thái Phase 2 (< 30% Max HP = < 300 HP)
        is_phase2 = global_warden_phase2:w_uuid || (hp / max_hp <= 0.30);
        
        // Kích hoạt Phase 2 (RAGE) nếu vừa chạm mốc
        if (is_phase2 && !global_warden_phase2:w_uuid,
            _trigger_warden_rage(entity, w_pos, w_uuid);
        );

        // Kiểm tra loại sát thương từ vật thể bắn (Projectile)
        att_type = if(attacking_entity != null, attacking_entity ~ 'type', null);
        is_projectile_entity = (att_type == 'arrow' || att_type == 'spectral_arrow' || att_type == 'trident' || att_type == 'potion' || att_type == 'small_fireball' || att_type == 'fireball' || att_type == 'dragon_fireball' || att_type == 'wither_skull' || att_type == 'shulker_bullet' || att_type == 'wind_charge' || att_type == 'breeze_wind_charge' || att_type == 'egg' || att_type == 'snowball' || att_type == 'firework_rocket');
        is_projectile_source = (source == 'arrow' || source == 'trident' || source == 'thrown' || source == 'fireball' || source == 'small_fireball' || source == 'wither_skull' || source == 'wind_charge' || source == 'breeze_wind_charge' || source == 'fireworks');
        is_projectile = is_projectile_entity || is_projectile_source || (is_phase2 && source == 'indirect_magic');
        
        is_magic = (source == 'magic' || source == 'indirect_magic');
        resistance = 0.0;
        
        if (is_phase2 && is_projectile,
            // Phase 2: Kháng 100% sát thương từ vật thể bắn
            resistance = 1.0;
            sound('minecraft:item.shield.block', w_pos, 1.2, 0.8);
            run(str('particle minecraft:crit %f %f %f 0.5 0.5 0.5 0.2 15', w_pos:0, w_pos:1 + 1.5, w_pos:2));
            run(str('title @a[x=%f,y=%f,z=%f,distance=..35] actionbar {"text":"§c§l[Phase 2] Warden miễn nhiễm 100%% với vật thể bắn! Hãy cận chiến!"}', w_pos:0, w_pos:1, w_pos:2));
        , is_magic,
            resistance = 0.8; // Kháng 80% thuốc instant damage / phép thuật
        ,
            ratio = hp / max_hp;
            if (ratio > 0.7,
                resistance = 0.3;
            , ratio < 0.5,
                resistance = 0.5;
            )
        );
        
        // Tính toán sát thương thực tế
        actual_damage = amount * (1 - resistance);
        remaining_hp = hp - actual_damage;
        
        // Kiểm tra cơ chế Hồi máu khẩn cấp Phase 2 (< 10% HP -> kích hoạt Huyết Tế Tối Thượng hồi lên 400 HP trong 10s)
        if (is_phase2 && remaining_hp < (max_hp * 0.10) && !global_warden_emergency_healed:w_uuid,
            global_warden_emergency_healed:w_uuid = true;
            global_warden_healing_ticks:w_uuid = 200; // Hồi máu dần trong 10 giây (200 ticks)
            
            // Đặt máu khởi điểm (tối thiểu 100 HP để bắt đầu quá trình hồi máu 10s)
            start_hp = max(100.0, remaining_hp);
            
            // Chống chết sốc ở tick hiện tại: buff tạm máu để sống sót
            temp_health = hp + amount + start_hp;
            run(str('attribute %s minecraft:generic.max_health base set %f', w_uuid, temp_health));
            modify(entity, 'health', temp_health);
            
            schedule(0, _(outer(entity), outer(max_hp), outer(start_hp), outer(w_uuid)) -> (
                if (entity && !query(entity, 'removed'),
                    run(str('attribute %s minecraft:generic.max_health base set %f', w_uuid, max_hp));
                    modify(entity, 'health', start_hp);
                    
                    p_pos = pos(entity);
                    sound('minecraft:item.totem.use', p_pos, 2.0, 0.8);
                    sound('minecraft:entity.warden.heartbeat', p_pos, 2.0, 1.2);
                    sound('minecraft:entity.wither.spawn', p_pos, 1.5, 1.2);
                    run(str('particle minecraft:totem_of_undying %f %f %f 1.0 1.5 1.0 0.5 150', p_pos:0, p_pos:1 + 1.5, p_pos:2));
                    
                    run(str('title @a[x=%f,y=%f,z=%f,distance=..40] title {"text":"§4§l[HUYẾT TẾ TỐI THƯỢNG]","bold":true}', p_pos:0, p_pos:1, p_pos:2));
                    run(str('title @a[x=%f,y=%f,z=%f,distance=..40] subtitle {"text":"§cWarden giải phóng chướng khí (10s) & Hồi phục về 40%% Máu!"}', p_pos:0, p_pos:1, p_pos:2));
                    
                    // Gây hiệu ứng Nausea II, Blindness, Poison II trong 10s (40 blocks)
                    _apply_warden_blood_sacrifice_debuffs(p_pos);
                    
                    // Chuyển sang nhạc Huyết Tế ngay lập tức cho người chơi trong 40m
                    for(player('all'),
                        p = _;
                        p_name = p ~ 'name';
                        if (distance(pos(p), p_pos) <= 40,
                            run(str('stopsound %s record', p_name));
                            run(str('playsound minecraft:custom.warden_sacrifice record %s ~ ~ ~ 1.0 1.0', p_name));
                            global_player_warden_music:p_name = 'sacrifice';
                            global_player_music_timer:p_name = 3500;
                            print(p, '§4§l[Warden] Kích hoạt Huyết Tế Tối Thượng! Chướng khí độc lan tỏa 40m và bắt đầu hấp thụ sinh lực về 40% Máu!');
                        )
                    );
                )
            ));
            return();
        );
        
        // Kiểm tra xem Warden có chết do đòn đánh này không để kích hoạt phần thưởng
        if (remaining_hp <= 0 && global_warden_emergency_healed:w_uuid && !global_warden_loot_dropped:w_uuid,
            global_warden_loot_dropped:w_uuid = true;
            killer_entity = if(attacking_entity ~ 'type' == 'player', attacking_entity, null);
            _drop_warden_loot(w_pos, killer_entity);
        );
        
        // Xử lý giảm trừ sát thương thông thường
        if (resistance > 0.0,
            if (amount >= hp,
                if (actual_damage < hp,
                    temp_health = hp + amount;
                    run(str('attribute %s minecraft:generic.max_health base set %f', entity ~ 'uuid', max(max_hp, temp_health)));
                    modify(entity, 'health', temp_health);
                    
                    schedule(0, _(outer(entity), outer(hp), outer(actual_damage), outer(max_hp)) -> (
                        if (entity && !query(entity, 'removed'),
                            run(str('attribute %s minecraft:generic.max_health base set %f', entity ~ 'uuid', max_hp));
                            modify(entity, 'health', max(0.5, hp - actual_damage));
                        )
                    ));
                )
            ,
                heal_back = amount * resistance;
                schedule(0, _(outer(entity), outer(heal_back), outer(max_hp)) -> (
                    if (entity && !query(entity, 'removed'),
                        curr_hp = entity ~ 'health';
                        modify(entity, 'health', min(max_hp, curr_hp + heal_back));
                    )
                ));
            )
        )
    );

    // 2. Xử lý sát thương Sonic Boom của Warden lên Player
    if (entity ~ 'type' == 'player' && source == 'sonic_boom',
        player = entity;
        p_name = player ~ 'name';
        hp = player ~ 'health';
        max_hp = attribute(player, 'generic.max_health');
        if (max_hp == null, max_hp = 20.0);
        
        // Kiểm tra xem Warden gây ra Sonic Boom có đang ở Phase 2 không
        is_phase2 = false;
        if (attacking_entity != null && attacking_entity ~ 'type' == 'warden',
            w_uuid = attacking_entity ~ 'uuid';
            is_phase2 = (global_warden_phase2:w_uuid || (attacking_entity ~ 'health') <= 300);
        ,
            // Quét tìm Warden gần player trong phạm vi 40m
            p_pos = pos(player);
            for(entity_list('warden'),
                w = _;
                if (distance(p_pos, pos(w)) <= 40,
                    w_uuid = w ~ 'uuid';
                    if (global_warden_phase2:w_uuid || (w ~ 'health') <= 300,
                        is_phase2 = true;
                        break();
                    )
                )
            )
        );
        
        // Phase 2: 45% máu tối đa | Phase 1: 33% máu tối đa
        sonic_ratio = if(is_phase2, 0.45, 0.33);
        true_damage = max_hp * sonic_ratio;
        target_hp = max(0, hp - true_damage);
        
        // Áp dụng debuff giảm hồi máu 50% trong 5 giây (100 ticks)
        global_sonic_debuff:p_name = 100;
        if (is_phase2,
            run(str('title %s actionbar {"text":"§4§lTrúng Sonic Boom Phase 2: Nhận 45%% Sát thương chuẩn & Giảm hồi máu 50%%!"}', p_name));
        ,
            run(str('title %s actionbar {"text":"§c§lBị trúng Sóng Âm: Nhận sát thương chuẩn & Giảm hồi máu 50%%!"}', p_name));
        );
        sound('minecraft:entity.warden.sonic_boom', pos(player), 1.0, if(is_phase2, 1.2, 1.0));
        
        if (target_hp <= 0,
            return();
        );
        
        if (amount >= hp,
            temp_health = hp + amount;
            run(str('attribute %s minecraft:generic.max_health base set %f', player ~ 'uuid', max(max_hp, temp_health)));
            modify(player, 'health', temp_health);
            
            schedule(0, _(outer(player), outer(target_hp), outer(max_hp)) -> (
                if (player && !query(player, 'removed'),
                    run(str('attribute %s minecraft:generic.max_health base set %f', player ~ 'uuid', max_hp));
                    modify(player, 'health', target_hp);
                )
            ));
        ,
            schedule(0, _(outer(player), outer(target_hp)) -> (
                if (player && !query(player, 'removed'),
                    modify(player, 'health', target_hp);
                )
            ));
        )
    );
);

// Register handler cho quái thường spawn ở Overworld (để phục vụ Trăng Máu)
entity_load_handler('monster', _(e, new) -> (
    if (new && e ~ 'dimension' == 'minecraft:overworld' && e ~ 'type' != 'wither' && e ~ 'type' != 'ender_dragon',
        day = floor(time() / 24000);
        daytime = time() % 24000;
        is_night = (daytime >= 12000 && daytime < 23000);
        
        if (day == global_blood_moon_day && is_night,
            // 1. Nhân 2.5x máu
            base_hp = attribute(e, 'generic.max_health');
            if (base_hp != null,
                new_hp = base_hp * 2.5;
                run(str('attribute %s minecraft:generic.max_health base set %f', e ~ 'uuid', new_hp));
                modify(e, 'health', new_hp);
            );
            
            // 2. Tăng 30% tốc độ di chuyển
            base_speed = attribute(e, 'generic.movement_speed');
            if (base_speed != null,
                new_speed = base_speed * 1.3;
                run(str('attribute %s minecraft:generic.movement_speed base set %f', e ~ 'uuid', new_speed));
            )
        )
    )
));

// Event hook khi người chơi nhận sát thương
__on_player_takes_damage(player, amount, source, source_entity) -> (
    if (source_entity == null, return());
    
    type = source_entity ~ 'type';
    
    // --- CƠ CHẾ PHẢN HIỆU ỨNG XẤU CỦA WARDEN ---
    is_warden_source = (type == 'warden');
    if (is_warden_source,
        effects = query(source_entity, 'effect');
        if (effects != null,
            for(effects,
                effect_info = _;
                effect_name = effect_info:0;
                amp = effect_info:1;
                dur = effect_info:2;
                
                // Nếu là hiệu ứng xấu
                if (global_negative_effects:effect_name,
                    // Phản lại hiệu ứng xấu với duration max 5 giây (100 ticks) hoặc bằng thời gian còn lại của Warden
                    apply_dur = min(100, if(dur == -1, 100, dur));
                    modify(player, 'effect', effect_name, apply_dur, amp);
                    
                    if (rand(1.0) < 0.3,
                        run(str('title %s actionbar {"text":"§c§lWarden phản lại hiệu ứng xấu: %s!"}', player ~ 'name', effect_name));
                    );
                )
            )
        )
    );

    is_mob = query(source_entity, 'category') == 'hostile' || type == 'wither' || type == 'ender_dragon';
    is_bullet = (type == 'shulker_bullet');
    is_boss_attack = (type == 'wither' || type == 'wither_skull' || type == 'ender_dragon' || type == 'dragon_fireball');
    is_wither_attack = (type == 'wither' || type == 'wither_skull');
    
    if (!is_mob && !is_bullet && !is_boss_attack, return());

    dim = player ~ 'dimension';
    
    // ── TĂNG SÁT THƯƠNG BOSS TẠI NETHER VÀ THE END (+1 damage = -1 HP trực tiếp) ──
    if (is_boss_attack && !is_wither_attack && (dim == 'minecraft:the_nether' || dim == 'minecraft:the_end'),
        modify(player, 'health', max(0, (player ~ 'health') - 1.0))
    );

    // ── SÁT THƯƠNG CHUẨN WITHER (2 HP = 1 tim trực tiếp) ──
    if (is_wither_attack,
        modify(player, 'health', max(0, (player ~ 'health') - 2.0));
        run(str('title %s actionbar {"text":"§4§lBị tấn công bởi Wither: Nhận 2 sát thương chuẩn! (Bỏ qua giáp)"}', player ~ 'name'));
    );

    // ── XỬ LÝ TẠI OVERWORLD ──
    if (dim == 'minecraft:overworld' && is_mob && !is_boss_attack,
        day = floor(time() / 24000);
        daytime = time() % 24000;
        is_night = (daytime >= 12000 && daytime < 23000);
        
        is_blood_moon = (day == global_blood_moon_day && is_night);
        
        if (is_blood_moon,
            // Đêm Trăng Máu: tăng 2 sát thương (-2 HP trực tiếp)
            modify(player, 'health', max(0, (player ~ 'health') - 2.0));
            
            // 50% gây mù Blindness II (3s = 60 ticks)
            if (rand(1.0) < 0.50,
                entity_status_effect(player, 'blindness', 60, 1, true, true);
                run(str('title %s actionbar {"text":"§4§lTrăng Máu: Bạn bị mù quáng (Blindness II)!"}', player ~ 'name'));
            )
        );
        
        // Giữ nguyên hiệu ứng poison & slow bình thường (Day: 15%, Night: 30%)
        chance = if(is_night, 0.30, 0.15);
        if (rand(1.0) < chance,
            entity_status_effect(player, 'poison', 100, 0, true, true);
            entity_status_effect(player, 'slowness', 60, 0, true, true);
            if (!is_blood_moon, // Tránh đè chữ actionbar trăng máu
                run(str('title %s actionbar {"text":"§cBạn bị nhiễm độc và làm chậm!"}', player ~ 'name'));
            );
        )
    );

    // ── XỬ LÝ TẠI NETHER ──
    if (dim == 'minecraft:the_nether' && is_mob && !is_boss_attack,
        if (rand(1.0) < 0.30,
            entity_status_effect(player, 'poison', 100, 0, true, true);
            entity_status_effect(player, 'slowness', 60, 0, true, true);
            entity_status_effect(player, 'wither', 60, 0, true, true);
            modify(player, 'fire', 100);
            run(str('title %s actionbar {"text":"§4§lCảnh báo: Bạn bị thiêu đốt và nguyền rủa bởi quái Nether!"}', player ~ 'name'));
        )
    );

    // ── XỬ LÝ TẠI THE END ──
    if (dim == 'minecraft:the_end',
        if (is_mob && !is_boss_attack && rand(1.0) < 0.30,
            entity_status_effect(player, 'slowness', 60, 1, true, true);
            run(str('title %s actionbar {"text":"§5Bạn bị làm chậm cực độ (Slowness II)!"}', player ~ 'name'));
        );
        
        if (is_bullet,
            modify(player, 'health', max(0, (player ~ 'health') - 1.0))
        )
    );
);

// Event hook khi người chơi gây sát thương (dùng để bắt khoảnh khắc quái chết)
__on_player_deals_damage(player, amount, entity) -> (
    // Kiểm tra xem quái có bị chết do đòn đánh này không
    if (entity ~ 'health' <= amount,
        type = entity ~ 'type';
        
        // ── XỬ LÝ DROP CHO BOSS WARDEN ──
        if (type == 'warden',
            w_uuid = entity ~ 'uuid';
            if (!global_warden_loot_dropped:w_uuid,
                global_warden_loot_dropped:w_uuid = true;
                _drop_warden_loot(pos(entity), player);
            );
            return();
        );
        
        is_hostile = query(entity, 'category') == 'hostile';
        
        if (is_hostile,
            day = floor(time() / 24000);
            daytime = time() % 24000;
            is_night = (daytime >= 12000 && daytime < 23000);
            is_blood_moon = (day == global_blood_moon_day && is_night);
            
            if (is_blood_moon,
                pos = pos(entity);
                
                // 1. Tăng 50% XP rơi ra (triệu hồi XP Orbs bổ sung)
                extra_xp = _get_additional_xp(type);
                run(str('summon experience_orb %f %f %f {value:%d}', pos:0, pos:1, pos:2, extra_xp));
                
                // Tỉ lệ r cho ngẫu nhiên drop
                r = rand(1.0);
                
                // Zombie: 10% rơi ra 1 Kim cương
                if (type ~ 'zombie' || type == 'husk' || type == 'drowned' || type == 'zombie_villager',
                    if (r < 0.10,
                        run(str('summon item %f %f %f {Item:{id:"minecraft:diamond",count:1}}', pos:0, pos:1, pos:2))
                    )
                );
                
                // Creeper: 10% rơi ra 1 TNT
                if (type == 'creeper',
                    if (r < 0.10,
                        run(str('summon item %f %f %f {Item:{id:"minecraft:tnt",count:1}}', pos:0, pos:1, pos:2))
                    )
                );
                
                // Enderman: 10% rơi ra 1 Eye of Ender
                if (type == 'enderman',
                    if (r < 0.10,
                        run(str('summon item %f %f %f {Item:{id:"minecraft:ender_eye",count:1}}', pos:0, pos:1, pos:2))
                    )
                );
                
                // Skeleton: 5% rơi ra cung xịn
                if (type == 'skeleton' || type == 'stray',
                    if (r < 0.05,
                        // Trong 5% đó, có 10% tỉ lệ có thêm mending
                        has_mending = rand(1.0) < 0.10;
                        if (has_mending,
                            run(str('summon item %f %f %f {Item:{id:"minecraft:bow",count:1,components:{"minecraft:enchantments":{levels:{"minecraft:power":5,"minecraft:punch":2,"minecraft:mending":1}}}}}', pos:0, pos:1, pos:2))
                        ,
                            run(str('summon item %f %f %f {Item:{id:"minecraft:bow",count:1,components:{"minecraft:enchantments":{levels:{"minecraft:power":5,"minecraft:punch":2}}}}}', pos:0, pos:1, pos:2))
                        )
                    )
                );
                
                // Spider: 10% rơi ra thuốc rơi chậm (Slow Falling Potion)
                if (type == 'spider' || type == 'cave_spider',
                    if (r < 0.10,
                        run(str('summon item %f %f %f {Item:{id:"minecraft:potion",count:1,components:{"minecraft:potion_contents":{potion:"minecraft:slow_falling"}}}}', pos:0, pos:1, pos:2))
                    )
                );
            )
        )
    )
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
    wardens = entity_list('warden');
    
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
    );
    
    // 4. Tiến trình hồi máu dần (10s = 200 ticks) khi Warden kích hoạt Huyết Tế Tối Thượng (100 -> 400 HP)
    for(wardens,
        w = _;
        w_uuid = w ~ 'uuid';
        heal_ticks = global_warden_healing_ticks:w_uuid;
        if (heal_ticks != null && heal_ticks > 0,
            global_warden_healing_ticks:w_uuid = heal_ticks - 1;
            curr_w_hp = w ~ 'health';
            w_max = attribute(w, 'generic.max_health');
            if (w_max == null, w_max = 1000.0);
            target_cap = w_max * 0.40; // 400.0 HP (40% Max HP)
            
            // Hồi 1.5 HP mỗi tick (Tổng 300 HP trong 200 ticks = 10 giây)
            if (curr_w_hp < target_cap,
                modify(w, 'health', min(target_cap, curr_w_hp + 1.5));
            );
            
            w_p = pos(w);
            // Hiệu ứng hạt linh hồn Sculk và Totem định kỳ
            if (heal_ticks % 4 == 0,
                run(str('particle minecraft:sculk_soul %f %f %f 0.6 0.8 0.6 0.05 10', w_p:0, w_p:1 + 1.2, w_p:2));
                run(str('particle minecraft:totem_of_undying %f %f %f 0.5 0.8 0.5 0.1 6', w_p:0, w_p:1 + 1.5, w_p:2));
            );
            // Tiếng nhịp tim dồn dập mỗi giây (20 ticks)
            if (heal_ticks % 20 == 0,
                sound('minecraft:entity.warden.heartbeat', w_p, 1.5, 1.3);
            );
            
            if (heal_ticks == 1,
                delete(global_warden_healing_ticks:w_uuid);
                sound('minecraft:entity.warden.roar', w_p, 2.0, 1.0);
                run(str('title @a[x=%f,y=%f,z=%f,distance=..40] actionbar {"text":"§a§lQuá trình Huyết Tế hoàn tất! Warden đã phục hồi 400 HP (40%% Máu)!"}', w_p:0, w_p:1, w_p:2));
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
            w_max = attribute(w, 'generic.max_health');
            if (w_max == null, w_max = 1000.0);
            
            // Check kích hoạt Phase 2 (RAGE) nếu máu <= 30% (<= 300 HP)
            if (w_hp <= (w_max * 0.30) && !global_warden_phase2:w_uuid,
                _trigger_warden_rage(w, w_pos, w_uuid);
            );
            
            is_p2 = global_warden_phase2:w_uuid;
            
            // Check Emergency Heal nếu máu < 10% trong Phase 2 (nếu bị giảm máu ngoài hook damage)
            if (is_p2 && w_hp < (w_max * 0.10) && w_hp > 0 && !global_warden_emergency_healed:w_uuid,
                global_warden_emergency_healed:w_uuid = true;
                global_warden_healing_ticks:w_uuid = 200; // 10 giây
                
                sound('minecraft:item.totem.use', w_pos, 2.0, 0.8);
                sound('minecraft:entity.warden.heartbeat', w_pos, 2.0, 1.2);
                sound('minecraft:entity.wither.spawn', w_pos, 1.5, 1.2);
                run(str('particle minecraft:totem_of_undying %f %f %f 1.0 1.5 1.0 0.5 150', w_pos:0, w_pos:1 + 1.5, w_pos:2));
                
                run(str('title @a[x=%f,y=%f,z=%f,distance=..40] title {"text":"§4§l[HUYẾT TẾ TỐI THƯỢNG]","bold":true}', w_pos:0, w_pos:1, w_pos:2));
                run(str('title @a[x=%f,y=%f,z=%f,distance=..40] subtitle {"text":"§cWarden giải phóng chướng khí (10s) & Hồi phục về 40%% Máu!"}', w_pos:0, w_pos:1, w_pos:2));
                
                _apply_warden_blood_sacrifice_debuffs(w_pos);
                
                for(players,
                    p = _;
                    p_name = p ~ 'name';
                    if (distance(pos(p), w_pos) <= 40,
                        run(str('stopsound %s record', p_name));
                        run(str('playsound minecraft:custom.warden_sacrifice record %s ~ ~ ~ 1.0 1.0', p_name));
                        global_player_warden_music:p_name = 'sacrifice';
                        global_player_music_timer:p_name = 3500;
                        print(p, '§4§l[Warden] Kích hoạt Huyết Tế Tối Thượng! Chướng khí độc lan tỏa 40m và bắt đầu hấp thụ sinh lực về 40% Máu!');
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
            track_dur = if(has_sacrificed, 3500, 4180); // 175s hoặc 209s
            
            for(players,
                p = _;
                p_name = p ~ 'name';
                p_dist = distance(pos(p), w_pos);
                curr_track = global_player_warden_music:p_name;
                tmr = global_player_music_timer:p_name;
                
                if (p_dist <= 40,
                    // Nếu chưa phát nhạc hoặc nhạc đã hết giờ (cần loop) hoặc cần chuyển sang bài Huyết Tế
                    if (curr_track != req_track || tmr == null || tmr <= 0,
                        if (curr_track != null,
                            run(str('stopsound %s record', p_name));
                        );
                        run(str('playsound %s record %s ~ ~ ~ 1.0 1.0', req_sound, p_name));
                        global_player_warden_music:p_name = req_track;
                        global_player_music_timer:p_name = track_dur;
                    );
                ,
                    // Người chơi ra ngoài phạm vi 40m: dừng nhạc
                    if (curr_track != null,
                        run(str('stopsound %s record minecraft:custom.warden_theme', p_name));
                        run(str('stopsound %s record minecraft:custom.warden_sacrifice', p_name));
                        delete(global_player_warden_music:p_name);
                        delete(global_player_music_timer:p_name);
                    );
                );
            );
        ,
            // Không có Warden nào trong thế giới: Tắt Bossbar và dừng toàn bộ nhạc Warden
            run('bossbar set minecraft:warden_boss visible false');
            run('bossbar set minecraft:warden_boss players');
            
            for(keys(global_player_warden_music),
                p_name = _;
                run(str('stopsound %s record minecraft:custom.warden_theme', p_name));
                run(str('stopsound %s record minecraft:custom.warden_sacrifice', p_name));
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
            if (distance(p_pos, pos(w)) <= 40,
                nearby_warden = true;
                break();
            )
        );
        
        if (nearby_warden,
            is_flying = query(p, 'flying');
            is_gliding = query(p, 'fall_flying');
            if (is_flying || is_gliding,
                if (is_flying, modify(p, 'flying', false));
                if (is_gliding, modify(p, 'fall_flying', false));
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
                        v_pull_y = -dy / total_dist * pull_strength + 0.2; // slight upward arc
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

    // 7. Cảnh báo hoàng hôn ngày Trăng Máu
    if (global_tick_count % 100 == 0,
        daytime = time() % 24000;
        day = floor(time() / 24000);
        
        // Cảnh báo hoàng hôn ngày Trăng Máu
        if (day == global_blood_moon_day && daytime >= 12000 && daytime < 13000,
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
        
        // Kết thúc ngày trăng máu (sang ngày mới hoặc hết đêm)
        if (global_was_blood_moon_notified && (daytime < 12000 || day != global_blood_moon_day),
            for(players,
                p = _;
                sound('minecraft:ui.toast.challenge_complete', pos(p), 0.8, 1.0);
                print(p, '§a§l[Yên bình] Đêm trăng máu đã qua. Sức mạnh quái vật trở lại bình thường.');
            );
            global_blood_moon_day = day + floor(rand(8)) + 8;
            _save_blood_moon_data();
            global_was_blood_moon_notified = false;
        )
    )
);

// ── LỆNH KIỂM TRA CHO DEVELOPER ──

// Lệnh gốc: khi gõ /custom_mob_effects không có tham số
__command() -> status();

// Lệnh: /custom_mob_effects trigger_blood_moon
trigger_blood_moon() -> (
    p = player();
    if (query(p, 'permission_level') < 2,
        print(p, '§cBạn không có quyền sử dụng lệnh này!');
        return()
    );
    current_day = floor(time() / 24000);
    global_blood_moon_day = current_day;
    global_was_blood_moon_notified = false;
    _save_blood_moon_data();
    run('time set 12000');
    print(p, '§4§l[Trăng Máu] Đã kích hoạt Trăng Máu cho hôm nay và chuyển giờ về Hoàng Hôn!');
);

// Lệnh: /custom_mob_effects test_warden_p2
// Đặt máu của Warden gần nhất về 310 HP để kiểm tra Phase 2
test_warden_p2() -> (
    p = player();
    if (query(p, 'permission_level') < 2,
        print(p, '§cBạn không có quyền sử dụng lệnh này!');
        return()
    );
    wardens = entity_list('warden');
    if (length(wardens) == 0,
        print(p, '§cKhông tìm thấy Warden nào trong tầm!');
        return();
    );
    w = wardens:0;
    modify(w, 'health', 310.0);
    print(p, '§aĐã đặt máu Warden về 310 HP (chuẩn bị kích hoạt Phase 2 khi xuống <= 300 HP)!');
);

// Lệnh: /custom_mob_effects test_warden_heal
// Đặt máu của Warden gần nhất về 95 HP trong Phase 2 để kiểm tra Hồi máu 10s lên 400 HP và Nhạc Sacrifice
test_warden_heal() -> (
    p = player();
    if (query(p, 'permission_level') < 2,
        print(p, '§cBạn không có quyền sử dụng lệnh này!');
        return()
    );
    wardens = entity_list('warden');
    if (length(wardens) == 0,
        print(p, '§cKhông tìm thấy Warden nào trong tầm!');
        return();
    );
    w = wardens:0;
    global_warden_phase2:(w ~ 'uuid') = true;
    delete(global_warden_emergency_healed:(w ~ 'uuid'));
    delete(global_warden_healing_ticks:(w ~ 'uuid'));
    modify(w, 'health', 95.0);
    print(p, '§aĐã đặt Warden vào Phase 2 với 95 HP để kiểm tra cơ chế Huyết Tế (< 10%) hồi lên 400 HP trong 10s và nhạc Sacrifice!');
);

// Lệnh: /custom_mob_effects test_warden_drop
// Thử nghiệm rơi drop phần thưởng Warden tại vị trí người chơi
test_warden_drop() -> (
    p = player();
    if (query(p, 'permission_level') < 2,
        print(p, '§cBạn không có quyền sử dụng lệnh này!');
        return()
    );
    _drop_warden_loot(pos(p), p);
    print(p, '§aĐã kích hoạt test phần thưởng rơi ra của Warden tại vị trí của bạn!');
);

// Lệnh: /custom_mob_effects status
// Xem trạng thái trăng máu hiện tại và lịch trình
status() -> (
    p = player();
    current_day = floor(time() / 24000);
    daytime = time() % 24000;
    print(p, '§7--- Trạng thái Custom Mob Effects ---');
    print(p, str('§7Ngày hiện tại: %d (Thời gian ngày: %d)', current_day, daytime));
    print(p, str('§7Ngày Trăng Máu tiếp theo: %d', global_blood_moon_day));
    is_night = (daytime >= 12000 && daytime < 23000);
    is_bm_now = (current_day == global_blood_moon_day && is_night);
    print(p, str('§7Trăng Máu đang hoạt động: %s', if(is_bm_now, '§4ĐANG CHẠY', '§aKhông')));
);
