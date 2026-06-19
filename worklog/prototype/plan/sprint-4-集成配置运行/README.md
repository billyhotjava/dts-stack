# Sprint-4: ② 集成 · 配置/运行/双视图

**时间**: 2026-06
**状态**: READY
**目标**: 在 S3 画布内核上补齐"节点属性抽屉 + 运行 dock + 画布⇄列表双视图"，并产出转换→dbt model 的隐藏生成映射，让阶段② 的可视化 ELT 端到端可点可走。

## 背景

设计文档 §7（`../2026-06-19-dts-platform-unified-elt-redesign-design.md`）把现网零散的 Transform / Orchestration / ScriptStudio / EltConsole 收编到阶段② 的**一张画布 + 列表视图双模**。

- **S3 已交付**：画布内核（@xyflow/react v12 + dnd-kit 拖入、节点卡片渲染、连线/平移/缩放、节点类型枚举）。
- **本 sprint 补齐**：画布右侧**属性抽屉**（随选中节点切换配置表单 + 20 行数据预览）、底部**运行 dock**（运行/调度/日志 + 运行历史）、**画布⇄列表双视图**（同一份转换作业数据），以及 **dbt 隐藏生成映射**——画布上搭转换即在底层自动生成 dbt model（mock 映射数据结构），普通用户全程不接触 dbt，仅在 S6 提供"查看生成的 dbt"只读抽屉消费本 sprint 产出的映射。

收编目标（现网 `source/dts-platform-webapp/src/pages/explore/etl/`）：

| 现网页面/组件 | 本 sprint 归属 |
|---|---|
| `TransformPage` / `OrchestrationPage` | F3 列表视图 |
| `OrchestrationRunsTab` / `TransformExecutionHistoryPage` / `ExecutionHistoryTable` | F2 运行历史 |
| `TransformCreatePage` / `TransformDetailPage`（配置/映射） | F1 属性抽屉 |
| `EltConsolePage`（运行/日志） | F2 运行 dock |
| `ScriptStudioPage`（高级 SQL） | 标记为"高级"入口，本 sprint 仅占位（不展开） |

## 约束基线（全 sprint 适用）

- **Chrome 95**：禁用 `oklch` / `:has()` / 容器查询 / `subgrid`；构建必开 `@vitejs/plugin-legacy`（chrome>=95）。抽屉/dock 布局用 flex/grid 固定栏 + ResizeObserver，不用 `:has()`/容器查询。
- **mock 契约**：分域 `*Service.ts` 返回 `Promise<Result<T>>`（`transformService` / `orchestrationService`），`VITE_USE_MOCK` 开关；**运行 / 预览 / 日志 / 运行历史 / dbt 映射全部走 mock**，不接真后端。
- **dbt 隐藏**：转换在画布上搭建 → 底层自动生成 dbt model（mock 映射）；"查看生成的 dbt"抽屉在 **S6** 实现，本 sprint 只产出生成映射数据结构。
- **设计系统**：Swiss 网格、HSL token、`CompactTable` 默认 10 条/页、`tabular-nums` 对齐数据列。命名对齐现网 `dts-platform-webapp`。
- **可回植**：组件 / token / service 契约命名对齐现网。

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|----|---------|---------|--------|------|
| F1 | 属性抽屉 | 3 | P0 | READY |
| F2 | 运行 dock | 2 | P0 | READY |
| F3 | 双视图 | 2 | P1 | READY |
| F4 | dbt 生成映射 | 1 | P1 | READY |

**统计**: Task 共 8 个；READY=8。

## 依赖

- **依赖 S3**（画布内核）：节点选中事件、节点类型枚举、转换作业数据模型、画布容器布局。
- 被 **S6**（④ 指标）依赖：F4 产出的 dbt 生成映射供 S6"查看生成的 dbt"抽屉消费。

## 完成标准

- [ ] 选中画布任一节点，右侧抽屉切换为对应节点类型的配置表单（去重 / join / 聚合 / 过滤 / 字段映射）。
- [ ] 任一节点可触发"预览 20 行"，展示 mock 结果（`CompactTable`）。
- [ ] 底部 dock 三 tab（▶运行 / ⏱调度 / 📜日志）可切换；运行触发 mock 进度 + 日志流；运行历史以 `CompactTable`（10 条/页）展示。
- [ ] 画布⇄列表视图可切换，两视图共享同一份转换作业数据；列表视图收编现网 Transform/Orchestration 表格列。
- [ ] 每个转换作业在 mock 层生成对应 dbt model 映射数据结构（供 S6 消费），普通用户界面不暴露 dbt 概念。
- [ ] 全链路在 `VITE_USE_MOCK=1` 下端到端可点可走；构建通过 legacy（chrome>=95）。
- [ ] 集成测试覆盖见 `it/README.md`，全部映射到具体 Feature/Task。
