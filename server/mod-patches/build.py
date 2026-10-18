"""Ap lai ban va cua GPT (fix_26_2_compatibility.py) len dung cac JAR dang chay tren server.
python build.py SOURCE_MODS OUT_DIR"""
import hashlib, json, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
import gptfix

FDM_FN = {"data/farmhouse_decorations/function/cleanup_legacy_seats.mcfunction",
          "data/farmhouse_decorations/functions/cleanup_legacy_seats.mcfunction"}
FDM_BODY = (b"# Patched for 26.2: tag 'farmhouse_decorations:seat' contains ':' which entity selectors\n"
            b"# cannot parse, so the original function failed to load and broke the whole\n"
            b"# #minecraft:load tag (load functions of every other mod/datapack were skipped).\n")
orig = gptfix.transform
def transform(name, raw, role):
    if role == "fdm":
        return (name, FDM_BODY) if name in FDM_FN else (name, raw)
    return orig(name, raw, role)
gptfix.transform = transform

SPECS = [
    ("alexsmobsfabric-26.2-1.2.0.jar", "alexsmobs"),
    ("mob-ai-tweaks-1.11.0-beta.jar", "mob-ai-tweaks"),
    ("MoreVillagersRe-26.1.x-fabric-1.26.7.1.jar", "morevillagers"),
    ("cubeanimals-2.1.1-26.2.jar", "cubeanimals"),
    ("jobsplustools-fabric-21.1.0.jar", "jobsplustools"),
    ("farmingforblockheads-fabric-26.2-26.2.0.5.jar", "farmingforblockheads"),
    ("PTS-Deco-14.0-Fabric26.2.jar", "ptsdeco"),
    ("fdm-26.9-fabric-26.2.jar", "fdm"),
]
src, out = Path(sys.argv[1]), Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
results = []
for f, role in SPECS:
    s = src / f
    d = out / f
    if d.exists(): d.unlink()
    try:
        ch = gptfix.build(s, d, role)
    except Exception as e:
        print(f"SKIP {role}: {e}")
        if d.exists(): d.unlink()
        continue
    results.append({"file": f, "role": role, "sha256": hashlib.sha256(s.read_bytes()).hexdigest(),
                    "candidate_sha256": hashlib.sha256(d.read_bytes()).hexdigest(), "changed_entries": len(ch)})
    print(f"OK   {role}: {len(ch)} entries")
(out / "changes.json").write_text(json.dumps(results, indent=2))
