# Quick commit & push cho mods cua MineServer
param(
    [string]$CommitMessage = "Update modpack files"
)

$repoDir = $PSScriptRoot

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
