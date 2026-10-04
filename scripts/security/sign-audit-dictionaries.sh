#!/usr/bin/env bash
# Sign audit dictionary files with the HMAC-SHA256 key held by the authorization administrator.
#
# Usage:
#   AUDIT_DICT_HMAC_KEY=<hex-encoded-key> ./sign-audit-dictionaries.sh [-o OUT_DIR] FILE [FILE ...]
#
# Behaviour:
#   For each FILE, writes a sidecar FILE.sig containing the HMAC-SHA256 hex tag.
#   The script is meant to be run by authadmin (or a CI step gated by authadmin
#   approval) — sysadmin must NOT have read access to AUDIT_DICT_HMAC_KEY.
#
# Generating a fresh key:
#   openssl rand -hex 32   # 32 bytes / 64 hex chars
#
# Verifying manually:
#   openssl dgst -sha256 -mac HMAC -macopt hexkey:$AUDIT_DICT_HMAC_KEY <FILE>
#
set -euo pipefail

usage() {
    cat <<EOF
Usage: AUDIT_DICT_HMAC_KEY=<hex> $0 [-o OUT_DIR] FILE [FILE ...]

Options:
  -o OUT_DIR    Write sidecars to OUT_DIR/<basename>.sig instead of next to the input.
  -h, --help    Show this help.

Environment:
  AUDIT_DICT_HMAC_KEY    Hex-encoded HMAC-SHA256 key (required, 32 bytes / 64 hex chars).
EOF
}

OUT_DIR=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help) usage; exit 0 ;;
        -o) OUT_DIR="$2"; shift 2 ;;
        --) shift; break ;;
        -*) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
        *) break ;;
    esac
done

if [[ $# -eq 0 ]]; then
    usage >&2
    exit 2
fi

if [[ -z "${AUDIT_DICT_HMAC_KEY:-}" ]]; then
    echo "AUDIT_DICT_HMAC_KEY is empty — refusing to produce empty signatures" >&2
    exit 3
fi

if ! [[ "$AUDIT_DICT_HMAC_KEY" =~ ^[0-9a-fA-F]+$ ]]; then
    echo "AUDIT_DICT_HMAC_KEY must be hex-encoded (0-9, a-f)" >&2
    exit 4
fi

if (( ${#AUDIT_DICT_HMAC_KEY} < 32 )); then
    echo "AUDIT_DICT_HMAC_KEY is shorter than 16 bytes (32 hex chars); refuse for safety" >&2
    exit 5
fi

for file in "$@"; do
    if [[ ! -r "$file" ]]; then
        echo "Cannot read input: $file" >&2
        exit 6
    fi
    tag=$(openssl dgst -sha256 -mac HMAC -macopt hexkey:"$AUDIT_DICT_HMAC_KEY" "$file" \
        | awk '{print $NF}')
    if [[ -z "$tag" ]]; then
        echo "Failed to compute HMAC for $file" >&2
        exit 7
    fi
    if [[ -n "$OUT_DIR" ]]; then
        mkdir -p "$OUT_DIR"
        sig_path="$OUT_DIR/$(basename "$file").sig"
    else
        sig_path="${file}.sig"
    fi
    printf '%s\n' "$tag" > "$sig_path"
    chmod 0644 "$sig_path"
    printf 'signed %s -> %s\n' "$file" "$sig_path"
done
