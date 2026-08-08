[CmdletBinding()]
param(
    [switch]$NoGui
)

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

# Logo ASCII "OLONGBELL" nhieu mau, tu dong bo qua neu terminal qua hep de khong bi vo dong
function Show-Logo {
    $logo = @(
        ' ██████╗ ██╗      ██████╗ ███╗   ██╗ ██████╗ ██████╗ ███████╗██╗     ██╗     ',
        '██╔═══██╗██║     ██╔═══██╗████╗  ██║██╔════╝ ██╔══██╗██╔════╝██║     ██║     ',
        '██║   ██║██║     ██║   ██║██╔██╗ ██║██║  ███╗██████╔╝█████╗  ██║     ██║     ',
        '██║   ██║██║     ██║   ██║██║╚██╗██║██║   ██║██╔══██╗██╔══╝  ██║     ██║     ',
        '╚██████╔╝███████╗╚██████╔╝██║ ╚████║╚██████╔╝██████╔╝███████╗███████╗███████╗',
        ' ╚═════╝ ╚══════╝ ╚═════╝ ╚═╝  ╚═══╝ ╚═════╝ ╚═════╝ ╚══════╝╚══════╝╚══════╝'
    )
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

# Tai file co retry (mac dinh 3 lan) + xac minh dung dung luong sau khi tai, tranh file loi/dut giua chung
# $Log la scriptblock nhan (message, mau) de ghi log ra console hoac GUI tuy noi goi
function Invoke-ModDownloadWithRetry {
    param(
        [string]$Url,
        [string]$TargetDir,
        [string]$FileName,
        [long]$ExpectedSize,
        [System.Net.WebClient]$Client,
        [scriptblock]$Log,
        [int]$MaxRetries = 3
    )

    $destPath = Join-Path $TargetDir $FileName
    $tempPath = "$destPath.tmp"

    for ($attempt = 1; $attempt -le $MaxRetries; $attempt++) {
        try {
            if ($attempt -gt 1) {
                & $Log "     [RETRY] Thu lai lan $attempt/$MaxRetries..." 'DarkYellow'
                Start-Sleep -Seconds ([Math]::Min(2 * ($attempt - 1), 5))
            }
            $Client.DownloadFile($Url, $tempPath)

            $actualSize = (Get-Item -LiteralPath $tempPath).Length
            if ($ExpectedSize -gt 0 -and $actualSize -ne $ExpectedSize) {
                throw "Kich thuoc file khong khop (tai duoc $actualSize byte, can $ExpectedSize byte) - co the do loi mang giua chung."
            }

            if (Test-Path -LiteralPath $destPath) { Remove-Item -LiteralPath $destPath -Force }
            Rename-Item -LiteralPath $tempPath -NewName $FileName
            return @{ Success = $true; Bytes = $actualSize }
        } catch {
            & $Log "     [LOI] Lan $attempt That bai: $_" 'Red'
            if (Test-Path -LiteralPath $tempPath) { Remove-Item -LiteralPath $tempPath -Force -ErrorAction SilentlyContinue }
        }
    }
    return @{ Success = $false; Bytes = 0 }
}

# Function to run update in console mode (Fallback)
function Run-ConsoleUpdate($targetDir) {
    Write-Banner "MOD UPDATER v26.2" "Cong cu dong bo & cap nhat Mod tu dong (Console)" Cyan
    $swTotal = [System.Diagnostics.Stopwatch]::StartNew()

    if ($null -eq $targetDir -or $targetDir -eq "") {
        # Prompt for Launcher choice in Console mode
        Write-Host " Chon launcher game cua ban:" -ForegroundColor Yellow
        Write-Host "  [1] Legacy Launcher (Mac dinh)" -ForegroundColor Gray
        Write-Host "  [2] Official (Minecraft Ban Quyen)" -ForegroundColor Gray
        Write-Host "  [3] TLauncher" -ForegroundColor Gray
        Write-Host "  [4] Duong dan tu chon (Custom)" -ForegroundColor Gray
        
        $choice = Read-Host " Nhap lua chon (1-4, Enter de lay Mac dinh [1])"
        
        $targetDir = $legacyPath
        if ($choice -eq "2") {
            $targetDir = $premiumPath
            Write-Host " -> Da chon: Official (Minecraft Ban Quyen)" -ForegroundColor Green
        } elseif ($choice -eq "3") {
            $targetDir = $tlauncherPath
            Write-Host " -> Da chon: TLauncher" -ForegroundColor Green
        } elseif ($choice -eq "4") {
            $targetDir = Read-Host " Nhap duong dan day du cua thu muc mods"
            if (!(Test-Path -LiteralPath $targetDir)) {
                Write-Host " [LOI] Duong dan khong ton tai!" -ForegroundColor Red
                return
            }
            Write-Host " -> Da chon duong dan custom: $targetDir" -ForegroundColor Green
        } else {
            Write-Host " -> Da chon: Legacy Launcher" -ForegroundColor Green
        }
    }
    
    Write-Divider '-' DarkGray
    Write-Host " [DIR] Thu muc dich: " -ForegroundColor Yellow -NoNewline
    Write-Host $targetDir -ForegroundColor White
    Write-Divider '-' DarkGray
    
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $apiUrl = "https://api.github.com/repos/QuangquyNguyenvo/MineServer/contents/mods"
        
        # Load list of files asynchronously using a Runspace to show a smooth loading spinner
        $iss = [system.management.automation.runspaces.initialsessionstate]::CreateDefault()
        $runspace = [runspacefactory]::CreateRunspace($iss)
        $runspace.Open()
        
        $ps = [powershell]::Create().AddScript({
            param($url)
            $headers = @{ "User-Agent" = "PowerShell-Minecraft-Updater" }
            Invoke-RestMethod -Uri $url -Headers $headers -Method Get | Write-Output
        }).AddArgument($apiUrl)
        
        $ps.Runspace = $runspace
        $asyncResult = $ps.BeginInvoke()
        
        # ASCII loading spinner animation
        $spinner = @('|', '/', '-', '\')
        $i = 0
        while (!$asyncResult.IsCompleted) {
            Write-Host -NoNewline "`r  $($spinner[$i]) Dang ket noi toi GitHub API de kiem tra danh sach mod..." -ForegroundColor Cyan
            $i = ($i + 1) % $spinner.Length
            Start-Sleep -Milliseconds 100
        }
        
        $repoFiles = $ps.EndInvoke($asyncResult)
        
        # Safely unroll nested array if wrapped by pipeline output
        if ($repoFiles -and $repoFiles.Count -eq 1 -and $repoFiles[0] -is [System.Array]) {
            $repoFiles = $repoFiles[0]
        }
        
        $ps.Dispose()
        $runspace.Close()
        
        # Clear spinner line
        Write-Host -NoNewline "`r                                                                           `r"
        
        if ($null -eq $repoFiles -or $repoFiles.Count -eq 0) {
            Write-Host " [LOI] Khong the doc danh sach mod tu GitHub hoac thu muc trong." -ForegroundColor Red
            return
        }
        
        Write-Host " [OK] Ket noi GitHub thanh cong!" -ForegroundColor Green
        Write-Divider '-' DarkGray

        # Ensure target folder exists
        if (!(Test-Path -LiteralPath $targetDir)) {
            Write-Host " [DIR] Tao thu muc moi: $targetDir" -ForegroundColor Gray
            New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
        }

        # Scan local mods
        $localFiles = Get-ChildItem -Path $targetDir -File | Where-Object { $_.Extension -eq ".jar" -or $_.Name.EndsWith(".jar.disabled") }
        
        # Build map of repo mods
        $repoMods = @{}
        $repoBaseNames = @{}
        foreach ($file in $repoFiles) {
            if ($file.name.EndsWith(".jar") -or $file.name.EndsWith(".jar.disabled")) {
                $repoMods[$file.name] = $file
                $base = Get-ModBaseName $file.name
                $repoBaseNames[$base] = $true
            }
        }

        # Calculate deletion list (Only delete if it is an old version of a server mod)
        $toDelete = @()
        foreach ($lf in $localFiles) {
            if (!$repoMods.ContainsKey($lf.Name)) {
                $localBase = Get-ModBaseName $lf.Name
                if ($repoBaseNames.ContainsKey($localBase)) {
                    $toDelete += $lf
                }
            }
        }

        # Calculate download list
        $toDownload = @()
        foreach ($key in $repoMods.Keys) {
            $rmName = "$key"
            $rm = $repoMods[$rmName]
            $localFilePath = Join-Path $targetDir $rmName
            
            if (!(Test-Path -LiteralPath $localFilePath)) {
                $toDownload += $rm
            } else {
                $localSize = (Get-Item -LiteralPath $localFilePath).Length
                if ($localSize -ne $rm.size) {
                    $toDownload += $rm
                }
            }
        }

        Write-Host " [STATS] Tim thay $($repoMods.Count) mod tren Server | $($localFiles.Count) mod o may ban." -ForegroundColor Green
        Write-Host " [SYNC] Can tai them: $($toDownload.Count) mod | Can xoa bo (mod cu): $($toDelete.Count) mod." -ForegroundColor Yellow
        Write-Divider '-' DarkGray

        # Delete old mods
        if ($toDelete.Count -gt 0) {
            Write-Section "XOA MOD CU KHONG SU DUNG" Red
            foreach ($df in $toDelete) {
                Write-Host "  - Dang xoa: $($df.Name)" -ForegroundColor DarkRed
                Remove-Item -LiteralPath $df.FullName -Force
            }
            Write-Divider '-' DarkGray
        }

        # Download missing/modified mods
        $failedDownloads = @()
        $totalBytesDownloaded = 0
        if ($toDownload.Count -gt 0) {
            Write-Section "TAI CAC MOD MOI CAP NHAT" Green
            $client = New-Object System.Net.WebClient
            $count = 0
            foreach ($rm in $toDownload) {
                $count++
                Write-Host "  -> Tai ($count/$($toDownload.Count)): $($rm.name) ($([Math]::Round($rm.size / 1MB, 2)) MB)..." -ForegroundColor White

                $result = Invoke-ModDownloadWithRetry -Url $rm.download_url -TargetDir $targetDir -FileName $rm.name -ExpectedSize $rm.size -Client $client -Log { param($msg, $color) Write-Host $msg -ForegroundColor $color }
                if ($result.Success) {
                    $totalBytesDownloaded += $result.Bytes
                    Write-Host "     [OK] Da tai xong!" -ForegroundColor Green
                } else {
                    Write-Host "     [X] That bai han sau nhieu lan thu: $($rm.name)" -ForegroundColor Red
                    $failedDownloads += $rm.name
                }
            }
            Write-Divider '-' DarkGray
        }

        # Reload local file list after sync for accurate mod counting
        $finalLocalFiles = Get-ChildItem -Path $targetDir -File | Where-Object { $_.Extension -eq ".jar" -or $_.Name.EndsWith(".jar.disabled") } | Sort-Object Name
        
        Write-Section "DANH SACH MOD HIEN TAI TREN MAY BAN ($($finalLocalFiles.Count) mods)" Green
        
        # Two-column list -- be rong cot tu tinh theo chieu rong terminal
        $w = Get-ConsoleWidth
        $colWidth = [Math]::Max(20, [int](($w - 9) / 2))   # "   - " + col1 + " - " + col2
        for ($idx = 0; $idx -lt $finalLocalFiles.Count; $idx += 2) {
            $file1 = $finalLocalFiles[$idx].Name
            $file2 = ""
            if ($idx + 1 -lt $finalLocalFiles.Count) {
                $file2 = $finalLocalFiles[$idx + 1].Name
            }
            
            # Cat bot neu dai hon be rong cot
            $col1 = $file1
            if ($col1.Length -gt $colWidth) { $col1 = $col1.Substring(0, $colWidth - 3) + "..." }
            $col1 = $col1.PadRight($colWidth)
            
            $col2 = $file2
            if ($col2.Length -gt $colWidth) { $col2 = $col2.Substring(0, $colWidth - 3) + "..." }
            
            Write-Host "   - $col1 - $col2" -ForegroundColor DarkGray
        }

        $swTotal.Stop()
        Write-Host ""
        Write-Section "TONG KET" Cyan
        Write-Host "   Da tai       : $([Math]::Round($totalBytesDownloaded / 1MB, 2)) MB" -ForegroundColor White
        Write-Host "   Thoi gian    : $([Math]::Round($swTotal.Elapsed.TotalSeconds, 1))s" -ForegroundColor White
        if ($failedDownloads.Count -gt 0) {
            Write-Host "   That bai     : $($failedDownloads.Count) mod -> $($failedDownloads -join ', ')" -ForegroundColor Red
            Write-Host "   -> Chay lai script de thu tai lai cac mod tren." -ForegroundColor Yellow
        }
        Write-Divider '-' DarkGray

        Write-Host ""
        Write-Divider '=' Green
        if ($failedDownloads.Count -gt 0) {
            Write-Centered "DONG BO HOAN TAT (CO $($failedDownloads.Count) MOD LOI)" Yellow
        } else {
            Write-Centered "HOAN THANH DONG BO HOA MODS" Green
        }
        Write-Centered "Chuc ban choi game vui ve!" Green
        Write-Divider '=' Green

    } catch {
        Write-Host " [LOI NGHIEM TRONG] $_" -ForegroundColor Red
    }
}

# In logo ngay khi script bat dau chay, du sau do mo GUI hay fallback console
Show-Logo

# Determine if GUI should run
$guiLoaded = $false
if ($guiSupported -and !$NoGui -and [System.Environment]::UserInteractive) {
    try {
        [xml]$xaml = @'
<Window xmlns="http://schemas.microsoft.com/winfx/2000/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2000/xaml"
        Title="OlongBell - Mod Updater" Height="500" Width="650"
        Background="#18181B" Foreground="#F4F4F5"
        WindowStartupLocation="CenterScreen" ResizeMode="NoResize" FontFamily="Segoe UI">
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
            <RowDefinition Height="Auto"/> <!-- Title -->
            <RowDefinition Height="Auto"/> <!-- Launcher Option -->
            <RowDefinition Height="Auto"/> <!-- Path Field -->
            <RowDefinition Height="*"/>    <!-- Log / Status Box -->
            <RowDefinition Height="Auto"/> <!-- Progress Bar -->
            <RowDefinition Height="Auto"/> <!-- Buttons -->
        </Grid.RowDefinitions>

        <!-- Title -->
        <StackPanel Grid.Row="0" Margin="0,0,0,15">
            <StackPanel Orientation="Horizontal">
                <Border Background="#3B82F6" Width="4" Margin="0,0,10,0"/>
                <StackPanel>
                    <TextBlock Text="OLONGBELL" FontSize="22" FontWeight="Bold" Foreground="#3B82F6"/>
                    <TextBlock Text="MOD UPDATER v26.2 - Dong bo &amp; cap nhat mod 1-Click" FontSize="12" Foreground="#A1A1AA" Margin="0,2,0,0"/>
                </StackPanel>
            </StackPanel>
        </StackPanel>

        <!-- Launcher Option -->
        <GroupBox Grid.Row="1" Header="Chon Launcher" BorderBrush="#27272A" Foreground="#A1A1AA" Padding="10" Margin="0,0,0,10">
            <WrapPanel>
                <RadioButton Name="radLegacy" Content="Legacy Launcher" IsChecked="True"/>
                <RadioButton Name="radPremium" Content="Official (Ban Quyen)"/>
                <RadioButton Name="radTLauncher" Content="TLauncher"/>
                <RadioButton Name="radCustom" Content="Duong dan tu chon"/>
            </WrapPanel>
        </GroupBox>

        <!-- Path Field -->
        <Grid Grid.Row="2" Margin="0,0,0,15">
            <Grid.ColumnDefinitions>
                <ColumnDefinition Width="*"/>
                <ColumnDefinition Width="Auto"/>
            </Grid.ColumnDefinitions>
            <TextBox Name="txtPath" Grid.Column="0" Height="28" Padding="5,3" Background="#09090B" Foreground="#F4F4F5" BorderBrush="#27272A" VerticalContentAlignment="Center" IsReadOnly="True"/>
            <Button Name="btnBrowse" Grid.Column="1" Content="Duyet..." Margin="5,0,0,0" Width="80" Height="28" IsEnabled="False"/>
        </Grid>

        <!-- Log Box -->
        <GroupBox Grid.Row="3" Header="Nhat ky cap nhat" BorderBrush="#27272A" Foreground="#A1A1AA" Margin="0,0,0,15">
            <TextBox Name="txtLog" Background="#09090B" Foreground="#E4E4E7" BorderThickness="0" AcceptsReturn="True" VerticalScrollBarVisibility="Auto" IsReadOnly="True" Padding="8" FontFamily="Consolas" FontSize="11" Text="San sang cap nhat mod... Click 'Kiem tra &amp; Cap nhat' de bat dau.&#x0d;&#x0a;"/>
        </GroupBox>

        <!-- Progress Bar -->
        <Grid Grid.Row="4" Margin="0,0,0,15">
            <Grid.ColumnDefinitions>
                <ColumnDefinition Width="*"/>
                <Grid.ColumnDefinitions>
                    <ColumnDefinition Width="*"/>
                    <ColumnDefinition Width="Auto"/>
                </Grid.ColumnDefinitions>
                <ProgressBar Name="progress" Grid.Column="0" Height="15" Background="#27272A" Foreground="#10B981" BorderThickness="0"/>
                <TextBlock Name="txtProgressVal" Grid.Column="1" Text="0/0" VerticalAlignment="Center" Margin="10,0,0,0" FontWeight="Bold" Foreground="#10B981"/>
            </Grid.Row>
        </Grid>

        <!-- Action Buttons -->
        <Grid Grid.Row="5">
            <Grid.ColumnDefinitions>
                <ColumnDefinition Width="*"/>
                <ColumnDefinition Width="Auto"/>
            </Grid.ColumnDefinitions>
            <TextBlock Name="txtStatus" Grid.Column="0" Text="Nhan nut de bat dau dong bo..." VerticalAlignment="Center" Foreground="#A1A1AA" FontSize="12"/>
            <Button Name="btnStart" Grid.Column="1" Content="Kiem tra &amp; Cap nhat" Background="#3B82F6" Foreground="White" Width="180" Height="35" FontSize="13"/>
        </Grid>
    </Grid>
</Window>
'@
        $reader = New-Object System.Xml.XmlNodeReader $xaml
        $window = [Windows.Markup.XamlReader]::Load($reader)
        $guiLoaded = $true
    } catch {
        # Silent fallback to console
    }
}

if ($guiLoaded) {
    # Bind elements as variables
    $xaml.SelectNodes("//*[@Name]") | ForEach-Object {
        Set-Variable -Name $_.Name -Value $window.FindName($_.Name) -Scope Script
    }

    $txtPath.Text = $legacyPath

    $asciiLogo = @'
 ██████╗ ██╗      ██████╗ ███╗   ██╗ ██████╗ ██████╗ ███████╗██╗     ██╗
██╔═══██╗██║     ██╔═══██╗████╗  ██║██╔════╝ ██╔══██╗██╔════╝██║     ██║
██║   ██║██║     ██║   ██║██╔██╗ ██║██║  ███╗██████╔╝█████╗  ██║     ██║
██║   ██║██║     ██║   ██║██║╚██╗██║██║   ██║██╔══██╗██╔══╝  ██║     ██║
╚██████╔╝███████╗╚██████╔╝██║ ╚████║╚██████╔╝██████╔╝███████╗███████╗███████╗
 ╚═════╝ ╚══════╝ ╚═════╝ ╚═╝  ╚═══╝ ╚═════╝ ╚═════╝ ╚══════╝╚══════╝╚══════╝
'@
    $divider = "=" * 78
    $txtLog.Text = "$asciiLogo`r`n$divider`r`nSan sang cap nhat mod... Click 'Kiem tra & Cap nhat' de bat dau.`r`n$divider`r`n"

    # Radio button actions
    $radLegacy.add_Checked({
        $txtPath.Text = $legacyPath
        $btnBrowse.IsEnabled = $false
    })
    $radPremium.add_Checked({
        $txtPath.Text = $premiumPath
        $btnBrowse.IsEnabled = $false
    })
    $radTLauncher.add_Checked({
        $txtPath.Text = $tlauncherPath
        $btnBrowse.IsEnabled = $false
    })
    $radCustom.add_Checked({
        $btnBrowse.IsEnabled = $true
    })

    # Browse folder dialog
    $btnBrowse.add_Click({
        Add-Type -AssemblyName System.Windows.Forms
        $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
        $dialog.Description = "Chon thu muc mods cua game Minecraft"
        if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
            $txtPath.Text = $dialog.SelectedPath
        }
    })

    # Keep UI responsive during synchronous download loop
    function Update-UI {
        [System.Windows.Threading.Dispatcher]::CurrentDispatcher.Invoke([System.Action] {}, [System.Windows.Threading.DispatcherPriority]::Background)
    }

    # Helper function to get UI responsive
    function Reset-UI {
        $btnStart.IsEnabled = $true
        $radLegacy.IsEnabled = $true
        $radPremium.IsEnabled = $true
        $radTLauncher.IsEnabled = $true
        $radCustom.IsEnabled = $true
        if ($radCustom.IsChecked) {
            $btnBrowse.IsEnabled = $true
        }
    }

    # Download & sync logic
    $btnStart.add_Click({
        $btnStart.IsEnabled = $false
        $radLegacy.IsEnabled = $false
        $radPremium.IsEnabled = $false
        $radTLauncher.IsEnabled = $false
        $radCustom.IsEnabled = $false
        $btnBrowse.IsEnabled = $false

        $txtStatus.Text = "Dang kiem tra cap nhat..."
        $txtLog.Text = "--- BAT DAU CAP NHAT ---`r`n"
        $txtLog.AppendText("Dang ket noi toi GitHub API...`r`n")
        Update-UI
        $swTotal = [System.Diagnostics.Stopwatch]::StartNew()

        try {
            [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
            $apiUrl = "https://api.github.com/repos/QuangquyNguyenvo/MineServer/contents/mods"
            $headers = @{ "User-Agent" = "PowerShell-Minecraft-Updater" }
            
            $repoFiles = Invoke-RestMethod -Uri $apiUrl -Headers $headers -Method Get
            
            if ($null -eq $repoFiles -or $repoFiles.Count -eq 0) {
                $txtLog.AppendText("[LOI] Khong the doc danh sach mod tu GitHub hoac thu muc trong.`r`n")
                $txtStatus.Text = "Loi ket noi GitHub API."
                Reset-UI
                return
            }

            # Ensure target folder exists
            $targetDir = $txtPath.Text
            if (!(Test-Path -LiteralPath $targetDir)) {
                $txtLog.AppendText("Tao thu muc mods moi: $targetDir`r`n")
                New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
            }

            # Scan local mods
            $localFiles = Get-ChildItem -Path $targetDir -File | Where-Object { $_.Extension -eq ".jar" -or $_.Name.EndsWith(".jar.disabled") }
            
            # Build map of repo mods
            $repoMods = @{}
            $repoBaseNames = @{}
            foreach ($file in $repoFiles) {
                if ($file.name.EndsWith(".jar") -or $file.name.EndsWith(".jar.disabled")) {
                    $repoMods[$file.name] = $file
                    $base = Get-ModBaseName $file.name
                    $repoBaseNames[$base] = $true
                }
            }

            # Calculate deletion list (Only delete if it is an old version of a server mod)
            $toDelete = @()
            foreach ($lf in $localFiles) {
                if (!$repoMods.ContainsKey($lf.Name)) {
                    $localBase = Get-ModBaseName $lf.Name
                    if ($repoBaseNames.ContainsKey($localBase)) {
                        $toDelete += $lf
                    }
                }
            }

            # Calculate download list
            $toDownload = @()
            foreach ($key in $repoMods.Keys) {
                $rmName = "$key"
                $rm = $repoMods[$rmName]
                $localFilePath = Join-Path $targetDir $rmName
                
                if (!(Test-Path -LiteralPath $localFilePath)) {
                    $toDownload += $rm
                } else {
                    $localSize = (Get-Item -LiteralPath $localFilePath).Length
                    if ($localSize -ne $rm.size) {
                        $toDownload += $rm
                    }
                }
            }

            $txtLog.AppendText("Tim thay: $($repoMods.Count) mod tren Repo, $($localFiles.Count) mod o may.`r`n")
            $txtLog.AppendText("Can tai them: $($toDownload.Count) mod.`r`n")
            $txtLog.AppendText("Can xoa bo (mod cu): $($toDelete.Count) mod.`r`n")
            Update-UI

            # Delete old mods
            if ($toDelete.Count -gt 0) {
                $txtLog.AppendText("--- DANG XOA CAC MOD CU ---`r`n")
                foreach ($df in $toDelete) {
                    $txtLog.AppendText("Xoa mod cu: $($df.Name)`r`n")
                    Remove-Item -LiteralPath $df.FullName -Force
                    Update-UI
                }
            }

            # Download missing/modified mods
            $failedDownloads = @()
            $totalBytesDownloaded = 0
            if ($toDownload.Count -gt 0) {
                $txtLog.AppendText("--- DANG TAI CAC MOD MOI ---`r`n")
                $progress.Maximum = $toDownload.Count
                $progress.Value = 0
                $txtProgressVal.Text = "0/$($toDownload.Count)"

                $client = New-Object System.Net.WebClient
                $count = 0

                foreach ($rm in $toDownload) {
                    $count++
                    $txtStatus.Text = "Dang tai: $($rm.name) ($count/$($toDownload.Count))"
                    $txtProgressVal.Text = "$count/$($toDownload.Count)"
                    $progress.Value = $count
                    $txtLog.AppendText("Tai ($count/$($toDownload.Count)): $($rm.name) ($([Math]::Round($rm.size / 1MB, 2)) MB)...`r`n")
                    Update-UI

                    $result = Invoke-ModDownloadWithRetry -Url $rm.download_url -TargetDir $targetDir -FileName $rm.name -ExpectedSize $rm.size -Client $client -Log { param($msg, $color) $txtLog.AppendText("$msg`r`n"); Update-UI }
                    if ($result.Success) {
                        $totalBytesDownloaded += $result.Bytes
                        $txtLog.AppendText("-> Hoan thanh!`r`n")
                    } else {
                        $txtLog.AppendText("[LOI] That bai han sau nhieu lan thu: $($rm.name)`r`n")
                        $failedDownloads += $rm.name
                    }
                    Update-UI
                }
            } else {
                $progress.Maximum = 1
                $progress.Value = 1
                $txtProgressVal.Text = "Xong"
            }

            $swTotal.Stop()
            $txtLog.AppendText("`r`n--- TONG KET ---`r`n")
            $txtLog.AppendText("Da tai: $([Math]::Round($totalBytesDownloaded / 1MB, 2)) MB trong $([Math]::Round($swTotal.Elapsed.TotalSeconds, 1))s`r`n")
            if ($failedDownloads.Count -gt 0) {
                $txtLog.AppendText("That bai: $($failedDownloads.Count) mod -> $($failedDownloads -join ', ')`r`n")
                $txtLog.AppendText("-> Nhan 'Kiem tra & Cap nhat' lan nua de thu tai lai.`r`n")
                $txtStatus.Text = "Hoan tat, con $($failedDownloads.Count) mod loi."
                [System.Windows.MessageBox]::Show("Dong bo xong nhung con $($failedDownloads.Count) mod tai loi. Xem log de biet chi tiet, roi thu lai.", "Hoan tat co loi", [System.Windows.MessageBoxButton]::OK, [System.Windows.MessageBoxImage]::Warning)
            } else {
                $txtLog.AppendText("Dong bo hoa thu muc mod thanh cong!`r`n")
                $txtStatus.Text = "Cap nhat thanh cong!"
                [System.Windows.MessageBox]::Show("Dong bo hoa mods Minecraft thanh cong!", "Thong bao", [System.Windows.MessageBoxButton]::OK, [System.Windows.MessageBoxImage]::Information)
            }

        } catch {
            $txtLog.AppendText("[LOI NGHIEM TRONG] $_ `r`n")
            $txtStatus.Text = "Gap loi trong qua trinh cap nhat."
            [System.Windows.MessageBox]::Show("Gap loi trong qua trinh cap nhat: `n$_", "Loi", [System.Windows.MessageBoxButton]::OK, [System.Windows.MessageBoxImage]::Error)
        }

        Reset-UI
    })

    # Show UI Window
    $window.ShowDialog() | Out-Null
} else {
    # Fallback to CLI mode for headless environment
    Run-ConsoleUpdate
}
