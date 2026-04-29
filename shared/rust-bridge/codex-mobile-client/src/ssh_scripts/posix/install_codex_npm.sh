# Install Codex via npm into $HOME/.litter/codex/. Used as the POSIX
# fallback when no binary release matches the platform, and on Windows.
# `@openai/codex@latest` forces past any semver range left in package.json
# from a previous install, so re-running this script reliably bumps to the
# newest published version.
#
# Output contract: CODEX_PATH:<absolute path>
{{PROFILE_INIT}}
set -e
litter_dir="$HOME/.litter/codex"
mkdir -p "$litter_dir"
cd "$litter_dir"
[ -f package.json ] || npm init -y >/dev/null 2>&1
npm install @openai/codex@latest >/dev/null 2>&1
bin="$litter_dir/node_modules/.bin/codex"
if [ -x "$bin" ]; then printf 'CODEX_PATH:%s' "$bin"; else echo "codex not found after install" >&2; exit 1; fi
