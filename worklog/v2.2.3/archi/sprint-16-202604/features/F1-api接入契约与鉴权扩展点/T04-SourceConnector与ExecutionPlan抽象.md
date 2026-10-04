# T04: SourceConnector 与 ExecutionPlan 抽象

**优先级**: P0
**状态**: PARTIAL
**依赖**: T01, T03

## 目标

引入运行时策略抽象，避免继续把数据库、文件、API 全部塞进 Addax job 拼装分支。

## 范围

- 定义 `SourceConnector`：`supports`、`validate`、`preview`、`buildExecutionPlan`、`buildRuntimeOverrides`。
- 定义 `ExecutionPlan`：`engine`、`jobPayloadRef`、`secretRefs`、`checkpointPolicy`、`observabilityTags`。
- 规划现有 JDBC/File Addax 适配层。
- 规划 API connector 的 Addax HTTP reader / custom runner 适配点，不把 Airbyte 作为实施依赖。

## 完成标准

- [ ] `IngestionTaskService` 后续可委托 connector，而不是直接依赖 Addax job 细节 —— 当前仍由 `IngestionTaskService` 持 Addax 拼装；预期在 F4/T01 完成委派改造，验收口径：`IngestionTaskService` 中不再 `import com.yuzhi.dts.ingestion.service.etl.AddaxJobBuilder`（或等价类）。
- [x] API 运行时选型可延后到 F4，不阻塞契约设计 —— `ApiHttpSourceConnector` 占位实现允许契约/UI 先行。
- [x] execution plan 不包含明文密钥 —— `ExecutionPlan.secretRefs` 仅传引用；`ApiHttpSourceConnector#buildExecutionPlan` 不再读 secret 明文。验收口径：`grep -rn "password\|token\|secret" source/dts-ingestion/src/main/java/.../connector/` 仅出现于字段名不出现于赋值。
- [ ] 现有数据库和文件链路有兼容迁移路径 —— 需补 JDBC/File 的 `SourceConnector` 适配实现，保留旧 Addax 拼装作为 fallback。

## 实现进展 / 关联代码

- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/connector/SourceConnector.java` —— SPI 接口。
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/connector/SourceConnectorContext.java` —— 上下文（task / source / capability）。
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/connector/SourceConnectorRegistry.java` —— 按 connectorType 路由。
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/connector/ExecutionPlan.java` —— `engine`/`jobPayloadRef`/`secretRefs`/`checkpointPolicy`/`observabilityTags` 全字段。
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/api/ApiHttpSourceConnector.java` —— 第一个具体实现（占位 engine = `api-http`）。
- `source/dts-ingestion/src/test/java/com/yuzhi/dts/ingestion/service/etl/api/ApiHttpSourceConnectorTest.java` —— 占位 connector 单测。
- 待办：JDBC/File 适配（F4/T01）、`IngestionTaskService` 委派改造（F4/T01）。
