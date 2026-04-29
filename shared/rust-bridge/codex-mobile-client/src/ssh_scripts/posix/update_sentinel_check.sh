# Emit FRESH if the Codex update sentinel was touched within INTERVAL
# seconds, otherwise STALE.
sentinel="$HOME/.litter/codex/.last-update-check"
if [ -f "$sentinel" ]; then
  now=$(date +%s 2>/dev/null || echo 0)
  last=$(stat -c %Y "$sentinel" 2>/dev/null || stat -f %m "$sentinel" 2>/dev/null || echo 0)
  if [ "$last" -gt 0 ] && [ "$now" -gt 0 ]; then
    age=$((now - last))
    if [ "$age" -lt {{INTERVAL}} ]; then
      printf 'FRESH'
      exit 0
    fi
  fi
fi
printf 'STALE'
