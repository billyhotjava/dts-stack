# T02: IamAssetActionPolicy 领域模型 + Liquibase changelog

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

新增 `IamAssetActionPolicy` 实体与持久化表，承载 subject(role/dept/user) × resource(catalog/table/dataset) × action × effect × validFrom/validTo 的集中授权矩阵，复用 `IamDatasetPolicy` 的字段约定与生效期模型。

## TDD 测试先行（RED）

- 新增 `IamAssetActionPolicyRepositoryIT`（Testcontainers PostgreSQL），放 `dts-platform/src/test/java/com/yuzhi/dts/platform/repository/iam/`。
- 断言：保存含 subjectType=ROLE/subjectId、resourceType=DATASET/resourceId、action=EXPORT、effect=ALLOW、validFrom/validTo 的策略后可按 (subjectId, resourceId, action) 查回。
- 断言唯一/查询契约：同 (subjectType, subjectId, resourceType, resourceId, action) 不重复落库；提供 `findEffective(subjectIds, resourceType, resourceId, action, now)` 仅返回生效期内记录。
- 断言 Liquibase changelog 在测试库可顺序执行（master 引入后无校验失败），新表索引存在。

## 技术设计（GREEN）

- 新增实体 `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/iam/IamAssetActionPolicy.java`，继承 `AbstractAuditingEntity<UUID>`，字段：`subjectType`、`subjectId`、`subjectName`、`resourceType`(CATALOG/TABLE/DATASET)、`resourceId`、`resourceName`、`action`（存 `AssetAction.code`）、`effect`(ALLOW/DENY)、`source`、`validFrom`、`validTo`，参照 `domain/iam/IamDatasetPolicy.java` 字段风格。
- 新增 `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/iam/IamAssetActionPolicyRepository.java`，参照 `repository/iam/IamDatasetPolicyRepository.java`，含 `findEffective(...)` 查询。
- 新增 changelog `source/dts-platform/src/main/resources/config/liquibase/changelog/20260601_01_iam_asset_action_policy.xml`（建表 `iam_asset_action_policy` + 复合索引 (subject_id, resource_id, action)），在 `config/liquibase/master.xml` 末尾 include，命名沿用现有 `20260518_03_asset_permission_policy_injection.xml` 约定。
- `action` 列值由 T01 `AssetAction` 约束；effect 默认 DENY 语义由 T03 校验层兜底，表层不放行。

## 影响范围

- 新增：`source/dts-platform/.../domain/iam/IamAssetActionPolicy.java`、`repository/iam/IamAssetActionPolicyRepository.java`
- 新增：`source/dts-platform/src/main/resources/config/liquibase/changelog/20260601_01_iam_asset_action_policy.xml`
- 改既有（需 `gitnexus_impact`）：`source/dts-platform/src/main/resources/config/liquibase/master.xml`（新增 include）

## 验证

- [ ] 实体 7 维字段（subject/resource/action/effect/生效期）完整，复用 AbstractAuditingEntity 审计列。
- [ ] changelog 在 Testcontainers 库可执行，复合索引创建成功。
- [ ] `findEffective` 仅返回 validFrom≤now≤validTo 的记录。

## 完成标准

- [ ] 矩阵实体与表落地，供 T03 `canPerform` 查询装配。
