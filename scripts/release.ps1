<#
.SYNOPSIS
  出一个手机测试包：提交未提交改动 → 打 annotated tag → 推送 tag（SSH:443）
  → GitHub Actions 自动编译并把 APK 挂到 Release → 本脚本轮询直到 APK 可下载。

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\release.ps1 -Tag "v0.1.0"

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\release.ps1 -Tag "v0.2.0" -TimeoutMin 25
#>
#Requires -Version 5.1
param(
    [Parameter(Mandatory = $true)]
    [string]$Tag,
    [int]$TimeoutMin = 15
)
$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path $PSScriptRoot -Parent
Set-Location $RepoRoot

if (-not (Test-Path ".git")) {
    Write-Host "[错误] $RepoRoot 不是 git 仓库" -ForegroundColor Red
    exit 1
}
if ($Tag -notmatch '^v\d+\.\d+\.\d+') {
    Write-Host "[错误] tag 需形如 v0.1.0" -ForegroundColor Red
    exit 1
}

function Invoke-Git {
    git @args
    if ($LASTEXITCODE -ne 0) { throw "git $($args -join ' ') 失败 (exit=$LASTEXITCODE)" }
}

$RemoteUrl = git remote get-url origin
if ($RemoteUrl -notmatch 'github\.com(?::\d+)?[:/](.+?)(?:\.git)?/?$') {
    Write-Host "[错误] 无法从 origin 解析 GitHub 仓库: $RemoteUrl" -ForegroundColor Red
    exit 1
}
$Repo = $Matches[1]

# 轮询 Release 状态用 api.github.com（HTTPS REST，与 git 走的不同域名，本机可达）
$H = @{ "User-Agent" = "release-script" }
$Token = $env:GITHUB_TOKEN
if (-not $Token) { $Token = $env:GH_TOKEN }
if ($Token) { $H["Authorization"] = "Bearer $Token" }

# 1) 先把未提交改动收进来
git add -A
$staged = git diff --cached --name-only
if ($staged) {
    git commit -m "release: $Tag"
    if ($LASTEXITCODE -ne 0) { throw "git commit 失败" }
    Invoke-Git push origin HEAD
    Write-Host "已推送待发布改动。" -ForegroundColor Green
} else {
    Write-Host "无未提交改动。"
}

# 2) 打 tag 并推送（CI 触发器）
if (git tag -l $Tag) {
    Write-Host "[错误] 本地已存在 tag $Tag，换个版本号。" -ForegroundColor Red
    exit 1
}
git tag -a $Tag -m "错题本 $Tag"
Invoke-Git push origin $Tag
Write-Host "tag $Tag 已推送，GitHub Actions 开始编译……" -ForegroundColor Green

# 3) 轮询 Release 直到 APK 就绪
$deadline = (Get-Date).AddMinutes($TimeoutMin)
$url = "https://github.com/$Repo/releases/tag/$Tag"
$api = "https://api.github.com/repos/$Repo/releases/tags/$Tag"
$asset = $null
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 15
    try {
        $rel = Invoke-RestMethod -Uri $api -Headers $H
        if ($rel.assets -and $rel.assets.Count -gt 0) {
            $asset = $rel.assets | Where-Object { $_.name -like "*.apk" } | Select-Object -First 1
            if ($asset) { break }
        }
        Write-Host "  编译中…… (资产 $($rel.assets.Count) 个)"
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode.value__ -ne 404) { Write-Host "  $_" }
    }
}

if ($asset) {
    Write-Host ""
    Write-Host "发布完成！手机下载：" -ForegroundColor Green
    Write-Host "  $url"
    Write-Host "  $($asset.browser_download_url)"
} else {
    Write-Host ""
    Write-Host "等待超时（${TimeoutMin} 分钟）。编译可能仍在进行，稍后查看：$url" -ForegroundColor Yellow
    exit 2
}
