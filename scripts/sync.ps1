<#
.SYNOPSIS
  同步当前改动到 GitHub：git add -A → commit → push（SSH:443 通道，推送后 Actions 自动打包）。

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\sync.ps1 -Message "feat: 设置页密钥管理"

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\sync.ps1 -Message "fix: 空题干校验" -NoPush
#>
#Requires -Version 5.1
param(
    [string]$Message = "",
    [switch]$NoPush
)
$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path $PSScriptRoot -Parent
Set-Location $RepoRoot

if (-not (Test-Path ".git")) {
    Write-Host "[错误] $RepoRoot 不是 git 仓库" -ForegroundColor Red
    exit 1
}

function Invoke-Git {
    git @args
    if ($LASTEXITCODE -ne 0) { throw "git $($args -join ' ') 失败 (exit=$LASTEXITCODE)" }
}

git add -A
$staged = git diff --cached --name-only
if (-not $staged) {
    Write-Host "没有需要提交的改动。" -ForegroundColor Yellow
    exit 0
}

if (-not $Message) { $Message = "chore: 同步 $(Get-Date -Format 'yyyy-MM-dd HH:mm')" }
git commit -m $Message
if ($LASTEXITCODE -ne 0) { throw "git commit 失败" }

if ($NoPush) {
    Write-Host "已本地提交（未推送）。" -ForegroundColor Green
    exit 0
}

$RemoteUrl = git remote get-url origin
$Repo = if ($RemoteUrl -match 'github\.com(?::\d+)?[:/](.+?)(?:\.git)?/?$') { $Matches[1] } else { $null }

Invoke-Git push origin HEAD
Write-Host ""
Write-Host "已同步到 GitHub" -ForegroundColor Green
if ($Repo) {
    Write-Host "打包进度: https://github.com/$Repo/actions"
    Write-Host "要出手机测试包: scripts\release.ps1 -Tag `"v0.1.0`""
}
