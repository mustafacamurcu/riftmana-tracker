# Runs track_riftmana.py, then commits and pushes total_value_history.csv /
# latest.json to GitHub if they changed (so the GitHub Pages chart and the
# Android widget - which polls latest.json - stay current).
#
# Intended to be run hourly by Windows Task Scheduler; see schedule_task.ps1
# to register it. Safe to run manually too.

$ErrorActionPreference = "Stop"
$RepoDir = Split-Path -Parent $PSScriptRoot
Set-Location $RepoDir

$LogDir = Join-Path $RepoDir "logs"
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }
$LogFile = Join-Path $LogDir "run_and_publish.log"

function Log($msg) {
    $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $msg
    Add-Content -Path $LogFile -Value $line
    Write-Host $line
}

Log "--- run start ---"

git pull --rebase
if ($LASTEXITCODE -ne 0) {
    Log "WARNING: git pull --rebase exited with code $LASTEXITCODE - aborting rebase to leave repo clean"
    git rebase --abort 2>$null
}

try {
    $pythonExe = (Get-Command python).Source
    & $pythonExe (Join-Path $RepoDir "track_riftmana.py")
    if ($LASTEXITCODE -ne 0) {
        Log "track_riftmana.py exited with code $LASTEXITCODE - skipping publish."
        exit 1
    }
} catch {
    Log "ERROR running track_riftmana.py: $_"
    exit 1
}

git add total_value_history.csv latest.json
$diff = git diff --cached --quiet; $hasChanges = ($LASTEXITCODE -ne 0)

if ($hasChanges) {
    $timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    git commit -m "Log total value $timestamp" | Out-Null
    git push
    if ($LASTEXITCODE -ne 0) {
        Log "ERROR: git push exited with code $LASTEXITCODE"
    } else {
        Log "Committed and pushed."
    }
} else {
    Log "No changes to commit."
}

Log "--- run end ---"
