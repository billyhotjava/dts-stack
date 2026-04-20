#!/bin/bash
# 打包 pjm dbt_model 为现场部署 zip
set -e

DIST_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$DIST_DIR/dbt_model"
ZIP_FILE="$DIST_DIR/pjm-dbt-model.zip"

if [ ! -d "$SRC_DIR" ]; then
  echo "[ERROR] 源目录不存在: $SRC_DIR" >&2
  exit 1
fi

rm -f "$ZIP_FILE"

echo "=== 打包 pjm dbt_model ==="
cd "$DIST_DIR"
zip -r "$ZIP_FILE" "dbt_model" \
  -x 'dbt_model/target/*' \
  -x 'dbt_model/logs/*' \
  -x 'dbt_model/dbt_packages/*' \
  -x 'dbt_model/.*' \
  -x '*/.DS_Store'

echo ""
echo "=== 完成 ==="
ls -lh "$ZIP_FILE"
