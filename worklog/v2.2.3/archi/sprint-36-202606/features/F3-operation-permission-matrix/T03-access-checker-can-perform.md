# T03: AccessChecker.canPerform + 写动作入口接入

**优先级**: P0
**状态**: DONE
**依赖**: T02

## 目标

为 `AccessChecker` 新增 `canPerform(resource, action)` 动作维度校验（默认拒绝），由独立 `AssetActionPolicyEvaluator` 解析当前 role/department/user 与 DATASET/TABLE/CATALOG 层级策略，并在真实资产写入口接入。

## TDD 测试先行（RED）

- 新增 `AccessCheckerCanPerformTest`，放 `dts-platform/src/test/java/com/yuzhi/dts/platform/service/security/`（Mockito，mock 新仓 + `ClassificationUtils`）。
- 断言默认拒绝：无任何匹配策略时 `canPerform(dataset, EXPORT)` 返回 false（不复用 `canRead` 的宽松回退）。
- 断言 ALLOW/DENY：命中 ALLOW 策略→true；显式 DENY 优先于 ALLOW；超管 `OP_ADMIN/ADMIN` 旁路与 `canRead` 一致。
- 断言生效期：validFrom 未到 / validTo 已过 的策略不生效。
- 入口测试（每个写入口至少一条越权用例）：`AssetResourceTest`、`CatalogLifecycleResourceTest`、`SqlResource`/`ExploreExecResource` 导出路径——无 EXPORT/DELETE/ARCHIVE/DESTROY 授权时返回 403 `asset_action_denied`，不泄露资源敏感名。

## 技术设计（GREEN）

## 落地结果

- `AccessChecker.canRead` 保持原行为；`canPerform` 独立委托 `AssetActionPolicyEvaluator`，无 evaluator/策略时默认拒绝，ADMIN/OP_ADMIN 保留既有旁路。
- DENY 跨主体、跨资源层级优先；未到期/已过期规则不参与判断。
- 写入口实际映射：
  - Catalog 数据集：CREATE/IMPORT/UPDATE/DELETE；
  - Schema 同步：UPDATE；
  - 生命周期治理：CREATE、COPY（留存副本/恢复）、ARCHIVE、DELETE（回收站）、DESTROY（永久销毁）；
  - SQL 结果导出：按 `QueryExecution.datasetId` 回查真实数据集后校验 EXPORT，缺失来源时 fail-closed。
- 未修改 `PolicyService`，避免把动作授权与既有 OBJECT/ROW/FIELD 读取策略耦合。
- 原任务列出的 `CatalogLifecycleResource`、`ExploreExecResource`、`CatalogSecurityResource` 与当前源码真实写入口不符，已以 `CatalogDatasetResource`、`CatalogLifecycleControlService`、`SqlIdeExecutionController` 替代。

## 原计划（落地时已校正）

- 改 `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/security/AccessChecker.java`：新增 `boolean canPerform(CatalogDataset resource, AssetAction action)`，注入 `IamAssetActionPolicyRepository`/`PolicyService`；逻辑：超管旁路 → 查 effective 策略 → DENY 优先 → ALLOW 命中放行 → 否则默认拒绝。
- 改 `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/iam/PolicyService.java`：增加 action 策略装配方法（解析当前 subject 的 role/dept/user 候选集，调 `findEffective`），与既有 OBJECT/ROW/FIELD 装配并行、互不覆盖。
- 写动作入口接入 `canPerform`（拒绝即抛 403 + 审计），动作映射用 T01 `AssetAction`：
  - `web/rest/AssetResource.java`（CREATE/UPDATE/DELETE/COPY）
  - `web/rest/CatalogLifecycleResource.java`（ARCHIVE/DESTROY）
  - `web/rest/SqlResource.java`、`web/rest/ExploreExecResource.java`（EXPORT/IMPORT）
  - `web/rest/catalog/CatalogSecurityResource.java`（授权相关写动作）

## 影响范围

- 改既有（均需 `gitnexus_impact`，HIGH 风险预警鉴权路径）：
  - `source/dts-platform/.../service/security/AccessChecker.java`
  - `source/dts-platform/.../service/iam/PolicyService.java`
  - `source/dts-platform/.../web/rest/AssetResource.java`
  - `source/dts-platform/.../web/rest/CatalogLifecycleResource.java`
  - `source/dts-platform/.../web/rest/SqlResource.java`、`source/dts-platform/.../web/rest/ExploreExecResource.java`
  - `source/dts-platform/.../web/rest/catalog/CatalogSecurityResource.java`

## 验证

- [x] 无匹配策略时 `canPerform` 默认拒绝；DENY 优先 ALLOW；生效期边界正确。
- [x] 资产写入口越权时抛 `AccessDeniedException` 并由 REST 安全链返回 403；导出与 Schema 同步拒绝路径写审计。
- [x] 既有 `canRead` 行为不回归（RLS/FIELD 路径不受影响）。

## 完成标准

- [x] 当前具有真实资产上下文的协议写入口默认拒绝、显式授权放行。
