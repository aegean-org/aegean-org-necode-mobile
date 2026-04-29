# Scan ~/.claude/projects for thread/session metadata and emit one
# tab-separated record per session:
#   C\t<jsonl_path>\t<session_id>\t<cwd>\t<modified_ms>\t<modified_ms>\t<first_user_text>
clean_field() {
  printf '%s' "$1" | tr '\t\r\n' '   '
}
mtime_ms() {
  if seconds=$(stat -c %Y "$1" 2>/dev/null); then
    :
  elif seconds=$(stat -f %m "$1" 2>/dev/null); then
    :
  else
    seconds=0
  fi
  case "$seconds" in
    ''|*[!0-9]*) seconds=0 ;;
  esac
  printf '%s000' "$seconds"
}
root="${CLAUDE_PROJECTS_DIR:-$HOME/.claude/projects}"
case "$root" in "~") root="$HOME" ;; "~/"*) root="$HOME/${root#~/}" ;; esac
[ -d "$root" ] || exit 0
find "$root" -type f -name '*.jsonl' 2>/dev/null | while IFS= read -r path; do
  [ -f "$path" ] || continue
  base=${path##*/}
  session_id=${base%.jsonl}
  modified_ms=$(mtime_ms "$path")
  meta=$(
    awk '
      function clean(s) {
        gsub(/\\n/, " ", s)
        gsub(/\\r/, " ", s)
        gsub(/\\t/, " ", s)
        gsub(/\t/, " ", s)
        gsub(/\r/, " ", s)
        gsub(/\n/, " ", s)
        gsub(/\\"/, "\"", s)
        return s
      }
      function field(line, key, pat, rest) {
        pat = "\"" key "\"[[:space:]]*:[[:space:]]*\""
        if (!match(line, pat)) return ""
        rest = substr(line, RSTART + RLENGTH)
        if (match(rest, /([^"\\]|\\.)*/)) return clean(substr(rest, RSTART, RLENGTH))
        return ""
      }
      {
        if (cwd == "") cwd = field($0, "cwd")
        if (first == "" && $0 ~ /"type"[[:space:]]*:[[:space:]]*"user"/) {
          text = field($0, "text")
          if (text == "") text = field($0, "content")
          if (text != "") first = text
        }
        if (cwd != "" && first != "") exit
      }
      END { printf "%s\t%s", cwd, first }
    ' "$path" 2>/dev/null
  ) || meta="$(printf '\t')"
  cwd=${meta%%	*}
  first=${meta#*	}
  printf 'C\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$(clean_field "$path")" \
    "$(clean_field "$session_id")" \
    "$(clean_field "$cwd")" \
    "$modified_ms" \
    "$modified_ms" \
    "$(clean_field "$first")"
done
