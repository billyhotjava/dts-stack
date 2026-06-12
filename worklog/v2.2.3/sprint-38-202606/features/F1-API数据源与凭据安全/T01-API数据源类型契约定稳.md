# T01: API数据源类型契约定稳

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标

冻结 API 数据源的登记契约：数据源级（baseUrl/defaultHeaders/auth/tls/rateLimit/requestPolicy）与任务级（resources/pagination/cursor/targetTable）字段边界划清，platform 数据源类型枚举与 `ApiConnectorTypes.API_SOURCE_TYPE_LIST` 对齐。

## 技术设计

- 数据源持有「怎么连」（baseUrl、鉴权、TLS、限流默认）；任务持有「取什么」（资源、分页、游标、目标表）。以 `ApiSourceContracts.CONTRACT_VERSION=1.2.0` 为基线：每个 authProvider 增加 `enabled: boolean` 字段。
- dts-platform 数据源模块新增/确认 `api` 类型（对齐 `ApiConnectorTypes.isApiSourceType` 的 8 个别名），`props` 存非敏感配置、`secrets` 存敏感项。
- 契约文档落 `assets/api-source-contract-v1.2.md`，作为前端 F4 与执行器 F2 的共同依据。
- ingestion connector capability 的 API 能力约束同步输出 `{id, enabled}` authProviders，避免 `/api/ingestion/api/contract` 与 `/api/ingestion/connectors/capabilities/api` 对前端给出不同口径。

## 影响范围

- `source/dts-ingestion/.../service/etl/api/ApiSourceContracts.java`、`ApiConnectorTypes.java`
- `source/dts-ingestion/.../service/etl/ConnectorCapabilityService.java`
- dts-platform 数据源 domain/REST（确认 api 类型支持，新增字段如缺）
- `web/rest/ApiConnectorContractResource.java`（契约版本与 enabled 透出）

## 2026-06-12 落地记录

- 契约端点 `/api/ingestion/api/contract` 返回 `contractVersion=1.2.0`，`jwtLogin.enabled=true`，`customSignature/mtls.enabled=false`。
- `ConnectorCapabilityService.apiConnectorConstraints` 补齐 `jwtLogin`，并将 API capability 的 authProviders 升级为 `{id, enabled}` 结构，避免能力端点和契约端点漂移。
- 契约资产 `assets/api-source-contract-v1.2.md` 已按当前 Java 执行器、连接测试、raw landing、能力端点状态复核。
- 运行态证据见 `it/evidence/api-contract-live-20260612.txt`。

## 验证

- [x] 单测：契约接口返回 1.2.0 且 provider 带 enabled（`ApiConnectorContractResourceTest`）
- [x] 单测：platform API 数据源默认 contractVersion=1.2.0，ingestion 可识别并解析 API 数据源（`ApiDataSourceSupportTest`, `IngestionSourceResolverTest`）
- [x] 单测：API connector capability authProviders 补齐 `jwtLogin` 且带 enabled（`ConnectorCapabilityServiceTest`）
- [x] 运行态接口实测：真实服务 `/api/ingestion/api/contract` 返回 1.2.0 且 enabled 字段可供前端消费
- [x] 运行态接口实测：真实服务 `/api/ingestion/connectors/capabilities/api` 返回 1.2.0 且 authProviders 带 enabled
- [x] API 契约文档评审通过

## 完成标准

- [x] 契约文档评审通过，前端/执行器以此并行开发
