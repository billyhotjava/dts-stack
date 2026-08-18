# 治理型商业智能运维手册（Gate G4）

**功能**：已发布数据集 → 分析 → 仪表板 → Platform 受众入口  
**负责人**：Analytics on-call（查询/发布）、Platform on-call（登记/受众）、业务 owner（口径）、release operator（回切）  
**风险等级**：高  
**当前 Gate**：GAP；运行手册和信号已定义，生产告警接线、目标容量校准和故障演练待执行。

## 1. 正常状态

- R1：`DTS_ANALYTICS_GOVERNED_BI_ENABLED=true`、`DTS_ANALYTICS_LEGACY_CARD_WRITE_ENABLED=true`，用于滚动升级连续性；`ANALYTICS_PUBLIC_SHARING_ENABLED=false` 为默认值。
- R2：确认旧写已冻结、备份可恢复且大屏无 legacy card 引用后，执行受控清理；完成后不再把旧 BI 写路径作为回切能力。
- 分析发布完成后 revision 为 `PUBLISHED`；仪表板发布先进入 `PENDING_REGISTRATION`，通常在一个 30 秒 reconcile 周期内转为 `AVAILABLE`。
- `analytics.report.registration.backlog` 通常为 0；短暂非 0 可以自愈。
- 消费者只看到 `PUBLISHED + AVAILABLE` 且部门/角色/密级/有效期匹配的仪表板。
- 所有查询通过 `AnalysisQueryGateway`，查询日志和错误响应可用 request/correlation id 关联。

## 2. 健康检查

| 检查项 | 命令/端点 | 期望结果 |
|---|---|---|
| Analytics 进程 | `GET /bi/api/health` | HTTP 200 |
| registration 功能健康 | `GET /actuator/health` 或组件 `biReportRegistration` | backlog <100 时 UP |
| Platform 进程 | `GET /api/health` | HTTP 200 |
| 新能力开关 | `analytics.bi.feature.enabled{feature="governed_bi"}` | pilot/default 为 1 |
| 旧写保护 | `analytics.bi.feature.enabled{feature="legacy_card_write"}` | R1 为 1；R2 清理窗口前先冻结并确认无新增写入 |
| 登记积压 | `analytics.report.registration.backlog` | 正常 0；不得持续增长 |
| 仪表板状态 | Dashboard API 的 `lifecycle_status`、`registration_status` | 发布后依次 PUBLISHED/PENDING → PUBLISHED/AVAILABLE |

## 3. 指标与告警

| 告警 | 条件与阈值 | 级别 | 含义 | 首个动作 |
|---|---|---|---|---|
| GovernedBiServerError | `analytics.bi.http.total{outcome="server_error"}` / total >5% 持续 5m；>15% 持续 5m 升级 | P2/P1 | 新 BI 请求明显失败 | 按 §5.1 用 correlationId 定位；P1 先关闭 governed flag |
| GovernedQueryP95 | `analytics.query.duration` P95 >5s 持续 10m | P2 | 交互查询超过预算 | 查 chain/result/error_code、并发和下游数据源；不要放宽 30s timeout |
| GovernedQueryTimeout | query `error_code=ANALYSIS_QUERY_TIMEOUT` >1% 持续 5m | P1 | 用户已遇到 504 | 暂停新发布，检查数据源/执行计划；必要时 flag 回切 |
| GovernedQuery429 | query 429 比例 >5% 持续 10m | P2 | 并发预算饱和 | 查 user/department/global scope，优先削峰，不直接无限增大并发 |
| ReportRegistrationBacklog | backlog ≥20 持续 5m；≥100 立即 | P2/P1 | 新发布仪表板不可消费；≥100 health DOWN | 按 §5.2 检查 Platform 与失败日志，修复后从 UI/服务重试当前版本 |
| UnauthorizedSpike | `analytics.bi.unauthorized.total` >20/min 且较 1h 基线增长 3 倍，持续 5m | P2 | 可能配置漂移或越权探测 | 按 surface/status 分组，核对 forward-auth 与角色，不临时 permitAll |
| PublicationFailure | `analytics.bi.publication.total{outcome="failure"}` ≥3/5m | P2 | 校验/并发/依赖故障阻断发布 | 查 assetKey/requestId 和错误码；禁止绕过 validate |
| RegistrationFailure | `analytics.report.registration.total{outcome="failure"}` ≥3/5m | P2 | Analytics→Platform 登记失败 | 检查 service token/base URL/Platform health，等待退避或安全重试 |

抑制规则：Platform 整体不可用时，以 Platform availability 为父告警，抑制同窗口的 registration failure 风暴；governed flag 主动关闭的维护窗口抑制 503 ratio，但不得抑制 legacy-write-disabled 信号。

R1 观察面板保留 `analytics.bi.legacy.calls{surface,operation}` 趋势，用于确定冻结窗口；R2 的硬门禁仍是明确截止水位、备份恢复成功、大屏引用为 0 和 dry-run 对账，UNKNOWN 不得补成 0。

## 4. 关键日志字段

| 字段 | 用途 | 示例 |
|---|---|---|
| correlationId/requestId | 串联浏览器、Analytics、Platform | `9b...` |
| actor | 定位操作主体；使用账号标识，不记录 token | `analyst-a1` |
| assetKey | 稳定资产身份 | `analysis:12`、`dashboard:22` |
| revisionId / version | 定位不可变发布版本 | `revisionId=101 version=3` |
| datasetVersion / checksum | 对账数据集契约和发布快照 | `datasetVersion=4 checksum=...` |
| eventId / attempts | 定位 outbox 重试 | UUID / 3 |
| outcome / status / errorCode | 区分业务阻断、权限和系统失败 | `PENDING_REGISTRATION` / `ANALYSIS_QUERY_TIMEOUT` |

禁止记录：原始 SQL、查询参数值、未脱敏结果、密码、cookie、service token、完整敏感业务字段。审计记录与运行日志分离，不能用删除审计代替日志降噪。

## 5. 故障处置

### 5.1 Platform contract 502/503 或 revision drift

1. 从 UI 错误或响应头取得 request/correlation id，检索 Analytics 日志。
2. 验证 Platform health 和指定 dataset/version contract；核对 checksum，不读取或复制底层 SQL到日志。
3. `UNRESOLVED`、checksum mismatch 或归档版本必须阻断；创建基于当前已发布版本的新草稿，禁止改历史 revision。
4. Platform 恢复后重新校验/发布。故障期间禁止绕过平台契约直接执行 SQL。

### 5.2 registration 长时间 pending/failed

1. 查看 `analytics.report.registration.backlog`、`biReportRegistration` health 和 `eventId/assetKey/version/attempts` 日志。
2. 检查 dts-platform health、内部 registration API、service name/token 配置和网络超时。
3. 修复依赖后，在仪表板页面点击“重试注册”；服务只重试当前 published version，旧版本事件不能覆盖当前状态。
4. 确认 Platform `bi_report_link(asset_type,asset_key,asset_version)` 与 dashboard published revision 一致，随后状态应为 `AVAILABLE`。
5. 不要手工把 dashboard 改成 `AVAILABLE`，不要重复插入 report link，不要删除 outbox 证据。

### 5.3 query 429/504

1. 用 queryId/requestId 查 `analytics.query.total` 与 `analytics.query.duration` 的 chain/result/error_code。
2. 429：确认是 user=3、department=20 还是 global=100 的预算；等待 `retryAfter`，优先降低并发。
3. 504：检查 Platform contract、数据源连接和执行计划；30 秒上限不因单个慢查询临时放宽。
4. 故障持续且影响 pilot 时关闭 governed flag，旧写保持开启；不得切到未治理的 raw SQL 路径。

### 5.4 public/consumer link denied spike

1. 按 surface/status、部门、角色和密级对账；检查有效期是否已过。
2. 核对 forward-auth 传递和 Platform published report filter；列表和直链必须同结论。
3. 修正受众需发布新版本或重试登记，不直接扩大到“全部部门”。
4. 任何 401/403 都不得通过 `permitAll` 或 anonymous fallback 临时规避。

### 5.5 RLS/cache mismatch

1. 立即停止受影响资产的新消费并保留 request/query id。
2. 对账 actor-policy-context、dataset checksum、query hash；确认敏感字段没有跨用户缓存。
3. 清理对应安全 cache key，而不是全局关闭 RLS/脱敏。
4. 若无法证明隔离，关闭 governed flag并按安全事件流程升级 P1。

## 6. 容量与保留

- 查询预算：每用户 3、每部门 20、全局 100；交互返回最多 10,000 行，30 秒超时。
- Analysis：派生指标 ≤20、filter ≤50、orderBy ≤10；Dashboard：组件 ≤50、参数 ≤20。
- outbox 每轮最多处理 50 条，默认 30 秒 reconcile，失败指数退避且最长 300 秒。
- backlog 20 是人工介入水位，100 是功能 health DOWN 水位。
- revision 与 outbox 属发布/审计证据，本 Sprint 不自动清理；归档/保留策略须在取得生产增长率后单独审批。
- 客户资产量、P95 和并发分布仍待 F0/T03 校准，本地空库不得作为容量 PASS 证据。

## 7. 禁止操作

- 禁止手改 revision pointer、registration status 或 checksum。
- 禁止 truncate/delete outbox 来消除告警。
- 禁止在本 Sprint 关闭 legacy Card write、重定向或删除旧路由。
- 禁止用公共分享、raw SQL、直接数据库查询绕过权限/RLS/密级。
- 禁止在未记录旧 image ID、未验证旧入口、未取得 release operator 授权时执行生产替换。

## 8. 待完成的 G4 证据

- [ ] 将上述告警接入实际监控并记录规则 ID/owner。
- [ ] 执行 Platform 不可用、query timeout、registration backlog 三个故障演练。
- [ ] 用 A1～A4 与 Chrome 95 完成一次聚焦纵向验收。
- [ ] 回填真实资产量、QPS、P95、并发和 outbox 增长率。

在这些证据完成前，G4 保持 GAP；文档落盘不等于生产运维验收通过。
