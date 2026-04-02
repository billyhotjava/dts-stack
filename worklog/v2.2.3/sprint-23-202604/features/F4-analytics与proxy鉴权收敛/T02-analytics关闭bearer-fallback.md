# T02: analytics 关闭 bearer fallback

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
移除 analytics 对浏览器 bearer token 的兜底消费，避免它与 platform session 链路并行竞争。

## 技术设计
- 将 `PlatformAuthProperties.allowBearerFallback` 默认值改为关闭
- 收敛 `PlatformTrustedUserService`，只信任 proxy 注入的受信头
- 补齐 trusted header 缺失时的拒绝逻辑和日志输出
- 为切换期保留受控开关，但默认部署路径必须关闭 fallback

## 影响范围
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/config/PlatformAuthProperties.java`
- `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/PlatformTrustedUserService.java`
- `source/dts-analytics/src/main/resources/config/`
- `source/dts-analytics/src/test/java/`

## 验证
- [ ] analytics 在无 `X-DTS-*` 头时拒绝请求
- [ ] analytics 在仅有 cookie session 且经 proxy 注入头时正常工作
- [ ] bearer fallback 关闭后不再出现 portal token 刷新竞争

## 完成标准
- [ ] analytics 身份来源单一且可观测
- [ ] fallback 逻辑默认下线，仅保留受控回滚开关
