#!/bin/bash
# 构建两个部署包：CLI 部署包 + UI 导入包
# 仅保留当前 finance dbt 项目与 ODS DDL 参考，不包含旧模型目录
set -e

DIST_DIR="$(cd "$(dirname "$0")" && pwd)"
TEMP_CLI="$DIST_DIR/_tmp_cli"
TEMP_UI="$DIST_DIR/_tmp_ui"
CLI_ZIP="$DIST_DIR/finance-cli-deploy.zip"
UI_ZIP="$DIST_DIR/finance-ui-import.zip"

rm -rf "$TEMP_CLI" "$TEMP_UI"
rm -f "$CLI_ZIP" "$UI_ZIP"
mkdir -p "$TEMP_CLI" "$TEMP_UI"

echo "=== 构建 CLI 部署包 ==="
cp -r "$DIST_DIR/macros" "$TEMP_CLI/"
cp -r "$DIST_DIR/models" "$TEMP_CLI/"
cp -r "$DIST_DIR/ods_table" "$TEMP_CLI/"
cp "$DIST_DIR/dbt_project.yml" "$TEMP_CLI/"
cp "$DIST_DIR/model-governance.md" "$TEMP_CLI/"

cd "$TEMP_CLI"
zip -r "$CLI_ZIP" . -x '*/.*'
echo "  -> finance-cli-deploy.zip"

echo "=== 构建 UI 导入包 ==="
cp -r "$DIST_DIR/macros" "$TEMP_UI/"
cp -r "$DIST_DIR/models" "$TEMP_UI/"
cp "$DIST_DIR/dbt_project.yml" "$TEMP_UI/"
cp "$DIST_DIR/model-governance.md" "$TEMP_UI/"
cp "$DIST_DIR/models.tsv" "$TEMP_UI/models.tsv"

cd "$TEMP_UI"
zip -r "$UI_ZIP" . -x '*/.*'
echo "  -> finance-ui-import.zip"

rm -rf "$TEMP_CLI" "$TEMP_UI"

echo ""
echo "=== 完成 ==="
ls -lh "$CLI_ZIP" "$UI_ZIP"
