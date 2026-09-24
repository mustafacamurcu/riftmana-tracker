# Runs track_riftmana.py, then commits and pushes total_value_history.csv /
# latest.json to GitHub if they changed (so the GitHub Pages chart and the
# Android widget - which polls latest.json - stay current). Also runs
# track_card_prices.py (per-card price history + 24h movers) - this one is
# best-effort: if it fails, we still publish whatever track_riftmana.py got,
# since the overall Total Value figure is the priority.
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
    # No stderr redirection here: in Windows PowerShell 5.1, redirecting a native
    # command's stderr (even to $null) wraps it as a terminating error under
    # $ErrorActionPreference = "Stop", which silently killed this whole script
    # the last two times a rebase actually needed aborting (no further log
    # output, no exception message - just a dead process and LastTaskResult=1).
    git rebase --abort
    if ($LASTEXITCODE -ne 0) {
        Log "WARNING: git rebase --abort exited with code $LASTEXITCODE (harmless if there was nothing to abort)"
    }
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

try {
    & $pythonExe (Join-Path $RepoDir "track_card_prices.py")
    if ($LASTEXITCODE -ne 0) {
        Log "WARNING: track_card_prices.py exited with code $LASTEXITCODE (non-fatal, continuing)"
    }
} catch {
    Log "WARNING: track_card_prices.py errored (non-fatal, continuing): $_"
}

git add total_value_history.csv latest.json card_price_history.csv movers.json
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
