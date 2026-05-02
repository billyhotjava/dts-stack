# F6: 服务鉴权审计与拒绝路径日志

**优先级**: P1
**状态**: READY
**目标**: 让 service-to-service 鉴权失败可观测、可审计,便于事后排查越权尝试或配置漂移。

**依赖**: F3

## 背景

F3 完成后,filter 会拒绝大量"仅带 service header 不带 token"的请求。如果没有结构化日志和审计事件,运维难以区分:
- 良性:某调用方 token 配置遗漏(部署问题)
- 恶意:外部网络注入伪造 header 试探(安全事件)

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | READY | filter 拒绝路径 LOG.warn 加结构化字段:`event=service_auth_denied service=<x> reason=<token_missing\|token_mismatch\|service_unknown> remoteIp=<x>` |
| T02 | READY | AuditService 新增 action 类型 `SERVICE_AUTH_DENIED`,filter 拒绝时调用 audit(若 AuditService 可用)。注意:filter 在 SecurityContext 设置之前,审计调用要 best-effort,不能影响响应 |
| T03 | READY | InfraDataSourceResource.runtimeDetail 拒绝路径同样审计(F3 简化后逻辑收敛在 filter,但端点级审计仍保留作为冗余信号) |
| T04 | READY | 单测:filter 拒绝时日志包含必要字段(用 LogCaptor 或 ListAppender 验证),audit 写入有 SERVICE_AUTH_DENIED 记录 |

## 影响范围

- 修改: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/ServiceDependencyAuthenticationFilter.java`(注入 AuditService 可选)
- 修改: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/audit/AuditService.java`(确认是否已支持任意 action,若无需扩展)
- 测试: `ServiceDependencyAuthenticationFilterTest`(F3 创建,F6 扩充)

## 注意事项

- filter 注入 AuditService 要 `@Autowired(required = false)` 或 ObjectProvider,避免循环依赖
- 审计写入失败不能影响响应,catch 所有异常仅 LOG
- production 环境下,过高频拒绝(如 > 100 次/分钟)说明可能被探测,需要后续增 rate-limit(本 Sprint 不做)

## 验证

- [ ] 故意发不带 token 的请求,日志包含 `event=service_auth_denied`
- [ ] audit 表查询 `actionCode=SERVICE_AUTH_DENIED` 有记录
- [ ] 审计写入失败不影响 403 响应

## 完成标准

- [ ] 拒绝路径结构化日志可 grep
- [ ] 审计事件可在审计中心查询到
- [ ] 测试覆盖日志字段与审计调用
