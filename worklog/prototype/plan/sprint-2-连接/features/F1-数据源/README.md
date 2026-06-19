# F1: 数据源

**优先级**: P0
**状态**: READY

## 目标

在阶段①「连接」内落地数据源的完整 CRUD + 连通测试闭环：列表（CompactTable，状态列）、详情页、新建/编辑表单 modal、连通测试（mock 成功/失败态）。这是黄金主线的入口，"已连通 ≥ 1 源"决定阶段①是否点亮 `✓`。命名对齐现网 `DataSourcesPage` / `DataSourceDetailPage` / `DataSourceFormModal`，service 用 `dataSourcesService`。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| [T01](./T01-数据源列表.md) | 数据源列表 | P0 | READY | S1 |
| [T02](./T02-数据源详情页.md) | 数据源详情页 | P0 | READY | T01 |
| [T03](./T03-新建编辑表单modal.md) | 新建/编辑表单 modal | P0 | READY | T01 |
| [T04](./T04-连通测试.md) | 连通测试 | P0 | READY | T03 |

## 完成标准

- [ ] `DataSourcesPage` 用 CompactTable（默认 10 条/页）渲染，含连接状态列（已连通/未连通/测试中）。
- [ ] `DataSourceDetailPage` 展示单个数据源的连接信息、schema 概览占位、关联调度占位。
- [ ] `DataSourceFormModal` 支持新建与编辑两种模式，字段对齐 `DataSourceUpsertPayload` 契约形状。
- [ ] 连通测试可从列表行与 modal 内触发，mock 返回成功/失败两态并在 UI 区分。
- [ ] 全部经 `dataSourcesService` 取数，`VITE_USE_MOCK` 开关下可注入样例（PLM 订单、ERP 客户）。
