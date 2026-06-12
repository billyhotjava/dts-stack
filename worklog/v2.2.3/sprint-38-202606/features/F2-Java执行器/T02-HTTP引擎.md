# T02: HTTP引擎

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

基于 `java.net.http.HttpClient` 实现抓取内核：超时/重试退避/429 处理/响应大小限制/重定向策略/限流，契约 `RequestPolicy`/`RateLimitPolicy` 字段全消费。

## 技术设计

- `RequestPolicy`：connectTimeoutMillis/readTimeoutMillis（替代写死 60s）、**maxResponseBytes 强制**（流式读+截断抛 `API_RUNTIME_RESPONSE_TOO_LARGE`，修复旧 `resp.read()` 无限读 OOM）、followRedirects（默认 NEVER，显式开启才跟随——配合 T06 防 SSRF）。
- 重试：maxRetries+指数退避（参数可配，替代写死 `2^n 封顶30s`）；429 读 `Retry-After`；4xx(非429) 不重试、5xx/网络错重试。
- `RateLimitPolicy`：令牌桶实现 requestsPerSecond+burst（修复旧 `sleep(1/rps)` 不支持突发）；maxConcurrency 控制单任务并发分页。
- 错误归类：HTTP 状态 → `API_RUNTIME_{AUTH|RATE_LIMIT|CLIENT|SERVER|NETWORK}`，供 `ExecutionFailureClassifier` 映射（F3-T02）。

## 影响范围

- 新增 `service/etl/api/ApiHttpEngine.java`（纯函数内核，可单测）
- `ApiProperties`（F5-T01）提供全局默认值

## 验证

- [x] 本地 HTTP server 单测：429+Retry-After、超大响应截断、重定向默认拒绝、限流时序
- [x] 退避序列断言（可注入 sleeper）
- [x] readTimeout/connectTimeout 中断路径断言
- [x] maxConcurrency 并发槽位断言

## 完成标准

- [x] 契约 RequestPolicy/RateLimitPolicy 字段 100% 消费（对照断言）

## 进展记录

### 2026-06-12

- 新增 `ApiHttpEngine` / `ApiHttpException`，基于 `java.net.http.HttpClient` 实现基础请求执行。
- 已消费字段：
  - `requestPolicy.connectTimeoutMillis`
  - `requestPolicy.readTimeoutMillis`
  - `requestPolicy.maxResponseBytes`
  - `requestPolicy.followRedirects`
  - `rateLimit.requestsPerSecond`
  - `rateLimit.burst`
  - `rateLimit.maxConcurrency`
- 已实现 429 `Retry-After`、5xx/网络重试基础分支、4xx 错误分类、默认拒绝重定向、流式读取并按 `maxResponseBytes` 截断。
- `ApiIngestionExecutor` 默认 runner 已从 no-op 改为调用 `ApiHttpEngine`；`rowsRead` 已按解析 records 合计，raw 落地与 `rowsWritten` 仍在 T05 完成。
- 补齐 `ApiHttpEngineTest` 覆盖：
  - 5xx 按 `retryPolicy.baseBackoffMillis/maxBackoffMillis` 退避重试；
  - `requestPolicy.readTimeoutMillis/connectTimeoutMillis` 超时归类为 `API_RUNTIME_NETWORK`；
  - `rateLimit.maxConcurrency` 对相同 resource 的并发请求做槽位限制。
- 已跑：
  - `./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-ingestion -Dtest=ApiHttpEngineTest test`
  - `./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-ingestion -Dtest=ApiHttpEngineTest,ApiIngestionExecutorTest test`
  - `./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-ingestion -Dtest=ApiAuthProviderRegistryTest,ApiConnectorContractResourceTest,ApiHttpSourceConnectorTest,ApiHttpEngineTest,IngestionSourceResolverTest,IngestionTaskResourceTest,IngestionTaskServiceTest,IngestionTaskFullRefreshExecutionTest,IngestionTaskExecutionFilterTest,ApiIngestionExecutorTest test`
