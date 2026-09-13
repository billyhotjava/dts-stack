# F3: 平台侧依赖清理

**优先级**: P1
**状态**: READY
**依赖**: F2 切断稳定（回退窗口后）

## 目标
退役稳定后，清理 dts-platform 侧为 dts-metrics 而设的 service-auth 授权与配置——精确定位，勿误伤其他内部调用。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | metrics service-auth 授权清理（MetricsInternalAccess / ServiceDependencyAuthenticationFilter） | P1 | READY | F2 |
| T02 | metrics 能力/配置清理（DtsMetricsCapabilityProperties + application.yml） | P1 | READY | F2 |

## 完成标准
- [ ] 移除/收紧仅授予 dts-metrics 的 X-DTS-Service service-auth 授权；其他内部调用（ingestion 等）不受影响。
- [ ] 移除 `DtsMetricsCapabilityProperties` + application.yml 的 metrics 能力/iframe href 配置（确认前端 iframe 已由 SP-3 移除后）。
- [ ] dts-platform clean compile/test 通过；无悬空引用。
