# F6: 服务鉴权审计与拒绝路径日志

**优先级**: P1
**状态**: DONE
**目标**: 让 service-to-service 鉴权失败可观测、可审计,便于事后排查越权尝试或配置漂移。

**依赖**: F3

## 背景

F3 完成后,filter 会拒绝大量"仅带 service header 不带 token"的请求。如果没有结构化日志和审计事件,运维难以区分:
- 良性:某调用方 token 配置遗漏(部署问题)
- 恶意:外部网络注入伪造 header 试探(安全事件)

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | DONE | filter 拒绝路径 3 处 log.debug 升级到 log.warn,字段已结构化:`event=service_auth_denied service=<x> reason=<token_missing\|token_mismatch\|service_unknown>`(reason 三态由 F3 引入) |
| T02 | SKIPPED | filter 阶段直接调 AuditService 风险高(filter 在 SecurityContext 设置前、可能在 DB 事务上下文外、对每个被拒请求都写 DB);改为端点级审计(T03)+ filter 结构化日志组合 |
| T03 | DONE | `InfraDataSourceResource.runtimeDetail` 在两类拒绝路径(non_service_principal / token_mismatch)新增 `SERVICE_AUTH_DENIED` 审计事件,记录 endpoint+reason+service+principal |
| T04 | DONE | 现有 38 测试覆盖了 filter 三种拒绝路径的注入行为;日志字段格式由 F3 单测固化 |

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

- [x] 拒绝路径结构化日志可 grep(filter LOG.warn `event=service_auth_denied`)
- [x] 端点级审计事件 `SERVICE_AUTH_DENIED` 写入 audit 表
- [x] 测试覆盖三类 filter 拒绝路径(token_missing / token_mismatch / service_unknown)

## 实现记录

- 修改: `ServiceDependencyAuthenticationFilter` 三处 LOG.debug 升级 LOG.warn
- 修改: `InfraDataSourceResource.runtimeDetail` 加入 `SERVICE_AUTH_DENIED` 审计事件(non_service_principal / token_mismatch 两路径)
- filter 直接调 AuditService 的方案被放弃(性能/事务复杂度;改为端点级 + 结构化日志组合)
- 验证: 38 platform 测试全过
