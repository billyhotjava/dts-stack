# F3: 血缘工作台真实页面拆分

**优先级**: P0
**状态**: DONE
**目标**: 将 `LineagePage` 从 section 巨型页拆为共享能力和独立任务页。

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | DONE | 提取 `lineageShared.tsx`，集中类型、导航、过滤器、表格列、图谱布局和导出能力 |
| T02 | DONE | 血缘查询状态下放到独立页面，旧 `LineagePage` 只保留兼容分发 |
| T03 | DONE | 拆出影响分析页，承载概览、分层影响、节点与关系表 |
| T04 | DONE | 拆出血缘图谱页，复用 `VisualFlowCanvas` 并保留 SVG/PNG 导出 |
| T05 | DONE | 拆出字段血缘页，只展示字段级输入输出关系 |
| T06 | DONE | 拆出血缘导入页，只处理 Addax 同步与 dbt manifest 导入 |
| T07 | DONE | 拆出快照对比页，只处理血缘版本差异对比 |

## 交付说明

- `source/dts-platform-webapp/src/pages/catalog/LineagePage.tsx` 已从 1200+ 行 section 工作台收缩为兼容入口。
- `LineageImpactPage`、`LineageGraphPage`、`LineageColumnsPage`、`LineageImportPage`、`LineageDiffPage` 均为真实页面，不再只是 wrapper。
- 血缘图谱与大屏编辑器继续复用同一套 `VisualFlowCanvas` 组件，便于后续统一拖拽画布交互。
- 未加入临时演示数据，页面只读取真实接口或展示空态。

## 验证

- `pnpm build` 通过。
- `it/scripts/lineage-real-page-smoke.sh` 通过。
