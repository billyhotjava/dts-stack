# Sprint-69 实施计划

> 本计划按可独立审查的小批次实现，但只设一个运行测试窗口。每个 Task 遵循“先写测试源码 → 最小实现 → 静态检查与专项审查 → 更新 Task 证据”；F1-F5 全部功能以及 F6 验收代码和 fixture 完成前，不运行 Task/Feature 测试。随后在 F6 统一执行一次整体门禁。不得把 source-contract 或 mock 截图当成真实链路完成证据。

## 0. 测试执行策略

1. **Task 层**：编写直接覆盖当前新增或修改符号的测试源码，执行静态检查与专项审查；不启动 Maven、pnpm、数据库、dbt、浏览器或部署测试。
2. **Feature 层**：完成同一 Feature 的全部实现和测试源码，静态核对跨 Task 契约；不单独运行 Feature 组合回归。
3. **Sprint 层**：F1-F5 全部功能以及 F6 验收代码、fixture 和矩阵完成后，统一执行一次后端全量、前端 production build、Liquibase/PostgreSQL、真实 dbt、Chrome 95、部署和回滚 Go/No-Go。
4. 同一全量命令在没有新修复或新代码进入时不重复执行；失败证据应复用日志，不以重复运行消耗时间和 token。
5. Task 可标记“实现完成/待统一验证”，但在最终整体门禁前不得以未运行的测试声明 DONE；Sprint DONE 必须等待 F6 单一测试窗口。

## 1. 实施边界

### 复用

- 计划真值：`WarehousePlan`、`WarehousePlanStageProjectionService`
- 模型真值：`ModelSpec`、`revision`、`checksum`
- 生命周期：`ModelLifecycleContract/Service/Resource/Repository`
- 编译执行：`ModelLifecycleCompilerPort` 与 dbt 适配器
- 发布注册：`ModelLifecyclePublicationService`
- 前端计划壳：`WarehousePlanDetailPage`
- 高级执行器：`SqlModelingPage`

### 新建

- `ReleaseCandidate/ReleaseCandidateEntry`
- `QualityRun/QualityCheckResult`
- 计划级候选聚合 API
- 计划级交付工作台 UI
- canonical PostgreSQL/dbt/Chrome 95 真实 E2E

### 不新建

- 第二套 WarehousePlan/ModelSpec 台账
- 第二套 dbt 项目 owner
- 第二套质量规则正文
- 新的一级菜单或独立发布中心

## 2. 关键接口

### ReleaseCandidate

```text
ReleaseCandidate
  id, tenantId, planId, environment
  status, version
  createdBy/At, submittedBy/At, approvedBy/At, publishedBy/At

ReleaseCandidateEntry
  id, candidateId
  modelSpecId, revision, checksum
  implementationId, implementationMode
  status, selectedReason
```

### Quality evidence

```text
QualityRun
  candidateId, entryId, rulePackVersion
  externalRunId, dataSnapshotAt, startedAt, finishedAt, status

QualityCheckResult
  qualityRunId, ruleId, ruleVersion
  dimension, severity, threshold, observedValue
  result, sampleSummary, sampleRef
```

### 计划级读模型

```text
DeliveryWorkbenchResponse
  plan
  candidate
  entries[]
  buildSummary
  qualitySummary
  reviewSummary
  publicationSummary
  timeline[]
  primaryBlocker
  allowedActions[]
  etag
```

## 3. 批次 1：冻结契约并建立候选真值

### 测试先行

- 新增：
  - `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelLifecycleContractTest.java`
  - `.../ModelReleaseCandidateServiceTest.java`
  - `.../web/rest/ModelReleaseCandidateResourceTest.java`
- 先证明：
  - RELEASE_READY 不能表示 Stage 6 COMPLETE；
  - 相同幂等键不同 payload 冲突；
  - 旧 ETag 不能覆盖新候选；
  - revision/checksum 漂移使候选 STALE；
  - 跨租户候选不可读写。

### 最小实现

- 修改：
  - `service/modeling/ModelLifecycleContract.java`
  - `service/modeling/warehouse/WarehousePlanDownstreamEvidencePort.java`
- 新增：
  - `domain/modeling/ModelReleaseCandidate.java`
  - `domain/modeling/ModelReleaseCandidateEntry.java`
  - `repository/modeling/ModelReleaseCandidateRepository.java`
  - `service/modeling/ModelReleaseCandidateService.java`
  - `web/rest/ModelReleaseCandidateResource.java`
  - `resources/config/liquibase/changelog/20260724_01_model_release_candidate.xml`
- 修改：
  - `resources/config/liquibase/master.xml`

### 验证

```bash
cd source/dts-platform
./mvnw -ntp -Dtest=ModelLifecycleContractTest,ModelReleaseCandidateServiceTest,ModelReleaseCandidateResourceTest test
```

## 4. 批次 2：构建证据与真实 dbt run 绑定

### 测试先行

- 扩展/新增：
  - `ModelLifecycleServiceTest.java`
  - `CanonicalModelLifecycleCompilerAdapterTest.java`
  - `ModelBuildRunVerificationIT.java`
- fixtures 覆盖成功、运行中、节点缺失、target 错误、旧 revision、checksum 漂移和失败终态。

### 最小实现

- 修改：
  - `service/modeling/ModelLifecycleCompilerPort.java`
  - `service/modeling/CanonicalModelLifecycleCompilerAdapter.java`
  - `service/modeling/ModelLifecycleService.java`
  - `web/rest/ModelLifecycleResource.java`
- 新增 artifact/build run 分离 contract、服务端 run-result 校验器和诊断归一化器。
- 对 Sprint-67 只消费稳定 `implementationId/mode/path/checksum`；DIMENSION adapter 等四层接口冻结后再接。

### 验证

```bash
cd source/dts-platform
./mvnw -ntp -Dtest=ModelLifecycleServiceTest,CanonicalModelLifecycleCompilerAdapterTest,ModelBuildRunVerificationIT test
```

## 5. 批次 3：结构化质量门禁

### 测试先行

- 新增：
  - `ModelQualityRulePackFactoryTest.java`
  - `ModelQualityEvidenceServiceTest.java`
  - `ModelQualityGateServiceTest.java`
- 覆盖四类模型默认规则、规则缺失、阈值篡改、敏感样本、超期、漂移和部分重跑。

### 最小实现

- 新增：
  - `domain/modeling/ModelQualityRun.java`
  - `domain/modeling/ModelQualityCheckResult.java`
  - `repository/modeling/ModelQualityRunRepository.java`
  - `service/modeling/ModelQualityRulePackFactory.java`
  - `service/modeling/ModelQualityEvidenceService.java`
  - `service/modeling/ModelQualityGateService.java`
- 修改：
  - `ModelLifecycleTestEvidencePort.java`
  - `ModelSpecStageGateService.java`
  - `WarehousePlanDownstreamEvidenceAdapter.java`
  - Sprint-69 Liquibase changeset

### 验证

```bash
cd source/dts-platform
./mvnw -ntp -Dtest=ModelQualityRulePackFactoryTest,ModelQualityEvidenceServiceTest,ModelQualityGateServiceTest test
```

## 6. 批次 4：审核、发布、PARTIAL 重试和回滚

### 测试先行

- 扩展：
  - `ModelLifecyclePublicationServiceTest.java`
  - `ModelLifecycleResourceTest.java`
  - `WarehousePlanStageProjectionServiceTest.java`
- 先证明自动连跳、同人审批、跨租户发布、成功步骤重复、PARTIAL 误判完成和 rollback 后不降级均失败。

### 最小实现

- 修改：
  - `ModelLifecycleService.java`
  - `ModelLifecyclePublicationService.java`
  - `ModelLifecycleResource.java`
  - `WarehousePlanDownstreamEvidenceAdapter.java`
  - `WarehousePlanStageProjectionService.java`
- 为每个专业注册步骤保存 attempt 和 externalRef；rollback 追加事件，不物理删除。

### 验证

```bash
cd source/dts-platform
./mvnw -ntp -Dtest=ModelLifecyclePublicationServiceTest,ModelLifecycleResourceTest,WarehousePlanStageProjectionServiceTest test
```

## 7. 批次 5：计划级交付工作台 UI

### 测试先行

- 新增：
  - `pages/modeling/delivery/deliveryViewModel.test.ts`
  - `pages/modeling/delivery/PlanDeliveryWorkbench.source-contract.test.ts`
  - `pages/modeling/delivery/deliveryCommands.test.ts`
- 扩展：
  - `WarehousePlanDetailPage.source-contract.test.ts`
  - `sqlModelReleaseSubmit.helpers.test.ts`
  - `modelLifecycleNavigation.source-contract.test.ts`

### 最小实现

- 新增：
  - `pages/modeling/delivery/PlanDeliveryWorkbench.tsx`
  - `pages/modeling/delivery/DeliveryCandidatePanel.tsx`
  - `pages/modeling/delivery/DeliveryEvidencePanel.tsx`
  - `pages/modeling/delivery/BuildEvidenceDrawer.tsx`
  - `pages/modeling/delivery/QualityEvidenceDrawer.tsx`
  - `pages/modeling/delivery/DeliveryReviewPanel.tsx`
  - `pages/modeling/delivery/DeliveryTimeline.tsx`
  - `pages/modeling/delivery/deliveryViewModel.ts`
  - `pages/modeling/delivery/useCanonicalDeliveryContext.ts`
- 修改：
  - `pages/modeling/WarehousePlanDetailPage.tsx`
  - `pages/modeling/ModelSpecDetailPage.tsx`
  - `pages/modeling/SqlModelingPage.tsx`
  - `pages/modeling/sqlModelReleaseSubmit.helpers.ts`
  - `api/modelSpecApi.ts`

### 验证

```bash
cd source/dts-platform-webapp
node --import tsx --test \
  src/pages/modeling/delivery/*.test.ts \
  src/pages/modeling/*source-contract.test.ts
pnpm build
```

## 8. 批次 6：真实集成与发布门禁

### 后端/API/PostgreSQL

- 新增 `ModelLifecycleCanonicalIT.java`。
- 经真实认证/API 依次执行 create candidate、lock、build、quality、submit、approve、publish、PARTIAL retry、rollback。
- 不直接写生命周期终态，不跳过 Spring Security 和 tenant filter。

### dbt

- 建立最小 PostgreSQL dbt fixture。
- 保存 `manifest.json/run_results.json/invocation_id` 摘要和服务端证据关联。
- 证明 build/test 失败时无法提交审核。

### Chrome 95

- 新增：
  - `source/dts-platform-webapp/e2e/sprint69-delivery-workbench.spec.ts`
  - `source/dts-platform-webapp/playwright.sprint69.chrome95.config.ts`
- 执行：

```bash
cd source/dts-platform-webapp
pnpm exec playwright test -c playwright.sprint69.chrome95.config.ts
```

### 全量门禁

```bash
cd source/dts-platform
npm run backend:unit:test

cd ../dts-platform-webapp
pnpm build

cd ../../..
git diff --check
```

## 9. 状态与证据更新规则

1. Task 完成代码、测试源码、静态检查和专项审查后标记“实现完成/待统一验证”；不得为单个 Task 执行运行测试。
2. Feature 完成全部实现和跨 Task 静态核对后保持 IN_PROGRESS，统一测试通过后再改为 DONE。
3. F1-F6 全部实现后统一执行最终全量门禁；Sprint 只有 code/test/migration/deploy/browser/rollback 六层均 PASS 后才能改为 DONE。
4. mock API 只能作为 F5 交互回归；不能关闭 F6。
5. DIMENSION 最终 E2E 因 Sprint-67 接口未冻结而未执行时，Sprint 保持 IN_PROGRESS，不得用其他模型类型替代。
