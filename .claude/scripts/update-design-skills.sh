#!/usr/bin/env bash
# Re-vendor the design skills from upstream into .claude/skills/.
#
# Usage:
#   .claude/scripts/update-design-skills.sh          # pull each upstream's default branch
#   PIN=1 .claude/scripts/update-design-skills.sh    # re-pull the commits in VENDORED.md
#
# After running, update the commit column in .claude/skills/VENDORED.md and review the
# diff before committing — these folders are third-party instructions the agent obeys.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
dest="$root/.claude/skills"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# repo|ref|src-path-in-repo|dest-folder
specs=(
  "emilkowalski/skills|d16ebe60d09a5ba2afcb7054ede9d0a10c9f6128|skills/emil-design-eng|emil-design-eng"
  "emilkowalski/skills|d16ebe60d09a5ba2afcb7054ede9d0a10c9f6128|skills/animate|animate"
  "emilkowalski/skills|d16ebe60d09a5ba2afcb7054ede9d0a10c9f6128|skills/review-animations|review-animations"
  "pbakaus/impeccable|4adabaf2c2bd3148f162d35aaa6acd7025343649|.claude/skills/impeccable|impeccable"
  "leonxlnx/taste-skill|ce26fc25c0e5e8cab638f883de62d9a86ee5e45b|skills/taste-skill|design-taste-frontend"
)

clone() { # repo ref -> echoes checkout dir
  local repo="$1" ref="$2" dir="$work/${1//\//-}"
  [ -d "$dir" ] && { echo "$dir"; return; }
  if [ "${PIN:-0}" = "1" ]; then
    git init -q "$dir"
    git -C "$dir" remote add origin "https://github.com/$repo.git"
    git -C "$dir" fetch -q --depth 1 origin "$ref"
    git -C "$dir" checkout -q FETCH_HEAD
  else
    git clone -q --depth 1 "https://github.com/$repo.git" "$dir"
  fi
  echo "$dir"
}

for spec in "${specs[@]}"; do
  IFS='|' read -r repo ref src folder <<<"$spec"
  checkout="$(clone "$repo" "$ref")"
  if [ ! -d "$checkout/$src" ]; then
    echo "!! $repo no longer has $src — upstream moved it; fix the spec" >&2
    exit 1
  fi
  rm -rf "$dest/$folder"
  cp -R "$checkout/$src" "$dest/$folder"
  find "$dest/$folder" -name '.DS_Store' -delete
  [ -f "$dest/$folder/scripts/impeccable" ] && chmod +x "$dest/$folder/scripts/impeccable"
  printf '  %-24s <- %s @ %s\n' "$folder" "$repo" "$(git -C "$checkout" rev-parse --short HEAD)"
done

echo
echo "Done. Now: review 'git diff --stat .claude/skills' and refresh the commit column"
echo "in .claude/skills/VENDORED.md."
