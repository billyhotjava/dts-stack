# 证据：安全对等（graph-lifecycle 链路 ≡ metric-pack 链路）

**对应缺陷**: #2 新旧两条 artifact 链路安全不对等
**测试**:
- `com.yuzhi.dts.metrics.service.MetricLifecycleSecurityParityTest`（6 例）— lifecycle 链路
- `com.yuzhi.dts.metrics.it.MetricArtifactGenerationIT`（3 例，Testcontainers）— pack 链路黄金 SQL（postgres + doris）

**结果**: 6 + 3 = 9 例全绿，`Failures: 0, Errors: 0`（见同目录 `*.txt` 原始报告）
**采集时间**: 2026-06-14
**运行命令**: `cd source && ./mvnw -pl dts-metrics test -Dtest='MetricLifecycleSecurityParityTest,MetricArtifactGenerationIT'`

## 阻断条件 → 证明映射

| it/README 阻断条件 | 证明测试方法 | 如何证明 |
|---|---|---|
| graph lifecycle 在无权限/无 RLS 解析下仍生成 artifact | `deniedPermissionFailsArtifactGenerationWith403` | 来源资产权限被拒 → `generateArtifacts` 抛 403 `asset_permission_denied`，不产出 artifact（fail-closed） |
| lifecycle 生成 SQL 缺少 pack 链路应有的 RLS WHERE / masking | `resolvedPolicyInjectsRlsWhereAndMasksDimensionInCandidateSql` | 解析到的平台 RLS/masking 策略被注入 lifecycle 候选 SQL（RLS WHERE + 维度 masking 宏），与 pack 链路一致 |
| （masking 越权防护对等） | `maskedColumnUsedAsMetricIsRejected` | 被平台 masking 的列用作指标公式 → 拒绝（与 pack 链路同义，422） |
| 高风险动作审计缺失 | `highRiskActionsEmitAuditEventsWhenEnabled` / `auditEventsStaySilentWhenDisabled` | 开关开启时 generate/validate/publish/rollback 发审计事件；关闭时静默（gating 正确） |
| （空策略字节不变量） | `emptyPolicyLeavesCandidateSqlWithoutRlsOrMasking` | 空策略下每个注入都是 no-op，候选 SQL 字节不变（保证 F5 拆分行为保持的同一不变量） |
| （pack 链路 RLS 黄金 SQL） | `MetricArtifactGenerationIT`（postgres/doris） | pack 链路对真实 DB 渲染含 RLS 的黄金 SQL，证明对等的另一端 |
