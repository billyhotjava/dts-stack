# T06: 安全防护(SSRF/TLS/响应限制)

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T02

## 目标

补齐出站请求安全面：SSRF 防护、TLS 策略消费（verifyTls/自定义 CA/mTLS 注入点）、与 T02 的响应大小限制共同构成出站安全基线。

## 技术设计

- **SSRF**：`baseUrl` 与响应回传的 `next_url`/Link 头 URL 统一过 `OutboundUrlGuard`——协议白名单(https，http 需显式允许)、禁私网/环回/链路本地地址（解析后校验 IP，防 DNS rebinding 做连接时二次校验）、`next_url` 必须同源或在数据源配置的 allowedHosts 内。
- **TLS**：`TlsPolicy.verifyTls=false` 仅对显式配置的数据源生效（默认严格校验）；支持数据源级自定义 CA（PEM 存 secrets）；mTLS 的 SSLContext 构建接口预留（证书+私钥从 secrets 读，PREVIEW）。
- 防护拒绝时错误归类 `API_RUNTIME_BLOCKED_URL`，审计记录被拦截 URL。

## 2026-06-12 进展

- 已在 `ApiHttpEngine` 内补齐 next/auth URL guard：响应体 `nextUrl`、Link header `rel=next`、JWT `loginUrl`、OAuth2 `tokenUrl` 均先执行协议/host 校验，再限制为 baseUrl same-origin 或显式 `allowedHosts`。
- 已补 SSRF 解析基线：未显式列入 `allowedHosts` 的 loopback/site-local/link-local/any-local/multicast 地址在请求前拦截为 `API_RUNTIME_BLOCKED_URL`；覆盖 IP literal 与 `localhost`/DNS 解析到本机地址，测试环境通过 `allowedHosts=127.0.0.1` 显式放行本地 mock server。
- 已消费 `TlsPolicy.verifyTls`：默认严格校验证书；仅当数据源/资源显式配置 `tls.verifyTls=false` 时，为该 API 请求和 JWT/OAuth2 鉴权请求使用受限的 insecure SSLContext。
- 已消费 `TlsPolicy.caSecretRef`：从数据源 `secrets` 读取 PEM CA 证书，构建请求级 trust store；可支持企业自签/内网 CA，不需要关闭证书校验。
- 已保持相对 URL 与同源绝对 URL 兼容，既有翻页/JWT/OAuth2/响应大小/限流路径由 `ApiHttpEngineTest` 全量覆盖通过。
- 尚未完成：mTLS SSLContext 预留与 WireMock/真实 TLS IT 证据。

## 影响范围

- 当前落点：`service/etl/api/ApiHttpEngine.java` 内部 URL guard；后续如 DNS rebinding/TLS 逻辑扩展，再拆出 `OutboundUrlGuard`、`ApiTlsContextFactory`
- `ApiProperties`（allowedHosts 默认策略、http 允许开关）

## 验证

- [x] 单测：非白名单 body `nextUrl`、Link header、JWT 登录 URL 在发请求前拒绝为 `API_RUNTIME_BLOCKED_URL`
- [x] 单测：未显式 allowlist 的 loopback IP baseUrl 在发请求前拒绝为 `API_RUNTIME_BLOCKED_URL`
- [x] 单测：hostname 解析到 loopback（`localhost`）时在发请求前拒绝为 `API_RUNTIME_BLOCKED_URL`
- [x] 单测：自签 HTTPS 默认拒绝；`tls.verifyTls=false` 显式放行
- [x] 单测：`tls.caSecretRef` 从 secrets 加载 PEM CA 后，自签/企业 CA HTTPS 可通过
- [x] 回归：`(cd source && ./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-ingestion -Dtest=ApiHttpEngineTest test)`
- [ ] WireMock/IT：SSRF + TLS 策略有运行态证据

## 完成标准

- [ ] 契约 TlsPolicy 字段完整消费；SSRF 拦截有 IT 证据
