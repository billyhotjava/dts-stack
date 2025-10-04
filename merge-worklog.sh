#!/usr/bin/env bash
set -euo pipefail

# Merge worklog files into per-day logs for dts-admin and dts-platform.
# - Input files: worklog/dts-admin-YYYYMMDD-HHMMSS.log, worklog/dts-platform-YYYYMMDD-HHMMSS.log
# - Output files: worklog/dts-admin-YYYYMMDD.log, worklog/dts-platform-YYYYMMDD.log
# - After merge, source timestamped files are archived to worklog/archive/<category>/<date>/
#
# Usage:
#   ./merge-worklog.sh              # merge both categories
#   ./merge-worklog.sh dts-admin    # merge only dts-admin
#   ./merge-worklog.sh dts-platform # merge only dts-platform
#
# Env:
#   WORKLOG_DIR: directory containing logs (default: worklog)

WORKLOG_DIR=${WORKLOG_DIR:-worklog}

err() { echo "[ERROR] $*" >&2; }
note() { echo "[INFO] $*"; }

normalize_eol() {
  # Normalize CRLF to LF from stdin to stdout
  sed $'s/\r$//'
}

list_categories() {
  if [[ $# -gt 0 ]]; then
    for a in "$@"; do
      case "$a" in
        dts-admin|dts_admin|dtsadmin) echo "dts-admin" ;;
        dts-platform|dts_platform|dtsplatform) echo "dts-platform" ;;
        *) err "Unknown category: $a"; exit 1 ;;
      esac
    done
  else
    echo "dts-admin"; echo "dts-platform"
  fi
}

ensure_dir() { mkdir -p "$1"; }

merge_one_category() {
  local category="$1"
  local pattern_regex=".*/${category}-[0-9]{8}-[0-9]{6}\.log$"

  # Collect timestamped files for this category
  mapfile -t files < <(find "$WORKLOG_DIR" -maxdepth 1 -type f -regextype posix-extended -regex "$pattern_regex" -printf '%f\n' | sort)

  if [[ ${#files[@]} -eq 0 ]]; then
    note "No files found for ${category}, skipping."
    return 0
  fi

  # Unique dates present in filenames
  mapfile -t dates < <(printf '%s\n' "${files[@]}" \
    | sed -E "s/^${category}-([0-9]{8})-[0-9]{6}\.log$/\\1/" \
    | sort -u)

  for date in "${dates[@]}"; do
    local out_file="$WORKLOG_DIR/${category}-${date}.log"
    local tmp_file
    tmp_file=$(mktemp)

    # Merge files for this date in chronological order by HHMMSS
    mapfile -t day_files < <(printf '%s\n' "${files[@]}" | grep -E "^${category}-${date}-[0-9]{6}\.log$" | sort)

    if [[ ${#day_files[@]} -eq 0 ]]; then
      continue
    fi

    note "Merging ${#day_files[@]} ${category} files for ${date} -> ${out_file}"

    for fname in "${day_files[@]}"; do
      # Extract HHMMSS and format timestamp
      # fname pattern: ${category}-YYYYMMDD-HHMMSS.log
      local hhmmss
      hhmmss=$(sed -E "s/^${category}-[0-9]{8}-([0-9]{6})\.log$/\\1/" <<<"$fname")
      local yyyy mm dd hh mm2 ss
      yyyy="${date:0:4}"; mm="${date:4:2}"; dd="${date:6:2}"
      hh="${hhmmss:0:2}"; mm2="${hhmmss:2:2}"; ss="${hhmmss:4:2}"
      local ts_fmt="${yyyy}-${mm}-${dd} ${hh}:${mm2}:${ss}"

      {
        printf '===== %s %s (%s) =====\n' "$category" "$ts_fmt" "$fname"
        # Normalize EOLs while appending content
        normalize_eol <"$WORKLOG_DIR/$fname"
        printf '\n' # separator newline between files
      } >>"$tmp_file"
    done

    # Atomically move into place
    mv "$tmp_file" "$out_file"

    # Archive source files
    local archive_dir="$WORKLOG_DIR/archive/${category}/${date}"
    ensure_dir "$archive_dir"
    for fname in "${day_files[@]}"; do
      mv "$WORKLOG_DIR/$fname" "$archive_dir/"
    done
  done
}

main() {
  if [[ ! -d "$WORKLOG_DIR" ]]; then
    err "WORKLOG_DIR '$WORKLOG_DIR' does not exist"
    exit 1
  fi

  mapfile -t cats < <(list_categories "$@")
  for c in "${cats[@]}"; do
    merge_one_category "$c"
  done
}

main "$@"
