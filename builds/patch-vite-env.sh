#!/usr/bin/env sh
set -eu

# Patch Vite config to merge process.env with loadEnv variables so that
# Compose-injected envs and .env.development both work.

ROOT="/workspace"
CFG=""
for f in vite.config.ts vite.config.js; do
  if [ -f "$ROOT/$f" ]; then CFG="$ROOT/$f"; break; fi
done

[ -n "$CFG" ] || exit 0

# If it already merges process.env, skip
if grep -q '\.\.\.process\.env' "$CFG" 2>/dev/null; then
  exit 0
fi

tmp="$CFG.tmp.$$"

# Replace pattern safely by printing two lines (no literal \n in source)
awk '
  BEGIN{done=0}
  {
    if (!done && $0 ~ /const[[:space:]]+env[[:space:]]*=[[:space:]]*loadEnv\(mode,[[:space:]]*process\.cwd\(\),[[:space:]]*["\047][^"\047]*["\047]\)[[:space:]]*;/) {
      print "const rawEnv = loadEnv(mode, process.cwd(), \"\");"
      print "  const env = { ...process.env, ...rawEnv };"
      done=1; next
    }
    print
  }
' "$CFG" > "$tmp" && mv "$tmp" "$CFG"

# If still not merged, attempt insert after first loadEnv() mention
if ! grep -q '\.\.\.process\.env' "$CFG" 2>/dev/null; then
  awk '
    BEGIN{inserted=0}
    {
      print $0
      if (!inserted && $0 ~ /loadEnv\(mode,[[:space:]]*process\.cwd\(\)/) {
        print "  // Injected by local-dev to merge process.env"
        print "  const rawEnv = loadEnv(mode, process.cwd(), \"\");"
        print "  const env = { ...process.env, ...rawEnv };"
        inserted=1
      }
    }
  ' "$CFG" > "$tmp" && mv "$tmp" "$CFG" || true
fi

exit 0
