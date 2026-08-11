#!/bin/bash
# 打包 finance dbt_model 为 DTS 逆向建模导入 zip。
set -euo pipefail

DIST_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$DIST_DIR/dbt_model"
ZIP_FILE="$DIST_DIR/finance-dbt-model-reverse-import.zip"

if [ ! -d "$SRC_DIR" ]; then
  echo "[ERROR] 源目录不存在: $SRC_DIR" >&2
  exit 1
fi

if [ ! -f "$SRC_DIR/models.tsv" ]; then
  echo "[ERROR] 缺少模型清单文件: $SRC_DIR/models.tsv" >&2
  echo "        UI 导入需要 models.tsv，请先生成或恢复该文件" >&2
  exit 1
fi

rm -f -- "$ZIP_FILE"

echo "=== 打包 Finance 逆向建模导入包 ==="
(
  cd "$DIST_DIR"
  find dbt_model -type f \
    ! -path 'dbt_model/target/*' \
    ! -path 'dbt_model/logs/*' \
    ! -path 'dbt_model/dbt_packages/*' \
    ! -name '.DS_Store' \
    -print \
    | LC_ALL=C sort \
    | zip -X "$ZIP_FILE" -@
)

echo ""
echo "--- 校验 zip 内含 models.tsv ---"
unzip -l "$ZIP_FILE" | grep -q 'dbt_model/models\.tsv$' || {
  echo "[ERROR] 打包后 zip 未包含 models.tsv，构建失败" >&2
  exit 1
}
echo "  -> dbt_model/models.tsv ✓"

echo ""
echo "--- 校验导入包结构 ---"
unzip -tq "$ZIP_FILE"
if unzip -Z1 "$ZIP_FILE" | grep -Eq '^dbt_model/(target|logs|dbt_packages)/'; then
  echo "[ERROR] 导入包中不应包含 dbt 运行产物" >&2
  exit 1
fi

model_count="$(unzip -Z1 "$ZIP_FILE" | grep -Ec '^dbt_model/models/.+\.sql$')"
if [ "$model_count" -ne 24 ]; then
  echo "[ERROR] dbt SQL 模型数应为 24，实际为 $model_count" >&2
  exit 1
fi
echo "  -> 24 个 SQL 模型，无 target/logs/dbt_packages ✓"

echo ""
echo "=== 完成 ==="
ls -lh "$ZIP_FILE"
