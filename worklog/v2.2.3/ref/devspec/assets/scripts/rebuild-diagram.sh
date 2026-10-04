#!/usr/bin/env bash
# 重建单张 Archify 图：validate → deliver → visual-check，回执与截图写入 receipts/。
# 用法（任意目录执行）：assets/scripts/rebuild-diagram.sh <architecture|sequence|workflow|dataflow|lifecycle> <图名>
set -euo pipefail
type="$1"; name="$2"
root="$(cd "$(dirname "$0")/../.." && pwd)"
cli="${ARCHIFY_CLI:-${HOME}/.claude/skills/archify/bin/archify.mjs}"
spec="$root/diagrams/$name.$type.json"
out="$root/$name.html"
[ -f "$spec" ] || { echo "图源不存在：$spec" >&2; exit 1; }
export ARCHIFY_UPDATE_CHECK_DISABLED=1
mkdir -p "$root/receipts"
node "$cli" validate "$type" "$spec" --quality showcase --json > "$root/receipts/$name.validation.json"
node "$cli" deliver "$type" "$spec" "$out" --quality showcase --json > "$root/receipts/$name.receipt.json"
node "$cli" visual-check "$out" --json > /dev/null
mv -f "$root/$name".visual-check.* "$root/receipts/"
echo "已重建 $out；回执见 receipts/$name.*"
