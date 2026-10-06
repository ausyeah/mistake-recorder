<#
.SYNOPSIS
    Jules API & GitHub 协作辅助脚本，用于自动化派发任务、审查、追加提示词及合并发布。
#>

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

# 1. 测试连接与获取可用仓库
function Get-JulesSources {
    $headers = Get-JulesHeaders
    $res = Invoke-RestMethod -Uri "https://jules.googleapis.com/v1alpha/sources" -Headers $headers -Method Get
    return $res.sources
}

# 2. 列出最近的任务 Session
function Get-JulesSessions {
    param([int]$PageSize = 5)
    $headers = Get-JulesHeaders
    $res = Invoke-RestMethod -Uri "https://jules.googleapis.com/v1alpha/sessions?pageSize=$PageSize" -Headers $headers -Method Get
    return $res.sessions
}

# 3. 获取单个 Session 详情及最新状态
function Get-JulesSession {
    param([Parameter(Mandatory)][string]$SessionId)
    $headers = Get-JulesHeaders
    $id = $SessionId -replace "^sessions/", ""
    $res = Invoke-RestMethod -Uri "https://jules.googleapis.com/v1alpha/sessions/$id" -Headers $headers -Method Get
    return $res
}

# 4. 获取 Session 的活动记录与 AI 消息
function Get-JulesActivities {
    param([Parameter(Mandatory)][string]$SessionId)
    $headers = Get-JulesHeaders
    $id = $SessionId -replace "^sessions/", ""
    $res = Invoke-RestMethod -Uri "https://jules.googleapis.com/v1alpha/sessions/$id/activities?pageSize=50" -Headers $headers -Method Get
    return $res.activities
}

# 5. 向 Session 追加提示词进行修改指导
function Send-JulesFeedback {
    param(
        [Parameter(Mandatory)][string]$SessionId,
        [Parameter(Mandatory)][string]$Message
    )
    $headers = Get-JulesHeaders
    $id = $SessionId -replace "^sessions/", ""
    $body = @{
        "prompt" = $Message
    } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "https://jules.googleapis.com/v1alpha/sessions/${id}:sendMessage" -Headers $headers -Method Post -Body $body
    return $res
}

# 6. 发起全新的 Jules 任务（开发、重构、修复或审查）
function New-JulesTask {
    param(
        [Parameter(Mandatory)][string]$Title,
        [Parameter(Mandatory)][string]$Prompt,
        [string]$RepoSource = "sources/github/ausyeah/mistake-recorder",
        [string]$StartingBranch = "main",
        [bool]$RequireApproval = $false
    )
    $headers = Get-JulesHeaders
    $payload = @{
        "title" = $Title
        "prompt" = $Prompt
        "sourceContext" = @{
            "source" = $RepoSource
            "githubRepoContext" = @{
                "startingBranch" = $StartingBranch
            }
        }
        "requirePlanApproval" = $RequireApproval
    }
    $body = $payload | ConvertTo-Json -Depth 5
    $res = Invoke-RestMethod -Uri "https://jules.googleapis.com/v1alpha/sessions" -Headers $headers -Method Post -Body $body
    return $res
}

# 7. 查看 GitHub PR 的 CI Check Runs 状态
function Get-GitHubPrChecks {
    param([Parameter(Mandatory)][int]$PrNumber)
    $headers = Get-GitHubHeaders
    $pr = Invoke-RestMethod -Uri "https://api.github.com/repos/ausyeah/mistake-recorder/pulls/$PrNumber" -Headers $headers
    $headSha = $pr.head.sha
    $checks = Invoke-RestMethod -Uri "https://api.github.com/repos/ausyeah/mistake-recorder/commits/$headSha/check-runs" -Headers $headers
    return [PSCustomObject]@{
        PR = $PrNumber
        Title = $pr.title
        HeadBranch = $pr.head.ref
        HeadSha = $headSha
        Mergeable = $pr.mergeable
        MergeableState = $pr.mergeable_state
        CheckRuns = $checks.check_runs
    }
}

# 8. 合并 GitHub PR
function Merge-GitHubPr {
    param(
        [Parameter(Mandatory)][int]$PrNumber,
        [string]$CommitTitle,
        [string]$MergeMethod = "squash" # merge, squash, rebase
    )
    $headers = Get-GitHubHeaders
    $payload = @{
        "merge_method" = $MergeMethod
    }
    if ($CommitTitle) {
        $payload["commit_title"] = $CommitTitle
    }
    $body = $payload | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "https://api.github.com/repos/ausyeah/mistake-recorder/pulls/$PrNumber/merge" -Headers $headers -Method Put -Body $body
    return $res
}
