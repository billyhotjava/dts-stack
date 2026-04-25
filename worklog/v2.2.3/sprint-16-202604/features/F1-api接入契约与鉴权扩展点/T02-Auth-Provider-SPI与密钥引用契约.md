# T02: Auth Provider SPI 与密钥引用契约

**优先级**: P0
**状态**: DRAFT
**依赖**: T01

## 目标

在客户鉴权机制未定的前提下，先定义可扩展的鉴权 provider 机制，避免把 API 接入固化为单一 token 或 header 模式。

## 范围

- 定义 provider id：`none`、`apiKey`、`bearerToken`、`basic`、`oauth2ClientCredentials`、`customSignature`、`mtls`。
- 定义 provider metadata：表单字段、敏感字段、校验规则、测试请求注入方式。
- 定义 `secretRef`、`secretVersion`、`maskedDisplay` 和 rotation 语义。
- 定义日志、审计、job payload 的脱敏要求。

## 完成标准

- [ ] 新增 provider 不需要改 ingestion task 主契约。
- [ ] 前端能按 provider metadata 动态渲染表单。
- [ ] 所有敏感值只通过 secret reference 传递。
- [ ] dry-run、preview、execute 三个阶段使用同一套鉴权注入逻辑。

