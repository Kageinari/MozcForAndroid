# Install MozcForAndroid signed APK when an adb device is available.
param(
    [string]$ApkPath = "$env:USERPROFILE\Downloads\MozcForAndroid-signed.apk",
    [string]$AdbPath = "C:\Program Files\platform-tools\adb.exe",
    [int]$WaitSeconds = 120
)

$ErrorActionPreference = "Stop"
$package = "org.mozc.android.inputmethod.japanese"

if (-not (Test-Path $ApkPath)) {
    Write-Error "APK not found: $ApkPath"
}
if (-not (Test-Path $AdbPath)) {
    Write-Error "adb not found: $AdbPath"
}

function Get-AdbDevice {
    $lines = & $AdbPath devices | Select-Object -Skip 1
    foreach ($line in $lines) {
        if ($line -match '^(?<id>\S+)\s+device$') {
            return $Matches['id']
        }
    }
    return $null
}

$deadline = (Get-Date).AddSeconds($WaitSeconds)
$deviceId = $null
while ((Get-Date) -lt $deadline) {
    $deviceId = Get-AdbDevice
    if ($deviceId) { break }
    Start-Sleep -Seconds 3
}

if (-not $deviceId) {
    Write-Error "No adb device detected within ${WaitSeconds}s. Enable USB debugging and reconnect the phone, or start an emulator."
}

Write-Host "Installing to device: $deviceId"
& $AdbPath -s $deviceId install -r $ApkPath
if ($LASTEXITCODE -ne 0) {
    Write-Error "adb install failed with exit code $LASTEXITCODE"
}

Write-Host "Launching IME settings..."
& $AdbPath -s $deviceId shell am start -a android.settings.INPUT_METHOD_SETTINGS
Write-Host "Installed package: $package"
& $AdbPath -s $deviceId shell pm list packages | Select-String $package