Add-Type -AssemblyName System.Drawing

$iconPath = "d:\Code\Project\MinecraftServer\plugins\MiniMOTD\icons\raumania.png"

if (Test-Path $iconPath) {
    try {
        $img = [System.Drawing.Image]::FromFile($iconPath)
        
        # Create new 64x64 bitmap
        $newImg = new-object System.Drawing.Bitmap 64, 64
        $graph = [System.Drawing.Graphics]::FromImage($newImg)
        $graph.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graph.DrawImage($img, 0, 0, 64, 64)
        
        # Dispose original to unlock file
        $img.Dispose()
        
        # Save to temp file first to ensure no locking issues, then replace
        $tempPath = $iconPath + ".tmp.png"
        $newImg.Save($tempPath, [System.Drawing.Imaging.ImageFormat]::Png)
        $newImg.Dispose()
        $graph.Dispose()
        
        Move-Item $tempPath $iconPath -Force
        Write-Host "Resized to 64x64 successfully."
    } catch {
        Write-Error "Failed to resize: $_"
    }
} else {
    Write-Error "File not found: $iconPath"
}
