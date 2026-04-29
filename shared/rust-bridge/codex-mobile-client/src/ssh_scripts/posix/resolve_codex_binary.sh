# Find an existing `codex` binary on the remote and emit "codex:<path>" to
# stdout, or nothing if none was found. Caller composes this template via
# the PROFILE_INIT, PACKAGE_MANAGER_PROBE, and SHARED_LINES placeholders so
# the local resolver and remote resolver share one search order and the
# list of fallback candidate dirs is owned by `crate::local_server`.
{{PROFILE_INIT}}
_litter_emit_candidate() {
  _litter_selector="$1"
  _litter_path="$2"
  if [ -n "$_litter_path" ] && [ -f "$_litter_path" ] && [ -x "$_litter_path" ]; then
    printf '%s:%s' "$_litter_selector" "$_litter_path"
    exit 0
  fi
}
_litter_emit_from_dir() {
  _litter_selector="$1"
  _litter_name="$2"
  _litter_dir="$3"
  if [ -n "$_litter_dir" ]; then
    _litter_emit_candidate "$_litter_selector" "$_litter_dir/$_litter_name"
  fi
}
{{SHARED_LINES}}
{{PACKAGE_MANAGER_PROBE}}
_litter_emit_from_dir codex codex "$_litter_bun_global_bin"
_litter_emit_from_dir codex codex "$_litter_npm_global_bin"
_litter_emit_from_dir codex codex "$_litter_pnpm_global_bin"
