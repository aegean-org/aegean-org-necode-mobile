# Install Codex via npm into ~\.litter\codex\.
# `@openai/codex@latest` forces npm past any semver range left in
# package.json from a previous install, so re-running this script reliably
# bumps to the newest published version.
#
# Output contract: CODEX_PATH:<absolute path>
$ErrorActionPreference = 'Stop'
$litterDir = Join-Path $env:USERPROFILE '.litter\codex'
if (-not (Test-Path $litterDir)) { New-Item -ItemType Directory -Path $litterDir -Force | Out-Null }
Set-Location $litterDir
if (-not (Test-Path 'package.json')) { npm init -y 2>$null | Out-Null }
npm install @openai/codex@latest 2>$null | Out-Null
$bin = Join-Path $litterDir 'node_modules\.bin\codex.cmd'
if (Test-Path $bin) { Write-Output "CODEX_PATH:$bin" } else { Write-Error 'codex.cmd not found after install'; exit 1 }
