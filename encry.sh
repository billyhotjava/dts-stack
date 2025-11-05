#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "用法: $(basename "$0") KEY [ENV_FILE]" >&2
  echo "示例: $(basename "$0") PG_PWD_DTADMIN .env" >&2
  exit 1
}

if [[ $# -lt 1 || $# -gt 2 ]]; then
  usage
fi

KEY="$1"
ENV_FILE="${2:-.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "文件不存在: $ENV_FILE" >&2
  exit 2
fi

python3 - "$KEY" "$ENV_FILE" <<'PY'
import hashlib
import pathlib
import re
import shutil
import sys

key = sys.argv[1]
env_path = pathlib.Path(sys.argv[2]).resolve()
text = env_path.read_text(encoding="utf-8")

pattern = re.compile(rf"^{re.escape(key)}=(.*)$", re.MULTILINE)
match = pattern.search(text)
if not match:
    sys.stderr.write(f"未找到键 {key} 对应的行\n")
    sys.exit(3)

raw_value = match.group(1).strip()
quoted = raw_value.startswith('"') and raw_value.endswith('"') and len(raw_value) >= 2
if quoted:
    value = raw_value[1:-1]
else:
    value = raw_value

digest = hashlib.sha256(value.encode("utf-8")).hexdigest()
replacement = f'{key}={"\"" if quoted else ""}{digest}{"\"" if quoted else ""}'
backup_path = env_path.with_suffix(env_path.suffix + ".bak")
shutil.copy2(env_path, backup_path)
env_path.write_text(pattern.sub(replacement, text, count=1), encoding="utf-8")
print(f"已更新 {env_path} 中的 {key}，原值备份至 {backup_path.name}")
PY
