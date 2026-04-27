# T01: API 数据源 CRUD 与后端校验

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: F1/T01, F1/T05

## 目标

让平台数据源管理正式支持 API 类型，并提供服务端强校验。

## 范围

- 新增或扩展数据源类型枚举：决议为 `api`（聚合 alias 由 `ApiDataSourceSupport.API_TYPES` 持有）。
- 创建/更新时校验 base URL、TLS、超时、限流和默认 header。
- 禁止在非敏感 config 中提交 token、password、secret、private key。
- 查询详情时返回 masked secret summary。

## 完成标准

- [x] API 数据源可以被创建、编辑、禁用、删除 —— 走现有 `InfraDataSourceResource` CRUD，type 进入 `ApiDataSourceSupport.isApiType` 分支。
- [x] 非法 URL、非法 header、超时过大、限流配置错误会被拒绝 —— `ApiDataSourceSupport.validate*` 抛 HTTP 400，含 baseUrl 协议、host、JDBC URL 拒绝。验收口径：见 `ApiDataSourceSupport` 单测覆盖 `http(s)` only / `jdbc:` 拒绝 / 空 host 拒绝。
- [x] 敏感字段误填到普通配置时返回明确错误 —— `ApiDataSourceSupport` 拦截 `apikey/api_key/token/password/secret/privateKey` 等 key。
- [x] API 数据源不触发 JDBC catalog sync —— `ApiDataSourceSupport.isApiType` 分支提前 return，跳过 JDBC schema discovery。
- [ ] 详情接口返回 masked secret summary —— 等待 F2/T04 + F1/T05 secret 表落地后接入。

## 实现进展 / 关联代码

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/ApiDataSourceSupport.java` —— 类型识别、baseUrl 校验、明文 secret 拦截、connectorType=api 默认归一化。
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/infra/InfraDataSourceResource.java` —— CRUD 入口，调用 `ApiDataSourceSupport`。
- 待办：详情接口 maskedSecretSummary（依赖 F2/T04）；契约测试在 F2/T05。

