# T02：收口认证、权限、RLS、审计和查询预算

**优先级**：P0
**状态**：DRAFT
**依赖**：T01、F0/T02、F0/T03

## 可测试目标

除显式审批的 public/embed endpoint 外，Analytics API 无认证返回 401；有认证但无资产/受众/密级/read-write-export capability 返回 403；所有查询执行已应用 RLS/脱敏和用户/部门/全局预算，公共分享默认 disabled 且全链审计。

## 安全契约

- 用已验证 session 或可信 forward-auth 建立 Spring `Authentication`；信任来源、签名/issuer/audience/expiry 明确。
- Security filter chain 默认 authenticated；public/embed 只按显式 matcher 和 feature policy 放行，不允许 `.anyRequest().permitAll()`。
- 平台 asset access 为 authority source；保留 `read/write/export`，不硬编码角色名。
- Screen access/grant/fallback 不属于本 Sprint；不得借安全收口扩大到 Screen。
- 公共匿名分享默认 disabled。启用需审批记录、expiresAt、classification、密码/IP 策略和 audit；高密级可按政策直接禁止。
- RLS/脱敏 plan 在 SQL 执行前生成，结果后再次验证 schema；缓存 key 含 policyContextHash。

## 查询预算

- 用户并发 3、部门 20、全局 100；超限 429+retryAfter。
- interactive limit 10,000、timeout 30s；export 需 export capability 和独立异步/容量约束。
- permit 在成功、错误、取消、超时均释放；不得因单用户耗尽全局池。
- 审计字段遵循 F6，错误和日志不写 SQL/敏感值。

## RED → GREEN

1. RED：未登录调用受保护 API 因 permitAll 可进入资源层。
2. GREEN MockMvc：每个 API prefix 的 401/403/allowed 矩阵；public 默认 403/404，显式策略才可用。
3. GREEN policy IT：A3 只见允许行/脱敏列；A4 列表过滤、直链/导出 403；cache 不串权。
4. GREEN concurrency/fault IT：3/20/100 边界、cancel/timeout permit release、429 retryAfter。

## 影响范围与先决 impact

预计 symbol：`SecurityConfiguration`、`PlatformPermissionFilter`、analytics access registrar/filter、public link、gateway policy ports。SecurityConfiguration 是高风险；fresh impact 若 HIGH/CRITICAL，必须先向用户展示 endpoint/flow blast radius 和兼容白名单，再实施 feature-flagged expand。

## Definition of Done

- [ ] 默认认证和 401/403 矩阵覆盖全部 Analytics API prefix。
- [ ] A1～A4 的 read/write/export、受众、密级、RLS/脱敏正负向通过。
- [ ] public 默认 disabled；显式分享的审批/到期/策略/审计通过。
- [ ] 并发、timeout、cache isolation 和日志脱敏适应度函数通过。
- [ ] 变更集不包含 Screen access/grant/fallback；相关契约已在后续阶段 handoff 中登记。
