[CmdletBinding()]
param(
    [switch]$NoGui,
    # So luong mod tai song song
    [int]$Parallel = 4
)

# Khi chay qua "irm | iex" param co the khong duoc gan -> dat mac dinh
if (-not $Parallel -or $Parallel -lt 1) { $Parallel = 4 }

# Set Output Encoding to UTF8 (harmless for ASCII, safe if extended later)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Try to load GUI assemblies
$guiSupported = $false
try {
    Add-Type -AssemblyName PresentationFramework, PresentationCore, WindowsBase -ErrorAction Stop
    $guiSupported = $true
} catch {
    # WPF not available
}

# Target paths
$legacyPath = Join-Path $env:APPDATA ".tlauncher\legacy\Minecraft\game\mods"
$premiumPath = Join-Path $env:APPDATA ".minecraft\mods"
$tlauncherPath = Join-Path $env:APPDATA ".minecraft\mods"

$defaultPath = $legacyPath

# -- Mod name helper to prevent deleting custom player mods --
function Get-ModBaseName($name) {
    if ($name -match '^([a-zA-Z_\-]+?)(?:-?\d|\sv\d|\bv\d)') {
        return $Matches[1].TrimEnd('-').TrimEnd('_').ToLower()
    }
    return $name.ToLower()
}

# -- Doc danh sach base-name mod da bi go HAN khoi server (xem mods/_removed.txt) --
# Get-ModBaseName chi cho phep xoa mod cu KHI con tim thay ban thay the cung ten trong
# repo (tranh xoa nham mod rieng nguoi choi tu them). Nhung khi 1 mod bi go han (khong
# co ban thay the) thi base-name do bien mat khoi repo luon, nen logic tren se khong bao
# gio xoa duoc file cu -> no nam lai vinh vien tren may nguoi choi. _removed.txt la danh
# sach tuong minh (commit-mods.ps1 tu ghi) de bu cho truong hop nay.
function Get-RemovedModBaseNames($repoFiles) {
    $result = @{}
    $entry = $repoFiles | Where-Object { $_.name -eq "_removed.txt" } | Select-Object -First 1
    if ($null -eq $entry) { return $result }
    try {
        $content = Invoke-RestMethod -Uri $entry.download_url -Headers @{ "User-Agent" = "PowerShell-Minecraft-Updater" }
        foreach ($line in ($content -split "`n")) {
            $t = $line.Trim()
            if ($t -and -not $t.StartsWith("#")) { $result[$t.ToLower()] = $true }
        }
    } catch {
        # Khong tai duoc _removed.txt thi bo qua, khong chan qua trinh cap nhat mod chinh
    }
    return $result
}

# -- Console UI helpers (tu dong co gian theo chieu rong terminal) --

# Lay chieu rong terminal hien tai (fallback 80 neu bi redirect)
function Get-ConsoleWidth {
    try {
        $w = [Console]::WindowWidth
        if ($w -lt 40) { return 80 }
        return $w
    } catch {
        return 80
    }
}

# Ve mot duong ke ngang lap day toan bo chieu rong man hinh
function Write-Divider {
    param([char]$Char = '-', [System.ConsoleColor]$Color = 'DarkGray')
    Write-Host ([string]$Char * (Get-ConsoleWidth)) -ForegroundColor $Color
}

# In mot dong chu can giua theo chieu rong man hinh
function Write-Centered {
    param([string]$Text, [System.ConsoleColor]$Color = 'White')
    $w = Get-ConsoleWidth
    $pad = [Math]::Max(0, [int](($w - $Text.Length) / 2))
    Write-Host ((' ' * $pad) + $Text) -ForegroundColor $Color
}

# Banner tieu de: 2 duong '=' full-width + tieu de can giua
function Write-Banner {
    param([string]$Title, [string]$Subtitle, [System.ConsoleColor]$Color = 'Cyan')
    Write-Host ""
    Write-Divider '=' $Color
    Write-Centered $Title $Color
    if ($Subtitle) { Write-Centered $Subtitle DarkCyan }
    Write-Divider '=' $Color
}

# Tieu de cua mot muc (section) voi dau '>' va duoi '-' keo dai het dong
function Write-Section {
    param([string]$Text, [System.ConsoleColor]$Color = 'White')
    $w = Get-ConsoleWidth
    $prefix = "> $Text "
    $tail = [Math]::Max(0, $w - $prefix.Length)
    Write-Host ($prefix + ('-' * $tail)) -ForegroundColor $Color
}

# Logo "OLONGBELL" ma hoa bang ky tu ASCII (B=block, g/c/v/d/k/h=cac net box-drawing, .=space)
# roi giai ma bang [char] code-point luc chay, thay vi go thang ky tu Unicode vao source.
# Ly do: file .ps1 chua ky tu Unicode phai co BOM thi Windows PowerShell 5.1 (-File) moi doc
# dung, nhung khi chay qua "irm URL | iex" thi BOM lai lam Invoke-Expression bao loi parse
# (BOM khong tu bi Invoke-RestMethod strip khoi chuoi). Giu source thuan ASCII de dung duoc
# ca 2 cach chay ma khong can quan tam BOM.
function Get-LogoLines {
    $map = @{
        'B' = [char]0x2588; 'g' = [char]0x2557; 'c' = [char]0x2554; 'v' = [char]0x2551
        'd' = [char]0x255D; 'k' = [char]0x255A; 'h' = [char]0x2550; '.' = ' '
    }
    $encoded = @(
        '.BBBBBBg.BBg......BBBBBBg.BBBg...BBg.BBBBBBg.BBBBBBg.BBBBBBBgBBg.....BBg.....',
        'BBchhhBBgBBv.....BBchhhBBgBBBBg..BBvBBchhhhd.BBchhBBgBBchhhhdBBv.....BBv.....',
        'BBv...BBvBBv.....BBv...BBvBBcBBg.BBvBBv..BBBgBBBBBBcdBBBBBg..BBv.....BBv.....',
        'BBv...BBvBBv.....BBv...BBvBBvkBBgBBvBBv...BBvBBchhBBgBBchhd..BBv.....BBv.....',
        'kBBBBBBcdBBBBBBBgkBBBBBBcdBBv.kBBBBvkBBBBBBcdBBBBBBcdBBBBBBBgBBBBBBBgBBBBBBBg',
        '.khhhhhd.khhhhhhd.khhhhhd.khd..khhhd.khhhhhd.khhhhhd.khhhhhhdkhhhhhhdkhhhhhhd'
    )
    return $encoded | ForEach-Object {
        -join ($_.ToCharArray() | ForEach-Object { $map[[string]$_] })
    }
}

# Logo ASCII "OLONGBELL" nhieu mau, tu dong bo qua neu terminal qua hep de khong bi vo dong
function Show-Logo {
    $logo = Get-LogoLines
    $colors = @('Blue', 'Cyan', 'Green', 'Yellow', 'Red', 'Magenta')
    $maxLen = ($logo | Measure-Object -Property Length -Maximum).Maximum

    Write-Host ""
    if ((Get-ConsoleWidth) -lt $maxLen) {
        Write-Centered "OLONGBELL" Cyan
    } else {
        for ($i = 0; $i -lt $logo.Count; $i++) {
            Write-Centered $logo[$i] $colors[$i % $colors.Count]
        }
    }
    Write-Centered "May Chu OlongBell" DarkGray
    Write-Host ""
}

# ===================================================================================
#  LOI DONG BO MOD (dung chung cho ca GUI va console)
# ===================================================================================
#  So sanh mod theo GIT BLOB SHA thay vi chi so dung luong: mod bi patch/chinh sua giu
#  nguyen ten (va nhieu khi ca dung luong, vd doi chu hoa -> chu thuong) truoc day bi bo
#  qua. GitHub Contents API tra ve san 'sha' cua tung file nen chi can tinh lai o may.
#  Timestamp khong dung duoc: GitHub khong tra mtime tung file, va mtime o may la luc tai.
#  Hash cache (.modsync-cache.json) giu sha theo (dung luong + mtime) -> lan chay sau
#  khong phai hash lai ~1GB mod.
# ===================================================================================

$RepoSlug   = "QuangquyNguyenvo/MineServer"
$RepoBranch = "main"
$UserAgent  = "PowerShell-Minecraft-Updater"
$CacheName  = ".modsync-cache.json"

# Code helper o dang chuoi de nap duoc ca vao runspace tai song song
$HelperCode = @'
function Get-GitBlobSha([string]$Path) {
    $fs = [System.IO.File]::OpenRead($Path)
    try {
        $sha1 = [System.Security.Cryptography.SHA1]::Create()
        $header = [System.Text.Encoding]::ASCII.GetBytes("blob $($fs.Length)`0")
        [void]$sha1.TransformBlock($header, 0, $header.Length, $null, 0)
        $buf = New-Object byte[] 1048576
        while (($n = $fs.Read($buf, 0, $buf.Length)) -gt 0) {
            [void]$sha1.TransformBlock($buf, 0, $n, $null, 0)
        }
        [void]$sha1.TransformFinalBlock($buf, 0, 0)
        return ([System.BitConverter]::ToString($sha1.Hash) -replace '-', '').ToLower()
    } finally {
        $fs.Dispose()
    }
}
'@
. ([scriptblock]::Create($HelperCode))

function Test-ModFileName($name) {
    return ($name -like "*.jar" -or $name -like "*.jar.disabled")
}

# Chay 1 scriptblock o runspace rieng, goi $OnTick lien tuc (spinner / giu GUI khong do)
function Invoke-InBackground {
    param([scriptblock]$Script, [object[]]$Arguments = @(), [scriptblock]$OnTick)
    $ps = [powershell]::Create()
    try {
        [void]$ps.AddScript($Script)
        foreach ($a in $Arguments) { [void]$ps.AddArgument($a) }
        $handle = $ps.BeginInvoke()
        while (-not $handle.IsCompleted) {
            if ($OnTick) { & $OnTick }
            Start-Sleep -Milliseconds 80
        }
        $out = $ps.EndInvoke($handle)
        if ($ps.Streams.Error.Count -gt 0) { throw $ps.Streams.Error[0].Exception }
        return , $out
    } finally {
        $ps.Dispose()
    }
}

# Lay danh sach mod tai 1 commit co dinh: link raw theo commit SHA la bat bien, khong bi
# CDN cua raw.githubusercontent.com tra ban cu trong vai phut sau khi vua push.
function Get-RepoModList {
    param([scriptblock]$OnTick)
    $result = Invoke-InBackground -OnTick $OnTick -Arguments @($RepoSlug, $RepoBranch, $UserAgent) -Script {
        param($slug, $branch, $ua)
        [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
        $commit = $null
        try {
            $commit = Invoke-RestMethod -Uri "https://api.github.com/repos/$slug/commits/$branch" -Headers @{ "User-Agent" = $ua; "Accept" = "application/vnd.github.sha" }
            $commit = "$commit".Trim()
            if ($commit -notmatch '^[0-9a-f]{40}$') { $commit = $null }
        } catch { $commit = $null }
        $ref = if ($commit) { $commit } else { $branch }
        $files = Invoke-RestMethod -Uri "https://api.github.com/repos/$slug/contents/mods?ref=$ref" -Headers @{ "User-Agent" = $ua }
        [pscustomobject]@{ Ref = $ref; Files = @($files) }
    }
    $info = $result | Select-Object -Last 1
    $info | Add-Member -NotePropertyName RawBase -NotePropertyValue "https://raw.githubusercontent.com/$RepoSlug/$($info.Ref)/mods/"
    return $info
}

function Get-RawUrl($repoInfo, $name) {
    return $repoInfo.RawBase + [Uri]::EscapeDataString($name)
}

# Doc danh sach base-name mod da bi go HAN khoi server (mods/_removed.txt).
# Get-ModBaseName chi cho phep xoa mod cu KHI con ban thay the cung ten trong repo (tranh
# xoa nham mod rieng nguoi choi tu them). Mod bi go han thi base-name bien mat khoi repo,
# nen can _removed.txt (commit-mods.ps1 tu ghi) de xoa cuong che.
function Get-RemovedModBaseNames($repoInfo) {
    $result = @{}
    if (-not ($repoInfo.Files | Where-Object { $_.name -eq "_removed.txt" })) { return $result }
    try {
        $content = Invoke-RestMethod -Uri (Get-RawUrl $repoInfo "_removed.txt") -Headers @{ "User-Agent" = $UserAgent }
        foreach ($line in ("$content" -split "`n")) {
            $t = $line.Trim()
            if ($t -and -not $t.StartsWith("#")) { $result[$t.ToLower()] = $true }
        }
    } catch {
        # Khong tai duoc thi bo qua, khong chan cap nhat
    }
    return $result
}

function Read-ShaCache($dir) {
    $cache = @{}
    $path = Join-Path $dir $CacheName
    if (Test-Path -LiteralPath $path) {
        try {
            $obj = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
            foreach ($p in $obj.PSObject.Properties) { $cache[$p.Name] = $p.Value }
        } catch { }
    }
    return $cache
}

function Write-ShaCache($dir, $cache) {
    try {
        $path = Join-Path $dir $CacheName
        ([pscustomobject]$cache | ConvertTo-Json -Depth 3 -Compress) | Set-Content -LiteralPath $path -Encoding ASCII -Force
        (Get-Item -LiteralPath $path -Force).Attributes = 'Hidden'
    } catch { }
}

# SHA cua file local, dung cache neu dung luong + mtime khong doi
function Get-LocalSha($file, $cache, $newCache) {
    $c = $cache[$file.Name]
    $ticks = $file.LastWriteTimeUtc.Ticks
    if ($c -and [long]$c.s -eq $file.Length -and [long]$c.t -eq $ticks -and $c.h) {
        $sha = "$($c.h)"
    } else {
        $sha = Get-GitBlobSha $file.FullName
    }
    $newCache[$file.Name] = @{ s = $file.Length; t = $ticks; h = $sha }
    return $sha
}

# Tinh ke hoach: file nao can xoa, file nao can tai (kem ly do)
function Get-SyncPlan {
    param($RepoInfo, [string]$TargetDir, [scriptblock]$OnTick)

    $repoMods = @{}
    $repoBaseNames = @{}
    foreach ($f in $RepoInfo.Files) {
        if ($f.type -eq "file" -and (Test-ModFileName $f.name)) {
            $repoMods[$f.name] = $f
            $repoBaseNames[(Get-ModBaseName $f.name)] = $true
        }
    }
    $removedBaseNames = Get-RemovedModBaseNames $RepoInfo

    $localFiles = @(Get-ChildItem -LiteralPath $TargetDir -File | Where-Object { Test-ModFileName $_.Name })
    $localByName = @{}
    foreach ($lf in $localFiles) { $localByName[$lf.Name] = $lf }

    # Mod cu cua 1 mod server (con ban thay the) HOAC mod da bi go han
    $toDelete = @()
    foreach ($lf in $localFiles) {
        if (-not $repoMods.ContainsKey($lf.Name)) {
            $base = Get-ModBaseName $lf.Name
            if ($repoBaseNames.ContainsKey($base) -or $removedBaseNames.ContainsKey($base)) { $toDelete += $lf }
        }
    }

    $cache = Read-ShaCache $TargetDir
    $newCache = @{}
    $toDownload = @()
    $changed = 0
    foreach ($name in $repoMods.Keys) {
        $rm = $repoMods[$name]
        $lf = $localByName[$name]
        if ($OnTick) { & $OnTick }
        if ($null -eq $lf) {
            $toDownload += [pscustomobject]@{ File = $rm; Reason = "moi" }
        } elseif ($lf.Length -ne $rm.size) {
            $toDownload += [pscustomobject]@{ File = $rm; Reason = "khac dung luong" }
        } elseif ((Get-LocalSha $lf $cache $newCache) -ne $rm.sha) {
            $toDownload += [pscustomobject]@{ File = $rm; Reason = "noi dung da chinh sua" }
            $changed++
        }
    }

    return [pscustomobject]@{
        RepoMods   = $repoMods
        LocalFiles = $localFiles
        ToDelete   = $toDelete
        ToDownload = @($toDownload | Sort-Object { $_.File.size } -Descending)
        Changed    = $changed
        Cache      = $newCache
    }
}

# Worker tai 1 file: tai ra .tmp -> kiem dung luong + SHA -> doi ten. Thu lai toi da N lan.
$DownloadWorker = {
    param($HelperCode, $Url, $DestDir, $Name, $Size, $Sha, $MaxRetries, $UserAgent)
    . ([scriptblock]::Create($HelperCode))
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    $dest = Join-Path $DestDir $Name
    $tmp = "$dest.tmp"
    $err = ""
    for ($a = 1; $a -le $MaxRetries; $a++) {
        try {
            if ($a -gt 1) { Start-Sleep -Seconds ([Math]::Min(2 * ($a - 1), 5)) }
            $wc = New-Object System.Net.WebClient
            $wc.Headers.Add("User-Agent", $UserAgent)
            try { $wc.DownloadFile($Url, $tmp) } finally { $wc.Dispose() }
            $len = (Get-Item -LiteralPath $tmp).Length
            if ($Size -gt 0 -and $len -ne $Size) { throw "Sai dung luong ($len / $Size byte), co the mat mang giua chung" }
            if ($Sha -and (Get-GitBlobSha $tmp) -ne $Sha) { throw "Sai SHA, file tai ve khong khop ban tren repo" }
            if (Test-Path -LiteralPath $dest) { Remove-Item -LiteralPath $dest -Force -ErrorAction Stop }
            Rename-Item -LiteralPath $tmp -NewName $Name -ErrorAction Stop
            $fi = Get-Item -LiteralPath $dest
            return [pscustomobject]@{ Name = $Name; Success = $true; Bytes = $len; Attempts = $a; Error = ""; Sha = $Sha; Size = $fi.Length; Ticks = $fi.LastWriteTimeUtc.Ticks }
        } catch {
            $err = $_.Exception.Message
            if ($err -match "being used by another process|dang duoc su dung") { $err = "File dang bi khoa (tat Minecraft roi chay lai)" }
            if (Test-Path -LiteralPath $tmp) { Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue }
        }
    }
    return [pscustomobject]@{ Name = $Name; Success = $false; Bytes = 0; Attempts = $MaxRetries; Error = $err }
}

# Tai song song bang RunspacePool. $OnDone(result, done, total) goi moi khi xong 1 file.
function Invoke-ParallelDownloads {
    param($Items, $RepoInfo, [string]$TargetDir, [int]$Throttle = 4, [scriptblock]$OnDone, [scriptblock]$OnTick)
    $results = @()
    if ($Items.Count -eq 0) { return , $results }
    $pool = [runspacefactory]::CreateRunspacePool(1, [Math]::Max(1, $Throttle))
    $pool.Open()
    try {
        $jobs = foreach ($it in $Items) {
            $f = $it.File
            $ps = [powershell]::Create()
            $ps.RunspacePool = $pool
            [void]$ps.AddScript($DownloadWorker)
            foreach ($arg in @($HelperCode, (Get-RawUrl $RepoInfo $f.name), $TargetDir, $f.name, [long]$f.size, $f.sha, 3, $UserAgent)) { [void]$ps.AddArgument($arg) }
            [pscustomobject]@{ PS = $ps; Handle = $ps.BeginInvoke(); Name = $f.name; Done = $false }
        }
        $done = 0
        while ($done -lt $jobs.Count) {
            foreach ($j in $jobs) {
                if ($j.Done -or -not $j.Handle.IsCompleted) { continue }
                $r = $null
                try { $r = $j.PS.EndInvoke($j.Handle) | Select-Object -Last 1 } catch { }
                if ($null -eq $r) {
                    $msg = if ($j.PS.Streams.Error.Count -gt 0) { $j.PS.Streams.Error[0].ToString() } else { "Loi khong ro" }
                    $r = [pscustomobject]@{ Name = $j.Name; Success = $false; Bytes = 0; Attempts = 0; Error = $msg }
                }
                $j.PS.Dispose()
                $j.Done = $true
                $done++
                $results += $r
                if ($OnDone) { & $OnDone $r $done $jobs.Count }
            }
            if ($OnTick) { & $OnTick }
            Start-Sleep -Milliseconds 100
        }
    } finally {
        $pool.Close()
        $pool.Dispose()
    }
    return , $results
}

function Format-Size([double]$bytes) {
    if ($bytes -ge 1MB) { return "{0:N1} MB" -f ($bytes / 1MB) }
    return "{0:N0} KB" -f ($bytes / 1KB)
}

# Toan bo qua trinh dong bo. $Log(msg, color), $OnTick(), $OnProgress(done, total, text)
function Invoke-ModSync {
    param([string]$TargetDir, [int]$Parallel = 4, [scriptblock]$Log, [scriptblock]$OnTick, [scriptblock]$OnProgress)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $summary = [pscustomobject]@{ Ok = $false; Downloaded = 0; Bytes = 0; Deleted = 0; Failed = @(); Seconds = 0; RepoCount = 0 }

    if (Get-Process -Name javaw -ErrorAction SilentlyContinue) {
        & $Log "[CANH BAO] Minecraft (javaw) dang chay - mod dang dung se bi khoa, nen tat game truoc." Yellow
    }

    if (-not (Test-Path -LiteralPath $TargetDir)) {
        & $Log "[DIR] Tao thu muc moi: $TargetDir" Gray
        New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
    }

    & $Log "Dang ket noi GitHub..." Cyan
    try {
        $repoInfo = Get-RepoModList -OnTick $OnTick
    } catch {
        $m = "$_"
        if ($m -match "403|rate limit") { $m = "GitHub gioi han 60 lan/gio moi IP. Doi khoang 1 tieng roi chay lai." }
        & $Log "[LOI] Khong doc duoc danh sach mod: $m" Red
        return $summary
    }
    if (-not $repoInfo.Files -or $repoInfo.Files.Count -eq 0) {
        & $Log "[LOI] Thu muc mods tren GitHub trong." Red
        return $summary
    }
    $refShort = "$($repoInfo.Ref)"; if ($refShort.Length -gt 7) { $refShort = $refShort.Substring(0, 7) }
    & $Log "[OK] Ket noi GitHub thanh cong (ban $refShort)." Green

    & $Log "Dang kiem tra mod o may (so SHA, lan dau hoi lau)..." Cyan
    $plan = Get-SyncPlan -RepoInfo $repoInfo -TargetDir $TargetDir -OnTick $OnTick
    $summary.RepoCount = $plan.RepoMods.Count
    & $Log "[STATS] Server: $($plan.RepoMods.Count) mod | May ban: $($plan.LocalFiles.Count) mod" Green
    & $Log "[SYNC] Can tai: $($plan.ToDownload.Count) (trong do $($plan.Changed) mod da bi chinh sua) | Can xoa: $($plan.ToDelete.Count)" Yellow

    foreach ($df in $plan.ToDelete) {
        try {
            Remove-Item -LiteralPath $df.FullName -Force -ErrorAction Stop
            $plan.Cache.Remove($df.Name)
            $summary.Deleted++
            & $Log "  - Da xoa: $($df.Name)" DarkRed
        } catch {
            & $Log "  [X] Khong xoa duoc $($df.Name) (tat Minecraft roi chay lai)" Red
            $summary.Failed += $df.Name
        }
    }

    if ($plan.ToDownload.Count -gt 0) {
        $total = ($plan.ToDownload | ForEach-Object { [long]$_.File.size } | Measure-Object -Sum).Sum
        & $Log "Dang tai $($plan.ToDownload.Count) mod ($(Format-Size $total)), $Parallel luong song song..." Cyan
        foreach ($it in $plan.ToDownload) {
            if ($it.Reason -ne "moi") { & $Log "  * $($it.File.name): $($it.Reason)" DarkGray }
        }
        if ($OnProgress) { & $OnProgress 0 $plan.ToDownload.Count "" }
        $results = Invoke-ParallelDownloads -Items $plan.ToDownload -RepoInfo $repoInfo -TargetDir $TargetDir -Throttle $Parallel -OnTick $OnTick -OnDone {
            param($r, $done, $n)
            if ($r.Success) {
                $retry = if ($r.Attempts -gt 1) { " (lan thu $($r.Attempts))" } else { "" }
                & $Log "  -> [$done/$n] $($r.Name) ($(Format-Size $r.Bytes))$retry" White
            } else {
                & $Log "  [X] [$done/$n] $($r.Name): $($r.Error)" Red
            }
            if ($OnProgress) { & $OnProgress $done $n $r.Name }
        }
        foreach ($r in $results) {
            if ($r.Success) {
                $summary.Downloaded++
                $summary.Bytes += $r.Bytes
                $plan.Cache[$r.Name] = @{ s = $r.Size; t = $r.Ticks; h = $r.Sha }
            } else {
                $summary.Failed += $r.Name
            }
        }
    } elseif ($OnProgress) {
        & $OnProgress 1 1 ""
    }

    Write-ShaCache $TargetDir $plan.Cache
    $sw.Stop()
    $summary.Seconds = [Math]::Round($sw.Elapsed.TotalSeconds, 1)
    $summary.Ok = ($summary.Failed.Count -eq 0)
    return $summary
}

# ===================================================================================
#  CONSOLE MODE
# ===================================================================================
function Run-ConsoleUpdate($targetDir) {
    Write-Banner "MOD UPDATER v26.2" "Cong cu dong bo & cap nhat Mod tu dong (Console)" Cyan

    if ([string]::IsNullOrEmpty($targetDir)) {
        Write-Host " Chon launcher game cua ban:" -ForegroundColor Yellow
        Write-Host "  [1] Legacy Launcher (Mac dinh)" -ForegroundColor Gray
        Write-Host "  [2] Official (Minecraft Ban Quyen)" -ForegroundColor Gray
        Write-Host "  [3] TLauncher" -ForegroundColor Gray
        Write-Host "  [4] Duong dan tu chon (Custom)" -ForegroundColor Gray
        $choice = Read-Host " Nhap lua chon (1-4, Enter de lay Mac dinh [1])"

        $targetDir = $legacyPath
        if ($choice -eq "2") {
            $targetDir = $premiumPath
        } elseif ($choice -eq "3") {
            $targetDir = $tlauncherPath
        } elseif ($choice -eq "4") {
            $targetDir = Read-Host " Nhap duong dan day du cua thu muc mods"
            if (-not (Test-Path -LiteralPath $targetDir)) {
                Write-Host " [LOI] Duong dan khong ton tai!" -ForegroundColor Red
                return
            }
        }
    }

    Write-Divider '-' DarkGray
    Write-Host " [DIR] Thu muc dich: " -ForegroundColor Yellow -NoNewline
    Write-Host $targetDir -ForegroundColor White
    Write-Divider '-' DarkGray

    $script:spin = 0
    $spinner = @('|', '/', '-', '\')
    $log = { param($msg, $color) Write-Host -NoNewline "`r   `r"; Write-Host " $msg" -ForegroundColor $color }
    $tick = { Write-Host -NoNewline "`r $($spinner[$script:spin % 4]) " -ForegroundColor Cyan; $script:spin++ }

    try {
        $s = Invoke-ModSync -TargetDir $targetDir -Parallel $Parallel -Log $log -OnTick $tick
    } catch {
        Write-Host " [LOI NGHIEM TRONG] $_" -ForegroundColor Red
        return
    }
    Write-Host -NoNewline "`r   `r"
    if ($s.RepoCount -eq 0) { return }

    Write-Divider '-' DarkGray
    $finalLocalFiles = @(Get-ChildItem -LiteralPath $targetDir -File | Where-Object { Test-ModFileName $_.Name } | Sort-Object Name)
    Write-Section "DANH SACH MOD HIEN TAI TREN MAY BAN ($($finalLocalFiles.Count) mods)" Green
    $w = Get-ConsoleWidth
    $colWidth = [Math]::Max(20, [int](($w - 9) / 2))
    for ($idx = 0; $idx -lt $finalLocalFiles.Count; $idx += 2) {
        $col1 = $finalLocalFiles[$idx].Name
        $col2 = if ($idx + 1 -lt $finalLocalFiles.Count) { $finalLocalFiles[$idx + 1].Name } else { "" }
        if ($col1.Length -gt $colWidth) { $col1 = $col1.Substring(0, $colWidth - 3) + "..." }
        if ($col2.Length -gt $colWidth) { $col2 = $col2.Substring(0, $colWidth - 3) + "..." }
        Write-Host "   - $($col1.PadRight($colWidth)) - $col2" -ForegroundColor DarkGray
    }

    Write-Host ""
    Write-Section "TONG KET" Cyan
    Write-Host "   Da tai       : $($s.Downloaded) mod, $(Format-Size $s.Bytes)" -ForegroundColor White
    Write-Host "   Da xoa       : $($s.Deleted) mod" -ForegroundColor White
    Write-Host "   Thoi gian    : $($s.Seconds)s" -ForegroundColor White
    if ($s.Failed.Count -gt 0) {
        Write-Host "   That bai     : $($s.Failed.Count) -> $($s.Failed -join ', ')" -ForegroundColor Red
        Write-Host "   -> Chay lai script de thu lai." -ForegroundColor Yellow
    }
    Write-Host ""
    Write-Divider '=' Green
    if ($s.Failed.Count -gt 0) {
        Write-Centered "DONG BO HOAN TAT (CO $($s.Failed.Count) LOI)" Yellow
    } else {
        Write-Centered "HOAN THANH DONG BO HOA MODS" Green
    }
    Write-Centered "Chuc ban choi game vui ve!" Green
    Write-Divider '=' Green
}

# ===================================================================================
#  GUI MODE (WPF)
# ===================================================================================
Show-Logo

$guiLoaded = $false
$isSta = [System.Threading.Thread]::CurrentThread.GetApartmentState() -eq 'STA'
if ($guiSupported -and $isSta -and -not $NoGui -and [System.Environment]::UserInteractive) {
    try {
        [xml]$xaml = @'
<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        Title="OlongBell - Mod Updater" Height="520" Width="680"
        Background="#18181B" Foreground="#F4F4F5"
        WindowStartupLocation="CenterScreen" ResizeMode="CanMinimize" FontFamily="Segoe UI">
    <Window.Resources>
        <Style TargetType="Button">
            <Setter Property="Background" Value="#27272A"/>
            <Setter Property="Foreground" Value="#F4F4F5"/>
            <Setter Property="BorderThickness" Value="0"/>
            <Setter Property="Padding" Value="10,5"/>
            <Setter Property="FontWeight" Value="SemiBold"/>
            <Setter Property="Cursor" Value="Hand"/>
        </Style>
        <Style TargetType="RadioButton">
            <Setter Property="Foreground" Value="#D4D4D8"/>
            <Setter Property="Margin" Value="0,5,15,5"/>
        </Style>
    </Window.Resources>

    <Grid Margin="25">
        <Grid.RowDefinitions>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="*"/>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="Auto"/>
        </Grid.RowDefinitions>

        <StackPanel Grid.Row="0" Margin="0,0,0,15">
            <StackPanel Orientation="Horizontal">
                <Border Background="#3B82F6" Width="4" Margin="0,0,10,0"/>
                <StackPanel>
                    <TextBlock Text="OLONGBELL" FontSize="22" FontWeight="Bold" Foreground="#3B82F6"/>
                    <TextBlock Text="MOD UPDATER v26.2 - Dong bo &amp; cap nhat mod 1-Click" FontSize="12" Foreground="#A1A1AA" Margin="0,2,0,0"/>
                </StackPanel>
            </StackPanel>
        </StackPanel>

        <GroupBox Grid.Row="1" Header="Chon Launcher" BorderBrush="#27272A" Foreground="#A1A1AA" Padding="10" Margin="0,0,0,10">
            <WrapPanel>
                <RadioButton Name="radLegacy" Content="Legacy Launcher" IsChecked="True"/>
                <RadioButton Name="radPremium" Content="Official (Ban Quyen)"/>
                <RadioButton Name="radTLauncher" Content="TLauncher"/>
                <RadioButton Name="radCustom" Content="Duong dan tu chon"/>
            </WrapPanel>
        </GroupBox>

        <Grid Grid.Row="2" Margin="0,0,0,15">
            <Grid.ColumnDefinitions>
                <ColumnDefinition Width="*"/>
                <ColumnDefinition Width="Auto"/>
            </Grid.ColumnDefinitions>
            <TextBox Name="txtPath" Grid.Column="0" Height="28" Padding="5,3" Background="#09090B" Foreground="#F4F4F5" BorderBrush="#27272A" VerticalContentAlignment="Center" IsReadOnly="True"/>
            <Button Name="btnBrowse" Grid.Column="1" Content="Duyet..." Margin="5,0,0,0" Width="80" Height="28" IsEnabled="False"/>
        </Grid>

        <GroupBox Grid.Row="3" Header="Nhat ky cap nhat" BorderBrush="#27272A" Foreground="#A1A1AA" Margin="0,0,0,15">
            <TextBox Name="txtLog" Background="#09090B" Foreground="#E4E4E7" BorderThickness="0" AcceptsReturn="True" VerticalScrollBarVisibility="Auto" HorizontalScrollBarVisibility="Auto" IsReadOnly="True" Padding="8" FontFamily="Consolas" FontSize="11"/>
        </GroupBox>

        <Grid Grid.Row="4" Margin="0,0,0,15">
            <Grid.ColumnDefinitions>
                <ColumnDefinition Width="*"/>
                <ColumnDefinition Width="Auto"/>
            </Grid.ColumnDefinitions>
            <ProgressBar Name="progress" Grid.Column="0" Height="15" Background="#27272A" Foreground="#10B981" BorderThickness="0"/>
            <TextBlock Name="txtProgressVal" Grid.Column="1" Text="0/0" VerticalAlignment="Center" Margin="10,0,0,0" FontWeight="Bold" Foreground="#10B981"/>
        </Grid>

        <Grid Grid.Row="5">
            <Grid.ColumnDefinitions>
                <ColumnDefinition Width="*"/>
                <ColumnDefinition Width="Auto"/>
            </Grid.ColumnDefinitions>
            <TextBlock Name="txtStatus" Grid.Column="0" Text="Nhan nut de bat dau dong bo..." VerticalAlignment="Center" Foreground="#A1A1AA" FontSize="12" TextTrimming="CharacterEllipsis"/>
            <Button Name="btnStart" Grid.Column="1" Content="Kiem tra &amp; Cap nhat" Background="#3B82F6" Foreground="White" Width="180" Height="35" FontSize="13"/>
        </Grid>
    </Grid>
</Window>
'@
        $reader = New-Object System.Xml.XmlNodeReader $xaml
        $window = [Windows.Markup.XamlReader]::Load($reader)
        $guiLoaded = $true
    } catch {
        Write-Host " [GUI] Khong mo duoc giao dien ($($_.Exception.Message)) -> dung che do console." -ForegroundColor DarkGray
    }
}

if ($guiLoaded) {
    $xaml.SelectNodes("//*[@Name]") | ForEach-Object {
        Set-Variable -Name $_.Name -Value $window.FindName($_.Name) -Scope Script
    }

    $txtPath.Text = $legacyPath
    $divider = "=" * 78
    $txtLog.Text = ((Get-LogoLines) -join "`r`n") + "`r`n$divider`r`nSan sang cap nhat mod... Click 'Kiem tra & Cap nhat' de bat dau.`r`n$divider`r`n"

    $radLegacy.add_Checked({ $txtPath.Text = $legacyPath; $btnBrowse.IsEnabled = $false })
    $radPremium.add_Checked({ $txtPath.Text = $premiumPath; $btnBrowse.IsEnabled = $false })
    $radTLauncher.add_Checked({ $txtPath.Text = $tlauncherPath; $btnBrowse.IsEnabled = $false })
    $radCustom.add_Checked({ $btnBrowse.IsEnabled = $true })

    $btnBrowse.add_Click({
        Add-Type -AssemblyName System.Windows.Forms
        $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
        $dialog.Description = "Chon thu muc mods cua game Minecraft"
        if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
            $txtPath.Text = $dialog.SelectedPath
        }
    })

    # Cho WPF xu ly su kien de cua so khong bi "Not Responding" trong luc dong bo
    function Update-UI {
        [System.Windows.Threading.Dispatcher]::CurrentDispatcher.Invoke([System.Action] {}, [System.Windows.Threading.DispatcherPriority]::Background)
    }

    function Set-ControlsEnabled([bool]$on) {
        $btnStart.IsEnabled = $on
        $radLegacy.IsEnabled = $on
        $radPremium.IsEnabled = $on
        $radTLauncher.IsEnabled = $on
        $radCustom.IsEnabled = $on
        $btnBrowse.IsEnabled = ($on -and $radCustom.IsChecked)
    }

    $btnStart.add_Click({
        Set-ControlsEnabled $false
        $txtStatus.Text = "Dang kiem tra cap nhat..."
        $txtLog.Text = "--- BAT DAU CAP NHAT ---`r`n"
        $progress.Value = 0
        $txtProgressVal.Text = "..."
        Update-UI

        $log = { param($msg, $color) $txtLog.AppendText("$msg`r`n"); $txtLog.ScrollToEnd(); Update-UI }
        $tick = { Update-UI }
        $prog = {
            param($done, $n, $name)
            $progress.Maximum = [Math]::Max(1, $n)
            $progress.Value = $done
            $txtProgressVal.Text = "$done/$n"
            if ($name) { $txtStatus.Text = "Xong: $name" }
            Update-UI
        }

        try {
            $s = Invoke-ModSync -TargetDir $txtPath.Text -Parallel $Parallel -Log $log -OnTick $tick -OnProgress $prog
            if ($s.RepoCount -eq 0) {
                $txtStatus.Text = "Loi ket noi GitHub."
            } else {
                & $log "`r`n--- TONG KET ---" White
                & $log "Da tai $($s.Downloaded) mod ($(Format-Size $s.Bytes)), xoa $($s.Deleted) mod, trong $($s.Seconds)s" White
                if ($s.Failed.Count -gt 0) {
                    & $log "That bai: $($s.Failed -join ', ')" Red
                    $txtStatus.Text = "Hoan tat, con $($s.Failed.Count) loi."
                    [System.Windows.MessageBox]::Show("Dong bo xong nhung con $($s.Failed.Count) loi. Xem log, tat Minecraft neu dang mo, roi bam lai.", "Hoan tat co loi", [System.Windows.MessageBoxButton]::OK, [System.Windows.MessageBoxImage]::Warning) | Out-Null
                } else {
                    $txtStatus.Text = "Cap nhat thanh cong!"
                    [System.Windows.MessageBox]::Show("Dong bo hoa mods Minecraft thanh cong!", "Thong bao", [System.Windows.MessageBoxButton]::OK, [System.Windows.MessageBoxImage]::Information) | Out-Null
                }
            }
        } catch {
            & $log "[LOI NGHIEM TRONG] $_" Red
            $txtStatus.Text = "Gap loi trong qua trinh cap nhat."
        }
        Set-ControlsEnabled $true
    })

    $window.ShowDialog() | Out-Null
} else {
    Run-ConsoleUpdate
}
