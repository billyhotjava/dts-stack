# Sprint-35 API / Feature Route Evidence (2026-06-07)

## 范围

- 删除旧 `/api/metrics/workspace/snapshot` 静态快照主链路。
- 前端 `MetricsShell` 从旧“工作台 / 语义建模”菜单重构为 Sprint-35 F1-F5 feature 页面。
- `SemanticDesignerPage` 保存动作从旧 `/api/metrics/semantic/metrics` 改为 `POST /api/metrics/graphs`。
- React Flow 画布节点已显示 `warehouseLayer`、asset key、grain、governance、permission、lineage；字段树显示 `standardCodeField` / `labelField`。
- 后端新增 graph draft 创建、读取和 preflight 诊断。
- 后端新增 model lifecycle API：artifact、validate、submit-review、publish dry-run、publish、versions、rollback。
- 前端 F4 feature 页面接入 model lifecycle API 与 version/rollback 操作，指标包操作保留为兼容辅助入口。
- Java 侧新增 `MetricLifecycleStatus` / `MetricContractErrorCode`，前端新增对应 TS union/type，覆盖 lifecycle、核心错误码、diagnostic、publish reference 和 version history。
- 前端 DWD 高级建模向导已读取 `layers=DWD&includeDrilldown=true`，要求 grain / standardCode / measure 后才保存候选 graph draft。
- Graph preflight 已阻断 DWD 缺标准码场景，候选 artifact 已包含 `securitySnapshot`。
- Model lifecycle 默认 DWS candidate SQL 已用 `golden-sql/dws-order-summary-model.sql` 固定输出。
- Platform model-validation gateway 已校验 `securitySnapshot.policySource` / `predicateHash` 与请求外层字段一致，不一致时在 release gate 前阻断。
- 派生指标前端已从自由表达式输入改为受控 DSL 构造器，表达式只读展示；后端 graph preflight 拒绝 raw SQL、SQL 注入片段和未登记字段引用。
- DSL 操作集已覆盖 `sum/count/count_distinct/avg/count_if/sum_if/ratio/case_when/date_trunc`；artifact generator 将 DSL 编译为带 identifier quoting 的 SQL，ratio 使用 `nullif(sum(denominator), 0)` 处理零分母。

## 关键文件

- `source/dts-metrics-webapp/src/app/MetricsShell.tsx`
- `source/dts-metrics-webapp/src/features/semantic/semanticApi.ts`
- `source/dts-metrics-webapp/src/features/semantic/SemanticModelCanvas.tsx`
- `source/dts-metrics-webapp/src/features/semantic/SemanticFieldExplorer.tsx`
- `source/dts-metrics-webapp/src/features/semantic/semanticCanvas.helpers.ts`
- `source/dts-metrics-webapp/src/features/semantic/semanticFieldExplorer.helpers.ts`
- `source/dts-metrics-webapp/src/pages/semantic/SemanticDesignerPage.tsx`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/web/rest/MetricGraphResource.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/web/rest/MetricVisualAssetResource.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/web/rest/MetricModelResource.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/MetricGraphDraftService.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/MetricModelLifecycleService.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/dto/MetricLifecycleStatus.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/dto/MetricContractErrorCode.java`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/PlatformContractClient.java`
- `source/dts-metrics/src/test/resources/golden-sql/dws-order-summary-model.sql`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/internal/MetricModelValidationInternalResource.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/metrics/MetricModelValidationService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/ServiceDependencyAuthenticationFilter.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/capability/PlatformCapabilityResource.java`

## 验证

```bash
pnpm --dir source/dts-metrics-webapp test:source
pnpm --dir source/dts-metrics-webapp typecheck
cd source && ./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-metrics -Dtest=MetricGraphResourceTest,MetricVisualAssetResourceTest,MetricModelLifecycleResourceTest,MetricsFrontendResourceContractTest,PlatformContractClientTest test
cd source && ./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-platform -Dtest=MetricModelValidationInternalResourceTest,ServiceDependencyAuthenticationFilterTest,PlatformCapabilityResourceTest test
cd source && ./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-metrics -Dtest=MetricGraphResourceTest test
cd source && ./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-metrics -Dtest=MetricGraphResourceTest,MetricModelLifecycleResourceTest test
```

结果：本文件记录的 focused 命令全部通过；2026-06-07 本轮新增受控 DSL 门禁后，`pnpm --dir source/dts-metrics-webapp test:source`、`pnpm --dir source/dts-metrics-webapp typecheck`、`cd source && ./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-metrics -Dtest=MetricGraphResourceTest,MetricModelLifecycleResourceTest test` 已重新通过。

## 搜索证明

```bash
rg -n "workspace/snapshot|MetricWorkspaceResource|/api/metrics/semantic|/bi/api/semantic|localSqlPreview|RLS placeholder|defaultWorkspace|sampleManifest|fallbackMeta|local-fallback" \
  source/dts-metrics-webapp/src source/dts-metrics-webapp/test source/dts-metrics/src/main/java source/dts-metrics/src/test/java
```

结果：只剩测试中的禁止项断言；主源码不再命中旧 snapshot、旧 semantic API、本地 fallback 或 RLS placeholder。

## 未完成

- graph draft 目前是服务内 store，数据库实体和 PATCH 更新仍未完成。
- model lifecycle 目前是服务内 store；service-local version history / rollback API 已完成，数据库实体、并发版本锁和生产级 rollback 执行仍未完成。
- platform 侧 `POST /api/internal/metrics/model-validation` 聚合网关已实现；audit validation trace、BI Dataset register、lineage register 仍未实现。
- DWD 高级建模向导 Playwright、完整 Postgres/Doris 方言 golden、audit validation trace、BI Dataset register、lineage register 仍在后续任务中。
- DSL 安全已覆盖默认入口、raw SQL、SQL injection、未登记字段、条件公式、ratio 零分母 null 处理和 Postgres/Doris identifier quoting。剩余 Sprint-35 缺口集中在 graph/lifecycle 持久化、DWD Playwright、audit validation trace、BI Dataset register 和 lineage register。
