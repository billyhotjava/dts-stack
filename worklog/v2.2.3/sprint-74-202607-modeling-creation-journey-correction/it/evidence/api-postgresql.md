# 真实 API 与 PostgreSQL 证据

**日期**：2026-07-27  
**认证**：真实 `portal_session`；凭据不落盘到 Sprint  
**结果**：PASS

## 1. 验收计划与 canonical 对象

- plan：`15a6bfc4-2b50-4f4b-8bf8-597d4d863a9b`
- domain：`e1f7371f-8c17-44e4-9a81-6a0754dce096`
- confirmed source binding：`9181d543-3e88-4e1e-b004-2e378dce5309`
- source version：`c6eaf2f90f7a8ebc3ac24600098454ddd7cd2abc41e5f6b6c3f9d505b1aacdac`
- release policy：`standardCoverage=KEY_AND_MEASURE`、`qualityGate=BLOCKING`

| 类型 | ModelSpec | current | 实现 |
|---|---|---|---|
| DIMENSION | `a737847a-355a-429f-809c-c3bb4644c75e` | r3 / DWD / `e39cccb…c647` | DESIGNER_GENERATED r1 / `dwd_s74_project_dimension` |
| FACT | `be1494e3-6aca-414b-b891-1d298caaecf8` | r3 / DWD / `f00169d6…ccfe` | DESIGNER_GENERATED r1 / `dwd_s74_finance_detail` |
| SUMMARY | `1667c566-8a43-45d3-baaa-0cc763dd9cbb` | r3 / DWS / `f9e173fb…8f0a` | DESIGNER_GENERATED r1 / `dws_s74_finance_summary` |
| APPLICATION | `42fe5aa7-7d89-46fd-88b9-4baae9b314a4` | r4 / ADS / `f7be70cd…3eb` | DBT_MANAGED r1 / `ads_s74_finance_dashboard` |

三条下游引用均固定六元 pin：modelSpecId、model revision、model checksum、implementationId、implementation revision、implementation checksum；没有“采用最新版本”的隐式升级。

## 2. 改型路径

- 隔离模型：`2a711ffc-5d6b-4247-bc89-87a0714181f3`
- 起点：FACT r2，checksum `c75d4181…277a`
- preview：eligible=true；明确列出 grain、factShape、timeSemantics、fields.roles 清理项
- 目标维度定义：`ffcd8c2d-4b74-427a-8368-d0dfc8255048` r2
- apply：DIMENSION r3，checksum `e9c31511…bce1`
- 幂等重放：返回相同 r3/checksum
- stale ETag + 新 idempotency key：HTTP 409 / `MODEL_SPEC_REVISION_CONFLICT`
- PostgreSQL command ledger：精确 1 条；FACT r2 与 DIMENSION r3 均可读

## 3. Schema 与迁移

真实 `databasechangelog` 已登记：

1. `20260727-05-model-spec-reclassification-command`
2. `20260727-06-warehouse-plan-governance-policy`
3. `20260727-07-model-implementation-settings-contract`

当前验收数据查询结果：

- current 类型/层：APPLICATION/ADS=1、DIMENSION/DWD=5、FACT/DWD=4、SUMMARY/DWS=1；
- implementation revision：DBT_MANAGED=1、DESIGNER_GENERATED=3；
- APPLICATION lifecycle：COMPILE/PASSED=1；
- 隔离改型 command ledger=1。

## 4. 兼容迁移

- 空选择 dry-run：HTTP 200；
- `total=10, eligible=1, conflict=2, skipped=7`；
- report checksum：`4439fc814d17d18dc4dd12dcfb64f2132b2f87017a14379a4bd0cf5b1e97199b`；
- 未执行存量 apply，避免修改用户对象；
- 未 apply checksum 的 rollback：HTTP 200，`compatibilityReadRetained=true`、`deletedImplementationRevisions=0`；
- 未知命令字段：HTTP 400 / `MODEL_IMPLEMENTATION_MIGRATION_COMMAND_INVALID`。

实际 apply/幂等/rollback 数据变更路径由 `ModelImplementationCompatibilityAdapterTest` 6 项覆盖；真实 PostgreSQL settings 约束由 `Sprint74ModelImplementationSettingsRepositoryIT` 覆盖。
