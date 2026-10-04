# 数据接入中心能力盘点与改进建议（v2.2.1）

## 1. 分析范围

本报告基于当前代码与既有任务清单，聚焦“数据接入中心”四个核心页面：

- 数据源管理：`source/dts-platform-webapp/src/pages/foundation/DataSourcesPage.tsx`
- 元数据采集：`source/dts-platform-webapp/src/pages/catalog/MetadataPage.tsx`
- 数据入湖任务：`source/dts-platform-webapp/src/pages/explore/etl/TransformCreatePage.tsx`、`source/dts-platform-webapp/src/pages/explore/etl/TransformPage.tsx`、`source/dts-platform-webapp/src/pages/explore/etl/TransformDetailPage.tsx`
- 接入变更记录：`source/dts-platform-webapp/src/pages/foundation/AccessChangesPage.tsx`

并交叉参考：

- `worklog/v2.2.0/platform-elt-task-list.md`
- `worklog/v2.2.0/platform-elt-p0-issues.md`
- `worklog/v2/platform/foundation/implementation.md`

---

## 2. 当前实现快照（结论）

### 已具备能力

- 已完成“数据源-元数据采集-入湖任务-执行历史-日志查看-变更记录”主链路。
- 已有异步执行与进度反馈（执行提交后轮询状态，不阻塞页面）。
- 已有增量检查点与增量审计接口/页面（针对数据库源）。
- 已有变更日志自动记录（任务创建/更新）+ 手工补录能力。
- 已有驱动管理与驱动版本选择能力（适配达梦/PG 等异构源）。

### 明确未闭环或需完善点

1. 同步模式能力与界面能力不一致
- 文件入湖强制禁用增量（UI 直接 disabled）：`TransformCreatePage.tsx:2425`
- CDC 模式仍是禁用状态：`TransformCreatePage.tsx:3028`
- 实时状态页标注为“预留”，尚未形成真实 CDC 监控闭环：`TransformDetailPage.tsx:608`

2. 调度与运维能力还在“可看不可配”
- 元数据采集页可展示 schedule，但缺少调度编辑入口：`MetadataPage.tsx:360`
- 入湖任务创建页没有 cron/interval 的可视化配置项（仅 API 支持 `syncSchedule`）。
- 执行历史虽支持失败重试，但 UI 仅暴露 `FAILED_ONLY`，未暴露 `FULL_RERUN`：`TransformExecutionHistoryPage.tsx:542`；服务端支持 `FULL_RERUN`：`IngestionTaskService.java:564`

3. 变更治理仍偏“记录型”，未形成审批闭环
- 变更记录仅提供查询+新建；缺少状态流转（审批/驳回/关闭）与责任人机制。
- 当前状态字段有 `PENDING/APPROVAL/DONE`，但无对应后续动作 API 与页面入口。

4. 目标设计与现实现存在缺口
- 既有设计包含“任务调度页面”：`worklog/v2/platform/foundation/implementation.md`，但当前前端路由未见 `foundation/task-scheduling` 对应页面。
- Schema Drift 在平台后端有能力基础，但接入中心缺少“漂移策略配置 + 漂移工单化处理”入口。

5. 历史 issue 仍有未彻底关单项（需复核）
- `P0-API-002`（全量语义统一）仍是 `doing`。
- `P0-DB-003` 与一组 `P0-QA-*` 仍为 `todo`。
- 说明代码已多轮修复，但缺少统一“门禁回归 + 验收证据”闭环。

---

## 3. 与商业软件差距（按能力域对比）

| 能力域 | 当前状态 | 商业软件常见能力 | 差距判断 |
|---|---|---|---|
| 连接器生态 | 以 JDBC/文件为主，驱动可管理 | 大量预置连接器 + OAuth + 托管升级 | 中等 |
| 同步模式 | 全量、增量（部分场景）、CDC预留 | 全量/增量/CDC 一体化，支持快照+日志切换 | 高 |
| 调度编排 | 可执行、可重试，调度配置弱 | 可视化调度、依赖、补数、SLA 告警 | 高 |
| Schema 漂移 | 后端有基础能力，前端治理薄弱 | 自动检测、策略执行、影响评估、审批 | 中高 |
| 可观测性 | 有执行历史、日志、失败分类 | 任务健康评分、SLO、告警分级、根因聚合 | 中高 |
| 变更治理 | 记录导向 | 变更审批流、影响范围自动计算、审计报表 | 中 |
| 多环境一致性 | 已有脚本化回归，但现场依赖人工 | 产品内置环境基线/兼容校验 | 中 |

---

## 4. 改进建议（按优先级）

## P0（先把“可用性与一致性”做硬）

- 建立统一验收门禁
  - 将 `P0-API-002`、`P0-DB-003`、`P0-QA-*` 收敛到一份自动化回归（含 x86/ARM + legacy/normal/dev）。
- 入湖任务补齐调度配置
  - 在 `TransformCreatePage` 增加 `cron/interval` 配置并回写 `syncSchedule`。
- 重试能力补齐
  - 执行历史页增加“整批重跑（FULL_RERUN）”选项，与后端能力对齐。
- 元数据采集可操作化
  - 在 `MetadataPage` 增加“调度编辑/启停/立即执行/最近失败详情”。

## P1（治理与效率）

- 变更记录升级为“流程化”
  - 增加状态流转 API：`PENDING -> APPROVAL -> DONE/REJECTED`。
  - 引入责任人、审批意见、变更影响对象（任务/表/字段）结构化字段。
- Schema Drift 产品化
  - 在接入中心直接配置策略：自动迁移/阻断/待审批。
  - 漂移事件联动到待办与影响分析。
- 可观测性增强
  - 提供任务 SLA、失败趋势、失败分类 TopN、恢复时长（MTTR）看板。

## P2（对齐商业化体验）

- Connector 能力模型统一
  - 以能力位（FULL/INCREMENTAL/CDC/BACKFILL）驱动 UI 与后端校验，避免“能力显示有，但不可用”。
- 模板化接入
  - 提供行业模板（ERP/CRM/Excel 批量导入）与一键参数生成。
- 运行与成本治理
  - 增加并发/限流/资源配额策略与可视化，避免高峰期任务互相挤压。

---

## 5. 建议的实施顺序（两周可落地版本）

1. 周1：P0
- 补齐调度配置 UI + FULL_RERUN UI。
- 打通 P0 回归门禁并固化验收脚本。

2. 周2：P1（第一批）
- 变更记录状态流转 API + 前端动作。
- 元数据采集失败详情与调度启停。

3. 后续迭代：P1/P2
- Schema Drift 流程化。
- Connector 能力统一与模板化接入。

---

## 6. 结论

“数据接入中心”主流程已可用，但仍属于“工程可用态”，与商业软件的主要差距在于：

- 模式完整性（CDC/实时链路）
- 调度与运维产品化（可配置、可观测、可治理）
- 变更与漂移闭环（从记录升级为流程）

建议优先完成 P0/P1 的产品化补齐，再推进 P2 的模板化与规模化能力。
