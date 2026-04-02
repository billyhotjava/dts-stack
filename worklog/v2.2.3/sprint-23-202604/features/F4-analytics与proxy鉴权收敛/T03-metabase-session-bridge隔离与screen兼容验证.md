# T03: metabase session bridge 隔离与 screen 兼容验证

**优先级**: P0
**状态**: READY
**依赖**: T01,T02

## 目标
保留 analytics 内部 `metabase.SESSION` 的兼容作用，但阻断它反向影响 platform 浏览器会话。

## 技术设计
- 审核 `PlatformSessionBridgeFilter` 的职责边界，只允许其在已认证身份下建立 analytics 内部 session
- 为 screen、dashboard、card 等高频接口验证 bridge 行为，确保只做内部补会话
- 定义 bridge 失败时的降级逻辑和日志，避免把 platform 会话错误翻译成 analytics 自己的认证错误
- 确认 analytics session 生命周期与 platform 浏览器 session 的主从关系

## 影响范围
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/filter/PlatformSessionBridgeFilter.java`
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/AnalyticsSessionService.java`
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/`
- `source/dts-platform-webapp/src/analytics/pages/screens/`

## 验证
- [ ] screen 预览、高频图表接口在切换后仍可正常访问
- [ ] analytics bridge 失败不会触发 platform 登录循环
- [ ] 日志可区分 platform 会话失败和 analytics 内部 session 失败

## 完成标准
- [ ] analytics session 退化为内部兼容层
- [ ] 大屏相关接口不再放大会话竞争
