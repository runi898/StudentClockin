$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$androidRoot = Join-Path $repoRoot "android-app"

if (-not $env:JAVA_HOME) {
  throw "JAVA_HOME 未设置。请先配置 JDK 21。"
}

Push-Location $androidRoot
try {
  .\gradlew.bat assembleDebug
  $apkPath = Join-Path $androidRoot "app\build\outputs\apk\debug\app-debug.apk"
  if (-not (Test-Path $apkPath)) {
    throw "未找到调试 APK: $apkPath"
  }

  Write-Host ""
  Write-Host "APK 已生成："
  Write-Host $apkPath
} finally {
  Pop-Location
}
