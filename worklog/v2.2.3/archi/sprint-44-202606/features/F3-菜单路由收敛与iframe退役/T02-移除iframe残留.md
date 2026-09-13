# T02: 移除 MetricsServiceFrame + iframe 残留

**优先级**: P1
**状态**: READY
**依赖**: T01；**SP-4（dts-metrics 服务下线）**

## 目标
SP-4 下线 dts-metrics 服务/webapp 后，删除前端 iframe 残留，彻底收口到原生页。

## 技术设计
- 删除 `MetricsServiceFrame` 组件 + `metricsServiceRoutes.ts`（`metricsServiceEmbeddedHrefFromPlatformLocation` 等）+ 相关 import/测试（`metricsServiceRoutes.test.ts`）。
- 清理灰度 flag（T01 引入的），原生页成为唯一路径。
- **gate**：必须在 SP-4 确认 dts-metrics 服务/webapp 已停用、无残留流量后执行；否则保留 iframe 兜底。

## 影响范围
- `dts-platform-webapp`：删 `MetricsServiceFrame`/`metricsServiceRoutes(.test)` + flag 清理。

## 验证
- [ ] iframe 代码移除后，语义建模全走原生页，无死链。
- [ ] 无对 dts-metrics 的残留引用（grep `metrics` iframe/embedded 清零）。

## 完成标准
- [ ] iframe 残留清除（SP-4 后）、原生页唯一、回归测试通过。
