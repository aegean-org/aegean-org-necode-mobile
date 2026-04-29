# Find an existing codex binary on a Windows remote and emit
# "codex:<path>" to stdout, or nothing if not found.
$litterBin = Join-Path $env:USERPROFILE '.litter\bin\codex.cmd'
if (Test-Path $litterBin) { Write-Output "codex:$litterBin"; exit 0 }
$litterNpm = Join-Path $env:USERPROFILE '.litter\codex\node_modules\.bin\codex.cmd'
if (Test-Path $litterNpm) { Write-Output "codex:$litterNpm"; exit 0 }
$found = Get-Command codex -ErrorAction SilentlyContinue
if ($found) { Write-Output "codex:$($found.Source)"; exit 0 }
