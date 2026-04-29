# Sprint-20: Data Lineage 端到端可视化打通

**时间**: 2026-04
**状态**: IN_PROGRESS
**类型**: Implementation（实施型，跨 dts-ingestion / dts-platform / dts-platform-webapp 三模块）
**目标**: 把"采集 → 编排 → 加工 → 资产 → 可视化"这条 ELT 主链路上散落的 lineage 信号收敛成统一血缘图，让用户在 LineagePage 上能从源系统一直追到 BI 报表，并支持影响分析、列级追溯、时间旅行。

## 背景

Sprint-19 已经把 OpenMetadata 服务跑通并修复了 FQN/血缘注册的硬故障，但完整的 ELT lineage 链路目前仍有 8 个断点（来自 v2.2.3 ELT 链路 review）：

| # | 断点 | 影响 |
|---|---|---|
| ❶ | DAG Python 模板没有 `inlets/outlets`，没接 OpenLineage | Airflow / OM 拿不到执行级血缘 |
| ❷ | OpenMetadata 上报只在"建任务"时调一次，execution 完成不回写 | 跑挂的也算"有 lineage"；状态丢失 |
| ❸ | `IngestionExecution` 不记录 source/target 表 | 无法从执行历史反推血缘 |
| ❹ | Addax 任务的 `tableMapping` 没回写到 `CatalogDatasetLineage` | dbt 之外的入湖路径在血缘图里完全消失 |
| ❺ | 列级 lineage 缺失 | "改一个字段会影响哪些下游报表"无法回答 |
| ❻ | lineage 节点只有 dataset，无 job/pipeline | 用户看不到"谁加工的" |
| ❼ | 前端布局是固定栅格 + 无 minimap + 无字段级 | 节点超 30 就难看清 |
| ❽ | API 无 `includeColumns` / 无时间旅行 / 无 job 维度 | 前端再好也喂不出更多信息 |

本 Sprint 用 6 个 Feature 闭环这 8 个断点，第 7 个 Feature 做集成验收。

## 产品原则

1. **采集即产血缘**：任何成功 execution 必须落一条至少表级的 lineage 边；失败也必须有"已知 lineage 但未验证"标记，绝不允许默认假定成功。
2. **AUTO/DBT/MANUAL/ADDAX/AIRFLOW 各自隔离**：`relationType` 区分来源，不同来源不能互相覆盖；只有同类来源才能 upsert。
3. **可视化以"能看清"为第一指标**：节点超 50 个时仍可读、可缩放、可定位中心节点。
4. **列级是可选 toggle，不是默认**：默认仍以表级为主，避免初次加载性能崩溃。
5. **时间旅行靠 SCD2 而非快照**：lineage 边带 `valid_from / valid_to`，永不物理删除自动产出的边。
6. **OpenMetadata 仍是补充而非真相源**：本平台 `catalog_dataset_lineage` 是真相源，OpenMetadata 是镜像与对外集成出口。

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 修补断点 | 状态 |
|----|---------|--------:|--------|---------|------|
| F1 | Addax 入湖血缘自动回写 | 4 | P0 | ❹ ❸ | IN_PROGRESS |
| F2 | Airflow 执行级血缘（OpenLineage） | 4 | P0 | ❶ ❷ ❸ | READY |
| F3 | 列级血缘（column-level lineage） | 4 | P1 | ❺ | READY |
| F4 | Job/Pipeline 节点维度引入 | 3 | P1 | ❻ ❽ | IN_PROGRESS |
| F5 | 前端可视化重做 | 5 | P0 | ❼ | IN_PROGRESS |
| F6 | 时间旅行与 Diff | 3 | P2 | ❽ | READY |
| F7 | 集成验收与发布材料 | 3 | P0 | — | READY |

**合计 26 个 task。**

## 当前落地切片（2026-04-29）

本轮先合并产品建议中的“完整链路可视化第一阶段”，以现有 `catalog_dataset_lineage` + `infra_ods_table_mapping` 为主形成可运行闭环；同时为执行级追溯在 `ingestion_execution` 增加轻量 source/target 表快照字段。

- `/api/catalog/lineage/impact` 增加 `withJobs=true` 兼容参数；默认仍保持 dataset-only 老结构。
- `withJobs=true` 时返回 `kind=dataset|job|source` 混合节点，并补充 `fromId/toId/kind` 边结构。
- 对 dbt dataset 边生成虚拟 job 节点，前端可显示 `dataset -> dbt job -> dataset`。
- 新增 `IngestionLineageWriter`，把 `infra_ods_table_mapping` 持久化为 `relationType=ADDAX` 的 `source -> ODS` 边；无法持久化或旧数据未同步时仍保留虚拟 `source -> Addax task -> ODS` 兜底展示。
- ODS 映射同步接口会自动写入 / 删除对应 ADDAX 血缘，并在审计日志中记录新增、更新、跳过、删除数量。
- `IngestionExecution` 增加 `source_tables` / `target_tables` JSON 快照，执行创建、Airflow 回填和状态同步时可从任务 `tableMapping` 固化源表与目标表。
- `catalog_dataset_lineage` 增加 `verification_status`、`last_execution_status`、`last_observed_at`、`last_verified_at` 等执行验证状态字段。
- Airflow 执行结束后，ingestion 会调用 platform `/api/catalog/lineage/ingestion-executions`，由 platform 复用 ODS 映射同步和 ADDAX 血缘写入逻辑做状态回写：成功标记 `VERIFIED`，失败标记 `KNOWN_UNVERIFIED`。
- `/api/catalog/lineage/sync-addax` 提供手动回填入口，前端 `LineagePage` 增加“同步 Addax 血缘”按钮。
- `LineagePage` 切到 job 维度请求，支持节点类型图标、关系颜色、验证状态标签、横向/纵向自动分层布局、MiniMap、节点点击影响高亮。
- 上传 dbt manifest 的旧导入关系类型统一为 `DBT`，避免与自动同步服务产生 `DBT_MODEL` / `DBT` 两套边。

仍未落地：真实 `lineage_job` 表、非 Airflow 本地执行完成回写、列级血缘、时间旅行、PNG/SVG 导出、TanStack Query 缓存。

## 依赖图

```text
F1 (Addax 回写) ─────────┐
                          ├──> F4 (Job 节点) ──> F5 (前端重做) ──> F7 (验收)
F2 (Airflow OpenLineage) ─┤                                          ▲
                          │                                          │
F3 (列级血缘) ────────────┴──> F6 (时间旅行) ───────────────────────┘
```

- F1、F2、F3 可并行启动（互不依赖）。
- F4 依赖 F1+F2 把执行级数据落库。
- F5 一旦 F4 的 API 改造完成即可启动；列级 toggle 等 F3。
- F6 依赖 F1/F2/F3 全部入库格式稳定。
- F7 是闸门，所有 Feature DONE 才能闭合。

## 完成标准

- [ ] 一条新建的 Addax 任务在执行成功后，自动在 `catalog_dataset_lineage` 写入 `relationType=ADDAX` 的边
- [ ] Airflow 跑一次 dbt run，执行级 lineage 通过 OpenLineage 事件回到 platform；`IngestionExecution` 记录到 source/target 表
- [ ] 前端 LineagePage 切到 dagre 自动布局，节点按类型（source/dataset/view/dbt model/job）有不同图标和颜色
- [ ] 用户在 LineagePage 可以打开"列级"toggle，看到 dwd_orders.amount ← ods_orders.gross_amount 这样的列血缘
- [ ] LineagePage 支持 PNG/SVG 导出和影响范围一键高亮
- [ ] `/api/catalog/lineage/impact?at=<timestamp>` 可以返回历史快照
- [ ] `it/` 目录有端到端冒烟脚本和实测截图

## 风险

| 风险 | 缓解 |
|---|---|
| OpenLineage Airflow provider 版本与现有 Airflow 2.x 不兼容 | F2-T02 先做 spike，确认版本矩阵后再编码 |
| 列级 SQL 解析对 Inceptor 方言覆盖不全 | F3-T02 限定先支持 dbt 编译后 SQL；Inceptor 原生方言放 v2.4.0 |
| dagre 渲染 500+ 节点时帧率掉 | F5-T05 启用虚拟化 + 默认深度收紧到 3 |
| SCD2 改造影响现有 lineage 查询性能 | F6-T01 加复合索引 (downstream_id, valid_from desc) |

## 相关文档

- `docs/implementation/menu.md` — 资产菜单与血缘入口
- `worklog/v2.2.3/sprint-19-202604/README.md` — 上游 OpenMetadata 闭环
- `source/dts-platform-webapp/src/pages/catalog/LineagePage.tsx` — 当前可视化实现
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/catalog/CatalogDatasetLineage.java` — 血缘实体
