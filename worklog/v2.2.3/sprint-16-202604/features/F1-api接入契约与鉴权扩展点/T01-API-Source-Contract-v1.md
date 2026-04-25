# T01: API Source Contract v1

**优先级**: P0
**状态**: DRAFT
**依赖**: 无

## 目标

定义 `source.type=api/http` 的稳定业务契约，屏蔽 Addax、Airbyte 或自研 runner 的实现细节。

## 范围

- 定义 `ApiSourceConfig`：`baseUrl`、`defaultHeaders`、`timeout`、`rateLimit`、`tls`、`proxy`。
- 定义 `ApiResourceConfig`：`path`、`method`、`query`、`bodyTemplate`、`recordPath`、`pagination`、`cursor`。
- 定义 `ApiSchemaMapping`：字段名、ODS 列名、类型、nullable、primary key、sensitive flag。
- 更新 ingestion task schema v2 文档。

## 完成标准

- [ ] 契约中不出现 Addax reader 参数名。
- [ ] 契约能表达单数据源多 endpoint/resource。
- [ ] 契约能承载 full_refresh 与 incremental cursor。
- [ ] 契约有 JSON 示例和非法示例。

