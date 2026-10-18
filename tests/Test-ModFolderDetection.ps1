# Test tu dong cho phan tu tim thu muc mods trong UpdateMinecraftMods.ps1.
# Dung "may ao" (APPDATA gia co ten nguoi dung co dau, thu muc gia lam o D/E) voi cac kieu
# cai pho bien: launcher chinh hang, TLauncher/Legacy, Prism, MultiMC, CurseForge, Modrinth,
# ATLauncher, GDLauncher, cai tay sau 5 cap, thu muc server... roi so voi ket qua mong doi.
# Chay: powershell -File tests\Test-ModFolderDetection.ps1
param(
    [string]$Sandbox = (Join-Path $env:TEMP 'olongbell-detect-test'),
    [string]$Script = (Join-Path $PSScriptRoot '..\UpdateMinecraftMods.ps1')
)
$ErrorActionPreference = 'Stop'
if (Test-Path $Sandbox) { Remove-Item $Sandbox -Recurse -Force }
$user = "Nguy" + [char]0x1EC5 + "n V" + [char]0x0103 + "n A"     # ten co dau
$home2 = Join-Path $Sandbox "Users\$user"
$appdata = Join-Path $home2 'AppData\Roaming'
$D = Join-Path $Sandbox 'D'
$E = Join-Path $Sandbox 'E'

function Make([string]$dir, [string[]]$parts, [int]$jars = 2) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    foreach ($p in $parts) {
        switch ($p) {
            'mods' { New-Item -ItemType Directory -Force (Join-Path $dir 'mods') | Out-Null; for ($i = 0; $i -lt $jars; $i++) { Set-Content (Join-Path $dir "mods\m$i.jar") 'x' } }
            'versions' { New-Item -ItemType Directory -Force (Join-Path $dir 'versions') | Out-Null }
            'config' { New-Item -ItemType Directory -Force (Join-Path $dir 'config') | Out-Null }
            'options' { Set-Content (Join-Path $dir 'options.txt') 'x' }
            'server' { Set-Content (Join-Path $dir 'server.properties') 'x' }
        }
    }
}

$expect = [ordered]@{}   # duong dan mods -> mo ta
$reject = [ordered]@{}

# 1. Launcher chinh hang mac dinh
Make (Join-Path $appdata '.minecraft') 'versions', 'mods' 3
$expect[(Join-Path $appdata '.minecraft\mods')] = 'Official default'
# 2. Launcher chinh hang, profile gameDir rieng tren o D (khong co versions)
$prof = Join-Path $D 'Games\FabricProfile'
Make $prof 'mods', 'options' 5
$json = '{"profiles":{"abc":{"name":"Fabric","gameDir":' + ($prof | ConvertTo-Json) + '}}}'
Set-Content (Join-Path $appdata '.minecraft\launcher_profiles.json') $json
$expect[(Join-Path $prof 'mods')] = 'Official custom gameDir on D'
# 3. TLauncher doi thu muc game sang D, nam sau 5 cap
$tlGame = Join-Path $D 'Games\Minecraft\Launchers\TLauncher\game'
Make $tlGame 'versions', 'options'
New-Item -ItemType Directory -Force (Join-Path $appdata '.tlauncher') | Out-Null
Set-Content (Join-Path $appdata '.tlauncher\tlauncher-2.0.properties') ("minecraft.gamedir=" + ($tlGame -replace '\\', '\\' -replace ':', '\:'))
$expect[(Join-Path $tlGame 'mods')] = 'TLauncher gamedir in config (D, deep)'
# 4. Legacy Launcher mac dinh, chua co thu muc mods
Make (Join-Path $appdata '.tlauncher\legacy\Minecraft\game') 'versions', 'options'
$expect[(Join-Path $appdata '.tlauncher\legacy\Minecraft\game\mods')] = 'Legacy default (no mods yet)'
# 5. Prism (thu muc minecraft, ten co dau cach)
Make (Join-Path $appdata 'PrismLauncher\instances\Olong 26.2\minecraft') 'mods', 'options' 7
$expect[(Join-Path $appdata 'PrismLauncher\instances\Olong 26.2\minecraft\mods')] = 'Prism instance'
# 6. MultiMC portable tren o E
Make (Join-Path $E 'Tools\MultiMC\instances\Olong\.minecraft') 'mods', 'config' 4
$expect[(Join-Path $E 'Tools\MultiMC\instances\Olong\.minecraft\mods')] = 'MultiMC portable on E'
# 7. CurseForge
Make (Join-Path $home2 'curseforge\minecraft\Instances\Olong Pack') 'mods', 'options', 'config' 6
$expect[(Join-Path $home2 'curseforge\minecraft\Instances\Olong Pack\mods')] = 'CurseForge instance'
# 8. Modrinth
Make (Join-Path $appdata 'ModrinthApp\profiles\Olong') 'mods', 'options' 2
$expect[(Join-Path $appdata 'ModrinthApp\profiles\Olong\mods')] = 'Modrinth profile'
# 9. ATLauncher
Make (Join-Path $appdata 'ATLauncher\instances\OlongBell') 'mods', 'options' 2
$expect[(Join-Path $appdata 'ATLauncher\instances\OlongBell\mods')] = 'ATLauncher instance'
# 10. GDLauncher (ban moi)
Make (Join-Path $appdata 'gdlauncher_carbon\data\instances\Olong\instance') 'mods', 'config' 2
$expect[(Join-Path $appdata 'gdlauncher_carbon\data\instances\Olong\instance\mods')] = 'GDLauncher instance'
# 11. Cai tay tren o D, sau 5 cap
Make (Join-Path $D 'A\B\C\D\game') 'versions', 'mods' 1
$expect[(Join-Path $D 'A\B\C\D\game\mods')] = 'Manual install depth 5 on D'
# 12. Thu muc server -> phai bo qua
Make (Join-Path $D 'Server') 'versions', 'mods', 'server'
$reject[(Join-Path $D 'Server\mods')] = 'Server folder'
# 13. Thu muc chi co "mods" (ban sao luu) -> bo qua cho do rac
Make (Join-Path $D 'Backup\oldpack') 'mods' 3
$reject[(Join-Path $D 'Backup\oldpack\mods')] = 'Lone mods backup'
# 14. Sau 6 cap -> gioi han da biet (chi tim duoc qua Browse)
Make (Join-Path $D 'A\B\C\D\E\game') 'versions', 'mods' 1
$reject[(Join-Path $D 'A\B\C\D\E\game\mods')] = 'Depth 6 (known limit)'
# 15. O D nhieu thu muc rac (3000) de thu ngan sach thoi gian
1..3000 | ForEach-Object { [void][IO.Directory]::CreateDirectory((Join-Path $D "Junk\j$_")) }

# --- chay ---
$src = Get-Content -Raw $Script
$m = [regex]::Match($src, "(?s)\`$DetectCode = @'\r?\n(.*?)\r?\n'@")
. ([scriptblock]::Create($m.Groups[1].Value))
$env:APPDATA = $appdata
$env:USERPROFILE = $home2
$sw = [Diagnostics.Stopwatch]::StartNew()
$res = Find-ModFolders $null @($D, $E)
$sw.Stop()
$paths = @{}
foreach ($r in $res) { $paths[$r.Path.ToLower()] = $r }

$pass = 0; $fail = 0
foreach ($k in $expect.Keys) {
    $r = $paths[$k.ToLower()]
    if ($r) { $pass++; "PASS  found   {0,-38} -> {1} ({2} mods, {3})" -f $expect[$k], $r.Label, $r.Mods, $r.Source }
    else { $fail++; "FAIL  MISSED  {0,-38} {1}" -f $expect[$k], $k }
}
foreach ($k in $reject.Keys) {
    if ($paths[$k.ToLower()]) { $fail++; "FAIL  WRONGLY {0,-38} {1}" -f $reject[$k], $k }
    else { $pass++; "PASS  skipped {0,-38}" -f $reject[$k] }
}
"---- $pass pass, $fail fail, $($res.Count) found, $([Math]::Round($sw.Elapsed.TotalSeconds, 2))s"
"Order shown to user:"
$i = 0; foreach ($r in $res) { $i++; "  $i. $($r.Label) - $($r.Mods) mods - $($r.Path.Replace($Sandbox, '<sb>'))" }

Remove-Item $Sandbox -Recurse -Force -ErrorAction SilentlyContinue
if ($fail -gt 0) { exit 1 }
