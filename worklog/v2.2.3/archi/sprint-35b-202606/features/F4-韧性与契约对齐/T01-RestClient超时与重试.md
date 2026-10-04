# T01: RestClient 连接/读超时 + 受限重试

**优先级**: P1（低成本，已随 F1 落地）
**状态**: DONE（SimpleClientHttpRequestFactory 连接 2s/读 5s，env 可覆盖；编译+84 单测绿）
**依赖**: 无

## 目标

给 `RestClientConfiguration` 的 RestClient 配置连接与读超时，并对幂等只读调用加受限重试。

## 技术设计

- `RestClientConfiguration`：用 `ClientHttpRequestFactorySettings` 或 `SimpleClientHttpRequestFactory`/Apache HttpClient 配置 connectTimeout（如 2s）、readTimeout（如 5s），超时值外部化到 `DtsMetricsProperties.Platform`。
- 受限重试：仅对幂等 GET/resolve（catalog/assets、glossary/domains/data-standards resolve）做有上限的重试（如 2 次、指数退避）；写类（validate/release/submit/audit）不重试或仅在明确幂等键下重试。
- 保持 fail-closed 语义：超时/耗尽重试 → `PlatformContractException` → 503。

## 影响范围
- `RestClientConfiguration`、`DtsMetricsProperties`、`application.yml`。

## 验证
- [ ] 配置生效：模拟 platform 慢响应触发读超时而非无限等待。
- [ ] 写调用不会因重试产生重复副作用。

## 完成标准
- [ ] platform 抖动不再让 metrics 请求无界挂起。
