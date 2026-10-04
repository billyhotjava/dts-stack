#!/usr/bin/env bash
# 一键构建 spec/ 下全部交付文档：outline.md → 正文.md → dist/*.docx → dist/*.pdf
#
# 用法：
#   bash _template/build_all.sh            # 构建全部
#   bash _template/build_all.sh 01 03      # 只构建指定编号
set -euo pipefail

SPEC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TPL="$SPEC_DIR/_template"
REF="$TPL/style-ref.docx"
VERSION="v2.2.3"
DATE="$(date +%Y-%m-%d)"
ORG="〔编制单位〕"

# 编号|目录名|文档正式名称
DOCS=(
  "01|01-需求规格说明书|数据管理平台需求规格说明书"
  "02|02-设计方案|数据管理平台设计方案"
  "03|03-测试报告|数据管理平台测试报告"
  "04|04-集成接口说明|集成接口说明"
  "05|05-安装部署手册|数据管理平台安装部署手册"
  "06|06-用户使用手册|数据管理平台用户使用手册"
  "07|07-三员操作及系统维护手册|数据管理平台三员操作及系统维护手册"
  "08|08-培训计划和培训材料|培训计划和培训材料"
)

WANT=("$@")
want() {
  [ ${#WANT[@]} -eq 0 ] && return 0
  for w in "${WANT[@]}"; do [ "$w" = "$1" ] && return 0; done
  return 1
}

DOCX_LIST=()
command -v soffice >/dev/null || { echo "缺少 soffice，无法生成 PDF"; exit 1; }
[ -f "$REF" ] || { echo "缺少样式模板 $REF"; exit 1; }

for entry in "${DOCS[@]}"; do
  IFS='|' read -r no dir name <<<"$entry"
  want "$no" || continue
  echo "[$no] $name"
  d="$SPEC_DIR/$dir"
  mkdir -p "$d/dist"

  python3 "$TPL/outline_to_skeleton.py" --src "$d/outline.md" --out "$d/正文.md"

  python3 "$TPL/build_docx.py" \
    --src "$d/正文.md" --ref "$REF" \
    --out "$d/dist/${name}-${VERSION}.docx" \
    --title "$name" --subtitle "$VERSION" --date "$DATE" --org "$ORG"

  DOCX_LIST+=("$d/dist/${name}-${VERSION}.docx")
done

# 统一导出 PDF：走 UNO 以便刷新目录域（soffice --convert-to 不会展开 TOC）
echo
echo "导出 PDF（刷新目录域）"
python3 "$TPL/docx_to_pdf.py" "${DOCX_LIST[@]}"

echo
echo "完成。产物位于各文档目录的 dist/ 下。"
