# T04: REST 资源 + 前端动作矩阵勾选 UI

**优先级**: P0
**状态**: DONE
**依赖**: T03

## 目标

提供动作矩阵查询、变更申请与独立审批 REST，并在平台数据安全页提供"主体 × 资源 × 动作"矩阵；所有变更先进入 PENDING，审批批准后才写入生效策略。

## TDD 测试先行（RED）

- 后端 `AssetActionPolicyResourceTest`（MockMvc），放 `dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/`：断言 `GET` 返回按 subject/resource 聚合的 8 动作矩阵；`POST/PUT` 仅 INSTITUTE_PRIVILEGED 可写、非授权角色 403；提交进入审批流（PENDING）而非直接生效。
- 前端 source-contract 测试，沿用 `dts-admin-webapp/src/admin/views/role-detail.assignment-table.source-contract.test.ts` 模式新增 `role-detail.action-matrix.source-contract.test.ts`：断言矩阵组件渲染 8 列动作、勾选差异（adds/removes）随审批提交、不再引用 `read/write/export` 三动作常量。

## 原计划（落地时已校正）

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

- [x] REST 矩阵按 8 动作聚合，写动作进审批流，方法级 authority 已固化。
- [x] 前端只提交发生变化的动作并落审批，不直接写生效策略。
- [x] 平台不再消费 `PlatformDirectoryResource` 的三动作常量作为权限真值。

## 完成标准

- [x] 平台资产授权页可视化配置 8 动作矩阵并经审批生效。

## 落地结果

- REST 路径为 `/api/iam/action-policies`：矩阵查询、待审批列表、提交申请、审批决定；不存在直写 effective policy 的 PUT/DELETE 接口。
- 查询属于 `IAM_MAINTAINERS`；提交与审批属于 `INSTITUTE_PRIVILEGED_ROLES`；服务层强制申请人不能审批本人申请。
- 前端新增 `AssetActionMatrixPanel` 并接入 `/security/data-security?tab=actionMatrix`，支持角色/部门/用户与数据集/库表/目录选择、ALLOW/DENY/NONE 差异申请、生效期和待审批处理；配置规则与运行时状态分列展示，明确区分未配置、待生效、生效中和已过期。
- `dts-admin` 只作为角色/组织目录来源，不写 `operationsCsv`，避免跨服务双真值。
- `AssetActionPolicyResourceTest` 与前端 `AssetActionMatrixPanel.source-contract.test.ts` 通过；生产前端构建通过。
