# 操作权限矩阵交付证据

**日期**：2026-07-27  
**范围**：Sprint-36/F3  
**结论**：DONE（代码与自动化证据）；Sprint-76 的 Candidate 发布/计划消费不属于本 Feature，仍由 Sprint-76/F4/F6/IT-14 关闭。

## 已交付能力

- 8 个动作：CREATE、DELETE、UPDATE、COPY、IMPORT、EXPORT、ARCHIVE、DESTROY。
- subject 支持 ROLE/DEPARTMENT/USER，resource 支持 DATASET/TABLE/CATALOG。
- deny-by-default、DENY 优先、有效期判断和超管旁路。
- 变更只创建 PENDING 申请；申请人与审批人隔离，审批后原子应用；同一 subject/resource 仅允许一条 PENDING。
- 数据集 CRUD/导入、Schema 同步、旧/新生命周期和 SQL 结果导出接入同一 `AccessChecker.canPerform`。
- 数据安全页提供操作权限矩阵、申请、批准和拒绝入口。

## 自动化结果

后端聚焦验证：

```bash
cd source/dts-platform
./mvnw -Dtest=AssetActionTest,OperationTypeNormalizerTest,AssetActionPolicyEvaluatorTest,AccessCheckerCanPerformTest,AccessCheckerTest,AssetActionPolicyServiceTest,AssetActionPolicyResourceTest,OperationPermissionMatrixIT,IamAssetActionPolicyRepositoryIT test
```

结果：`Tests run: 90, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。

其中：

- `IamAssetActionPolicyRepositoryIT`：4 个 PostgreSQL Testcontainers 测试，覆盖 08/09 migration、有效期、策略唯一元组、单 PENDING 与历史审批后再次申请。
- `OperationPermissionMatrixIT`：11 个入口契约测试，覆盖数据集 CREATE/IMPORT/UPDATE/DELETE、生命周期 COPY/ARCHIVE/DELETE/DESTROY 与带 dataset provenance 的 SQL EXPORT。
- 既有 `AccessCheckerTest`：4 个测试继续通过，确认 `canRead` 行为未回归。
- `AssetActionPolicyServiceTest`：4 个测试覆盖申请/审批/职责分离，并确认未来规则显示“待生效”而不是冒充当前生效。

前端验证：

```bash
cd source/dts-platform-webapp
node --test src/pages/security/AssetActionMatrixPanel.source-contract.test.ts
pnpm build
```

结果：source-contract 2 个测试通过；TypeScript 与 Vite Chrome95 legacy 生产构建通过。仅保留既有 browserslist/chunk-size warning。

## 架构边界

- 资产动作策略唯一真值位于 `dts-platform`；`dts-admin` 只提供角色、部门、用户目录。
- `IamAssetActionPolicyRequest` 是审批事实，`IamAssetActionPolicy` 是生效策略，不允许绕过审批直接写生效规则。
- 动作权限与既有 OBJECT/ROW/FIELD 读取策略并行，`canPerform` 不替代 `canRead`、RLS 或字段权限。
- Sprint-76 只消费 `AccessChecker.canPerform`，不得复制权限实体或把 Candidate 职责分离冒充资产动作授权。
