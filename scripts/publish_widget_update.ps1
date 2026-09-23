# Builds android-widget, publishes the APK + version.json to releases/, and
# pushes to GitHub. The installed app polls releases/version.json (served via
# GitHub Pages) and, once a newer versionCode is seen, downloads
# releases/riftmana-widget.apk and prompts to install it - so running this is
# the only step needed to ship an update after v1.1 (the first
# self-updating build) is installed on the phone.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\publish_widget_update.ps1 -Notes "What changed in this release"
#
# Remember to bump versionCode/versionName in android-widget\app\build.gradle.kts
# before running this - it reads whatever is currently there.

param(
    [Parameter(Mandatory = $true)]
    [string]$Notes
)

$ErrorActionPreference = "Stop"
$RepoDir = Split-Path -Parent $PSScriptRoot
$WidgetDir = Join-Path $RepoDir "android-widget"
$BuildGradlePath = Join-Path $WidgetDir "app\build.gradle.kts"
$ReleasesDir = Join-Path $RepoDir "releases"

Set-Location $WidgetDir
Write-Host "Building debug APK..."
& "$WidgetDir\gradlew.bat" assembleDebug --console=plain
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle build failed."
    exit 1
}

$buildGradleText = Get-Content $BuildGradlePath -Raw
$versionCodeMatch = [regex]::Match($buildGradleText, 'versionCode\s*=\s*(\d+)')
$versionNameMatch = [regex]::Match($buildGradleText, 'versionName\s*=\s*"([^"]+)"')
if (-not $versionCodeMatch.Success -or -not $versionNameMatch.Success) {
    Write-Error "Could not read versionCode/versionName from $BuildGradlePath"
    exit 1
}
$versionCode = [int]$versionCodeMatch.Groups[1].Value
$versionName = $versionNameMatch.Groups[1].Value

Write-Host "Publishing versionCode=$versionCode versionName=$versionName"

if (-not (Test-Path $ReleasesDir)) { New-Item -ItemType Directory -Path $ReleasesDir | Out-Null }

$ApkSource = Join-Path $WidgetDir "app\build\outputs\apk\debug\app-debug.apk"
Copy-Item $ApkSource (Join-Path $ReleasesDir "riftmana-widget.apk") -Force

$versionJson = @{
    versionCode = $versionCode
    versionName = $versionName
    apkUrl      = "https://mustafacamurcu.github.io/riftmana-tracker/releases/riftmana-widget.apk"
    notes       = $Notes
} | ConvertTo-Json

# Set-Content -Encoding utf8 writes a BOM in Windows PowerShell 5.1, which
# org.json fails to parse on the Android side - write BOM-less UTF-8 instead.
$versionJsonPath = Join-Path $ReleasesDir "version.json"
[System.IO.File]::WriteAllText($versionJsonPath, $versionJson, (New-Object System.Text.UTF8Encoding $false))

Set-Location $RepoDir
git add releases/version.json releases/riftmana-widget.apk
git diff --cached --quiet
if ($LASTEXITCODE -eq 0) {
    Write-Host "No changes to publish (APK/version already match)."
    exit 0
}

git commit -m "Publish widget v$versionName (versionCode $versionCode): $Notes" | Out-Null
git push
if ($LASTEXITCODE -ne 0) {
    Write-Error "git push failed."
    exit 1
}

Write-Host "Published. Installed apps will pick this up on their next check (app open or hourly background refresh)."
