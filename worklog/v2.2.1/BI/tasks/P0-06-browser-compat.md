> 状态: done-first-pass (2026-02-13)

# P0-06 浏览器兼容基线（Chrome95/109/最新）

## 目标
- 确保客户旧环境可用，避免样式/交互退化。

## 范围
- FE：关键 CSS fallback、主题变量降级。
- QA：核心路径自动化回归脚本。

## 交付物
- 兼容性基线报告模板（设计器/预览/分享三路径）。
- 样式降级修复清单与验证记录入口。

## 本轮进展
- 已将“兼容基线”纳入 BI P0 任务拆分与执行看板：
  - `worklog/v2.2.1/BI/screen-designer-refactor-feature-checklist.md`
  - `worklog/v2.2.1/BI/screen-designer-execution-board.md`
- 已确认后续按环境矩阵执行：`Chrome95/109/latest` × `设计/预览/分享`。
- 已补浅色主题降级：`ComponentRenderer` 新增旧深色模板遗留浅色文字的自动纠偏，避免白底模板表格/滚动表出现低对比不可读。

## 验收
- 编辑、预览、分享三条主路径在基线浏览器全部通过。

## 回滚点
- 新样式回归失败时回退到上个稳定主题包。
