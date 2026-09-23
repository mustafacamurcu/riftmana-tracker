# Registers an hourly Windows Task Scheduler job that runs run_and_publish.ps1.
# Run this once, from a normal PowerShell prompt:
#   powershell -ExecutionPolicy Bypass -File scripts\schedule_task.ps1
#
# StartWhenAvailable means: if the computer was off/asleep when an hourly
# trigger was due, it runs as soon as the computer is next on - matching
# "hourly whenever the computer is on" rather than requiring it stay on.
#
# The task runs under an S4U logon (LogonType S4U): it fires even when this
# account is logged out or at the lock screen, without storing the Windows
# password in Task Scheduler. S4U only grants local-machine resources - that's
# fine here (local Playwright browser, local file writes, and outbound HTTPS
# for git push all work under S4U; it just can't reach other machines using
# this account's network credentials, which nothing here needs).

$TaskName = "RiftmanaTotalValueTracker"
$RepoDir = Split-Path -Parent $PSScriptRoot
$ScriptPath = Join-Path $PSScriptRoot "run_and_publish.ps1"

$Action = New-ScheduledTaskAction -Execute "powershell.exe" `
    -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$ScriptPath`"" `
    -WorkingDirectory $RepoDir

$Trigger = New-ScheduledTaskTrigger -Once -At (Get-Date) `
    -RepetitionInterval (New-TimeSpan -Hours 1) `
    -RepetitionDuration (New-TimeSpan -Days 3650)

$Settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -DontStopOnIdleEnd `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 10) `
    -MultipleInstances IgnoreNew

$Principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" `
    -LogonType S4U -RunLevel Limited

Register-ScheduledTask -TaskName $TaskName -Action $Action -Trigger $Trigger `
    -Settings $Settings -Principal $Principal `
    -Description "Hourly scrape of Total Value from riftmana.com/collection/?user=moose, pushed to GitHub" `
    -Force

Write-Host "Scheduled task '$TaskName' created. It will run hourly whenever this computer is on (missed runs fire on next wake, not backlogged)."
Write-Host "Run it once by hand to test: Start-ScheduledTask -TaskName '$TaskName'"
Write-Host "Check status: Get-ScheduledTask -TaskName '$TaskName' | Get-ScheduledTaskInfo"
Write-Host "Logs: $RepoDir\logs\run_and_publish.log"
Write-Host "To remove later: Unregister-ScheduledTask -TaskName '$TaskName' -Confirm:`$false"
