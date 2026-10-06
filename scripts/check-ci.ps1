$token = $env:GITHUB_TOKEN
if (-not $token) {
    Write-Host "GITHUB_TOKEN not found"
    exit 1
}

$headers = @{
    "Authorization" = "Bearer $token"
    "Accept" = "application/vnd.github+json"
    "User-Agent" = "Antigravity-Agent"
}

$response = Invoke-RestMethod -Uri "https://api.github.com/repos/ausyeah/mistake-recorder/actions/runs?per_page=3" -Headers $headers
foreach ($run in $response.workflow_runs) {
    [PSCustomObject]@{
        Id = $run.id
        Name = $run.name
        Sha = $run.head_sha.Substring(0, 7)
        Status = $run.status
        Conclusion = $run.conclusion
        CreatedAt = $run.created_at
        Url = $run.html_url
    }

    if ($run.conclusion -eq "failure" -and ($response.workflow_runs.IndexOf($run) -eq 0)) {
        Write-Host "`n--- Latest Failed Run Logs ($($run.id)) ---"
        try {
            $jobs = (Invoke-RestMethod -Uri "https://api.github.com/repos/ausyeah/mistake-recorder/actions/runs/$($run.id)/jobs" -Headers $headers).jobs
            foreach ($job in $jobs) {
                if ($job.conclusion -eq "failure") {
                    $log = Invoke-RestMethod -Uri "https://api.github.com/repos/ausyeah/mistake-recorder/actions/jobs/$($job.id)/logs" -Headers $headers
                    $lines = $log -split "`n"
                    $lines | Select-String -Pattern "error:|FAILURE:|Compilation error" -Context 2,2 | Select-Object -First 10 | ForEach-Object { $_.ToString() }
                }
            }
        } catch {
            Write-Host "Log read error: $_"
        }
    }
}
