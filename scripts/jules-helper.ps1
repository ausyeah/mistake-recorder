<#
.SYNOPSIS
    Jules API & GitHub 协作辅助脚本，用于自动化派发任务、审查、追加提示词及合并发布。
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action list-sources
    powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action list
    powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action new -Title "测试任务" -Prompt "补充单测"
    powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action feedback -SessionId "12345" -Message "请修复编译错误"
    powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action check-pr -PrNumber 18
    powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action merge-pr -PrNumber 18 -CommitTitle "feat: merge"
#>
param(
    [string]$Action = "help",
    [string]$SessionId = "",
    [string]$Title = "",
    [string]$Prompt = "",
    [string]$Message = "",
    [int]$PrNumber = 0,
    [string]$StartingBranch = "main",
    [int]$PageSize = 10,
    [string]$CommitTitle = ""
)

$ErrorActionPreference = "Stop"

function Get-JulesHeaders {
    if (-not $env:JULES_API_KEY) {
        throw "环境变量 JULES_API_KEY 未设置！"
    }
    return @{
        "X-Goog-Api-Key" = $env:JULES_API_KEY
        "Content-Type"   = "application/json"
    }
}

function Get-GitHubHeaders {
    if (-not $env:GITHUB_TOKEN) {
        throw "环境变量 GITHUB_TOKEN 未设置！"
    }
    return @{
        "Authorization" = "token $env:GITHUB_TOKEN"
        "Accept"        = "application/vnd.github.v3+json"
        "User-Agent"    = "Jules-Helper-Agent"
    }
}

function Get-JulesSources {
    $headers = Get-JulesHeaders
    $res = Invoke-RestMethod -Uri "https://jules.googleapis.com/v1alpha/sources" -Headers $headers -Method Get
    return $res.sources
}

function Get-JulesSessions {
    param([int]$Limit = 10)
    $headers = Get-JulesHeaders
    $res = Invoke-RestMethod -Uri "https://jules.googleapis.com/v1alpha/sessions?pageSize=$Limit" -Headers $headers -Method Get
    return $res.sessions
}

function Get-JulesSession {
    param([string]$TargetSessionId)
    $headers = Get-JulesHeaders
    $id = $TargetSessionId -replace "^sessions/", ""
    $res = Invoke-RestMethod -Uri "https://jules.googleapis.com/v1alpha/sessions/$id" -Headers $headers -Method Get
    return $res
}

function Get-JulesActivities {
    param([string]$TargetSessionId)
    $headers = Get-JulesHeaders
    $id = $TargetSessionId -replace "^sessions/", ""
    $res = Invoke-RestMethod -Uri "https://jules.googleapis.com/v1alpha/sessions/$id/activities?pageSize=50" -Headers $headers -Method Get
    return $res.activities
}

function Send-JulesFeedback {
    param([string]$TargetSessionId, [string]$FeedbackMessage)
    $headers = Get-JulesHeaders
    $id = $TargetSessionId -replace "^sessions/", ""
    $body = @{
        "prompt" = $FeedbackMessage
    } | ConvertTo-Json
    $url = "https://jules.googleapis.com/v1alpha/sessions/$($id):sendMessage"
    $res = Invoke-RestMethod -Uri $url -Headers $headers -Method Post -Body $body
    return $res
}

function New-JulesTask {
    param(
        [string]$TaskTitle,
        [string]$TaskPrompt,
        [string]$RepoSource = "sources/github/ausyeah/mistake-recorder",
        [string]$Branch = "main",
        [bool]$RequireApproval = $false
    )
    $headers = Get-JulesHeaders
    $payload = @{
        "title" = $TaskTitle
        "prompt" = $TaskPrompt
        "sourceContext" = @{
            "source" = $RepoSource
            "githubRepoContext" = @{
                "startingBranch" = $Branch
            }
        }
        "requirePlanApproval" = $RequireApproval
    }
    $body = $payload | ConvertTo-Json -Depth 5
    $res = Invoke-RestMethod -Uri "https://jules.googleapis.com/v1alpha/sessions" -Headers $headers -Method Post -Body $body
    return $res
}

function Get-GitHubPrChecks {
    param([int]$Number)
    $headers = Get-GitHubHeaders
    $pr = Invoke-RestMethod -Uri "https://api.github.com/repos/ausyeah/mistake-recorder/pulls/$Number" -Headers $headers
    $headSha = $pr.head.sha
    $checks = Invoke-RestMethod -Uri "https://api.github.com/repos/ausyeah/mistake-recorder/commits/$headSha/check-runs" -Headers $headers
    return [PSCustomObject]@{
        PR = $Number
        Title = $pr.title
        HeadBranch = $pr.head.ref
        HeadSha = $headSha
        Mergeable = $pr.mergeable
        MergeableState = $pr.mergeable_state
        CheckRuns = $checks.check_runs
    }
}

function Merge-GitHubPr {
    param([int]$Number, [string]$TitleText = "", [string]$MergeMethod = "squash")
    $headers = Get-GitHubHeaders
    $payload = @{
        "merge_method" = $MergeMethod
    }
    if ($TitleText) {
        $payload["commit_title"] = $TitleText
    }
    $body = $payload | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "https://api.github.com/repos/ausyeah/mistake-recorder/pulls/$Number/merge" -Headers $headers -Method Put -Body $body
    return $res
}

# CLI 调度分支
if ($Action -eq "list-sources") {
    Get-JulesSources | Select-Object name, id | Format-Table -AutoSize
} elseif ($Action -eq "list") {
    $sessions = Get-JulesSessions -Limit $PageSize
    if ($sessions) {
        $sessions | Select-Object name, title, state, createTime | Format-Table -AutoSize
    } else {
        Write-Host "当前无活动 Session。" -ForegroundColor Yellow
    }
} elseif ($Action -eq "get") {
    if (-not $SessionId) { throw "-SessionId 必填" }
    Get-JulesSession -TargetSessionId $SessionId | ConvertTo-Json -Depth 5
} elseif ($Action -eq "activities") {
    if (-not $SessionId) { throw "-SessionId 必填" }
    Get-JulesActivities -TargetSessionId $SessionId | Select-Object name, createTime, type | Format-Table -AutoSize
} elseif ($Action -eq "new") {
    if (-not $Title -or -not $Prompt) { throw "-Title 和 -Prompt 必填" }
    Write-Host "正在下发任务给 Jules..." -ForegroundColor Cyan
    $res = New-JulesTask -TaskTitle $Title -TaskPrompt $Prompt -Branch $StartingBranch
    Write-Host "任务下发成功！Session ID: $($res.name)" -ForegroundColor Green
    $res | ConvertTo-Json -Depth 5
} elseif ($Action -eq "feedback") {
    if (-not $SessionId -or -not $Message) { throw "-SessionId 和 -Message 必填" }
    Write-Host "正在给 Jules 追加指令..." -ForegroundColor Cyan
    $res = Send-JulesFeedback -TargetSessionId $SessionId -FeedbackMessage $Message
    Write-Host "指令追加成功！" -ForegroundColor Green
    $res | ConvertTo-Json -Depth 5
} elseif ($Action -eq "check-pr") {
    if ($PrNumber -le 0) { throw "-PrNumber 必填" }
    Get-GitHubPrChecks -Number $PrNumber
} elseif ($Action -eq "merge-pr") {
    if ($PrNumber -le 0) { throw "-PrNumber 必填" }
    Merge-GitHubPr -Number $PrNumber -TitleText $CommitTitle
} else {
    Write-Host "Jules-Helper 用法:" -ForegroundColor Cyan
    Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action list-sources"
    Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action list"
    Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action new -Title <标题> -Prompt <提示词>"
    Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action feedback -SessionId <ID> -Message <消息>"
    Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action check-pr -PrNumber <PR号>"
    Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action merge-pr -PrNumber <PR号> -CommitTitle <信息>"
}
