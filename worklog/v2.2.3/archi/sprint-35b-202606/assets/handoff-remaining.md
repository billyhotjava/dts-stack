# Sprint-35b 交接清单（剩余工作）

**写于**: 2026-06-14
**分支**: `feat/sprint-35b-dts-metrics-hardening`（已推 GitHub）
**已完成（committed + clean 验证）**: F1 持久化 `77fae55ed`、F2 安全对等+审计 `3ecc46a3d`、F3 发布闭环 metrics 侧 `95d5c316d`、F4-T01 RestClient 超时（随 F1）。
**验证基线**: `cd source && ./mvnw -pl dts-metrics clean test`（94 单测）+ `./mvnw -pl dts-metrics test -Dtest=MetricLifecyclePersistenceIT`（4 IT，需 Docker）。
**铁律**: 改完必须 `clean compile` 再信测试结果（增量编译会假绿——本 sprint 真实踩过）。

剩余三块 + 跨服务 followup。

---

## F5 领域类型化（P1）——先做拆分，再类型化

### 为什么先拆
`MetricModelLifecycleService` 已 **799 行**逼近 800 上限，直接加类型化会超限。必须先把"候选 artifact 构建"抽到独立类。

### 拆分方案：新建 `MetricCandidateArtifactBuilder`（@Component）
- 注入 `MetricSecurityPolicyService`。
- **从 `MetricModelLifecycleService` 移出**这些方法（它们是无状态 SQL/artifact 生成，唯一外部依赖是 securityPolicyService）：
  `artifacts(...)`、`securitySnapshot(...)`、`dbtModelSql(...)`、`metricExpressions(...)`、`schemaYml`、`exposureYml`、`metricDoc`、`targetLayer`、`compileDerivedExpression`、`dateTruncSql`、`conditionSql`、`comparisonOperator`、`literalSql`、`splitArgs`、`requiredArg`、`quoteIdentifier`、`dialect`、`rejectUnsafeExpression`、`UNSAFE_EXPRESSION` 常量、`safeModelName`/`safeIdentifier`（注意 `safeIdentifier` 也被 lifecycle 的 modelName 用，可留一份或共用 util）。
- lifecycle 的 `generateArtifacts` 改为 `artifactBuilder.build(modelName, graph, policy, policySource, predicateHash)`。
- 这会把 lifecycle 降到 ~500 行，给类型化和未来 F3 saga 留空间。
- **风险低**：纯方法搬移，由现有 90+ 单测（尤其黄金 SQL 测试 `generatedDwsModelSqlMatchesGoldenCandidateSql`）守护，搬移后 SQL 必须字节不变。

### 类型化（拆分后）
- 按 `worklog/v2.2.3/sprint-35-202605/assets/dts-metrics-api-contract.md` 建 record：`VisualAssetSummary`、`GraphNode`、`ValidationDiagnostic`、`ModelState`，字段与前端 `semanticTypes.ts` 同源。
- 边界优先用 record 替换 `Map<String,Object>`；`MetricModelStateMapper` 的 `transient_state` jsonb 桶可逐步收紧（F2 评审标的 MEDIUM 项）。
- 减少 `@SuppressWarnings("unchecked")` 与 text()/firstText()/maps() 手写强转。

## F4-T02/T03 契约对齐（P1）——contained，不涉及大重构
- **T02**: `MetricVisualAssetResource`（L70）当前调通用 `/catalog/assets-v2`，与契约文档声明的 `/internal/metrics/visual-assets` 漂移。二选一：(a) 改实现走 `/internal/metrics/visual-assets`（需 platform 提供该端点）；(b) 更新 `dts-metrics-api-contract.md` 承认 `/catalog/assets-v2`。建议 (b) 短期、(a) 中期。
- **T03**: visual-assets 响应补逐资产 `permissionDecision` 富化（当前缺）。

## F6 IT 准入与验收证据（P0）——汇总已有测试为证据
- 把已绿的测试导出为证据落 `it/evidence/`：`persistence/`（F1-T05 重启/并发锁）、`security-parity/`（MetricLifecycleSecurityParityTest 6 例）、`publish-closure/`（MetricPublishDownstreamTest 4 例）。
- 核对 `it/README.md` 的 7 条阻断条件均未触发。

---

## 跨服务 followup（dts-platform 职责，需平台团队配合联调）

metrics 侧已实现调用 + 开关 gating + fail-closed，**等 platform 端点就绪后开开关联调**：

| 端点 | metrics 侧开关 | 触发点 | metrics 已就绪 |
|------|---------------|--------|---------------|
| `POST /internal/audit-events` | `dts.metrics.platform.audit-events-enabled`（默认关） | generate/validate/publish/rollback | ✅ recordAuditEvent + AuditEventRequest |
| `POST /internal/bi/datasets/register` | `dts.metrics.platform.bi-lineage-register-enabled`（默认关） | publish commit 后 | ✅ registerBiDataset + BiDatasetRegisterRequest |
| `POST /internal/lineage/register` | 同上 | publish commit 后 | ✅ registerLineage + LineageRegisterRequest |

请求 DTO 字段见 `source/dts-metrics/.../service/PlatformContractClient.java`（AuditEventRequest / BiDatasetRegisterRequest / LineageRegisterRequest）。
注册失败时 metrics 侧落 `PUBLISH_BLOCKED`；完整 outbox 重试/补偿是 F3 的进一步 followup（当前是幂等守卫 + 单次有序注册）。

## 合并前
- 该分支可独立成 PR（P0 骨架 self-contained）。CI 绿后合并。
- 与并行的 Sprint-39 / dts-platform goldenchain 工作无文件冲突（本 sprint 只动 source/dts-metrics + worklog/sprint-35b）。
