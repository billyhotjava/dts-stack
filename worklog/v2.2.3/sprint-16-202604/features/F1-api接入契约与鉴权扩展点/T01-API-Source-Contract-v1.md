# T01: API Source Contract v1

**优先级**: P0
**状态**: PARTIAL
**依赖**: 无

## 目标

定义 `source.type=api/http` 的稳定业务契约，屏蔽 Addax HTTP reader 或自研 runner 的实现细节，并承接 Sprint-18 的 ODS/stg 分层边界。

## 范围

- 定义 `ApiSourceConfig`：`baseUrl`、`defaultHeaders`、`timeout`、`rateLimit`、`tls`、`proxy`。
- 定义 `ApiResourceConfig`：`path`、`method`、`query`、`bodyTemplate`、`recordPath`、`pagination`、`cursor`。
- 定义 `ApiLandingPolicy`：API ODS 固定为原始 record + `_dts_*` 技术字段，不承载业务字段映射。
- 定义 `SchemaSnapshotPolicy`：preview/inference 的字段顺序、类型、nullable、样本和 drift 策略进入 schema snapshot。
- 定义 `ApiStagingFieldMapping`：字段名、stg 列名、类型、nullable、primary key、sensitive flag，供 stg 自动生成使用。
- 更新 ingestion task schema v2 文档（`docs/implementation/ingestion-task-schema.md`）。

## 完成标准

- [x] 契约中不出现 Addax reader 参数名 —— 验收口径：`grep -rn "reader.parameter" source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/api/ApiSourceContracts.java` 必须无命中；`httpreader` 仅作为兼容 reader type 在 capability/API 响应中出现。
- [x] 契约能表达单数据源多 endpoint/resource —— `ApiResourceConfig` list + `ApiSourceContracts.ApiSourceConfig` 持有 default policy。
- [x] 契约能承载 full_refresh 与 incremental cursor —— `CursorPolicy` 已支持 timestamp/numeric/opaque。
- [x] 契约已声明 ODS 原样落地边界 —— `CONTRACT_VERSION=1.1.0`，`ApiResourceConfig` 使用 `ApiLandingPolicy` / `SchemaSnapshotPolicy` / `ApiStagingFieldMapping`，不再使用 `ApiFieldMapping` 写 ODS 字段。
- [ ] 契约有 JSON 示例和非法示例 —— 待补充到 `docs/implementation/ingestion-task-schema.md` v2 章节。
- [ ] ingestion-task-schema v2 文档（任务级 source/auth/pagination/cursor/schema 字段）评审通过。

## 实现进展 / 关联代码

- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/api/ApiSourceContracts.java` —— `ApiSourceConfig`、`ApiResourceConfig`、`AuthConfig`、`PaginationPolicy`、`CursorPolicy`、`RequestPolicy`、`RateLimitPolicy`、`TlsPolicy`、`ApiLandingPolicy`、`SchemaSnapshotPolicy`、`ApiStagingFieldMapping` 已落。
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/ApiConnectorContractResource.java` —— 暴露 `/api/ingestion/api/contract`，作为前端读取 contract 的入口；`odsLanding` 明确 ODS 只保存 `_dts_raw_record` 和 `_dts_*` 技术字段。
- `source/dts-ingestion/src/test/java/com/yuzhi/dts/ingestion/web/rest/ApiConnectorContractResourceTest.java` —— contract 端点单测。
- 待办：v2 schema 文档、JSON 示例 / 非法示例 fixtures（与 F2/T05 联动）。
