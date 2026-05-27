$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot

if (-not (Get-Command npx -ErrorAction SilentlyContinue)) {
  throw "未找到 npx，请先安装 Node.js。"
}

Push-Location $repoRoot
try {
  try {
    npx supabase --version | Out-Null
  } catch {
    throw "当前 Windows 原生环境无法直接运行 Supabase CLI 二进制。请改用 WSL/Ubuntu，或直接使用 GitHub Actions 工作流 .github/workflows/deploy-supabase.yml。"
  }

  npx supabase db push
  npx supabase functions deploy daily-rollover --use-api
  npx supabase functions deploy send-notifications --use-api
  npx supabase functions deploy recalculate-reports --use-api
} finally {
  Pop-Location
}
