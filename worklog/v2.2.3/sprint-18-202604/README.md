# Sprint-18: 企业级数据接入中心 Phase 1

**时间**: 2026-04  
**状态**: READY  
**类型**: Implementation（接入中心主链路收敛 + ODS 契约固化 + 离线文件接入）  
**目标**: 在不引入 Airbyte 的前提下，把 DTS 数据接入中心第一阶段做成可交付能力：数据库、Excel、CSV 均统一落 ODS，源数据不做业务计算，允许追加 DTS 技术血缘字段，后续所有清洗、映射、标准化和业务口径都从 dbt `stg` 开始。

## 背景

当前 DTS 接入链路已经具备任务创建、数据源绑定、表发现、Addax job 生成、自动建 ODS、增量水位、Airflow 调度、执行记录、ODS 映射同步和 dbt source 刷新等基础能力。问题不在“没有接入功能”，而在产品契约还不够清晰：

- ODS 的定位需要从“可自由规范化的落地区”收敛为“源数据原样落地 + DTS 技术字段”。
- 数据库、Excel、CSV、后续 API 必须共享一套 ODS、批次、血缘、观测和验收规则。
- dbt 建模边界需要明确：ODS 不做业务计算，`stg` 才做字段重命名、类型修正、枚举翻译、部门编码转名称和口径处理。
- 源端 schema snapshot 需要成为后续自动建 ODS、变更检测和 dbt source 生成的唯一依据，不能继续散落在 reader config、前端配置和临时 JDBC metadata 中。
- 现有技术字段 `source_system/import_time` 方向正确，但需要补齐 `batch_id/execution_id/task_id/source_table`，并明确命名、冲突和兼容策略。
- 离线 Excel/CSV 是现场主链路能力，必须纳入同一接入中心，而不是作为旁路工具存在。

## 产品原则

1. **不考虑 Airbyte**：DTS 标准交付继续以 Addax + dbt + Airflow + 自研接入中心为主线。
2. **ODS 原样落地**：源表业务字段值不做业务计算、不做清洗、不做口径加工。
3. **允许技术字段**：ODS 可追加 DTS 技术字段，用于批次、文件、任务、执行和血缘排查。
4. **stg 承担建模**：字段标准化、类型转换、部门编码转名称、业务派生字段全部从 dbt `stg` 层开始。
5. **Schema Snapshot 作为真源**：源端字段顺序、nullable、default、comment、PK/index 等元数据进入快照；ODS DDL、schema drift 和 dbt source 都从快照生成。
6. **数据库与文件统一契约**：数据库、Excel、CSV 共享 execution、batch、ODS mapping、dbt source、质量和观测链路。
7. **先收敛已有能力**：本 Sprint 优先改正现有链路契约，不做大规模新运行时。

## ODS 契约 v1

ODS 表由两类字段组成：

| 字段类型 | 规则 |
|---|---|
| 源业务字段 | 复制源端值，不做业务计算；允许为适配目标库做必要类型承载，但不得改变业务语义 |
| DTS 技术字段 | 用于血缘、批次、执行、文件和排障，不进入业务口径计算 |

推荐固定技术字段：

| 字段 | 说明 | 适用范围 |
|---|---|---|
| `_dts_source_system` | 来源系统 / 数据源标识 | 数据库、Excel、CSV |
| `_dts_source_table` | 来源表或逻辑资源名 | 数据库、Excel、CSV |
| `_dts_source_file` | 来源文件名 | Excel、CSV |
| `_dts_source_sheet` | Excel sheet 名 | Excel |
| `_dts_file_hash` | 文件内容 hash | Excel、CSV |
| `_dts_row_number` | 原始文件行号 | Excel、CSV |
| `_dts_import_time` | 入湖写入时间 | 数据库、Excel、CSV |
| `_dts_batch_id` | 本次执行批次 ID | 数据库、Excel、CSV |
| `_dts_execution_id` | 接入执行记录 ID / Airflow DAGRun ID | 数据库、Excel、CSV |
| `_dts_task_id` | 接入任务 ID | 数据库、Excel、CSV |

兼容说明：历史字段 `source_system/import_time` 可在过渡期继续支持，但新契约、自动建表、dbt source 和前端展示以 `_dts_*` 为准。

## 阶段路线

| 阶段 | 目标 | 与本 Sprint 的关系 |
|---|---|---|
| Phase 1 | ODS 原样落地 + DTS 技术字段 + 数据库/Excel/CSV 主链路收敛 | Sprint-18 主实施范围 |
| Phase 2 | 补齐源端 schema snapshot，采集字段顺序、nullable、default、comment、PK/index；ODS 建表、变更检测、dbt source 都从 snapshot 生成 | Sprint-18 需要预留数据模型和接口边界，避免返工 |
| Phase 3 | schema drift 检测、确认、应用和回滚流程 | Sprint-18 只做最小检测和阻断 |
| Phase 4 | 增强 stg 自动生成：字段重命名、类型标准化、技术字段处理、数据质量测试、source freshness，并承接后续 DWD/DWS 建模 | Sprint-18 固化 stg 边界和生成蓝图，不在 ODS 做规范化 |

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|----|---------|--------:|--------|------|
| F1 | ODS 原样落地契约与技术字段 | 5 | P0 | READY |
| F2 | 数据库接入自动建 ODS 收敛 | 5 | P0 | READY |
| F3 | Excel/CSV 离线文件接入 | 5 | P0 | READY |
| F4 | 执行批次血缘与运行观测 | 4 | P0 | READY |
| F5 | dbt stg 建模入口与 source 元数据 | 5 | P1 | READY |
| F6 | 前端向导与验收门禁 | 4 | P1 | READY |

**合计 28 个 task。**

## 依赖图

```text
F1 ODS 契约
  -> F2 数据库自动建 ODS
  -> F3 Excel/CSV 文件接入
  -> F4 batch/execution 血缘观测
  -> F5 dbt source 与 stg 边界
  -> F6 前端向导与验收门禁
```

F1 是所有实现的边界条件；F4 的 batch/execution 字段必须被 F2/F3 共同使用；F5/F6 只消费统一后的 ODS 契约。

## 完成标准

- [ ] 数据库接入创建的 ODS 表包含源业务字段和统一 `_dts_*` 技术字段。
- [ ] 源业务字段值不经过业务计算、清洗、翻译或口径转换。
- [ ] 每次执行生成稳定 `_dts_batch_id`，同一次执行内所有目标表批次一致。
- [ ] `IngestionExecution`、Addax job、Airflow DAGRun、ODS 技术字段可以互相追溯。
- [ ] Excel/CSV 文件接入有上传、预检、schema 确认、ODS 建表、导入、坏行记录和文件血缘字段。
- [ ] schema snapshot 模型至少预留字段顺序、nullable、default、comment、PK/index，后续 ODS DDL、schema drift 和 dbt source 可统一从快照生成。
- [ ] dbt `ods_sources.yml` 至少能输出表级和技术字段元数据，stg 模型明确从 ODS source 读取。
- [ ] stg 自动生成蓝图明确字段重命名、类型标准化、技术字段处理、数据质量测试、source freshness 和 DWD/DWS 承接方式。
- [ ] 前端向导明确展示“ODS 原样落地，stg 开始建模”的边界，不允许在 ODS 步骤配置业务计算。
- [ ] 自动化测试覆盖数据库建表、技术字段注入、文件导入、batch 追踪和 dbt source 刷新。

## 非目标

- 不引入 Airbyte。
- 不做 SaaS 连接器市场。
- 不在 ODS 层做业务清洗、部门编码转名称、枚举翻译或指标口径。
- 不要求 ODS 物理 schema 与源库 DDL 逐字符一致。
- 不在本 Sprint 完成全量 schema drift 工作流；只做最小检测和阻断策略。
- 不替换 dbt 建模链路，dbt 继续作为 ODS 之后的建模入口。

## 关键代码触点

- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/TargetTableProvisioner.java`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/AddaxJobService.java`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/IngestionTaskService.java`
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/ExcelParseService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/OdsTableMappingSyncService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtSourceService.java`
- `source/dts-platform-webapp/src/pages/explore/etl/`

## 风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 技术字段与源字段重名 | ODS 写入冲突或字段覆盖 | 统一 `_dts_*` 前缀，历史字段只做兼容 |
| 目标库类型与源库类型不完全一致 | 用户误解“100%复制”为 DDL 复制 | 文档明确“数据值原样 + 必要承载类型”，stg 做语义类型 |
| 文件 schema 不稳定 | 重复导入失败或污染 ODS | 预检确认 + schema snapshot + drift 阻断 |
| batch_id 未贯穿 Airflow/Addax/ODS | 排障无法串联 | F4 先定义 execution context，再接入 F2/F3 |
| 旧任务使用 `source_system/import_time` | 兼容期混乱 | 新任务默认 `_dts_*`，旧字段保留读兼容和迁移说明 |
