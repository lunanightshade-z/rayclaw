$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
Set-Location D:\XRAPPINIT-0306
.\gradlew.bat assembleDebug
if ($LASTEXITCODE -eq 0) {
    Write-Host "Build OK, installing..."
    & 'C:\Users\xinfu4.zhang\AppData\Local\Android\Sdk\platform-tools\adb.exe' install -r app\build\outputs\apk\debug\app-debug.apk
} else {
    Write-Host "Build FAILED"
}
