# Emit FRESH if the Codex update sentinel was touched within INTERVAL
# seconds, otherwise STALE.
$sentinel = Join-Path $env:USERPROFILE '.litter\codex\.last-update-check'
if (Test-Path $sentinel) {
  $age = (Get-Date) - (Get-Item $sentinel).LastWriteTime
  if ($age.TotalSeconds -lt {{INTERVAL}}) { Write-Output 'FRESH'; exit 0 }
}
Write-Output 'STALE'
