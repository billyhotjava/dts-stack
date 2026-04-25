# T04: SourceConnector 与 ExecutionPlan 抽象

**优先级**: P0
**状态**: DRAFT
**依赖**: T01, T03

## 目标

引入运行时策略抽象，避免继续把数据库、文件、API 全部塞进 Addax job 拼装分支。

## 范围

- 定义 `SourceConnector`：`supports`、`validate`、`preview`、`buildExecutionPlan`、`buildRuntimeOverrides`。
- 定义 `ExecutionPlan`：`engine`、`jobPayloadRef`、`secretRefs`、`checkpointPolicy`、`observabilityTags`。
- 规划现有 JDBC/File Addax 适配层。
- 规划 API connector 的 Addax/Airbyte/custom runner 适配点。

## 完成标准

- [ ] `IngestionTaskService` 后续可委托 connector，而不是直接依赖 Addax job 细节。
- [ ] API 运行时选型可延后到 F4，不阻塞契约设计。
- [ ] execution plan 不包含明文密钥。
- [ ] 现有数据库和文件链路有兼容迁移路径。

