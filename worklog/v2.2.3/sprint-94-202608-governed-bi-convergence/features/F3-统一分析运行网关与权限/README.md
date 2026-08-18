# F3：统一分析运行网关与权限

**优先级**：P0
**状态**：DRAFT（依赖 F1/F2）

## 目标

建立 `AnalysisQueryGateway` 作为语义预览、Analysis 和 Dashboard 的唯一应用层查询入口；用真实认证、平台资产权限、RLS/脱敏、查询预算和审计替代宽泛 `permitAll` 与分散直调。

**范围变更（2026-08-19）**：本 Sprint 不修改 Screen 查询实现，只保护历史大屏链。未来若让大屏复用 governed Analysis，必须新立 Feature 并重新过当期门禁。

## 网关契约

```text
AnalysisQueryGateway.preview(actor, AnalysisQuerySpec, requestContext)
AnalysisQueryGateway.execute(actor, AnalysisAssetRef, parameters, requestContext)

AnalysisQueryResult {
  queryId, columns, rows, rowCount, truncated,
  cacheHit, durationMs, contractChecksum
}
```

执行顺序不可调整：

```text
validated Authentication
  → platform dataset + asset read/export capability
  → pinned version/checksum
  → semantic compile
  → RLS + masking plan
  → QueryExecutionFacade read-only/compliance
  → timeout + concurrency + policy-scoped cache
  → datasource
  → masked result
  → audit + lineage + usage
```

`/api/semantic/query`、`/api/analysis/*/query`、Card 兼容查询和 Dashboard 查询都必须委托该 gateway。禁止 `SemanticQueryService` 或资源层直接调用 `DatasetQueryService.runNative`；低层 adapter 仅由 gateway 使用。

直调 `runNative` 的生产调用点共 3 处（账本 L22）。`ScreenWarmupService:152` 是历史大屏的非交互预热 owner：Sprint-94 不改该符号，架构测试只允许精确类/方法/source location 例外；禁止扩大到 package 级白名单。

## 安全与错误矩阵

| HTTP | 语义 | UI 行为 |
|---:|---|---|
| 400 | query spec/参数结构非法 | 定位表单字段，不发送 SQL |
| 401 | 无有效认证 | 进入统一登录/会话失效处理 |
| 403 | 无 read/write/export、受众或密级不匹配 | 无数据泄漏；列表过滤、直链明确拒绝 |
| 409 | dataset/revision/checksum 已漂移或非 published | 保留当前草稿，提示新建升级草稿 |
| 422 | 语义字段、类型、表达式、RLS 参数不可编译 | 指向字段/指标和稳定 errorCode |
| 429 | 用户/部门/全局并发或速率超限 | 显示 retryAfter，可取消旧查询 |
| 502/503 | Platform contract 或数据源依赖不可用 | 可诊断重试；不得绕过治理降级裸查询 |
| 504 | 查询超过 30 秒 | 提示缩小范围；审计 timeout |

## 认证与授权边界

- Spring Security 必须从已验证 Analytics session 或可信 platform forward-auth 建立 Authentication。
- 除显式 public/embed endpoint 外默认 authenticated；公共匿名分享默认 disabled。
- 资产动作继续使用 `read/write/export`；发布资格由 lifecycle/workflow 验证，不新增 permission verb。
- public link 显式开启时必须审批、到期时间、classification 检查、密码/IP 策略和审计。
- Screen 本地 access、AssetGrant 与历史运行链不在本 Sprint 改造；F0/T04/F6/T03 只负责保护与清理阻断。
- 复用 `AnalyticsAssetAccessRegistrar` 与 `PlatformPermissionFilter`（2026-08-17 `af19ed44e` 已落地），在其上扩展默认拒绝，**不重写**（见 `../../assets/dependency-boundary.md` §1）。

## UI/UX

- 统一 Query 状态组件显示 queued/running/success/truncated/denied/timeout，并支持取消。
- 不展示 SQL、stack trace、数据库异常或敏感字段值；错误展示 `errorCode + correlationId`。
- 缓存命中/耗时可在维护者诊断区查看，消费者只看到必要性能提示。
- 未授权与空数据必须是不同状态；403 不渲染空图表。

## Task

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 汇聚语义、分析、看板查询链 | P0 | DRAFT | F1/T01、F2/T01 |
| T02 | 收口认证、权限、RLS、审计和查询预算 | P0 | DRAFT | T01、F0/T02、F0/T03 |

## DoR / 完成标准

- [x] 执行顺序、结果 DTO、错误码和低层复用边界已冻结。
- [x] 不新增权限词汇，public 默认关闭。
- [ ] fresh impact 证明所有 query 入口和 public/embed 调用范围。
- [ ] 架构测试证明无 direct `runNative` 绕行。
- [ ] 401/403/409/422/429/502/504、RLS/脱敏、缓存隔离和审计有正负向 IT。
- [ ] 关闭 `.permitAll()` 后现有合法嵌入/公开链按显式策略工作或可回滚。
