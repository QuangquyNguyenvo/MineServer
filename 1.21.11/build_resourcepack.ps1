# Build Resource Pack Script
# Chay script nay de dong goi resourcepack va tinh SHA1

$packFolder = "RaumaniaPack"
$outputZip = "server-resourcepack.zip"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  DONG GOI RESOURCE PACK" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Xoa file cu neu co
if (Test-Path $outputZip) {
    Remove-Item $outputZip -Force
    Write-Host "[OK] Da xoa file cu: $outputZip" -ForegroundColor Yellow
}

# Dong goi thanh ZIP
Write-Host "[...] Dang dong goi $packFolder..." -ForegroundColor Gray
Compress-Archive -Path "$packFolder\*" -DestinationPath $outputZip -Force
Write-Host "[OK] Da tao: $outputZip" -ForegroundColor Green

# Tinh SHA1
Write-Host "[...] Dang tinh SHA1..." -ForegroundColor Gray
$sha1 = (Get-FileHash -Path $outputZip -Algorithm SHA1).Hash.ToLower()
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  THONG TIN RESOURCE PACK" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "File: $outputZip" -ForegroundColor White
Write-Host "SHA1: $sha1" -ForegroundColor Yellow
Write-Host ""
Write-Host "HUONG DAN:" -ForegroundColor Magenta
Write-Host "1. Upload file $outputZip len host (Dropbox, GitHub, etc.)" -ForegroundColor White
Write-Host "2. Lay direct download link" -ForegroundColor White
Write-Host "3. Sua file server.properties:" -ForegroundColor White
Write-Host "   resource-pack=<URL>" -ForegroundColor Gray
Write-Host "   resource-pack-sha1=$sha1" -ForegroundColor Gray
Write-Host ""

# Luu SHA1 vao file de tien copy
$sha1 | Out-File -FilePath "resourcepack-sha1.txt" -Encoding ASCII
Write-Host "[OK] Da luu SHA1 vao resourcepack-sha1.txt" -ForegroundColor Green
