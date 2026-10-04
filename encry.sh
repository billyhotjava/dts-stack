#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "用法: $(basename "$0") <enc|dec> KEY [ENV_FILE]" >&2
  echo "示例: $(basename "$0") enc PG_PWD_DTADMIN .env" >&2
  exit 1
}

if [[ $# -lt 2 || $# -gt 3 ]]; then
  usage
fi

ACTION="$1"
KEY="$2"
ENV_FILE="${3:-.env}"

if [[ "$ACTION" != "enc" && "$ACTION" != "dec" ]]; then
  usage
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "文件不存在: $ENV_FILE" >&2
  exit 2
fi

SECRET="${ENCRY_SECRET:-}"
if [[ -z "$SECRET" ]]; then
  read -rsp "请输入加密口令: " SECRET
  echo
fi
if [[ -z "$SECRET" ]]; then
  echo "口令不能为空" >&2
  exit 3
fi

export ENCRY_SECRET_PASSPHRASE="$SECRET"

python3 - "$ACTION" "$KEY" "$ENV_FILE" <<'PY'
import os
import pathlib
import re
import shutil
import subprocess
import sys

action = sys.argv[1]
key = sys.argv[2]
env_path = pathlib.Path(sys.argv[3]).resolve()
secret = os.environ.get("ENCRY_SECRET_PASSPHRASE")

if not secret:
    sys.stderr.write("缺少加密口令\n")
    sys.exit(3)

try:
    text = env_path.read_text(encoding="utf-8")
except FileNotFoundError:
    sys.stderr.write(f"文件不存在: {env_path}\n")
    sys.exit(2)

pattern = re.compile(rf"^{re.escape(key)}=(.*)$", re.MULTILINE)
match = pattern.search(text)
if not match:
    sys.stderr.write(f"未找到键 {key} 对应的行\n")
    sys.exit(4)

raw_value = match.group(1).strip()
quoted = raw_value.startswith('"') and raw_value.endswith('"') and len(raw_value) >= 2
value = raw_value[1:-1] if quoted else raw_value

def run_openssl(data: bytes, decrypt: bool) -> bytes:
    cmd = ["openssl", "enc", "-aes-256-cbc", "-base64", "-A", "-salt", "-pass", f"pass:{secret}"]
    if decrypt:
        cmd.insert(2, "-d")
    try:
        completed = subprocess.run(
            cmd,
            input=data if not decrypt else data + b"\n",
            capture_output=True,
            check=True,
        )
    except FileNotFoundError:
        sys.stderr.write("未找到 openssl，请安装 openssl 或调整脚本\n")
        sys.exit(6)
    except subprocess.CalledProcessError as exc:
        sys.stderr.write(exc.stderr.decode("utf-8", errors="replace"))
        sys.exit(7)
    return completed.stdout.strip()

if action == "enc":
    if value.startswith("ENC(") and value.endswith(")"):
        sys.stderr.write(f"{key} 已经是加密值，跳过\n")
        sys.exit(5)
    ciphertext = run_openssl(value.encode("utf-8"), decrypt=False).decode("utf-8")
    stored = f'ENC({ciphertext})'
    replacement_value = f'"{stored}"' if quoted else stored
    message = f"已加密 {key} 并写回 {env_path.name}\n"
elif action == "dec":
    if not (value.startswith("ENC(") and value.endswith(")")):
        sys.stderr.write(f"{key} 当前值不是 ENC(...) 格式，无法解密\n")
        sys.exit(5)
    payload = value[4:-1].encode("utf-8")
    plain_bytes = run_openssl(payload, decrypt=True)
    plain = plain_bytes.decode("utf-8")
    replacement_value = f'"{plain}"' if quoted else plain
    message = f"{key} 已解密并写回 {env_path.name}\n原始值: {plain}\n"
else:
    sys.stderr.write("未知操作\n")
    sys.exit(1)

replacement_line = f"{key}={replacement_value}"
backup_path = env_path.with_suffix(env_path.suffix + ".bak")
shutil.copy2(env_path, backup_path)
updated = pattern.sub(replacement_line, text, count=1)
env_path.write_text(updated, encoding="utf-8")
sys.stdout.write(f"已备份为 {backup_path.name}\n{message}")
PY

unset ENCRY_SECRET_PASSPHRASE
