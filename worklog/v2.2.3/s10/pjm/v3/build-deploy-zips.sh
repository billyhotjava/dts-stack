#!/bin/bash
# 构建两个部署包：CLI 部署包 + UI 导入包 (v3)
# dbt 只管 STG/DWD/DWS/ADS 模型，不碰 ODS 表
set -e
DIST_DIR="$(cd "$(dirname "$0")" && pwd)"
TEMP_CLI="$DIST_DIR/_tmp_cli"
TEMP_UI="$DIST_DIR/_tmp_ui"
CLI_ZIP="$DIST_DIR/project-management-v3-cli-deploy.zip"
UI_ZIP="$DIST_DIR/project-management-v3-ui-import.zip"

rm -rf "$TEMP_CLI" "$TEMP_UI"
rm -f "$CLI_ZIP" "$UI_ZIP"
mkdir -p "$TEMP_CLI" "$TEMP_UI"

echo "=== 构建 CLI 部署包 (v3) ==="
cp -r "$DIST_DIR/macros" "$TEMP_CLI/"
cp -r "$DIST_DIR/models" "$TEMP_CLI/"
cp "$DIST_DIR/dbt_project.yml" "$TEMP_CLI/"
cp "$DIST_DIR/model-governance-v3.md" "$TEMP_CLI/"
cp "$DIST_DIR/patch-cleanup.sh" "$TEMP_CLI/"

cd "$TEMP_CLI"
zip -r "$CLI_ZIP" . -x '*/.*'
echo "  -> project-management-v3-cli-deploy.zip"

echo "=== 构建 UI 导入包 (v3) ==="
cp -r "$DIST_DIR/macros" "$TEMP_UI/"
cp -r "$DIST_DIR/models" "$TEMP_UI/"
cp "$DIST_DIR/dbt_project.yml" "$TEMP_UI/"
cp "$DIST_DIR/model-governance-v3.md" "$TEMP_UI/"
cp "$DIST_DIR/models.tsv" "$TEMP_UI/models.tsv"

cd "$TEMP_UI"
zip -r "$UI_ZIP" . -x '*/.*'
echo "  -> project-management-v3-ui-import.zip"

rm -rf "$TEMP_CLI" "$TEMP_UI"

echo ""
echo "=== 完成 ==="
ls -lh "$CLI_ZIP" "$UI_ZIP"
