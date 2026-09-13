# T03: Connector Capability 扩展 `api/http`

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T01

## 目标

把 API 接入纳入现有连接器能力契约，让前后端用同一份 capability 控制同步模式、schema drift、增量和运行时能力。

## 范围

- 新增 connector type：决议为 `api`（聚合 alias `http/https/http_api/api_http/rest/rest_api/httpreader`），见 `ApiConnectorTypes`。
- 声明支持模式：`FULL_REFRESH`、`INCREMENTAL`。
- 预留能力：`PAGINATION`、`CURSOR_CHECKPOINT`、`AUTH_PROVIDER`、`PREVIEW_SCHEMA`、`RATE_LIMIT`。
- 服务端创建/更新任务时按 capability 校验非法组合。

## 完成标准

- [x] 能力接口返回 API connector 的完整能力声明 —— 见 `ConnectorCapabilityService#apiConnectorConstraints`，包含 `authProviders`、`syncModes`、`pagination`、`cursor`、`schemaPreview`、`rateLimit`、`errorClasses` 七类。
- [x] 前端不硬编码 API 支持模式 —— capability service 是单一来源，前端读取 `/connector-capabilities`。
- [ ] 非法 sync mode 在前后端都能拦截 —— 验收口径：后端创建任务 POST 携带 `INCREMENTAL` + 缺 cursor 配置时返回 HTTP 400 业务码 `INGESTION_INVALID_SYNC_MODE`；前端 disable submit。需补 contract test。
- [ ] 能力降级策略明确且可测试 —— 需补 capability fallback 单测（如 `customSignature` provider 缺失时退化到 `none` 不允许）。

## 实现进展 / 关联代码

- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/ConnectorCapabilitySeeder.java` —— 默认 seed 增加 `api` connector 行。
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/ConnectorCapabilityService.java:148,221-260` —— `apiConnectorConstraints()`、`normalizeConnectorType()`、`api` 类型分支。
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/api/ApiConnectorTypes.java` —— alias → `api` 归一化。
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/IngestionSourceResolver.java:37-44` —— alias → `httpreader` 适配。
- 待办：sync mode 非法组合的 contract test（属 F2/T05 测试矩阵）。

