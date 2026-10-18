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
        # Hien tung dong cho co chuyen dong (bo qua khi output bi redirect)
        $animate = $true
        try { $animate = -not [Console]::IsOutputRedirected } catch { }
        for ($i = 0; $i -lt $logo.Count; $i++) {
            Write-Centered $logo[$i] $colors[$i % $colors.Count]
            if ($animate) { Start-Sleep -Milliseconds 55 }
        }
    }
    Write-Centered "OlongBell Minecraft Server" DarkGray
    Write-Centered "made by @stuwupidwjbu" DarkGray
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

# Cho $ms mili-giay. GUI gan WaitHook de vua cho vua bom su kien WPF (animation muot ~60fps)
$script:WaitHook = $null
function Invoke-Wait([int]$ms) {
    if ($script:WaitHook) { & $script:WaitHook $ms } else { Start-Sleep -Milliseconds $ms }
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
            Invoke-Wait 80
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
            $toDownload += [pscustomobject]@{ File = $rm; Reason = "new" }
        } elseif ($lf.Length -ne $rm.size) {
            $toDownload += [pscustomobject]@{ File = $rm; Reason = "size changed" }
        } elseif ((Get-LocalSha $lf $cache $newCache) -ne $rm.sha) {
            $toDownload += [pscustomobject]@{ File = $rm; Reason = "content modified" }
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

# Worker tai 1 file: doc stream tung khoi 64KB, ghi so byte da nhan vao bang $Progress
# (dung chung giua cac luong) -> UI ve thanh tien do theo byte that, ke ca file lon.
# Tai ra .tmp -> kiem dung luong + SHA -> doi ten. Thu lai toi da N lan.
$DownloadWorker = {
    param($HelperCode, $Url, $DestDir, $Name, $Size, $Sha, $MaxRetries, $UserAgent, $Progress)
    . ([scriptblock]::Create($HelperCode))
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    $dest = Join-Path $DestDir $Name
    $tmp = "$dest.tmp"
    $err = ""
    for ($a = 1; $a -le $MaxRetries; $a++) {
        $Progress[$Name] = [long]0
        try {
            if ($a -gt 1) { Start-Sleep -Seconds ([Math]::Min(2 * ($a - 1), 5)) }
            $req = [System.Net.HttpWebRequest]::Create($Url)
            $req.UserAgent = $UserAgent
            $req.Timeout = 30000
            $req.ReadWriteTimeout = 30000
            $resp = $req.GetResponse()
            $in = $resp.GetResponseStream()
            $out = [System.IO.File]::Create($tmp)
            try {
                $buf = New-Object byte[] 65536
                $got = [long]0
                while (($n = $in.Read($buf, 0, $buf.Length)) -gt 0) {
                    $out.Write($buf, 0, $n)
                    $got += $n
                    $Progress[$Name] = $got
                }
            } finally {
                $out.Dispose()
                $in.Dispose()
                $resp.Dispose()
            }
            $len = (Get-Item -LiteralPath $tmp).Length
            if ($Size -gt 0 -and $len -ne $Size) { throw "Size mismatch ($len / $Size bytes) - connection dropped?" }
            if ($Sha -and (Get-GitBlobSha $tmp) -ne $Sha) { throw "SHA mismatch - downloaded file differs from the repo" }
            if (Test-Path -LiteralPath $dest) { Remove-Item -LiteralPath $dest -Force -ErrorAction Stop }
            Rename-Item -LiteralPath $tmp -NewName $Name -ErrorAction Stop
            $fi = Get-Item -LiteralPath $dest
            $Progress[$Name] = [long]$len
            return [pscustomobject]@{ Name = $Name; Success = $true; Bytes = $len; Attempts = $a; Error = ""; Sha = $Sha; Size = $fi.Length; Ticks = $fi.LastWriteTimeUtc.Ticks }
        } catch {
            $err = $_.Exception.Message
            if ($err -match "being used by another process") { $err = "File is locked - close Minecraft and run again" }
            if (Test-Path -LiteralPath $tmp) { Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue }
        }
    }
    $Progress[$Name] = [long]0
    return [pscustomobject]@{ Name = $Name; Success = $false; Bytes = 0; Attempts = $MaxRetries; Error = $err }
}

# Tai song song bang RunspacePool.
# $OnDone(result, done, total) moi khi xong 1 file; $OnBytes(bytesDone) moi vong lap (~10 lan/giay).
function Invoke-ParallelDownloads {
    param($Items, $RepoInfo, [string]$TargetDir, [int]$Throttle = 4, [scriptblock]$OnDone, [scriptblock]$OnTick, [scriptblock]$OnBytes)
    $results = @()
    if ($Items.Count -eq 0) { return , $results }
    $progress = [hashtable]::Synchronized(@{})
    $pool = [runspacefactory]::CreateRunspacePool(1, [Math]::Max(1, $Throttle))
    $pool.Open()
    try {
        $jobs = foreach ($it in $Items) {
            $f = $it.File
            $ps = [powershell]::Create()
            $ps.RunspacePool = $pool
            [void]$ps.AddScript($DownloadWorker)
            foreach ($arg in @($HelperCode, (Get-RawUrl $RepoInfo $f.name), $TargetDir, $f.name, [long]$f.size, $f.sha, 3, $UserAgent, $progress)) { [void]$ps.AddArgument($arg) }
            [pscustomobject]@{ PS = $ps; Handle = $ps.BeginInvoke(); Name = $f.name; Done = $false }
        }
        $done = 0
        while ($done -lt $jobs.Count) {
            foreach ($j in $jobs) {
                if ($j.Done -or -not $j.Handle.IsCompleted) { continue }
                $r = $null
                try { $r = $j.PS.EndInvoke($j.Handle) | Select-Object -Last 1 } catch { }
                if ($null -eq $r) {
                    $msg = if ($j.PS.Streams.Error.Count -gt 0) { $j.PS.Streams.Error[0].ToString() } else { "Unknown error" }
                    $r = [pscustomobject]@{ Name = $j.Name; Success = $false; Bytes = 0; Attempts = 0; Error = $msg }
                }
                $j.PS.Dispose()
                $j.Done = $true
                $done++
                $results += $r
                if ($OnDone) { & $OnDone $r $done $jobs.Count }
            }
            if ($OnBytes) {
                # Khoa SyncRoot khi doc: cac luong tai dang ghi vao bang cung luc
                $vals = $null
                [System.Threading.Monitor]::Enter($progress.SyncRoot)
                try {
                    $vals = New-Object object[] $progress.Count
                    $progress.Values.CopyTo($vals, 0)
                } finally {
                    [System.Threading.Monitor]::Exit($progress.SyncRoot)
                }
                $sum = [long]0
                foreach ($v in $vals) { $sum += [long]$v }
                & $OnBytes $sum
            }
            if ($OnTick) { & $OnTick }
            Invoke-Wait 100
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

# Toan bo qua trinh dong bo.
#   $Log(msg, color)    $OnTick()    $OnPhase(connect|scan|download|done|error)
#   $OnProgress(filesDone, filesTotal, lastName, bytesDone, bytesTotal)
function Invoke-ModSync {
    param([string]$TargetDir, [int]$Parallel = 4, [scriptblock]$Log, [scriptblock]$OnTick, [scriptblock]$OnProgress, [scriptblock]$OnPhase)
    $phase = { param($p) if ($OnPhase) { & $OnPhase $p } }
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $summary = [pscustomobject]@{ Ok = $false; Downloaded = 0; Bytes = 0; Deleted = 0; Failed = @(); Seconds = 0; RepoCount = 0 }

    if (Get-Process -Name javaw -ErrorAction SilentlyContinue) {
        & $Log "[WARN] Minecraft (javaw) is running - mods in use will be locked. Close the game first." Yellow
    }
    if (-not (Test-Path -LiteralPath $TargetDir)) {
        & $Log "[DIR] Creating folder: $TargetDir" Gray
        New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
    }

    & $phase "connect"
    & $Log "Connecting to GitHub..." Cyan
    try {
        $repoInfo = Get-RepoModList -OnTick $OnTick
    } catch {
        $m = "$_"
        if ($m -match "403|rate limit") { $m = "GitHub allows 60 requests/hour per IP. Wait about an hour and try again." }
        & $Log "[ERROR] Could not read the mod list: $m" Red
        & $phase "error"
        return $summary
    }
    if (-not $repoInfo.Files -or $repoInfo.Files.Count -eq 0) {
        & $Log "[ERROR] The mods folder on GitHub is empty." Red
        & $phase "error"
        return $summary
    }
    $refShort = "$($repoInfo.Ref)"; if ($refShort.Length -gt 7) { $refShort = $refShort.Substring(0, 7) }
    & $Log "[OK] Connected to GitHub (version $refShort)." Green

    & $phase "scan"
    & $Log "Checking local mods (SHA compare, slower on first run)..." Cyan
    $plan = Get-SyncPlan -RepoInfo $repoInfo -TargetDir $TargetDir -OnTick $OnTick
    $summary.RepoCount = $plan.RepoMods.Count
    & $Log "[STATS] Server: $($plan.RepoMods.Count) mods | Your PC: $($plan.LocalFiles.Count) mods" Green
    & $Log "[SYNC] To download: $($plan.ToDownload.Count) ($($plan.Changed) modified) | To delete: $($plan.ToDelete.Count)" Yellow

    foreach ($df in $plan.ToDelete) {
        try {
            Remove-Item -LiteralPath $df.FullName -Force -ErrorAction Stop
            $plan.Cache.Remove($df.Name)
            $summary.Deleted++
            & $Log "  - Deleted: $($df.Name)" DarkRed
        } catch {
            & $Log "  [X] Could not delete $($df.Name) (close Minecraft and run again)" Red
            $summary.Failed += $df.Name
        }
    }

    if ($plan.ToDownload.Count -gt 0) {
        $total = [long](($plan.ToDownload | ForEach-Object { [long]$_.File.size } | Measure-Object -Sum).Sum)
        $n = $plan.ToDownload.Count
        & $Log "Downloading $n mods ($(Format-Size $total)), $Parallel at a time..." Cyan
        foreach ($it in $plan.ToDownload) {
            if ($it.Reason -ne "new") { & $Log "  * $($it.File.name): $($it.Reason)" DarkGray }
        }
        & $phase "download"
        $script:syncState = @{ Done = 0; Last = ""; Bytes = [long]0 }
        if ($OnProgress) { & $OnProgress 0 $n "" 0 $total }
        $results = Invoke-ParallelDownloads -Items $plan.ToDownload -RepoInfo $repoInfo -TargetDir $TargetDir -Throttle $Parallel -OnTick $OnTick -OnDone {
            param($r, $done, $cnt)
            $script:syncState.Done = $done
            $script:syncState.Last = $r.Name
            if ($r.Success) {
                $retry = if ($r.Attempts -gt 1) { " (attempt $($r.Attempts))" } else { "" }
                & $Log "  -> [$done/$cnt] $($r.Name) ($(Format-Size $r.Bytes))$retry" White
            } else {
                & $Log "  [X] [$done/$cnt] $($r.Name): $($r.Error)" Red
            }
        } -OnBytes {
            param($bytes)
            $script:syncState.Bytes = $bytes
            if ($OnProgress) { & $OnProgress $script:syncState.Done $n $script:syncState.Last $bytes $total }
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
        if ($OnProgress) { & $OnProgress $n $n "" $total $total }
    } elseif ($OnProgress) {
        & $OnProgress 1 1 "" 1 1
    }

    Write-ShaCache $TargetDir $plan.Cache
    $sw.Stop()
    $summary.Seconds = [Math]::Round($sw.Elapsed.TotalSeconds, 1)
    $summary.Ok = ($summary.Failed.Count -eq 0)
    & $phase $(if ($summary.Ok) { "done" } else { "error" })
    return $summary
}

# ===================================================================================
#  TU DONG TIM THU MUC GAME (khong hardcode o C)
# ===================================================================================
#  Nguon: (1) thu muc da chon lan truoc, (2) cau hinh launcher (TLauncher/Legacy
#  *.properties co "gamedir", launcher_profiles.json cua launcher chinh hang),
#  (3) quet nhanh moi o cung (C, D, E...) toi da 4 cap tim thu muc game Minecraft
#  (co "versions" + "mods"/"options.txt"), (4) duong dan mac dinh.
# ===================================================================================
$UpdaterConfigPath = Join-Path $env:APPDATA "OlongBell\mod-updater.json"

# Code dang chuoi de chay duoc trong runspace nen (GUI van chay animation khi dang quet)
$DetectCode = @'
function Get-LauncherLabel([string]$path) {
    $p = $path.ToLower()
    if ($p -like '*\.tlauncher\legacy\*') { return 'Legacy Launcher' }
    if ($p -like '*atlauncher*') { return 'ATLauncher' }
    if ($p -like '*tlauncher*') { return 'TLauncher' }
    if ($p -like '*curseforge*') { return 'CurseForge' }
    if ($p -like '*modrinth*' -or $p -like '*theseus*') { return 'Modrinth' }
    if ($p -like '*prismlauncher*' -or $p -like '*polymc*') { return 'Prism Launcher' }
    if ($p -like '*multimc*') { return 'MultiMC' }
    if ($p -like '*gdlauncher*') { return 'GDLauncher' }
    if ($env:APPDATA -and $p.StartsWith((Join-Path $env:APPDATA '.minecraft').ToLower())) { return 'Minecraft (default)' }
    return "Folder on $($path.Substring(0, 2).ToUpper())"
}

# Thu muc game CLIENT? Launcher thuong: versions + (mods|options.txt).
# Launcher dang "instance" (CurseForge, Modrinth, Prism...) khong co versions trong
# instance: chap nhan mods + (options.txt|config). Thu muc server thi loai.
function Test-GameDir([string]$dir) {
    try {
        if ([IO.File]::Exists((Join-Path $dir 'server.properties'))) { return $false }
        $hasMods = [IO.Directory]::Exists((Join-Path $dir 'mods'))
        $hasOptions = [IO.File]::Exists((Join-Path $dir 'options.txt'))
        if ([IO.Directory]::Exists((Join-Path $dir 'versions'))) { return ($hasMods -or $hasOptions) }
        return ($hasMods -and ($hasOptions -or [IO.Directory]::Exists((Join-Path $dir 'config'))))
    } catch { return $false }
}

# Cac goc "instances" cua launcher pho bien; moi instance co the de game o
# <inst>, <inst>\.minecraft, <inst>\minecraft hoac <inst>\instance
function Get-InstanceRoots {
    $a = $env:APPDATA
    $u = $env:USERPROFILE
    return @(
        (Join-Path $a 'PrismLauncher\instances'),
        (Join-Path $a 'PolyMC\instances'),
        (Join-Path $a 'ATLauncher\instances'),
        (Join-Path $a 'ModrinthApp\profiles'),
        (Join-Path $a 'com.modrinth.theseus\profiles'),
        (Join-Path $a 'gdlauncher_next\instances'),
        (Join-Path $a 'gdlauncher_carbon\data\instances'),
        (Join-Path $u 'curseforge\minecraft\Instances'),
        (Join-Path $u 'Documents\Curse\Minecraft\Instances')
    )
}

function Find-ModFolders([string]$lastPath, [string[]]$ScanRoots) {
    $seen = @{}
    $found = New-Object System.Collections.Generic.List[object]
    $add = {
        param([string]$modsDir, [string]$source)
        if ([string]::IsNullOrWhiteSpace($modsDir)) { return }
        try { $full = [IO.Path]::GetFullPath($modsDir.Trim().Trim('"')).TrimEnd('\') } catch { return }
        $key = $full.ToLower()
        if ($seen.ContainsKey($key)) { return }
        $count = 0
        if ([IO.Directory]::Exists($full)) {
            try { $count = @([IO.Directory]::GetFiles($full, '*.jar')).Count } catch { }
        } elseif (-not [IO.Directory]::Exists((Split-Path $full -Parent))) {
            return
        }
        $seen[$key] = $true
        $found.Add([pscustomobject]@{ Path = $full; Label = (Get-LauncherLabel $full); Mods = $count; Source = $source })
    }
    # Kiem 1 thu muc "instance": game o chinh no hoac 1 cap con quen thuoc
    $addInstance = {
        param([string]$inst, [string]$source)
        foreach ($cand in @($inst, (Join-Path $inst '.minecraft'), (Join-Path $inst 'minecraft'), (Join-Path $inst 'instance'))) {
            if (Test-GameDir $cand) { & $add (Join-Path $cand 'mods') $source; return }
        }
    }

    if ($lastPath) { & $add $lastPath 'last used' }

    # 1. Cau hinh launcher
    $tl = Join-Path $env:APPDATA '.tlauncher'
    if (Test-Path -LiteralPath $tl) {
        Get-ChildItem -LiteralPath $tl -Filter '*.properties' -Recurse -Depth 3 -File -ErrorAction SilentlyContinue | ForEach-Object {
            Get-Content -LiteralPath $_.FullName -ErrorAction SilentlyContinue | ForEach-Object {
                if ($_ -match '^\s*[\w.\-]*game\.?dir[\w.\-]*\s*[=:]\s*(.+)$') {
                    # .properties escape: "D\:\\Games" -> "D:\Games"
                    $dir = $Matches[1].Trim() -replace '\\(.)', '$1'
                    if ($dir -match '^[A-Za-z]:') { & $add (Join-Path $dir 'mods') 'launcher config' }
                }
            }
        }
    }
    $profiles = Join-Path $env:APPDATA '.minecraft\launcher_profiles.json'
    if (Test-Path -LiteralPath $profiles) {
        try {
            $j = Get-Content -LiteralPath $profiles -Raw -ErrorAction Stop | ConvertFrom-Json
            foreach ($p in $j.profiles.PSObject.Properties) {
                if ($p.Value.gameDir) { & $add (Join-Path $p.Value.gameDir 'mods') 'launcher config' }
            }
        } catch { }
    }

    # 2. Instance cua cac launcher pho bien
    foreach ($root in (Get-InstanceRoots)) {
        if (-not [IO.Directory]::Exists($root)) { continue }
        try {
            foreach ($inst in [IO.Directory]::EnumerateDirectories($root)) { & $addInstance $inst 'launcher instances' }
        } catch { }
    }

    # 3. Quet o cung: BFS toi da 5 cap. Moi o co ngan sach rieng (thoi gian + so thu muc)
    #    de o C nhieu file khong an het thoi gian cua o D. O khac C quet truoc.
    #    Gap thu muc "instances" thi kiem tung instance ben trong (MultiMC/Prism portable).
    if (-not $ScanRoots) {
        $sys = "$env:SystemDrive\".ToUpper()
        $drives = @([IO.DriveInfo]::GetDrives() | Where-Object { $_.DriveType -eq 'Fixed' -and $_.IsReady } | ForEach-Object { $_.RootDirectory.FullName })
        $ScanRoots = @($drives | Where-Object { $_.ToUpper() -ne $sys }) + @($drives | Where-Object { $_.ToUpper() -eq $sys })
    }
    $skip = @('windows', 'program files', 'program files (x86)', 'programdata', '$recycle.bin', 'system volume information',
              'node_modules', '.git', 'steamapps', 'appdata', 'recovery', 'perflogs', 'msocache', 'intel', 'amd', 'nvidia',
              '$windows.~bt', '$windows.~ws', 'windows.old', 'onedrivetemp', '.cache', '.gradle', '.m2', '.nuget', 'site-packages')
    foreach ($rootDir in $ScanRoots) {
        $deadline = [DateTime]::UtcNow.AddSeconds(3.5)
        $queue = New-Object System.Collections.Generic.Queue[object]
        $queue.Enqueue(@($rootDir, 0))
        $visited = 0
        while ($queue.Count -gt 0 -and $visited -lt 60000 -and [DateTime]::UtcNow -lt $deadline) {
            $item = $queue.Dequeue()
            $dir = $item[0]
            $depth = $item[1]
            $visited++
            # Doc thu muc con 1 lan; chi kiem ky (doc them file) khi thay mods/versions
            try { $subs = [IO.Directory]::GetDirectories($dir) } catch { continue }
            $names = @{}
            foreach ($sub in $subs) { $names[[IO.Path]::GetFileName($sub).ToLower()] = $true }
            if ($depth -gt 0) {
                if (($names.ContainsKey('mods') -or $names.ContainsKey('versions')) -and (Test-GameDir $dir)) {
                    & $add (Join-Path $dir 'mods') 'disk scan'
                    continue
                }
                $leaf = [IO.Path]::GetFileName($dir).ToLower()
                if ($leaf -eq 'instances' -or $leaf -eq 'profiles') {
                    foreach ($inst in $subs) { & $addInstance $inst 'disk scan' }
                    continue
                }
            }
            if ($depth -ge 5) { continue }
            foreach ($sub in $subs) {
                if ($skip -contains [IO.Path]::GetFileName($sub).ToLower()) { continue }
                $queue.Enqueue(@($sub, ($depth + 1)))
            }
        }
    }

    # 4. Mac dinh (van hien de nguoi moi chua co thu muc nao)
    & $add (Join-Path $env:APPDATA '.tlauncher\legacy\Minecraft\game\mods') 'default'
    & $add (Join-Path $env:APPDATA '.minecraft\mods') 'default'

    # Thu muc lan truoc dung dau, sau do uu tien thu muc co nhieu mod
    $last = @($found | Where-Object { $_.Source -eq 'last used' })
    $rest = @($found | Where-Object { $_.Source -ne 'last used' } | Sort-Object @{ Expression = 'Mods'; Descending = $true })
    return @($last + $rest)
}
'@
. ([scriptblock]::Create($DetectCode))

function Get-LastModsPath {
    try { return (Get-Content -LiteralPath $UpdaterConfigPath -Raw -ErrorAction Stop | ConvertFrom-Json).LastPath } catch { return $null }
}
function Save-LastModsPath([string]$path) {
    try {
        $dir = Split-Path $UpdaterConfigPath -Parent
        if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        (@{ LastPath = $path } | ConvertTo-Json) | Set-Content -LiteralPath $UpdaterConfigPath -Encoding ASCII
    } catch { }
}

# Quet trong runspace nen ($OnTick giu spinner / animation chay)
function Find-ModFoldersAsync([scriptblock]$OnTick) {
    $r = Invoke-InBackground -OnTick $OnTick -Arguments @($DetectCode, (Get-LastModsPath)) -Script {
        param($code, $last)
        . ([scriptblock]::Create($code))
        Find-ModFolders $last
    }
    return @($r | Where-Object { $_ })
}

# ===================================================================================
#  CONSOLE MODE
# ===================================================================================
function Run-ConsoleUpdate($targetDir) {
    Write-Banner "MOD UPDATER v26.2" "Automatic mod sync for the OlongBell server (console)" Cyan

    if ([string]::IsNullOrEmpty($targetDir)) {
        $script:spin = 0
        $sp = @('|', '/', '-', [string][char]92)
        $cands = Find-ModFoldersAsync -OnTick { Write-Host -NoNewline "`r $($sp[$script:spin % 4]) Looking for Minecraft folders on all drives..." -ForegroundColor Cyan; $script:spin++ }
        Write-Host -NoNewline ("`r" + (' ' * ((Get-ConsoleWidth) - 1)) + "`r")
        Write-Host " Pick your mods folder (found $($cands.Count)):" -ForegroundColor Yellow
        for ($i = 0; $i -lt $cands.Count; $i++) {
            $c = $cands[$i]
            Write-Host ("  [{0}] {1,-20} {2} ({3} mods)" -f ($i + 1), $c.Label, $c.Path, $c.Mods) -ForegroundColor Gray
        }
        Write-Host "  [C] Enter another path" -ForegroundColor Gray
        $choice = Read-Host " Your choice (Enter = [1])"

        if ($choice -match '^[cC]$') {
            $targetDir = (Read-Host " Full path of your mods folder").Trim().Trim('"')
            if (-not $targetDir -or -not (Test-Path -LiteralPath (Split-Path $targetDir -Parent))) {
                Write-Host " [ERROR] That path does not exist!" -ForegroundColor Red
                return
            }
        } else {
            $idx = 0
            if ($choice -match '^\d+$') { $idx = [int]$choice - 1 }
            if ($idx -lt 0 -or $idx -ge $cands.Count) { $idx = 0 }
            $targetDir = if ($cands.Count -gt 0) { $cands[$idx].Path } else { $legacyPath }
        }
    }
    Save-LastModsPath $targetDir

    Write-Divider '-' DarkGray
    Write-Host " [DIR] Target folder: " -ForegroundColor Yellow -NoNewline
    Write-Host $targetDir -ForegroundColor White
    Write-Divider '-' DarkGray

    # Thanh tien do theo byte, chay tai cho o dong cuoi; log in phia tren.
    # Ky tu khoi lay bang [char] luc chay de source van thuan ASCII (xem Get-LogoLines)
    $script:spin = 0
    $script:bar = $null
    $spinner = @('|', '/', '-', [string][char]92)
    $full = [char]0x2588
    $light = [char]0x2591
    $clearLine = { Write-Host -NoNewline ("`r" + (' ' * ((Get-ConsoleWidth) - 1)) + "`r") }
    $drawBar = {
        $sp = $spinner[$script:spin % $spinner.Count]
        if (-not $script:bar) {
            Write-Host -NoNewline "`r $sp " -ForegroundColor Cyan
            return
        }
        $b = $script:bar
        $w = Get-ConsoleWidth
        $bw = [Math]::Max(10, [Math]::Min(36, $w - 48))
        $ratio = [Math]::Min(1.0, $b.Shown / [Math]::Max(1, $b.Total))
        $filled = [int][Math]::Floor($bw * $ratio)
        Write-Host -NoNewline "`r $sp " -ForegroundColor Cyan
        Write-Host -NoNewline ([string]$full * $filled) -ForegroundColor Green
        Write-Host -NoNewline ([string]$light * ($bw - $filled)) -ForegroundColor DarkGray
        Write-Host -NoNewline (" {0,3}%  {1}/{2}  {3}/s  [{4}/{5}] " -f [int]($ratio * 100), (Format-Size $b.Bytes), (Format-Size $b.Total), (Format-Size $b.Speed), $b.Done, $b.N) -ForegroundColor White
    }
    $log = { param($msg, $color) & $clearLine; Write-Host " $msg" -ForegroundColor $color; & $drawBar }
    # Moi tick: xoay spinner + thanh "duoi" muot toi gia tri that
    $tick = {
        $script:spin++
        if ($script:bar) { $script:bar.Shown += ($script:bar.Bytes - $script:bar.Shown) * 0.35 }
        & $drawBar
    }
    $prog = {
        param($done, $n, $name, $bytes, $total)
        $now = [DateTime]::UtcNow
        if (-not $script:bar) { $script:bar = @{ Shown = 0.0; Speed = 0.0; T = $now; B = [long]0 } }
        $b = $script:bar
        $dt = ($now - $b.T).TotalSeconds
        if ($dt -ge 0.5) {
            $inst = ($bytes - $b.B) / $dt
            $b.Speed = if ($b.Speed -eq 0) { $inst } else { $b.Speed * 0.7 + $inst * 0.3 }
            $b.T = $now
            $b.B = $bytes
        }
        $b.Done = $done; $b.N = $n; $b.Bytes = $bytes; $b.Total = $total
    }

    try {
        $s = Invoke-ModSync -TargetDir $targetDir -Parallel $Parallel -Log $log -OnTick $tick -OnProgress $prog
    } catch {
        & $clearLine
        Write-Host " [FATAL] $_" -ForegroundColor Red
        return
    }
    if ($script:bar -and $script:bar.N -gt 1) { $script:bar.Shown = $script:bar.Total; & $drawBar; Write-Host "" }
    & $clearLine
    $script:bar = $null
    if ($s.RepoCount -eq 0) { return }

    $animate = $true
    try { $animate = -not [Console]::IsOutputRedirected } catch { }
    Write-Divider '-' DarkGray
    $finalLocalFiles = @(Get-ChildItem -LiteralPath $targetDir -File | Where-Object { Test-ModFileName $_.Name } | Sort-Object Name)
    Write-Section "MODS ON YOUR PC ($($finalLocalFiles.Count))" Green
    $w = Get-ConsoleWidth
    $colWidth = [Math]::Max(20, [int](($w - 9) / 2))
    for ($idx = 0; $idx -lt $finalLocalFiles.Count; $idx += 2) {
        $col1 = $finalLocalFiles[$idx].Name
        $col2 = if ($idx + 1 -lt $finalLocalFiles.Count) { $finalLocalFiles[$idx + 1].Name } else { "" }
        if ($col1.Length -gt $colWidth) { $col1 = $col1.Substring(0, $colWidth - 3) + "..." }
        if ($col2.Length -gt $colWidth) { $col2 = $col2.Substring(0, $colWidth - 3) + "..." }
        Write-Host "   - $($col1.PadRight($colWidth)) - $col2" -ForegroundColor DarkGray
        if ($animate) { Start-Sleep -Milliseconds 8 }
    }

    Write-Host ""
    Write-Section "SUMMARY" Cyan
    Write-Host "   Downloaded : $($s.Downloaded) mods, $(Format-Size $s.Bytes)" -ForegroundColor White
    Write-Host "   Deleted    : $($s.Deleted) mods" -ForegroundColor White
    Write-Host "   Time       : $($s.Seconds)s" -ForegroundColor White
    if ($s.Failed.Count -gt 0) {
        Write-Host "   Failed     : $($s.Failed.Count) -> $($s.Failed -join ', ')" -ForegroundColor Red
        Write-Host "   -> Run the script again to retry." -ForegroundColor Yellow
    }
    Write-Host ""
    Write-Divider '=' Green
    if ($s.Failed.Count -gt 0) {
        Write-Centered "SYNC FINISHED WITH $($s.Failed.Count) ERROR(S)" Yellow
    } else {
        Write-Centered "ALL MODS ARE UP TO DATE" Green
    }
    Write-Centered "Have fun!" Green
    Write-Divider '=' Green
}

# ===================================================================================
#  GUI MODE (WPF) - phong cach neobrutalism: nen kem, vien den day, bong do cung, bo goc
# ===================================================================================
Show-Logo

$guiLoaded = $false
$isSta = [System.Threading.Thread]::CurrentThread.GetApartmentState() -eq 'STA'
if ($guiSupported -and $isSta -and -not $NoGui -and [System.Environment]::UserInteractive) {
    try {
        [xml]$xaml = @'
<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        Title="OlongBell - Mod Updater" Height="660" Width="760"
        Background="#FFF4E0" Foreground="#111111"
        WindowStartupLocation="CenterScreen" ResizeMode="CanMinimize" FontFamily="Segoe UI">
    <Window.Resources>
        <!-- Nut: mat nut + bong den lech 5px; re chuot -> nut truot ve phia bong, bam -> lun han -->
        <Style TargetType="Button">
            <Setter Property="Background" Value="#FFFFFF"/>
            <Setter Property="Foreground" Value="#111111"/>
            <Setter Property="FontWeight" Value="Bold"/>
            <Setter Property="Cursor" Value="Hand"/>
            <Setter Property="Template">
                <Setter.Value>
                    <ControlTemplate TargetType="Button">
                        <Grid Margin="0,0,5,5">
                            <Border Background="#111111" CornerRadius="10" Margin="5,5,-5,-5"/>
                            <Border x:Name="face" Background="{TemplateBinding Background}" BorderBrush="#111111" BorderThickness="3" CornerRadius="10">
                                <Border.RenderTransform>
                                    <TranslateTransform x:Name="move"/>
                                </Border.RenderTransform>
                                <ContentPresenter HorizontalAlignment="Center" VerticalAlignment="Center" Margin="12,4"/>
                            </Border>
                        </Grid>
                        <ControlTemplate.Triggers>
                            <Trigger Property="IsMouseOver" Value="True">
                                <Trigger.EnterActions>
                                    <BeginStoryboard>
                                        <Storyboard>
                                            <DoubleAnimation Storyboard.TargetName="move" Storyboard.TargetProperty="X" To="2" Duration="0:0:0.12"/>
                                            <DoubleAnimation Storyboard.TargetName="move" Storyboard.TargetProperty="Y" To="2" Duration="0:0:0.12"/>
                                        </Storyboard>
                                    </BeginStoryboard>
                                </Trigger.EnterActions>
                                <Trigger.ExitActions>
                                    <BeginStoryboard>
                                        <Storyboard>
                                            <DoubleAnimation Storyboard.TargetName="move" Storyboard.TargetProperty="X" To="0" Duration="0:0:0.2"/>
                                            <DoubleAnimation Storyboard.TargetName="move" Storyboard.TargetProperty="Y" To="0" Duration="0:0:0.2"/>
                                        </Storyboard>
                                    </BeginStoryboard>
                                </Trigger.ExitActions>
                            </Trigger>
                            <Trigger Property="IsPressed" Value="True">
                                <Trigger.EnterActions>
                                    <BeginStoryboard>
                                        <Storyboard>
                                            <DoubleAnimation Storyboard.TargetName="move" Storyboard.TargetProperty="X" To="5" Duration="0:0:0.06"/>
                                            <DoubleAnimation Storyboard.TargetName="move" Storyboard.TargetProperty="Y" To="5" Duration="0:0:0.06"/>
                                        </Storyboard>
                                    </BeginStoryboard>
                                </Trigger.EnterActions>
                                <Trigger.ExitActions>
                                    <BeginStoryboard>
                                        <Storyboard>
                                            <DoubleAnimation Storyboard.TargetName="move" Storyboard.TargetProperty="X" To="2" Duration="0:0:0.25">
                                                <DoubleAnimation.EasingFunction><BackEase EasingMode="EaseOut" Amplitude="0.8"/></DoubleAnimation.EasingFunction>
                                            </DoubleAnimation>
                                            <DoubleAnimation Storyboard.TargetName="move" Storyboard.TargetProperty="Y" To="2" Duration="0:0:0.25">
                                                <DoubleAnimation.EasingFunction><BackEase EasingMode="EaseOut" Amplitude="0.8"/></DoubleAnimation.EasingFunction>
                                            </DoubleAnimation>
                                        </Storyboard>
                                    </BeginStoryboard>
                                </Trigger.ExitActions>
                            </Trigger>
                            <Trigger Property="IsEnabled" Value="False">
                                <Setter TargetName="face" Property="Background" Value="#E7E2D6"/>
                                <Setter Property="Foreground" Value="#8A8578"/>
                            </Trigger>
                        </ControlTemplate.Triggers>
                    </ControlTemplate>
                </Setter.Value>
            </Setter>
        </Style>

        <!-- Chip chon thu muc (RadioButton): trang -> vang khi chon, co bong cung -->
        <Style TargetType="RadioButton">
            <Setter Property="Foreground" Value="#111111"/>
            <Setter Property="Margin" Value="0,0,8,8"/>
            <Setter Property="Cursor" Value="Hand"/>
            <Setter Property="Template">
                <Setter.Value>
                    <ControlTemplate TargetType="RadioButton">
                        <Grid x:Name="chipRoot" Margin="0,0,4,4">
                            <Border x:Name="sh" Background="#111111" CornerRadius="9" Margin="4,4,-4,-4" Opacity="0"/>
                            <Border x:Name="chip" Background="#FFFFFF" BorderBrush="#111111" BorderThickness="2.5" CornerRadius="9" Padding="10,6">
                                <Border.RenderTransform>
                                    <TranslateTransform x:Name="lift"/>
                                </Border.RenderTransform>
                                <StackPanel Orientation="Horizontal">
                                    <Border x:Name="dot" Width="14" Height="14" CornerRadius="7" BorderBrush="#111111" BorderThickness="2.5" Background="#FFFFFF" Margin="0,0,9,0" VerticalAlignment="Center"/>
                                    <ContentPresenter VerticalAlignment="Center"/>
                                </StackPanel>
                            </Border>
                        </Grid>
                        <ControlTemplate.Triggers>
                            <Trigger Property="IsMouseOver" Value="True">
                                <Setter TargetName="chip" Property="Background" Value="#FFF0B8"/>
                            </Trigger>
                            <Trigger Property="IsChecked" Value="True">
                                <Setter TargetName="chip" Property="Background" Value="#FFD23F"/>
                                <Setter TargetName="dot" Property="Background" Value="#111111"/>
                                <Trigger.EnterActions>
                                    <BeginStoryboard>
                                        <Storyboard>
                                            <DoubleAnimation Storyboard.TargetName="sh" Storyboard.TargetProperty="Opacity" To="1" Duration="0:0:0.12"/>
                                            <DoubleAnimation Storyboard.TargetName="lift" Storyboard.TargetProperty="Y" From="3" To="0" Duration="0:0:0.25">
                                                <DoubleAnimation.EasingFunction><BackEase EasingMode="EaseOut" Amplitude="1"/></DoubleAnimation.EasingFunction>
                                            </DoubleAnimation>
                                        </Storyboard>
                                    </BeginStoryboard>
                                </Trigger.EnterActions>
                                <Trigger.ExitActions>
                                    <BeginStoryboard>
                                        <Storyboard>
                                            <DoubleAnimation Storyboard.TargetName="sh" Storyboard.TargetProperty="Opacity" To="0" Duration="0:0:0.12"/>
                                        </Storyboard>
                                    </BeginStoryboard>
                                </Trigger.ExitActions>
                            </Trigger>
                            <Trigger Property="IsEnabled" Value="False">
                                <Setter TargetName="chipRoot" Property="Opacity" Value="0.5"/>
                            </Trigger>
                        </ControlTemplate.Triggers>
                    </ControlTemplate>
                </Setter.Value>
            </Setter>
        </Style>

        <!-- Nhan tieu de cua tung khoi -->
        <Style x:Key="Label" TargetType="TextBlock">
            <Setter Property="FontSize" Value="12"/>
            <Setter Property="FontWeight" Value="Black"/>
            <Setter Property="Foreground" Value="#111111"/>
            <Setter Property="Margin" Value="0,0,0,8"/>
        </Style>
    </Window.Resources>

    <!-- Ca giao dien hien dan + truot len khi mo cua so -->
    <Grid Name="rootGrid" Margin="24,20,24,18" Opacity="0">
        <Grid.RenderTransform>
            <TranslateTransform Y="22"/>
        </Grid.RenderTransform>
        <Grid.Triggers>
            <EventTrigger RoutedEvent="FrameworkElement.Loaded">
                <BeginStoryboard>
                    <Storyboard>
                        <DoubleAnimation Storyboard.TargetProperty="Opacity" From="0" To="1" Duration="0:0:0.45"/>
                        <DoubleAnimation Storyboard.TargetProperty="(UIElement.RenderTransform).(TranslateTransform.Y)" From="22" To="0" Duration="0:0:0.6">
                            <DoubleAnimation.EasingFunction><BackEase EasingMode="EaseOut" Amplitude="0.5"/></DoubleAnimation.EasingFunction>
                        </DoubleAnimation>
                    </Storyboard>
                </BeginStoryboard>
            </EventTrigger>
        </Grid.Triggers>
        <Grid.RowDefinitions>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="*"/>
            <RowDefinition Height="Auto"/>
            <RowDefinition Height="Auto"/>
        </Grid.RowDefinitions>

        <!-- Tieu de: "sticker" vang nghieng, lac nhe khi mo -->
        <StackPanel Grid.Row="0" Orientation="Horizontal" Margin="0,0,0,16">
            <Grid Margin="0,0,14,0" RenderTransformOrigin="0.5,0.5">
                <Grid.RenderTransform>
                    <RotateTransform Angle="-3"/>
                </Grid.RenderTransform>
                <Grid.Triggers>
                    <EventTrigger RoutedEvent="FrameworkElement.Loaded">
                        <BeginStoryboard>
                            <Storyboard>
                                <DoubleAnimation Storyboard.TargetProperty="(UIElement.RenderTransform).(RotateTransform.Angle)" From="-14" To="-3" Duration="0:0:0.8" BeginTime="0:0:0.15">
                                    <DoubleAnimation.EasingFunction><ElasticEase EasingMode="EaseOut" Oscillations="2" Springiness="4"/></DoubleAnimation.EasingFunction>
                                </DoubleAnimation>
                            </Storyboard>
                        </BeginStoryboard>
                    </EventTrigger>
                </Grid.Triggers>
                <Border Background="#111111" CornerRadius="10" Margin="5,5,-5,-5"/>
                <Border Background="#FFD23F" BorderBrush="#111111" BorderThickness="3" CornerRadius="10" Padding="16,4,16,6">
                    <TextBlock Text="OLONGBELL" FontSize="28" FontWeight="Black" Foreground="#111111"/>
                </Border>
            </Grid>
            <StackPanel VerticalAlignment="Center">
                <StackPanel Orientation="Horizontal">
                    <Border Background="#FF5D8F" BorderBrush="#111111" BorderThickness="2.5" CornerRadius="20" Padding="10,2">
                        <TextBlock Text="MOD UPDATER v26.2" FontSize="11" FontWeight="Black" Foreground="#111111"/>
                    </Border>
                    <Border Name="accentBar" Width="14" Height="14" CornerRadius="7" Background="#B8F35C" BorderBrush="#111111" BorderThickness="2.5" Margin="8,0,0,0" VerticalAlignment="Center"/>
                </StackPanel>
                <TextBlock Text="One-click mod sync for the OlongBell server" FontSize="13" FontWeight="SemiBold" Foreground="#3D3A33" Margin="2,5,0,0"/>
                <TextBlock Text="made by @stuwupidwjbu" FontSize="10.5" FontWeight="SemiBold" FontStyle="Italic" Foreground="#8A8578" Margin="2,2,0,0"/>
            </StackPanel>
        </StackPanel>

        <!-- Khoi 1: chon thu muc mods (tu dong tim tren moi o dia) -->
        <Grid Grid.Row="1" Margin="0,0,5,18">
            <Border Background="#111111" CornerRadius="12" Margin="5,5,-5,-5"/>
            <Border Background="#FFFFFF" BorderBrush="#111111" BorderThickness="3" CornerRadius="12" Padding="16,14,16,10">
                <StackPanel>
                    <TextBlock Style="{StaticResource Label}" Text="1. PICK YOUR MODS FOLDER"/>
                    <WrapPanel Name="pathChips">
                        <TextBlock Name="txtScanning" Text="Looking for Minecraft folders on all drives..." FontWeight="SemiBold" Foreground="#6B6658" Margin="2,4,0,10"/>
                    </WrapPanel>
                    <Grid Margin="0,2,0,0">
                        <Grid.ColumnDefinitions>
                            <ColumnDefinition Width="*"/>
                            <ColumnDefinition Width="Auto"/>
                        </Grid.ColumnDefinitions>
                        <Border Grid.Column="0" Background="#FFF9EC" BorderBrush="#111111" BorderThickness="2.5" CornerRadius="9" Margin="0,0,10,5" Height="38">
                            <TextBox Name="txtPath" Background="Transparent" BorderThickness="0" Padding="8,0" VerticalContentAlignment="Center" IsReadOnly="True" FontFamily="Cascadia Mono, Consolas" FontSize="12" Foreground="#111111"/>
                        </Border>
                        <Button Name="btnBrowse" Grid.Column="1" Content="Browse..." Height="43" Background="#8FD3FF" FontSize="12"/>
                    </Grid>
                </StackPanel>
            </Border>
        </Grid>

        <!-- Khoi 2: nhat ky kieu cua so terminal -->
        <Grid Grid.Row="2" Margin="0,0,5,18">
            <Border Background="#111111" CornerRadius="12" Margin="5,5,-5,-5"/>
            <Border Background="#1A1A1A" BorderBrush="#111111" BorderThickness="3" CornerRadius="12">
                <Grid>
                    <Grid.RowDefinitions>
                        <RowDefinition Height="Auto"/>
                        <RowDefinition Height="*"/>
                    </Grid.RowDefinitions>
                    <Border Grid.Row="0" Background="#FFFFFF" BorderBrush="#111111" BorderThickness="0,0,0,3" CornerRadius="9,9,0,0" Padding="12,7">
                        <StackPanel Orientation="Horizontal">
                            <Ellipse Width="12" Height="12" Fill="#FF5D8F" Stroke="#111111" StrokeThickness="2" Margin="0,0,6,0"/>
                            <Ellipse Width="12" Height="12" Fill="#FFD23F" Stroke="#111111" StrokeThickness="2" Margin="0,0,6,0"/>
                            <Ellipse Width="12" Height="12" Fill="#B8F35C" Stroke="#111111" StrokeThickness="2" Margin="0,0,12,0"/>
                            <TextBlock Text="2. LOG" FontSize="12" FontWeight="Black" VerticalAlignment="Center"/>
                        </StackPanel>
                    </Border>
                    <TextBox Name="txtLog" Grid.Row="1" Background="Transparent" Foreground="#E9E4D8" BorderThickness="0" AcceptsReturn="True" VerticalScrollBarVisibility="Auto" HorizontalScrollBarVisibility="Auto" IsReadOnly="True" Padding="12,8" FontFamily="Cascadia Mono, Consolas" FontSize="11"/>
                </Grid>
            </Border>
        </Grid>

        <!-- Khoi 3: thanh tien do soc cheo chay + so dem -->
        <Grid Grid.Row="3" Margin="0,0,0,16">
            <Grid.ColumnDefinitions>
                <ColumnDefinition Width="*"/>
                <ColumnDefinition Width="Auto"/>
            </Grid.ColumnDefinitions>
            <Grid Grid.Column="0" Margin="0,0,5,5">
                <Border Background="#111111" CornerRadius="10" Margin="4,4,-4,-4"/>
                <Border Name="barTrack" Height="28" Background="#FFFFFF" BorderBrush="#111111" BorderThickness="3" CornerRadius="10" ClipToBounds="True">
                    <Grid>
                        <Border Name="barFill" HorizontalAlignment="Left" Width="0" CornerRadius="6" Background="#4D96FF">
                            <Border Name="barStripes" Opacity="0">
                                <Border.Background>
                                    <LinearGradientBrush MappingMode="Absolute" StartPoint="0,0" EndPoint="14,14" SpreadMethod="Repeat">
                                        <LinearGradientBrush.Transform>
                                            <TranslateTransform X="0"/>
                                        </LinearGradientBrush.Transform>
                                        <GradientStop Color="#00FFFFFF" Offset="0"/>
                                        <GradientStop Color="#00FFFFFF" Offset="0.5"/>
                                        <GradientStop Color="#FFFFFFFF" Offset="0.5"/>
                                        <GradientStop Color="#FFFFFFFF" Offset="1"/>
                                    </LinearGradientBrush>
                                </Border.Background>
                            </Border>
                        </Border>
                        <Border Name="barSweep" HorizontalAlignment="Left" Width="80" Background="#FFD23F" BorderBrush="#111111" BorderThickness="3,0,3,0" Opacity="0">
                            <Border.RenderTransform>
                                <TranslateTransform X="-80"/>
                            </Border.RenderTransform>
                        </Border>
                    </Grid>
                </Border>
            </Grid>
            <Grid Grid.Column="1" Margin="12,0,0,0">
                <Border Background="#111111" CornerRadius="10" Margin="4,4,0,1"/>
                <Border Name="progChip" Background="#FFFFFF" BorderBrush="#111111" BorderThickness="3" CornerRadius="10" Margin="0,0,4,5" MinWidth="86" Padding="10,0">
                    <TextBlock Name="txtProgressVal" Text="0/0" FontSize="14" FontWeight="Black" HorizontalAlignment="Center" VerticalAlignment="Center" FontFamily="Cascadia Mono, Consolas"/>
                </Border>
            </Grid>
        </Grid>

        <!-- Hang cuoi: trang thai + nut chinh mau hong -->
        <Grid Grid.Row="4">
            <Grid.ColumnDefinitions>
                <ColumnDefinition Width="*"/>
                <ColumnDefinition Width="Auto"/>
            </Grid.ColumnDefinitions>
            <TextBlock Name="txtStatus" Grid.Column="0" Text="Pick a folder, then hit the button" VerticalAlignment="Center" Foreground="#111111" FontSize="13" FontWeight="Bold" TextTrimming="CharacterEllipsis" Margin="0,0,12,0"/>
            <Button Name="btnStart" Grid.Column="1" Content="CHECK &amp; UPDATE" Background="#FF5D8F" Width="230" Height="54" FontSize="14" FontWeight="Black"/>
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

    function New-Brush([string]$hex) {
        return New-Object System.Windows.Media.SolidColorBrush ([System.Windows.Media.ColorConverter]::ConvertFromString($hex))
    }

    # =================================================================================
    #  Dong co chuyen dong tu ve: moi khung hinh dat thang gia tri (Width, X, Scale).
    #  Khong dung WPF Storyboard cho phan chay trong luc dong bo vi khi vong lap dang
    #  bom su kien bang tay, animation cua WPF khong duoc ve len man hinh (thanh dung yen).
    # =================================================================================
    $script:fx = @{
        FillCur = 0.0; FillTarget = 0.0; Mode = 'off'; Busy = $false
        SweepX = -80.0; StripeX = 0.0; T0 = [DateTime]::UtcNow; Last = [DateTime]::UtcNow
    }
    $stripeBrush = $barStripes.Background
    if ($stripeBrush.IsFrozen) { $stripeBrush = $stripeBrush.Clone(); $barStripes.Background = $stripeBrush }
    $stripeTT = New-Object System.Windows.Media.TranslateTransform
    $stripeBrush.Transform = $stripeTT
    $sweepTT = New-Object System.Windows.Media.TranslateTransform -80, 0
    $barSweep.RenderTransform = $sweepTT
    $dotScale = New-Object System.Windows.Media.ScaleTransform 1, 1
    $accentBar.RenderTransformOrigin = New-Object System.Windows.Point 0.5, 0.5
    $accentBar.RenderTransform = $dotScale

    function Step-Fx {
        $f = $script:fx
        $now = [DateTime]::UtcNow
        $dt = [Math]::Min(0.1, ($now - $f.Last).TotalSeconds)
        if ($dt -le 0) { return }
        $f.Last = $now
        $inner = [Math]::Max(0.0, $barTrack.ActualWidth - 6)
        # thanh day: tien dan toi muc tieu (nhanh khi xa, cham khi gan)
        $f.FillCur += ($f.FillTarget - $f.FillCur) * [Math]::Min(1.0, $dt * 9)
        if ([Math]::Abs($f.FillTarget - $f.FillCur) -lt 0.0005) { $f.FillCur = $f.FillTarget }
        $barFill.Width = $inner * $f.FillCur
        if ($f.Mode -eq 'download') {
            $f.StripeX = ($f.StripeX + $dt * 40) % 28
            $stripeTT.X = $f.StripeX
        } elseif ($f.Mode -eq 'scan') {
            $f.SweepX += $dt * 420
            if ($f.SweepX -gt $barTrack.ActualWidth) { $f.SweepX = -$barSweep.Width }
            $sweepTT.X = $f.SweepX
        }
        $s = 1.0
        if ($f.Busy) { $s = 1 + 0.35 * [Math]::Abs([Math]::Sin(($now - $f.T0).TotalSeconds * 4)) }
        $dotScale.ScaleX = $s
        $dotScale.ScaleY = $s
    }

    # Cho WPF xu ly su kien (ve lai, nhan click) trong luc vong lap dong bo dang chay
    function Update-UI {
        Step-Fx
        [System.Windows.Threading.Dispatcher]::CurrentDispatcher.Invoke([System.Action] {}, [System.Windows.Threading.DispatcherPriority]::Background)
    }
    # Thay Start-Sleep bang vong ve ~60 khung/giay
    $script:WaitHook = {
        param($ms)
        $end = [DateTime]::UtcNow.AddMilliseconds($ms)
        do { Update-UI; Start-Sleep -Milliseconds 14 } while ([DateTime]::UtcNow -lt $end)
    }
    # Luc ranh (khong dong bo) van chay hieu ung bang timer
    $fxTimer = New-Object System.Windows.Threading.DispatcherTimer
    $fxTimer.Interval = [TimeSpan]::FromMilliseconds(16)
    $fxTimer.add_Tick({ Step-Fx })
    $fxTimer.Start()

    function Set-Busy([bool]$on) { $script:fx.Busy = $on }
    function Set-BarRatio([double]$r, [switch]$Instant) {
        $script:fx.FillTarget = [Math]::Max(0.0, [Math]::Min(1.0, $r))
        if ($Instant) { $script:fx.FillCur = $script:fx.FillTarget }
    }
    # 'scan' = khoi vang chay qua lai (chua biet tien do) | 'download' = soc cheo chay | 'off'
    function Set-BarMode([string]$mode) {
        $script:fx.Mode = $mode
        $barSweep.Opacity = if ($mode -eq 'scan') { 1 } else { 0 }
        $barStripes.Opacity = if ($mode -eq 'download') { 0.35 } else { 0 }
        if ($mode -eq 'scan') { $script:fx.SweepX = -$barSweep.Width }
    }
    function Set-AccentColor([string]$hex) {
        $barFill.Background = New-Brush $hex
        $accentBar.Background = New-Brush $hex
        $progChip.Background = New-Brush $hex
    }
    function Set-Status([string]$text, [string]$hex = "#111111") {
        $txtStatus.Text = $text
        $txtStatus.Foreground = New-Brush $hex
    }
    function Set-ControlsEnabled([bool]$on) {
        $btnStart.IsEnabled = $on
        $btnBrowse.IsEnabled = $on
        foreach ($c in $pathChips.Children) { $c.IsEnabled = $on }
    }

    # ---------- chip thu muc ----------
    function Add-PathChip($cand, [bool]$select) {
        $panel = New-Object System.Windows.Controls.StackPanel
        $t1 = New-Object System.Windows.Controls.TextBlock
        $t1.Text = "$($cand.Label)  -  $($cand.Mods) mods"
        $t1.FontWeight = 'Bold'
        $t1.FontSize = 12
        $t2 = New-Object System.Windows.Controls.TextBlock
        $t2.Text = $cand.Path
        $t2.FontSize = 10.5
        $t2.Foreground = New-Brush "#4A463C"
        $t2.MaxWidth = 300
        $t2.TextTrimming = 'CharacterEllipsis'
        $t2.ToolTip = $cand.Path
        [void]$panel.Children.Add($t1)
        [void]$panel.Children.Add($t2)
        $rb = New-Object System.Windows.Controls.RadioButton
        $rb.GroupName = "modsPath"
        $rb.Content = $panel
        $rb.Tag = $cand.Path
        $rb.add_Checked({ param($s, $e) $txtPath.Text = $s.Tag })
        [void]$pathChips.Children.Add($rb)
        if ($select) { $rb.IsChecked = $true }
        return $rb
    }

    $btnBrowse.add_Click({
        Add-Type -AssemblyName System.Windows.Forms
        $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
        $dialog.Description = "Pick your Minecraft mods folder (e.g. D:\Games\Minecraft\mods)"
        if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
            $p = $dialog.SelectedPath
            # Chon nham thu muc game (co versions) -> tu tro vao mods ben trong
            if ((Split-Path $p -Leaf) -ne 'mods' -and (Test-Path -LiteralPath (Join-Path $p 'versions'))) { $p = Join-Path $p 'mods' }
            $count = @(Get-ChildItem -LiteralPath $p -Filter *.jar -File -ErrorAction SilentlyContinue).Count
            [void](Add-PathChip ([pscustomobject]@{ Path = $p; Label = 'Custom'; Mods = $count }) $true)
        }
    })

    # ---------- khi mo cua so: logo "go chu" + quet o dia tim thu muc ----------
    $divider = "=" * 78
    $script:introLines = @(Get-LogoLines) + @($divider, "Ready! Pick your mods folder, then hit 'CHECK & UPDATE'.", $divider)
    $script:introIdx = 0
    $script:introTimer = New-Object System.Windows.Threading.DispatcherTimer
    $script:introTimer.Interval = [TimeSpan]::FromMilliseconds(70)
    $script:introTimer.add_Tick({
        if ($script:introIdx -ge $script:introLines.Count) { $script:introTimer.Stop(); return }
        $txtLog.AppendText($script:introLines[$script:introIdx] + "`r`n")
        $script:introIdx++
    })

    $window.add_ContentRendered({
        $script:introTimer.Start()
        Set-ControlsEnabled $false
        Set-Busy $true
        Set-BarMode 'scan'
        Set-Status "Looking for Minecraft folders on all drives..." "#1D4ED8"
        $cands = @()
        try { $cands = Find-ModFoldersAsync -OnTick { Update-UI } } catch { }
        $pathChips.Children.Remove($txtScanning)
        if ($cands.Count -eq 0) {
            $cands = @([pscustomobject]@{ Path = $legacyPath; Label = 'Legacy Launcher'; Mods = 0 })
        }
        $first = $true
        foreach ($c in ($cands | Select-Object -First 8)) {
            [void](Add-PathChip $c $first)
            $first = $false
            Invoke-Wait 70
        }
        Set-BarMode 'off'
        Set-Busy $false
        Set-ControlsEnabled $true
        $word = if ($cands.Count -eq 1) { "folder" } else { "folders" }
        Set-Status "Found $($cands.Count) $word. Double-check the path, then hit the button."
    })

    $btnStart.add_Click({
        $script:introTimer.Stop()
        if ([string]::IsNullOrWhiteSpace($txtPath.Text)) { Set-Status "Pick a mods folder first!" "#C2410C"; return }
        Save-LastModsPath $txtPath.Text
        Set-ControlsEnabled $false
        $txtLog.Text = "--- SYNC STARTED ---`r`n"
        $txtProgressVal.Text = "..."
        $script:spd = @{ T = [DateTime]::UtcNow; B = [long]0; Speed = 0.0 }

        $log = { param($msg, $color) $txtLog.AppendText("$msg`r`n"); $txtLog.ScrollToEnd(); Update-UI }
        $tick = { Update-UI }
        $prog = {
            param($done, $n, $name, $bytes, $total)
            Set-BarRatio ($bytes / [Math]::Max(1, $total))
            $pct = [int](100 * $bytes / [Math]::Max(1, $total))
            $txtProgressVal.Text = "$pct%"
            # toc do tai (lam min de khong nhay so lien tuc)
            $now = [DateTime]::UtcNow
            $dt = ($now - $script:spd.T).TotalSeconds
            if ($dt -ge 0.5) {
                $inst = ($bytes - $script:spd.B) / $dt
                $script:spd.Speed = if ($script:spd.Speed -eq 0) { $inst } else { $script:spd.Speed * 0.7 + $inst * 0.3 }
                $script:spd.T = $now
                $script:spd.B = $bytes
            }
            if ($n -gt 1 -or $total -gt 1) {
                Set-Status ("Downloading {0}/{1} mods  -  {2} / {3}  -  {4}/s" -f $done, $n, (Format-Size $bytes), (Format-Size $total), (Format-Size $script:spd.Speed)) "#1D4ED8"
            }
        }
        $phase = {
            param($p)
            switch ($p) {
                "connect" { Set-AccentColor "#4D96FF"; Set-BarRatio 0 -Instant; Set-Busy $true; Set-BarMode 'scan'; Set-Status "Connecting to GitHub..." "#1D4ED8" }
                "scan" { Set-Status "Comparing your mods with the server (SHA)..." "#1D4ED8" }
                "download" { Set-BarMode 'download' }
                "done" { Set-Busy $false; Set-BarMode 'off'; Set-BarRatio 1; Set-AccentColor "#B8F35C" }
                "error" { Set-Busy $false; Set-BarMode 'off'; Set-AccentColor "#FF9F1C" }
            }
            Update-UI
        }

        try {
            $s = Invoke-ModSync -TargetDir $txtPath.Text -Parallel $Parallel -Log $log -OnTick $tick -OnProgress $prog -OnPhase $phase
            if ($s.RepoCount -eq 0) {
                Set-Status "Could not reach GitHub." "#C2410C"
            } else {
                & $log "`r`n--- SUMMARY ---" White
                & $log "Downloaded $($s.Downloaded) mods ($(Format-Size $s.Bytes)), deleted $($s.Deleted), in $($s.Seconds)s" White
                if ($s.Failed.Count -gt 0) {
                    & $log "Failed: $($s.Failed -join ', ')" Red
                    $txtProgressVal.Text = "!"
                    Set-Status "Done with $($s.Failed.Count) error(s) - check the log." "#C2410C"
                    [System.Windows.MessageBox]::Show("Sync finished with $($s.Failed.Count) error(s). Check the log, close Minecraft if it is open, then try again.", "Finished with errors", [System.Windows.MessageBoxButton]::OK, [System.Windows.MessageBoxImage]::Warning) | Out-Null
                } else {
                    $txtProgressVal.Text = "DONE!"
                    Set-Status "All mods up to date! Downloaded $($s.Downloaded), deleted $($s.Deleted)." "#15803D"
                    [System.Windows.MessageBox]::Show("All mods are up to date. Have fun!", "OlongBell", [System.Windows.MessageBoxButton]::OK, [System.Windows.MessageBoxImage]::Information) | Out-Null
                }
            }
        } catch {
            & $log "[FATAL] $_" Red
            Set-Busy $false
            Set-BarMode 'off'
            Set-AccentColor "#FF9F1C"
            Set-Status "Something went wrong during the sync." "#C2410C"
        }
        Set-ControlsEnabled $true
    })

    $window.ShowDialog() | Out-Null
    $fxTimer.Stop()
} else {
    Run-ConsoleUpdate
}
