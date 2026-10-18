"""Build audited, reproducible 26.2 compatibility candidates; never edit inputs.

python fix_26_2_compatibility.py SOURCE_MODS CANDIDATE_DIRECTORY

Entity schema: https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2
Versions/codecs and action handlers were also checked against the local JARs.
"""

import argparse
from copy import copy
import hashlib
import json
from pathlib import Path
import struct
import zipfile


ENTITY_FIELDS = {
    "entity", "player", "villager", "victim", "child", "parent", "partner",
    "direct_entity", "source_entity", "vehicle", "passenger", "targeted_entity",
    "looking_at", "lightning", "bystander", "zombie", "shooter",
}


def encode_json(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def entity_predicate(value):
    if isinstance(value, list):
        return [walk(v) for v in value]
    if not isinstance(value, dict):
        return value
    result = {}
    for key, item in value.items():
        if key in ("type", "minecraft:type"):
            new_key, item = "minecraft:entity_type", item
        elif key in ("type_specific", "minecraft:type_specific"):
            if not isinstance(item, dict) or "type" not in item:
                raise ValueError("Unexpected type_specific predicate")
            kind = item["type"].removeprefix("minecraft:")
            if kind == "slime":
                kind = "cube_mob"
            if kind not in ("player", "lightning", "fishing_hook", "cube_mob", "raider", "sheep"):
                raise ValueError(f"Unknown entity sub-predicate: {kind}")
            new_key = "minecraft:type_specific/" + kind
            item = walk({k: v for k, v in item.items() if k != "type"})
        else:
            new_key = key
            item = entity_predicate(item) if key.removeprefix("minecraft:") in ENTITY_FIELDS else walk(item)
        if new_key in result:
            raise ValueError(f"Predicate key collision: {new_key}")
        result[new_key] = item
    return result


def walk(value):
    if isinstance(value, list):
        return [walk(v) for v in value]
    if not isinstance(value, dict):
        return value
    entity_condition = value.get("condition") in ("entity_properties", "minecraft:entity_properties")
    return {
        key: entity_predicate(item)
        if key.removeprefix("minecraft:") in ENTITY_FIELDS or (key == "predicate" and entity_condition)
        else walk(item)
        for key, item in value.items()
    }


def fix_magma_class(data):
    """Change the obsolete Slime superclass references, keeping method bytecode."""
    old = b"net/minecraft/world/entity/monster/cubemob/Slime"
    new = b"net/minecraft/world/entity/monster/cubemob/AbstractCubeMob"
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("Invalid class file")
    count = struct.unpack_from(">H", data, 8)[0]
    output = bytearray(data[:10])
    cursor, index, changes = 10, 1, 0
    widths = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4,
              11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        tag = data[cursor]
        output.append(tag)
        cursor += 1
        if tag == 1:
            length = struct.unpack_from(">H", data, cursor)[0]
            cursor += 2
            original = data[cursor:cursor + length]
            updated = original.replace(old, new)
            changes += original != updated
            output.extend(struct.pack(">H", len(updated)))
            output.extend(updated)
            cursor += length
        else:
            if tag not in widths:
                raise ValueError(f"Unknown constant-pool tag {tag}")
            width = widths[tag]
            output.extend(data[cursor:cursor + width])
            cursor += width
            index += tag in (5, 6)
        index += 1
    if changes != 3:
        raise ValueError(f"Unexpected number of obsolete superclass constants: {changes}")
    output.extend(data[cursor:])
    return bytes(output)


def fix_pts_model_map(data):
    """Use a concurrent map only for MODELS, which is filled by runAsync tasks."""
    count = struct.unpack_from(">H", data, 8)[0]
    pool, cursor, index = [None] * count, 10, 1
    while index < count:
        tag = data[cursor]
        cursor += 1
        if tag == 1:
            size = struct.unpack_from(">H", data, cursor)[0]
            cursor += 2
            pool[index] = (tag, data[cursor:cursor + size])
            cursor += size
        elif tag in (3, 4, 5, 6):
            cursor += 8 if tag in (5, 6) else 4
            index += tag in (5, 6)
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = (tag, struct.unpack_from(">H", data, cursor)[0])
            cursor += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[index] = (tag, *struct.unpack_from(">HH", data, cursor))
            cursor += 4
        elif tag == 15:
            cursor += 3
        else:
            raise ValueError(f"Unknown constant-pool tag {tag}")
        index += 1
    fields, constructors = [], []
    for index, entry in enumerate(pool):
        if not entry or entry[0] not in (9, 10):
            continue
        owner = pool[pool[entry[1]][1]][1]
        name_and_type = pool[entry[2]]
        name, descriptor = pool[name_and_type[1]][1], pool[name_and_type[2]][1]
        if entry[0] == 9 and owner == b"com/nik123123555/ptsdeco/core/ModuleBlocks" and name == b"MODELS":
            fields.append(index)
        if entry[0] == 10 and owner == b"java/util/LinkedHashMap" and name == b"<init>" and descriptor == b"()V":
            constructors.append(index)
    if len(fields) != 1 or len(constructors) != 1:
        raise ValueError("Unexpected PTS model-map initializer")
    constructor = constructors[0]
    old = (b"\xbb" + struct.pack(">H", pool[constructor][1]) + b"\x59\xb7" +
           struct.pack(">H", constructor) + b"\xb3" + struct.pack(">H", fields[0]))
    new = (b"\xbb" + struct.pack(">H", count + 1) + b"\x59\xb7" +
           struct.pack(">H", count + 2) + b"\xb3" + struct.pack(">H", fields[0]))
    tail = data[cursor:]
    if tail.count(old) != 1:
        raise ValueError("Expected one MODELS constructor sequence")
    classname = b"java/util/concurrent/ConcurrentHashMap"
    extra = (b"\x01" + struct.pack(">H", len(classname)) + classname +
             b"\x07" + struct.pack(">H", count) +
             b"\x0a" + struct.pack(">HH", count + 1, pool[constructor][2]))
    return data[:8] + struct.pack(">H", count + 3) + data[10:cursor] + extra + tail.replace(old, new, 1)


def transform(name, raw, role):
    if role == "ptsdeco" and name == "com/nik123123555/ptsdeco/core/ModuleBlocks.class":
        return name, fix_pts_model_map(raw)
    if role in ("alexsmobs", "mob-ai-tweaks", "morevillagers") and "/advancement/" in name and name.endswith(".json"):
        original = json.loads(raw)
        updated = walk(original)
        if original != updated:
            return name, encode_json(updated)
    if role == "mob-ai-tweaks" and name == "com/notunanancyowen/mait/mixin/MagmaCubeEntityMixin.class":
        return name, fix_magma_class(raw)
    if role == "morevillagers" and name == "data/morevillagers/recipe/gilded_station.json":
        value = json.loads(raw)
        for key, ingredient in value["key"].items():
            if not isinstance(ingredient, dict) or set(ingredient) != {"item"}:
                raise ValueError("Unexpected workstation ingredient")
            value["key"][key] = ingredient["item"]
        return name, encode_json(value)
    if role == "cubeanimals":
        for old, new in (("emerald_sleep_arrow_III", "emerald_sleep_arrow_iii"),
                         ("emerald_sleep_arrow_II", "emerald_sleep_arrow_ii"),
                         ("emerald_sleep_arrow_I", "emerald_sleep_arrow_i")):
            name = name.replace(old, new)
            if name.endswith(".json"):
                raw = raw.replace(old.encode(), new.encode())
        return name, raw
    if role == "jobsplustools" and name.startswith("data/jobsplustools/itemrestrictions/restrictions/") and name.endswith("_trowel.json"):
        value = json.loads(raw)
        if value["types"].count("use") != 1:
            raise ValueError("Unexpected trowel actions")
        value["types"] = [new for old in value["types"] for new in
                          (["use_item", "interact_block"] if old == "use" else [old])]
        return name, encode_json(value)
    if role == "farmingforblockheads" and name in (
        "data/farmingforblockheads/recipe/market/biomesoplenty/origin_sapling.json",
        "data/farmingforblockheads/recipe/market/biomesoplenty/rainbow_birch_sapling.json",
    ):
        value = json.loads(raw)
        value["fabric:load_conditions"].append({
            "condition": "fabric:registry_contains", "registry": "minecraft:item",
            "values": [value["result"]["item"]],
        })
        return name, encode_json(value)
    if name == "pack.mcmeta" and role in ("cutecats", "missingvillages", "sitanywhere"):
        value = json.loads(raw)
        pack = value["pack"]
        if role == "cutecats":
            pack.update(pack_format=88, min_format=88, max_format=88)
        elif role == "missingvillages":
            pack.update(pack_format=107, min_format=107, max_format=107)
        else:
            # Preserve the original 48+ pack and its version-specific overlays.
            pack.update(pack_format=48, min_format=48, max_format=9999,
                        supported_formats=[48, 9999])
        return name, encode_json(value)
    return name, raw


def build(source, destination, role):
    changes = []
    with zipfile.ZipFile(source) as original:
        names = original.namelist()
        if len(names) != len(set(names)) or original.testzip() is not None:
            raise ValueError(f"Invalid ZIP: {source}")
        if any(n.upper().endswith((".RSA", ".DSA", ".EC", ".SF")) for n in names):
            raise ValueError(f"Signed mod needs a source build: {source}")
        with zipfile.ZipFile(destination, "x") as candidate:
            candidate.comment = original.comment
            written = set()
            for entry in original.infolist():
                raw = original.read(entry.filename)
                name, updated = transform(entry.filename, raw, role)
                if name in written:
                    raise ValueError(f"ZIP filename collision: {name}")
                written.add(name)
                info = copy(entry)
                info.filename = name
                candidate.writestr(info, updated)
                if name != entry.filename or raw != updated:
                    changes.append({"before": entry.filename, "after": name,
                                    "before_sha256": hashlib.sha256(raw).hexdigest(),
                                    "after_sha256": hashlib.sha256(updated).hexdigest()})
        with zipfile.ZipFile(destination) as candidate:
            if candidate.testzip() is not None or len(candidate.infolist()) != len(names):
                raise ValueError("Candidate verification failed")
            for entry in original.infolist():
                expected_name, expected = transform(entry.filename, original.read(entry.filename), role)
                if candidate.read(expected_name) != expected:
                    raise ValueError(f"Unexpected output: {expected_name}")
    if not changes:
        raise ValueError(f"No changes for {source}")
    return changes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_dir", type=Path)
    parser.add_argument("candidate_dir", type=Path)
    args = parser.parse_args()
    specs = json.loads(Path(__file__).with_name("compatibility-inputs.json").read_text(encoding="utf-8"))
    args.candidate_dir.mkdir(parents=True, exist_ok=True)
    results = []
    for spec in specs:
        source = args.source_dir / spec["file"]
        if hashlib.sha256(source.read_bytes()).hexdigest() != spec["sha256"]:
            raise ValueError(f"Input changed: {source}")
        destination = args.candidate_dir / source.name
        changes = build(source, destination, spec["role"])
        result = dict(spec, candidate_sha256=hashlib.sha256(destination.read_bytes()).hexdigest(), changes=changes)
        results.append(result)
        print(f"{spec['role']}: {len(changes)} changed entries; CRC verified")
    (args.candidate_dir / "changes.json").write_bytes(encode_json(results))


if __name__ == "__main__":
    main()
