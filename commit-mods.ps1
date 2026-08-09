# Quick commit & push cho mods cua MineServer
param(
    [string]$CommitMessage = "Update modpack files"
)

$repoDir = $PSScriptRoot
$modsDir = Join-Path $repoDir "mods"
$removedListPath = Join-Path $modsDir "_removed.txt"

# -- PHAI giong het Get-ModBaseName trong UpdateMinecraftMods.ps1 --
# Dung de tinh base-name ghi vao _removed.txt, phia client doi chieu bang chinh
# ham nay o ben do. Sua 1 ben thi phai sua ca 2 ben, khong thi tombstone se sai lech.
function Get-ModBaseName($name) {
    if ($name -match '^([a-zA-Z_\-]+?)(?:-?\d|\sv\d|\bv\d)') {
        return $Matches[1].TrimEnd('-').TrimEnd('_').ToLower()
    }
    return $name.ToLower()
}

# -- Chuan hoa ten mod de nhom cac phien ban cua cung 1 mod lai voi nhau --
# Bo phan mo rong .jar/.jar.disabled, bo chu so/dau cham/dau cong (thuong la phan version),
# giu lai phan chu de phan biet cac mod that su khac nhau (vd: supermartijn642configlib vs
# supermartijn642corelib van phai duoc coi la 2 mod khac nhau, khong duoc gop nham).
function Get-NormalizedModKey($name) {
    $n = $name.ToLower()
    $isDisabled = $n -like "*.jar.disabled"
    $n = $n -replace '\.jar(\.disabled)?$', ''
    $n = $n -replace '[0-9]+', ''
    $n = $n -replace '[.+_]', ''
    $n = $n -replace '-{2,}', '-'
    $n = $n.Trim('-', ' ')
    return "$n|$isDisabled"
}

# -- Tu dong xoa ban mod cu hon khi phat hien 2 file cung 1 mod trong mods/ --
# Chi tu dong xoa khi ro rang la 1 cap (cu/moi), qua 2 file cung nhom thi canh bao
# thay vi doan mo, tranh xoa nham.
if (Test-Path -LiteralPath $modsDir) {
    $jarFiles = Get-ChildItem -Path $modsDir -File | Where-Object { $_.Extension -eq ".jar" -or $_.Name -like "*.jar.disabled" }
    $groups = @{}
    foreach ($f in $jarFiles) {
        $key = Get-NormalizedModKey $f.Name
        if (-not $groups.ContainsKey($key)) { $groups[$key] = @() }
        $groups[$key] += $f
    }

    $removedOld = @()
    foreach ($key in $groups.Keys) {
        $files = $groups[$key] | Sort-Object LastWriteTime -Descending
        if ($files.Count -eq 2) {
            $newest = $files[0]
            $older = $files[1]
            Write-Host "Phat hien 2 phien ban cua cung 1 mod:" -ForegroundColor Yellow
            Write-Host "  Giu lai (moi hon): $($newest.Name)" -ForegroundColor Green
            Write-Host "  Xoa (cu hon)      : $($older.Name)" -ForegroundColor Red
            Remove-Item -LiteralPath $older.FullName -Force
            $removedOld += $older.Name
        } elseif ($files.Count -gt 2) {
            Write-Host "[CANH BAO] Tim thay $($files.Count) file co ve cung 1 mod, KHONG tu dong xoa (qua nhieu de doan dung):" -ForegroundColor Magenta
            $files | ForEach-Object { Write-Host "   - $($_.Name)" -ForegroundColor Magenta }
            Write-Host "  -> Tu kiem tra va xoa thu cong ban cu neu dung." -ForegroundColor Magenta
        }
    }

    if ($removedOld.Count -gt 0) {
        Write-Host "Da tu dong xoa $($removedOld.Count) mod ban cu truoc khi commit: $($removedOld -join ', ')" -ForegroundColor Yellow
    } else {
        Write-Host "Khong phat hien mod nao bi trung ban cu/moi." -ForegroundColor DarkGray
    }
}

# -- Phat hien mod bi go HAN khoi mods/ (khong phai doi version, khong con ban thay the) --
# So sanh base-name cua mods/ o commit HEAD voi base-name con lai tren dia (sau buoc dedup
# o tren). Base-name nao bien mat hoan toan -> ghi vao _removed.txt de UpdateMinecraftMods.ps1
# biet ma xoa cuong che ben client, vi logic doan base-name o do chi xoa duoc khi con tim thay
# ban thay the cung ten trong repo (dung de tranh xoa nham mod rieng nguoi choi tu them).
if (Test-Path -LiteralPath $modsDir) {
    $headFiles = git -C $repoDir ls-tree -r --name-only HEAD -- mods 2>$null |
        ForEach-Object { Split-Path $_ -Leaf } |
        Where-Object { $_ -and $_ -ne "_removed.txt" -and ($_ -like "*.jar" -or $_ -like "*.jar.disabled") }

    $currentBaseNames = @{}
    Get-ChildItem -Path $modsDir -File |
        Where-Object { $_.Extension -eq ".jar" -or $_.Name -like "*.jar.disabled" } |
        ForEach-Object { $currentBaseNames[(Get-ModBaseName $_.Name)] = $true }

    $existingRemoved = @{}
    if (Test-Path -LiteralPath $removedListPath) {
        Get-Content -LiteralPath $removedListPath | ForEach-Object {
            $line = $_.Trim()
            if ($line -and -not $line.StartsWith("#")) { $existingRemoved[$line.ToLower()] = $true }
        }
    }

    $newlyRemoved = @($headFiles | ForEach-Object { Get-ModBaseName $_ } |
        Where-Object { -not $currentBaseNames.ContainsKey($_) -and -not $existingRemoved.ContainsKey($_) } |
        Select-Object -Unique)

    if ($newlyRemoved.Count -gt 0) {
        Write-Host "Phat hien $($newlyRemoved.Count) mod bi go han (khong con ban thay the): $($newlyRemoved -join ', ')" -ForegroundColor Yellow
        Write-Host "  -> Ghi vao _removed.txt de client tu dong xoa ban cu tren may nguoi choi." -ForegroundColor Yellow

        $allRemoved = @($existingRemoved.Keys) + $newlyRemoved | Select-Object -Unique | Sort-Object
        $header = @(
            "# Danh sach base-name cua cac mod da bi go HAN khoi server (khong phai doi version)."
            "# Moi dong 1 base-name (chu thuong, tinh theo cung thuat toan Get-ModBaseName ben"
            "# UpdateMinecraftMods.ps1). File nay duoc commit-mods.ps1 tu dong cap nhat khi phat"
            "# hien mod bien mat hoan toan khoi mods/ - khong can sua tay, tru khi muon xoa cuong"
            "# che 1 mod ma script tu dong chua kip ghi nhan."
        )
        ($header + $allRemoved) | Set-Content -LiteralPath $removedListPath -Encoding UTF8
    }
}

Write-Host "Staging mod changes..." -ForegroundColor Cyan
git -C $repoDir add mods

$status = git -C $repoDir status --porcelain mods
if ($null -eq $status -or $status.Trim() -eq "") {
    Write-Host "No mod changes detected to commit." -ForegroundColor Yellow
    exit 0
}

Write-Host "Committing changes: $CommitMessage" -ForegroundColor Green
git -C $repoDir commit -m $CommitMessage

Write-Host "Pushing to GitHub origin main..." -ForegroundColor Cyan
git -C $repoDir push origin main

Write-Host "Successfully committed and pushed modpack updates!" -ForegroundColor Green
