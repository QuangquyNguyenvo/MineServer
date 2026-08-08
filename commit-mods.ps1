# Quick commit & push cho mods cua MineServer
param(
    [string]$CommitMessage = "Update modpack files"
)

$repoDir = $PSScriptRoot
$modsDir = Join-Path $repoDir "mods"

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
