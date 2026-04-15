#!/bin/bash
# 构建两个部署包：CLI 部署包 + UI 导入包 (v2)
# dbt 只管 DWD/DWS/ADS 模型，不碰 ODS 表
set -e
DIST_DIR="$(cd "$(dirname "$0")" && pwd)"
TEMP_CLI="$DIST_DIR/_tmp_cli"
TEMP_UI="$DIST_DIR/_tmp_ui"

rm -rf "$TEMP_CLI" "$TEMP_UI"
mkdir -p "$TEMP_CLI" "$TEMP_UI"

echo "=== 构建 CLI 部署包 (v2) ==="
cp -r "$DIST_DIR/macros" "$TEMP_CLI/"
cp -r "$DIST_DIR/models" "$TEMP_CLI/"
cp "$DIST_DIR/patch-cleanup.sh" "$TEMP_CLI/"

cat > "$TEMP_CLI/dbt_project.yml" << 'EOF'
name: 'pm_analytics_v2'
version: '2.0.0'
config-version: 2
profile: 'pm_analytics_v2'
model-paths: ["models"]
macro-paths: ["macros"]
clean-targets: ["target", "dbt_packages"]
EOF

cd "$TEMP_CLI"
zip -r "$DIST_DIR/project-management-v3-cli-deploy.zip" . -x '*/.*'
echo "  -> project-management-v3-cli-deploy.zip"

echo "=== 构建 UI 导入包 (v2) ==="
cp -r "$DIST_DIR/macros" "$TEMP_UI/"
cp -r "$DIST_DIR/models" "$TEMP_UI/"
cp "$DIST_DIR/models.tsv" "$TEMP_UI/models.tsv"

cd "$TEMP_UI"
zip -r "$DIST_DIR/project-management-v3-ui-import.zip" . -x '*/.*'
echo "  -> project-management-v3-ui-import.zip"

rm -rf "$TEMP_CLI" "$TEMP_UI"

echo ""
echo "=== 完成 ==="
ls -lh "$DIST_DIR"/*.zip
