#!/usr/bin/env sh
set -eu

# Patch application-dev.yml (under src/main/resources/config/) in
# dts-admin / dts-platform / dts-common to use env placeholders
# for datasource url/username/password with sensible defaults.
# Usage: sh /patches/patch-app-dev-yml.sh {admin|platform|common}

MOD="${1:-}"
case "$MOD" in
  admin)    APP_DIR="/workspace-src/dts-admin";    DEF_DB="dts_admin";     DEF_USER="dts_admin" ;;
  platform) APP_DIR="/workspace-src/dts-platform"; DEF_DB="dts_platform";  DEF_USER="dts_platform" ;;
  common)   APP_DIR="/workspace-src/dts-common";   DEF_DB="dts_common";    DEF_USER="dts_common" ;;
  *) echo "usage: $0 {admin|platform|common}" >&2; exit 0;;
esac

# Prefer config/application-dev.yml (JHipster layout), fallback to flat path
YML="$APP_DIR/src/main/resources/config/application-dev.yml"
if [ ! -f "$YML" ]; then
  YML="$APP_DIR/src/main/resources/application-dev.yml"
fi
[ -f "$YML" ] || exit 0

tmp="${YML}.tmp.$$"

# Replace in-place, preserving indentation. Be robust to existing blanks.
awk -v db="$DEF_DB" -v user="$DEF_USER" '
  BEGIN{in_ds=0}
  {
    if ($0 ~ /^\s*spring:\s*$/) { print; next }
    if ($0 ~ /^\s*datasource:\s*$/) { in_ds=1; print; next }
    if (in_ds==1) {
      if ($0 ~ /^\s*url:\s*/) {
        match($0, /^[[:space:]]*/); sp=substr($0, RSTART, RLENGTH)
        print sp "url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://dts-pg:5432/" db "}"
        next
      }
      if ($0 ~ /^\s*username:\s*/) {
        match($0, /^[[:space:]]*/); sp=substr($0, RSTART, RLENGTH)
        print sp "username: ${SPRING_DATASOURCE_USERNAME:" user "}"
        next
      }
      if ($0 ~ /^\s*password:\s*/) {
        match($0, /^[[:space:]]*/); sp=substr($0, RSTART, RLENGTH)
        print sp "password: ${SPRING_DATASOURCE_PASSWORD:}"
        next
      }
      # leave block when encountering non-indented or new top-level key
      if ($0 ~ /^[^[:space:]]/) { in_ds=0 }
    }
    print
  }
' "$YML" > "$tmp" && mv "$tmp" "$YML"

exit 0
