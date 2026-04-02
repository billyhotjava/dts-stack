# T02: 续租机制改为 renew-in-place

**优先级**: P0
**状态**: READY
**依赖**: F1/T02

## 目标
去掉当前 successor session 刷新链路，避免多个 tab 因 refresh 轮换而互相打掉会话。

## 技术设计
- 改造 `PortalSessionRegistry.refreshSession()` 语义为“续租当前 session”，只更新 `expires_at`、`last_seen_at`、`last_renewed_at`
- 废弃对 `successor_session_id` 的运行时依赖，仅保留必要审计字段
- 按 `browser_id` 查询当前活跃 session，替换当前按用户名全局唯一的查找逻辑
- 将 idle timeout 和 absolute timeout 的判断放到统一的 session 生命周期服务中
- 为续租操作增加限流和幂等保护，避免多个并发请求重复写库

## 影响范围
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/session/PortalSessionRegistry.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/session/PortalSessionActivityService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/security/PortalSessionRepository.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/security/session/PortalSessionRegistryTest.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/security/session/PortalSessionActivityServiceTest.java`

## 验证
- [ ] 同浏览器双 tab 并发访问不会产生 successor session
- [ ] 续租后 session id 保持不变
- [ ] 超时和接管场景仍能正确失效

## 完成标准
- [ ] 续租不再制造并发冲突
- [ ] 会话生命周期逻辑集中在单一服务中维护
