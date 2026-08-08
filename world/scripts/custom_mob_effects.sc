// ==============================================================================
//              ✦ CUSTOM MOB EFFECTS & EVENTS SYSTEM ✦
//   Gây hiệu ứng bất lợi, buff quái Overworld/Nether/End và Đêm Trăng Máu
// ==============================================================================

global_blood_moon_day = null;
global_was_blood_moon_notified = false;

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
        run(str('attribute %s minecraft:generic.max_health base set 400', e ~ 'uuid'));
        modify(e, 'health', 400.0);
    )
));

// Register handler cho Ender Dragon spawn (bất kể đâu)
entity_load_handler('ender_dragon', _(e, new) -> (
    if (new,
        run(str('attribute %s minecraft:generic.max_health base set 700', e ~ 'uuid'));
        modify(e, 'health', 700.0);
    )
));

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
    is_mob = query(source_entity, 'category') == 'hostile' || type == 'wither' || type == 'ender_dragon';
    is_bullet = (type == 'shulker_bullet');
    is_boss_attack = (type == 'wither' || type == 'wither_skull' || type == 'ender_dragon' || type == 'dragon_fireball');
    
    if (!is_mob && !is_bullet && !is_boss_attack, return());

    dim = player ~ 'dimension';
    
    // ── TĂNG SÁT THƯƠNG BOSS TẠI NETHER VÀ THE END (+1 damage = -1 HP trực tiếp) ──
    if (is_boss_attack && (dim == 'minecraft:the_nether' || dim == 'minecraft:the_end'),
        modify(player, 'health', max(0, (player ~ 'health') - 1.0))
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

// Quét định kỳ mỗi 100 ticks để kiểm tra chuyển giao ngày/đêm và thông báo Trăng Máu
global_tick_count = 0;

__on_tick() -> (
    global_tick_count = global_tick_count + 1;
    if (global_tick_count % 100 == 0,
        daytime = time() % 24000;
        day = floor(time() / 24000);
        
        // Cảnh báo hoàng hôn ngày Trăng Máu
        if (day == global_blood_moon_day && daytime >= 12000 && daytime < 13000,
            if (!global_was_blood_moon_notified,
                for(player('all'),
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
            for(player('all'),
                p = _;
                sound('minecraft:ui.toast.challenge_complete', pos(p), 0.8, 1.0);
                print(p, '§a§l[Yên bình] Đêm trăng máu đã qua. Sức mạnh quái vật trở lại bình thường.');
            );
            // Lên lịch đêm Trăng Máu tiếp theo: từ 8 đến 15 ngày tới
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
// Cưỡng bức trăng máu diễn ra hôm nay và chuyển thời gian về tối
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
