# DTS v2.2.0 P3 Issue 清单（架构演进：K8s/Airbyte/实时）

## 1. 使用说明
- 目标：为后续 `K8s + Airbyte` 与实时化能力准备可迁移架构。
- 结构：按四类拆分 `接口/API`、`前端/UI`、`数据库/DDL`、`回归/测试`。
- 状态字段：`todo` / `doing` / `done`。

## 2. 接口/API 类

### P3-API-001 连接器能力抽象层
- 状态：`done`
- 模块：`source/dts-ingestion`
- 目标：屏蔽 Addax/Airbyte 差异。
- 改动点：
  - 统一能力模型：`FULL`、`INCREMENTAL`、`CDC`、`BACKFILL`。
  - 统一任务状态机与错误码。
- 验收：
  - 业务层不依赖具体连接器实现。

### P3-API-002 Addax -> Airbyte 语义映射
- 状态：`done`
- 模块：`source/dts-ingestion`
- 目标：迁移前明确语义差异。
- 改动点：
  - 生成差异矩阵：全量、增量水位、重试、幂等。
  - 定义兼容策略与降级策略。
- 验收：
  - 每种任务类型有明确迁移路径。

### P3-API-003 实时化接口预留
- 状态：`done`
- 模块：`source/dts-ingestion` / `source/dts-platform`
- 目标：为 Kafka 等消息系统预留。
- 改动点：
  - 任务定义增加实时参数（topic/group/checkpoint）。
  - 提供准实时与实时两套编排入口。
- 验收：
  - 不影响现有批处理能力。

### P3-API-004 多租户/多项目隔离增强
- 状态：`done`
- 模块：`source/dts-platform`
- 目标：项目级数据资产隔离。
- 改动点：
  - QueryDataset 按 `X-Active-Dept` 隔离读写。
  - BI Link 绑定 QueryDataset 时校验项目作用域。
  - reports 列表支持按 `queryDatasetId` 过滤。
- 验收：
  - 跨项目不可见且不可引用。

## 3. 前端/UI 类

### P3-UI-001 连接器能力可视化
- 状态：`done`
- 模块：`source/dts-platform-webapp`
- 目标：创建任务时清晰展示能力边界。
- 改动点：
  - 在连接器选择中显示支持能力标签（全量/增量/CDC）。
  - 不支持能力直接禁用并提示。
- 验收：
  - 用户不会选择不可用模式。

### P3-UI-002 实时任务监控页
- 状态：`done`
- 模块：`source/dts-platform-webapp`
- 目标：展示实时链路运行状态。
- 改动点：
  - 指标：延迟、吞吐、checkpoint、堆积量。
  - 异常告警：延迟超阈值高亮。
- 验收：
  - 1 分钟内识别链路异常。

### P3-UI-003 跨模块血缘视图
- 状态：`done`
- 模块：`source/dts-platform-webapp` / `source/dts-platform`
- 目标：从 ODS 到可视化形成统一血缘视图。
- 改动点：
  - `LineagePage` 升级为 impact 视图，支持方向/深度/项目过滤。
  - 血缘边返回 `upstreamAssetType/downstreamAssetType/direction/projectName`。
- 验收：
  - 可按项目维度追溯上下游关系。

## 4. 数据库/DDL 类

### P3-DB-001 连接器能力配置表
- 状态：`done`
- 模块：`source/dts-ingestion` Liquibase
- 目标：持久化连接器能力描述。
- 改动点：
  - 表字段：connector_type、capabilities、version、constraints。
- 验收：
  - 前后端均可按配置驱动。

### P3-DB-002 实时任务状态表
- 状态：`done`
- 模块：`source/dts-ingestion` Liquibase
- 目标：记录实时任务状态与 checkpoint。
- 改动点：
  - 状态快照表 + 历史表。
- 验收：
  - 支持重启恢复与审计。

### P3-DB-003 血缘关系表增强
- 状态：`done`
- 模块：`source/dts-platform` Liquibase
- 目标：统一建模与可视化依赖关系。
- 改动点：
  - 血缘表增加资产类型、方向、项目空间字段。
- 验收：
  - 支持双向追溯查询。

## 5. 回归/测试类

### P3-QA-001 连接器兼容回归
- 状态：`done`
- 场景：同一任务在 Addax/Airbyte 语义对比。
- 验收：
  - 结果一致或有可解释差异报告。

### P3-QA-002 实时链路稳定性回归
- 状态：`done`
- 场景：连续运行 24h。
- 验收：
  - 无不可恢复中断，延迟在阈值内。

### P3-QA-003 跨项目隔离回归
- 状态：`done`
- 场景：多项目并行建模与可视化。
- 验收：
  - 无跨项目数据泄露。

### P3-QA-004 血缘正确性回归
- 状态：`done`
- 场景：从看板反查到 ODS。
- 验收：
  - 路径完整、节点一致。

## 6. 设计文档
- Addax/Airbyte 语义矩阵：`worklog/v2.2.0/p3-addax-airbyte-semantic-matrix.md`
- 回归清单：`worklog/v2.2.0/p3-qa-regression-checklist.md`

## 7. Definition of Done
- 完成连接器抽象，具备 Airbyte 迁移可行路径。
- 实时化能力具备试点条件（非生产强 SLA）。
