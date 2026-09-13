# T02: metrics 能力/配置清理

**优先级**: P1
**状态**: READY
**依赖**: F2 切断稳定；SP-3 F3（iframe 已移除）

## 目标
移除 dts-platform 中 metrics 能力/iframe href 配置（确认前端 iframe 已由 SP-3 移除后）。

## 技术设计
- `config/metrics/DtsMetricsCapabilityProperties.java` + `application.yml` 中 metrics 能力/服务地址/iframe href（如 `metricsServiceEmbeddedHref` 来源）配置：确认 SP-3 F3-T02 已删前端 iframe + 无后端消费 → 移除属性类与 yml 段。
- 若该能力还驱动其他前端开关（capability 响应）→ 同步前端清理或保留必要标记。
- grep 残留 `DtsMetrics`/`metrics` capability 引用，清零或归档说明。

## 影响范围
- `dts-platform`：`DtsMetricsCapabilityProperties` + `application.yml`（+ capability resource 若有）。

## 验证
- [ ] 移除后平台启动/能力接口正常；前端无依赖该 capability 的死分支。
- [ ] `clean compile/test` 通过。

## 完成标准
- [ ] 配置/属性清理、无悬空、构建测试通过。
