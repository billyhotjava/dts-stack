# F1: API数据源与凭据安全

**优先级**: P0
**状态**: DONE

## 目标

API 数据源成为平台一等数据源类型：客户 API 的 baseUrl/鉴权/凭据在 dts-platform 登记、密文落库；dts-ingestion 进程内解密使用，废除 env 明文密钥路径。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | API数据源类型契约定稳 | P0 | DONE | - |
| T02 | 凭据加密存储与secretRef解析 | P0 | DONE | T01 |
| T03 | 鉴权注册表与运行时能力对齐 | P0 | DONE | T01 |
| T04 | API连接测试端点 | P1 | DONE | T02 |

## 进展记录

- 2026-06-12：契约版本升至 1.2.0，authProviders 增加 enabled；新增 jwtLogin provider；customSignature/mTLS 标记 disabled。
- 2026-06-12：platform API 数据源默认 contractVersion 对齐 1.2.0；jwtLogin secrets metadata 支持登录模板的动态 secret placeholder。
- 2026-06-12：ingestion `IngestionSourceResolver.resolveApiInfo` 与 API 分支落地，可解析 baseUrl/defaultHeaders/auth/secrets/resources。
- 2026-06-12：API 入湖任务创建/更新校验 disabled auth provider，后端拒绝 customSignature/mTLS 等未开放鉴权。
- 2026-06-12：契约 enabled provider 与 `ApiHttpEngine.supportedAuthProviders()` 建立单测对照，运行时未开放 provider 抛 `API_RUNTIME_AUTH_UNSUPPORTED`。
- 2026-06-12：API 数据源 secrets 创建/详情/运行时解析链路补齐安全断言；连接测试日志对 API props/secrets 递归脱敏。
- 2026-06-12：API 连接测试端点支持已保存 `dataSourceId` 与保存前 `sourceConfig+secrets` 两种探活；服务级 mock API 覆盖 bearer 成功采样与 401 AUTH 分类。
- 2026-06-12：live `dts-ingestion` 重建并验证 `/api/ingestion/api/contract` 与 `/api/ingestion/connectors/capabilities/api` 均返回 contractVersion=1.2.0，`jwtLogin.enabled=true`，`customSignature/mtls.enabled=false`；证据见 `it/evidence/api-contract-live-20260612.txt`。
- 2026-06-12：live `dts-platform` 重建并验证 API 数据源凭据走 `DTS_INFRA_*` AES-GCM 密文链路；用户/analytics 脱敏详情不回显，`runtime-detail` 仅有效内部服务 token 可解密；证据见 `it/evidence/api-secret-security-20260612.txt`。

## 完成标准

- [x] API 数据源可在平台登记，凭据 AES-GCM 密文落库，查询接口不回显明文
- [x] `IngestionSourceResolver` 可解析 API 数据源（baseUrl/headers/auth/secrets），与 JDBC 路径同构
- [x] `/api/ingestion/api/contract` 返回的 authProviders 带 enabled 标记，与运行时实现严格一致
- [x] 连接测试可用解密凭据探活客户 API 并返回错误分类
