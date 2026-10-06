<#
.SYNOPSIS
    Jules API & GitHub collaboration helper script.
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action list-sources
    powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action list
    powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action new -Title "Task" -Prompt "Prompt"
    powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action feedback -SessionId "12345" -Message "Fix this"
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
        throw "Environment variable JULES_API_KEY is not set!"
    }
    return @{
        "X-Goog-Api-Key" = $env:JULES_API_KEY
    }
}

function Get-GitHubHeaders {
    if (-not $env:GITHUB_TOKEN) {
        throw "Environment variable GITHUB_TOKEN is not set!"
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
    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    $url = "https://jules.googleapis.com/v1alpha/sessions/$($id):sendMessage"
    $res = Invoke-RestMethod -Uri $url -Headers $headers -Method Post -Body $bodyBytes -ContentType "application/json; charset=utf-8"
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
    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    $res = Invoke-RestMethod -Uri "https://jules.googleapis.com/v1alpha/sessions" -Headers $headers -Method Post -Body $bodyBytes -ContentType "application/json; charset=utf-8"
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

# CLI routing
if ($Action -eq "list-sources") {
    Get-JulesSources | Select-Object name, id | Format-Table -AutoSize
} elseif ($Action -eq "list") {
    $sessions = Get-JulesSessions -Limit $PageSize
    if ($sessions) {
        $sessions | Select-Object name, title, state, createTime | Format-Table -AutoSize
    } else {
        Write-Host "No active Jules sessions found." -ForegroundColor Yellow
    }
} elseif ($Action -eq "get") {
    if (-not $SessionId) { throw "-SessionId is required" }
    Get-JulesSession -TargetSessionId $SessionId | ConvertTo-Json -Depth 5
} elseif ($Action -eq "activities") {
    if (-not $SessionId) { throw "-SessionId is required" }
    Get-JulesActivities -TargetSessionId $SessionId | Select-Object name, createTime, type | Format-Table -AutoSize
} elseif ($Action -eq "new") {
    if (-not $Title -or -not $Prompt) { throw "-Title and -Prompt are required" }
    Write-Host "Dispatching new task to Jules..." -ForegroundColor Cyan
    $res = New-JulesTask -TaskTitle $Title -TaskPrompt $Prompt -Branch $StartingBranch
    Write-Host "Task successfully dispatched! Session ID: $($res.name)" -ForegroundColor Green
    $res | ConvertTo-Json -Depth 5
} elseif ($Action -eq "feedback") {
    if (-not $SessionId -or -not $Message) { throw "-SessionId and -Message are required" }
    Write-Host "Sending feedback to Jules..." -ForegroundColor Cyan
    $res = Send-JulesFeedback -TargetSessionId $SessionId -FeedbackMessage $Message
    Write-Host "Feedback sent successfully!" -ForegroundColor Green
    $res | ConvertTo-Json -Depth 5
} elseif ($Action -eq "check-pr") {
    if ($PrNumber -le 0) { throw "-PrNumber is required" }
    Get-GitHubPrChecks -Number $PrNumber
} elseif ($Action -eq "merge-pr") {
    if ($PrNumber -le 0) { throw "-PrNumber is required" }
    Merge-GitHubPr -Number $PrNumber -TitleText $CommitTitle
} else {
    Write-Host "Usage:" -ForegroundColor Cyan
    Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action list-sources"
    Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action list"
    Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action new -Title <title> -Prompt <prompt>"
    Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action feedback -SessionId <id> -Message <msg>"
    Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action check-pr -PrNumber <num>"
    Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\jules-helper.ps1 -Action merge-pr -PrNumber <num> -CommitTitle <msg>"
}
