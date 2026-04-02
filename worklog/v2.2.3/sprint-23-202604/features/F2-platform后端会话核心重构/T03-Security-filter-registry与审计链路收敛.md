# T03: Security filter、registry 与审计链路收敛

**优先级**: P0
**状态**: READY
**依赖**: T01,T02,F1/T03

## 目标
把 platform 的认证入口、过滤器、会话查询和审计记录全部切换到浏览器作用域会话模型。

## 技术设计
- 在认证链路中优先从 cookie 解析 `portal_session`，不再依赖 bearer opaque token
- 改造 `PortalOpaqueTokenIntrospector` 或替换为 cookie session 解析组件
- 收敛 `PortalSessionInactivityFilter` 的 header 和错误码输出，统一使用 F1 约定的 reason code
- 更新 `AuditService`，记录 `browser_id`、takeover 来源、session 状态变更
- 清理无用的 portal token 返回字段和过时的冲突判断分支

## 影响范围
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/SecurityConfiguration.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/session/PortalOpaqueTokenIntrospector.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/session/PortalSessionInactivityFilter.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/audit/AuditService.java`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/security/session/`

## 验证
- [ ] 后端在无 bearer token 时仍能仅凭 cookie 认证
- [ ] 审计日志能追踪 browser_id 和 takeover 事件
- [ ] reason code 与前端消费结果一致

## 完成标准
- [ ] platform 后端不再依赖浏览器可见 portal token
- [ ] 认证、会话、审计输出语义统一
