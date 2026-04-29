# Download and install a tagged Codex release tarball under
# $HOME/.litter/codex/<tag>/ and refresh the $HOME/.litter/bin/codex
# symlink. Re-running with the same tag is a no-op (status="up-to-date").
#
# Caller passes TAG, ASSET_NAME, BINARY_NAME, DOWNLOAD_URL — all already
# shell-quoted.
#
# Output contract (machine-readable, single line):
#   STATUS:<installed|up-to-date>
#   PATH:<absolute path to stable bin>
set -e
tag={{TAG}}
asset_name={{ASSET_NAME}}
binary_name={{BINARY_NAME}}
download_url={{DOWNLOAD_URL}}
dest_dir="$HOME/.litter/codex/$tag"
dest_bin="$dest_dir/codex"
stable_bin="$HOME/.litter/bin/codex"
tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/litter-codex.XXXXXX")"
cleanup() {
  rm -rf "$tmpdir"
}
trap cleanup EXIT
mkdir -p "$dest_dir" "$HOME/.litter/bin"
status="up-to-date"
if [ ! -x "$dest_bin" ]; then
  status="installed"
  archive_path="$tmpdir/$asset_name"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$download_url" -o "$archive_path"
  elif command -v wget >/dev/null 2>&1; then
    wget -qO "$archive_path" "$download_url"
  else
    echo "curl or wget is required to install Codex" >&2
    exit 1
  fi
  tar -xzf "$archive_path" -C "$tmpdir"
  extracted="$tmpdir/$binary_name"
  if [ ! -f "$extracted" ]; then
    echo "expected binary '$binary_name' not found in release archive" >&2
    exit 1
  fi
  if command -v install >/dev/null 2>&1; then
    install -m 0755 "$extracted" "$dest_bin"
  else
    cp "$extracted" "$dest_bin"
    chmod 0755 "$dest_bin"
  fi
fi
ln -sf "$dest_bin" "$stable_bin"
printf 'STATUS:%s\nPATH:%s\n' "$status" "$stable_bin"
