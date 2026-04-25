# T01: API 数据源 CRUD 与后端校验

**优先级**: P0
**状态**: DRAFT
**依赖**: F1/T01, F1/T05

## 目标

让平台数据源管理正式支持 API 类型，并提供服务端强校验。

## 范围

- 新增或扩展数据源类型枚举：`API`、`HTTP_API` 或统一命名。
- 创建/更新时校验 base URL、TLS、超时、限流和默认 header。
- 禁止在非敏感 config 中提交 token、password、secret、private key。
- 查询详情时返回 masked secret summary。

## 完成标准

- [ ] API 数据源可以被创建、编辑、禁用、删除。
- [ ] 非法 URL、非法 header、超时过大、限流配置错误会被拒绝。
- [ ] 敏感字段误填到普通配置时返回明确错误。
- [ ] API 数据源不触发 JDBC catalog sync。

