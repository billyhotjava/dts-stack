# 证据：韧性（platform 同步依赖超时）

**对应缺陷**: #5 同步依赖零韧性（RestClient 无超时 → 慢响应无界挂起）
**性质**: 配置级证据（代码引用），**非**自动化慢服务端测试。

## 实现位置

- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/config/RestClientConfiguration.java`
  - `factory.setConnectTimeout(Duration.ofMillis(platform.getConnectTimeoutMs()))`
  - `factory.setReadTimeout(Duration.ofMillis(platform.getReadTimeoutMs()))`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/config/DtsMetricsProperties.java`
  - `connectTimeoutMs = 2000`（默认 2s）
  - `readTimeoutMs = 5000`（默认 5s）

## 阻断条件 → 证明映射

| it/README 阻断条件 | 证明 | 如何证明 |
|---|---|---|
| platform 慢响应导致 metrics 请求无界挂起（无超时） | RestClientConfiguration 显式连接+读超时 | 全部 platform 调用经此单一 `RestClient` bean；读超时 5s 后快速失败为 `PlatformContractException` → 上层映射 503，不再无界挂起 |

## 余留 followup（非本 sprint 阻断项）

1. **自动化慢服务端超时测试**：用 mock 慢响应（如 MockWebServer 注入延迟）断言读超时触发 503。当前为配置级保证，建议后续补真实计时测试。
2. **幂等只读调用受限重试**：catalog/resolve 等幂等 GET 可加受限重试；写调用不盲目重试。拆为 F4 后续 followup。
