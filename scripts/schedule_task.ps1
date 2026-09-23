# Registers an hourly Windows Task Scheduler job that runs run_and_publish.ps1.
# Run this once, from a normal PowerShell prompt:
#   powershell -ExecutionPolicy Bypass -File scripts\schedule_task.ps1
#
# StartWhenAvailable means: if the computer was off/asleep when an hourly
# trigger was due, it runs as soon as the computer is next on - matching
# "hourly whenever the computer is on" rather than requiring it stay on.

$TaskName = "RiftmanaTotalValueTracker"
$RepoDir = Split-Path -Parent $PSScriptRoot
$ScriptPath = Join-Path $PSScriptRoot "run_and_publish.ps1"

$Action = New-ScheduledTaskAction -Execute "powershell.exe" `
    -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$ScriptPath`"" `
    -WorkingDirectory $RepoDir

$Trigger = New-ScheduledTaskTrigger -Once -At (Get-Date) `
    -RepetitionInterval (New-TimeSpan -Hours 1) `
    -RepetitionDuration ([TimeSpan]::MaxValue)

$Settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -DontStopOnIdleEnd `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 10) `
    -MultipleInstances IgnoreNew

Register-ScheduledTask -TaskName $TaskName -Action $Action -Trigger $Trigger `
    -Settings $Settings `
    -Description "Hourly scrape of Total Value from riftmana.com/collection/?user=moose, pushed to GitHub" `
    -Force

Write-Host "Scheduled task '$TaskName' created. It will run hourly whenever this computer is on (missed runs fire on next wake, not backlogged)."
Write-Host "Run it once by hand to test: Start-ScheduledTask -TaskName '$TaskName'"
Write-Host "Check status: Get-ScheduledTask -TaskName '$TaskName' | Get-ScheduledTaskInfo"
Write-Host "Logs: $RepoDir\logs\run_and_publish.log"
Write-Host "To remove later: Unregister-ScheduledTask -TaskName '$TaskName' -Confirm:`$false"
