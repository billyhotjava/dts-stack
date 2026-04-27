# T02: Auth Provider SPI 与密钥引用契约

**优先级**: P0
**状态**: PARTIAL
**依赖**: T01

## 目标

在客户鉴权机制未定的前提下，先定义可扩展的鉴权 provider 机制，避免把 API 接入固化为单一 token 或 header 模式。

## 范围

- 定义 provider id：`none`、`apiKey`、`bearerToken`、`basic`、`oauth2ClientCredentials`、`customSignature`、`mtls`。
- 定义 provider metadata：表单字段、敏感字段、校验规则、测试请求注入方式。
- 定义 `secretRef`、`secretVersion`、`maskedDisplay` 和 rotation 语义。
- 定义日志、审计、job payload 的脱敏要求。

## 完成标准

- [x] 新增 provider 不需要改 ingestion task 主契约 —— `ApiAuthProviderRegistry` 注册 metadata，`ApiSourceContracts.AuthConfig` 仅持 provider id + secretRef。
- [ ] 前端能按 provider metadata 动态渲染表单 —— 等待 F5/T02 落地（已有后端 metadata 端点 `/api/ingestion/api/auth-providers`）。
- [ ] 所有敏感值只通过 secret reference 传递 —— 当前 metadata 已声明 sensitive flag，但 secret 表与读取通路待 F1/T05 + F2/T04 联动。
- [ ] dry-run、preview、execute 三个阶段使用同一套鉴权注入逻辑 —— 等待 F2/T02 与 F4/T02 实现。

## 实现进展 / 关联代码

- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/api/ApiAuthProvider.java` —— SPI 接口。
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/api/ApiAuthProviderRegistry.java` —— 7 种 provider 注册表 + 默认注入策略。
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/api/ApiAuthProviderDescriptor.java` —— 表单 metadata 描述。
- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/api/ApiAuthProviderField.java` —— 字段 metadata（含 sensitive flag）。
- `source/dts-ingestion/src/test/java/com/yuzhi/dts/ingestion/service/etl/api/ApiAuthProviderRegistryTest.java` —— provider 注册与字段元数据单测。
- 待办：secret 表设计（F1/T05）、masked display 协议（F2/T04）、统一注入入口（F2/T02 / F4/T02）。

