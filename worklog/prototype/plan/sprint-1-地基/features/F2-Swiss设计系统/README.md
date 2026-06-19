# F2: Swiss 设计系统

**优先级**: P0
**状态**: READY

## 目标

落地 Swiss 网格工作台风格的设计系统（国际主义/瑞士风，亮色为主、高信息密度、单一功能强调色），全部 HSL/hex token（禁 oklch），8px 基线 + 12 列网格，并交付原子组件与高密度数据表 CompactTable，供外壳与后续全部阶段页面复用。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 设计 token（色彩/字号/间距/网格） | P0 | READY | F1-T01 |
| T02 | 原子组件（Button/Card/Surface/StatusDot/SectionTitle） | P0 | READY | T01 |
| T03 | 高密度数据表 CompactTable | P0 | READY | T01 |

## 完成标准

- [ ] 设计 token 以 CSS 变量落地：中性灰阶 + 单一蓝强调色 + 语义色（成功/警告/错误/信息）、字号层次、8px 间距刻度、12 列内容网格——全部 HSL/hex，无 oklch。
- [ ] 原子组件齐备且符合 Swiss 视觉（克制、网格对齐、强字号层次、动效仅 transform/opacity）。
- [ ] CompactTable 默认 10 条/页、切换条数刷新、数据列 tabular-nums 对齐，命名/API 对齐现网 `src/components/table/CompactTable.tsx`。
- [ ] 提供一个 token + 组件预览页（dev-only），用于目视回归。
