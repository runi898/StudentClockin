$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$androidRoot = Join-Path $repoRoot "android-app"
$gradleLocal = Join-Path $androidRoot "gradle-local.properties"
$gradleLocalExample = Join-Path $androidRoot "gradle-local.example.properties"

if (-not $env:JAVA_HOME) {
  throw "JAVA_HOME 未设置。请先安装并配置 JDK 17 或更高版本。"
}

if (-not (Test-Path $gradleLocal) -and -not $env:STUDENTCLOCKIN_SUPABASE_URL) {
  Write-Host "未检测到 android-app/gradle-local.properties，当前会使用 Demo 模式配置。"
  if (Test-Path $gradleLocalExample) {
    Write-Host "如需连接你自己的后端，请先复制并编辑："
    Write-Host $gradleLocalExample
  }
  Write-Host ""
}

Push-Location $androidRoot
try {
  .\gradlew.bat assembleDebug
  $apkPath = Join-Path $androidRoot "app\build\outputs\apk\debug\app-debug.apk"
  if (-not (Test-Path $apkPath)) {
    throw "未找到调试 APK：$apkPath"
  }

  Write-Host ""
  Write-Host "APK 已生成："
  Write-Host $apkPath
} finally {
  Pop-Location
}
