# Overlay datapack - adds player-distance gate to Animated Java's global idle-tick entry point.
# Without this, every aj.global.root rig entity (item_display) runs its full animation tick
# unconditionally every server tick, even with 0 players online - this was the dominant cause
# of severe main-thread lag (Fortress of War / warft mod, 111 rig entities across 3 structures).
# Mirrors warft's own player-distance gate pattern used in tick_entities/root/initiate.mcfunction
# (distance=..140). Safe to delete this datapack folder + /reload to fully revert.
execute as @e[type=item_display,tag=aj.global.root] at @s if entity @a[distance=..128] run function animated_java:global/root/on_tick
