# T04: REST 资源 + 前端动作矩阵勾选 UI

**优先级**: P0
**状态**: READY
**依赖**: T03

## 目标

提供 `IamAssetActionPolicy` 的查询/编辑 REST，并在前端角色详情/资产授权页提供"资产 × 动作"矩阵勾选；变更沿用 sprint-33 审批流落库，替换硬编码 `read/write/export`。

## TDD 测试先行（RED）

- 后端 `AssetActionPolicyResourceTest`（MockMvc），放 `dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/`：断言 `GET` 返回按 subject/resource 聚合的 8 动作矩阵；`POST/PUT` 仅 INSTITUTE_PRIVILEGED 可写、非授权角色 403；提交进入审批流（PENDING）而非直接生效。
- 前端 source-contract 测试，沿用 `dts-admin-webapp/src/admin/views/role-detail.assignment-table.source-contract.test.ts` 模式新增 `role-detail.action-matrix.source-contract.test.ts`：断言矩阵组件渲染 8 列动作、勾选差异（adds/removes）随审批提交、不再引用 `read/write/export` 三动作常量。

## 技术设计（GREEN）

- 新增 REST `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/AssetActionPolicyResource.java`，参照既有 `web/rest/SecurityPolicyResource.java` 与 `web/rest/CatalogDatasetAccessApprovalResource.java`（请求/审批/批量决策）的鉴权 + 审计风格；写操作进审批流，不直接落生效策略。
- 前端：
  - 资产授权页扩展 `dts-platform-webapp/src/pages/security/data-security.tsx` 与 `DatasetAccessApprovalPage.tsx`，新增"操作权限矩阵" Tab（资产/库表 × 8 动作勾选）。
  - 角色侧扩展 `dts-admin-webapp/src/admin/views/role-detail.tsx`（沿用现有 memberAdds/memberRemoves 差异 + 审批模式，增加 actionAdds/actionRemoves），`role-management.tsx` 列表透出矩阵入口。
- 收口硬编码：`dts-admin/.../web/rest/platform/PlatformDirectoryResource.java`（51/58/65/72/329 行的 `List.of("read","write","export")`）改为读取矩阵动作集，`AdminRoleAssignment.operationsCsv` 语义升级为 `AssetAction` 动作集（需 `gitnexus_impact`）。

## 影响范围

- 新增：`source/dts-platform/.../web/rest/AssetActionPolicyResource.java`
- 改前端：`source/dts-platform-webapp/src/pages/security/data-security.tsx`、`source/dts-platform-webapp/src/pages/security/DatasetAccessApprovalPage.tsx`、`source/dts-admin-webapp/src/admin/views/role-detail.tsx`、`source/dts-admin-webapp/src/admin/views/role-management.tsx`
- 改既有（需 `gitnexus_impact`）：`source/dts-admin/.../web/rest/platform/PlatformDirectoryResource.java`、`source/dts-admin/.../domain/AdminRoleAssignment.java`

## 验证

- [ ] REST 矩阵读写按 8 动作聚合，写动作进审批流，非授权角色 403。
- [ ] 前端矩阵勾选以 adds/removes 差异提交并落审批，不再出现 read/write/export 三动作常量。
- [ ] `PlatformDirectoryResource` 不再硬编码动作集。

## 完成标准

- [ ] 角色/资产授权页可视化配置 8 动作矩阵并经审批生效。
