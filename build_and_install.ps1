# Build and install RayClaw on connected RayNeo X3 device.
# Requires: ADB in PATH or ANDROID_HOME / ANDROID_SDK_ROOT env var set.

$ErrorActionPreference = "Stop"

# Locate adb: prefer PATH, then ANDROID_HOME / ANDROID_SDK_ROOT
$adb = "adb"
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    $sdkRoot = $env:ANDROID_HOME ?? $env:ANDROID_SDK_ROOT
    if ($sdkRoot) {
        $adb = Join-Path $sdkRoot "platform-tools\adb.exe"
    } else {
        Write-Error "adb not found in PATH and ANDROID_HOME/ANDROID_SDK_ROOT is not set."
        exit 1
    }
}

# Use Android Studio's bundled JBR if JAVA_HOME is not already set
if (-not $env:JAVA_HOME) {
    $studioJbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path $studioJbr) { $env:JAVA_HOME = $studioJbr }
}

Push-Location $PSScriptRoot
try {
    .\gradlew.bat assembleDebug
    if ($LASTEXITCODE -ne 0) { Write-Error "Build FAILED"; exit 1 }
    Write-Host "Build OK — installing..."
    & $adb install -r app\build\outputs\apk\debug\app-debug.apk
} finally {
    Pop-Location
}
