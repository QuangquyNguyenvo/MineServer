[CmdletBinding()]
param(
    [switch]$NoGui
)

# Set UTF-8 Output Encoding
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

# Function to run update in console mode (Fallback)
function Run-ConsoleUpdate($targetDir) {
    Write-Host "=========================================" -ForegroundColor Cyan
    Write-Host "🚀 MINESERVER v26.2 MOD UPDATER (CONSOLE)" -ForegroundColor Cyan
    Write-Host "=========================================" -ForegroundColor Cyan
    Write-Host "Thu muc dich: $targetDir" -ForegroundColor Yellow
    Write-Host "Dang ket noi toi GitHub API..." -ForegroundColor Gray
    
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $apiUrl = "https://api.github.com/repos/QuangquyNguyenvo/MineServer/contents/26.2/mods"
        $headers = @{ "User-Agent" = "PowerShell-Minecraft-Updater" }
        
        $repoFiles = Invoke-RestMethod -Uri $apiUrl -Headers $headers -Method Get
        
        if ($null -eq $repoFiles -or $repoFiles.Count -eq 0) {
            Write-Host "[LOI] Khong the doc danh sach mod tu GitHub hoac thu muc trong." -ForegroundColor Red
            return
        }

        # Ensure target folder exists
        if (!(Test-Path $targetDir)) {
            Write-Host "Tao thu muc moi: $targetDir" -ForegroundColor Gray
            New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
        }

        # Scan local mods
        $localFiles = Get-ChildItem -Path $targetDir -File | Where-Object { $_.Extension -eq ".jar" -or $_.Name.EndsWith(".jar.disabled") }
        
        # Build map of repo mods
        $repoMods = @{}
        foreach ($file in $repoFiles) {
            if ($file.name.EndsWith(".jar") -or $file.name.EndsWith(".jar.disabled")) {
                $repoMods[$file.name] = $file
            }
        }

        # Calculate deletion list
        $toDelete = @()
        foreach ($lf in $localFiles) {
            if (!$repoMods.ContainsKey($lf.Name)) {
                $toDelete += $lf
            }
        }

        # Calculate download list
        $toDownload = @()
        foreach ($rmName in $repoMods.Keys) {
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

        Write-Host "Tim thay: $($repoMods.Count) mod tren Repo | $($localFiles.Count) mod o may." -ForegroundColor Green
        Write-Host "Can tai them: $($toDownload.Count) mod | Can xoa bo: $($toDelete.Count) mod." -ForegroundColor Yellow

        # Delete old mods
        if ($toDelete.Count -gt 0) {
            Write-Host "`n[ XOA MOD CU ]" -ForegroundColor Red
            foreach ($df in $toDelete) {
                Write-Host "- Dang xoa: $($df.Name)" -ForegroundColor DarkRed
                Remove-Item -LiteralPath $df.FullName -Force
            }
        }

        # Download missing/modified mods
        if ($toDownload.Count -gt 0) {
            Write-Host "`n[ TAI MOD MOI ]" -ForegroundColor Green
            $client = New-Object System.Net.WebClient
            $count = 0
            foreach ($rm in $toDownload) {
                $count++
                Write-Host "-> Tai ($count/$($toDownload.Count)): $($rm.name) ($([Math]::Round($rm.size / 1MB, 2)) MB)..." -ForegroundColor White
                
                $destPath = Join-Path $targetDir $rm.name
                $tempDestPath = $destPath + ".tmp"
                
                try {
                    $client.DownloadFile($rm.download_url, $tempDestPath)
                    if (Test-Path -LiteralPath $destPath) { Remove-Item -LiteralPath $destPath -Force }
                    Rename-Item -LiteralPath $tempDestPath -NewName $rm.name
                    Write-Host "   [ OK ] Tai xong!" -ForegroundColor Green
                } catch {
                    Write-Host "   [ LOI ] That bai khi tai: $($rm.name)" -ForegroundColor Red
                    if (Test-Path -LiteralPath $tempDestPath) { Remove-Item -LiteralPath $tempDestPath -Force }
                }
            }
        } else {
            Write-Host "Tat ca cac mod da dong bo va o ban moi nhat!" -ForegroundColor Green
        }

        Write-Host "`n=========================================" -ForegroundColor Green
        Write-Host "HOAN THANH DONG BO HOA MODS!" -ForegroundColor Green
        Write-Host "=========================================" -ForegroundColor Green

    } catch {
        Write-Host "[LOI NGHIEM TRONG] $_" -ForegroundColor Red
    }
}

# Determine if GUI should run
$guiLoaded = $false
if ($guiSupported -and !$NoGui -and [System.Environment]::UserInteractive) {
    try {
        [xml]$xaml = @'
<Window xmlns="http://schemas.microsoft.com/winfx/2000/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2000/xaml"
        Title="MineServer v26.2 Mod Updater" Height="500" Width="650"
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
            <TextBlock Text="🚀 MINESERVER v26.2 MOD UPDATER" FontSize="20" FontWeight="Bold" Foreground="#3B82F6"/>
            <TextBlock Text="Công cụ tự động đồng bộ và cập nhật Mod 1-Click" FontSize="12" Foreground="#A1A1AA" Margin="0,2,0,0"/>
        </StackPanel>

        <!-- Launcher Option -->
        <GroupBox Grid.Row="1" Header="Chọn Launcher" BorderBrush="#27272A" Foreground="#A1A1AA" Padding="10" Margin="0,0,0,10">
            <WrapPanel>
                <RadioButton Name="radLegacy" Content="Legacy Launcher" IsChecked="True"/>
                <RadioButton Name="radPremium" Content="Official (Bản Quyền)"/>
                <RadioButton Name="radTLauncher" Content="TLauncher"/>
                <RadioButton Name="radCustom" Content="Đường dẫn tự chọn"/>
            </WrapPanel>
        </GroupBox>

        <!-- Path Field -->
        <Grid Grid.Row="2" Margin="0,0,0,15">
            <Grid.ColumnDefinitions>
                <ColumnDefinition Width="*"/>
                <ColumnDefinition Width="Auto"/>
            </Grid.ColumnDefinitions>
            <TextBox Name="txtPath" Grid.Column="0" Height="28" Padding="5,3" Background="#09090B" Foreground="#F4F4F5" BorderBrush="#27272A" VerticalContentAlignment="Center" IsReadOnly="True"/>
            <Button Name="btnBrowse" Grid.Column="1" Content="Duyệt..." Margin="5,0,0,0" Width="80" Height="28" IsEnabled="False"/>
        </Grid>

        <!-- Log Box -->
        <GroupBox Grid.Row="3" Header="Nhật ký cập nhật" BorderBrush="#27272A" Foreground="#A1A1AA" Margin="0,0,0,15">
            <TextBox Name="txtLog" Background="#09090B" Foreground="#E4E4E7" BorderThickness="0" AcceptsReturn="True" VerticalScrollBarVisibility="Auto" IsReadOnly="True" Padding="8" FontFamily="Consolas" FontSize="11" Text="Sẵn sàng cập nhật mod... Click 'Kiểm tra &amp; Cập nhật' để bắt đầu.&#x0d;&#x0a;"/>
        </GroupBox>

        <!-- Progress Bar -->
        <Grid Grid.Row="4" Margin="0,0,0,15">
            <Grid.ColumnDefinitions>
                <ColumnDefinition Width="*"/>
                <ColumnDefinition Width="Auto"/>
            </Grid.ColumnDefinitions>
            <ProgressBar Name="progress" Grid.Column="0" Height="15" Background="#27272A" Foreground="#10B981" BorderThickness="0"/>
            <TextBlock Name="txtProgressVal" Grid.Column="1" Text="0/0" VerticalAlignment="Center" Margin="10,0,0,0" FontWeight="Bold" Foreground="#10B981"/>
        </Grid>

        <!-- Action Buttons -->
        <Grid Grid.Row="5">
            <Grid.ColumnDefinitions>
                <ColumnDefinition Width="*"/>
                <ColumnDefinition Width="Auto"/>
            </Grid.ColumnDefinitions>
            <TextBlock Name="txtStatus" Grid.Column="0" Text="Nhấn nút để bắt đầu đồng bộ..." VerticalAlignment="Center" Foreground="#A1A1AA" FontSize="12"/>
            <Button Name="btnStart" Grid.Column="1" Content="Kiểm tra &amp; Cập nhật" Background="#3B82F6" Foreground="White" Width="180" Height="35" FontSize="13"/>
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
        $dialog.Description = "Chọn thư mục mods của game Minecraft"
        if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
            $txtPath.Text = $dialog.SelectedPath
        }
    })

    # Keep UI responsive during synchronous download loop
    function Update-UI {
        [System.Windows.Threading.Dispatcher]::CurrentDispatcher.Invoke([System.Action] {}, [System.Windows.Threading.DispatcherPriority]::Background)
    }

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

        $txtStatus.Text = "Đang kiểm tra cập nhật..."
        $txtLog.Text = "--- BẮT ĐẦU CẬP NHẬT ---`r`n"
        $txtLog.AppendText("Đang kết nối tới GitHub API...`r`n")
        Update-UI

        try {
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            $apiUrl = "https://api.github.com/repos/QuangquyNguyenvo/MineServer/contents/26.2/mods"
            $headers = @{ "User-Agent" = "PowerShell-Minecraft-Updater" }
            
            $repoFiles = Invoke-RestMethod -Uri $apiUrl -Headers $headers -Method Get
            
            if ($null -eq $repoFiles -or $repoFiles.Count -eq 0) {
                $txtLog.AppendText("[LỖI] Không thể đọc danh sách mod từ GitHub hoặc thư mục trống.`r`n")
                $txtStatus.Text = "Lỗi kết nối GitHub API."
                Reset-UI
                return
            }

            # Ensure target folder exists
            $targetDir = $txtPath.Text
            if (!(Test-Path $targetDir)) {
                $txtLog.AppendText("Tạo thư mục mods mới: $targetDir`r`n")
                New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
            }

            # Scan local mods
            $localFiles = Get-ChildItem -Path $targetDir -File | Where-Object { $_.Extension -eq ".jar" -or $_.Name.EndsWith(".jar.disabled") }
            
            # Build map of repo mods
            $repoMods = @{}
            foreach ($file in $repoFiles) {
                if ($file.name.EndsWith(".jar") -or $file.name.EndsWith(".jar.disabled")) {
                    $repoMods[$file.name] = $file
                }
            }

            # Calculate deletion list
            $toDelete = @()
            foreach ($lf in $localFiles) {
                if (!$repoMods.ContainsKey($lf.Name)) {
                    $toDelete += $lf
                }
            }

            # Calculate download list
            $toDownload = @()
            foreach ($rmName in $repoMods.Keys) {
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

            $txtLog.AppendText("Tìm thấy: $($repoMods.Count) mod trên Repo, $($localFiles.Count) mod ở máy.`r`n")
            $txtLog.AppendText("Cần tải thêm: $($toDownload.Count) mod.`r`n")
            $txtLog.AppendText("Cần xóa bỏ: $($toDelete.Count) mod.`r`n")
            Update-UI

            # Delete old mods
            if ($toDelete.Count -gt 0) {
                $txtLog.AppendText("--- ĐANG XÓA CÁC MOD CŨ ---`r`n")
                foreach ($df in $toDelete) {
                    $txtLog.AppendText("Xóa mod cũ: $($df.Name)`r`n")
                    Remove-Item -LiteralPath $df.FullName -Force
                    Update-UI
                }
            }

            # Download missing/modified mods
            if ($toDownload.Count -gt 0) {
                $txtLog.AppendText("--- ĐANG TẢI CÁC MOD MỚI ---`r`n")
                $progress.Maximum = $toDownload.Count
                $progress.Value = 0
                $txtProgressVal.Text = "0/$($toDownload.Count)"
                
                $client = New-Object System.Net.WebClient
                $count = 0
                
                foreach ($rm in $toDownload) {
                    $count++
                    $txtStatus.Text = "Đang tải: $($rm.name) ($count/$($toDownload.Count))"
                    $txtProgressVal.Text = "$count/$($toDownload.Count)"
                    $progress.Value = $count
                    $txtLog.AppendText("Tải ($count/$($toDownload.Count)): $($rm.name) ($([Math]::Round($rm.size / 1MB, 2)) MB)...`r`n")
                    Update-UI
                    
                    $destPath = Join-Path $targetDir $rm.name
                    $tempDestPath = $destPath + ".tmp"
                    
                    try {
                        $client.DownloadFile($rm.download_url, $tempDestPath)
                        if (Test-Path -LiteralPath $destPath) { Remove-Item -LiteralPath $destPath -Force }
                        Rename-Item -LiteralPath $tempDestPath -NewName $rm.name
                        $txtLog.AppendText("-> Hoàn thành!`r`n")
                    } catch {
                        $txtLog.AppendText("[LỖI] Thất bại khi tải: $($rm.name). Chi tiết: $_ `r`n")
                        if (Test-Path -LiteralPath $tempDestPath) { Remove-Item -LiteralPath $tempDestPath -Force }
                    }
                    Update-UI
                }
            } else {
                $progress.Maximum = 1
                $progress.Value = 1
                $txtProgressVal.Text = "Xong"
            }

            $txtLog.AppendText("`r`n--- HOÀN THÀNH CẬP NHẬT ---`r`n")
            $txtLog.AppendText("Đồng bộ hóa thư mục mod thành công!`r`n")
            $txtStatus.Text = "Cập nhật thành công!"
            [System.Windows.MessageBox]::Show("Đồng bộ hóa mods Minecraft thành công!", "Thông báo", [System.Windows.MessageBoxButton]::OK, [System.Windows.MessageBoxImage]::Information)

        } catch {
            $txtLog.AppendText("[LỖI NGHIÊM TRỌNG] $_ `r`n")
            $txtStatus.Text = "Gặp lỗi trong quá trình cập nhật."
            [System.Windows.MessageBox]::Show("Gặp lỗi trong quá trình cập nhật: `n$_", "Lỗi", [System.Windows.MessageBoxButton]::OK, [System.Windows.MessageBoxImage]::Error)
        }

        Reset-UI
    })

    # Show UI Window
    $window.ShowDialog() | Out-Null
} else {
    # Fallback to CLI mode for headless environment
    Run-ConsoleUpdate $defaultPath
}
