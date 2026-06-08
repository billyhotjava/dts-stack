# F3: 操作权限矩阵

**优先级**: P0
**状态**: READY

## 目标

闭合协议 2.3.2.5（数据安全）操作权限硬要求：把【新增/删除/修改/复制/导入/导出/归档/销毁】8 个动作纳入"资产/库表 × 角色 × 动作"集中授权模型。当前运行时动作集被硬编码为 `read/write/export`，无法覆盖协议列举的合规动作，是机密级测评的阻断项，本期建模 + 切面接入 + 矩阵 UI 一次补齐。

## 协议依据与缺口

- 协议条款：2.3.2.5（数据安全）操作权限集中配置（M05 §1.3）。
- 当前缺口（带证据，引自 `assets/gap-evidence/M05-数据安全.md` §1.3）：
  - `dts-admin/.../web/rest/platform/PlatformDirectoryResource.java:329` 将动作集硬编码为 `List.of("read", "write", "export")`（同文件 51/58/65/72 行重复）。
  - `dts-admin/.../domain/AdminRoleAssignment.java:35` 注释即 `// comma-separated read/write/export`，无更细动作维度。
  - `dts-platform/.../domain/catalog/CatalogDatasetAccessRequest.java:61-65` 仅有 `canQuery`/`canPreview` 两个布尔动作位。
  - `dts-platform/.../service/security/AccessChecker.java:46` 仅 `canRead(CatalogDataset)`，无动作维度校验。
  - 既有 `IamDatasetPolicy`（OBJECT/ROW/FIELD 粒度）无 action 维度；全仓无 `AssetAction`/`PermissionMatrix` 集中配置实体。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | AssetAction 动作枚举（8 动作）+ 审计 OperationType 语义对齐 | P0 | READY | — |
| T02 | IamAssetActionPolicy 领域模型 + Liquibase changelog | P0 | READY | T01 |
| T03 | AccessChecker.canPerform 实现 + 写动作入口接入（默认拒绝） | P0 | READY | T02 |
| T04 | REST 资源 + 前端动作矩阵勾选 UI（沿用审批流落库） | P0 | READY | T03 |
| T05 | 集成测试：8 动作 / 越权拦截 / 矩阵生效 / 生效期 / 不冲突 | P0 | READY | T04 |

## 完成标准

- [ ] `AssetAction` 完整枚举 CREATE/DELETE/UPDATE/COPY/IMPORT/EXPORT/ARCHIVE/DESTROY，并与审计 `AuditOperationType` 语义对齐，不再造动作源。
- [ ] `IamAssetActionPolicy` 支持 subject(role/dept/user) × resource(catalog/table/dataset) × action × effect × validFrom/validTo，含 Liquibase changelog 与索引。
- [ ] `AccessChecker.canPerform(resource, action)` 实现且默认拒绝；导入/导出/删除/归档/销毁等写动作入口全部接入校验。
- [ ] 前端在角色详情/资产授权页提供动作矩阵勾选，变更经审批流落库（沿用 sprint-33 审批模式）。
- [ ] 集成测试覆盖 8 动作全量、越权动作被拦截、矩阵配置生效、生效期边界，且与既有 RLS/FIELD 策略不冲突。

## TDD 约定

- 每个 task RED→GREEN→REFACTOR，测试先行；覆盖率 ≥80%，鉴权/口令/会话路径要求分支覆盖。
- 改既有 symbol（`AccessChecker`、`PolicyService`、各 `*Resource` 写入口）前先 `gitnexus_impact`，提交前 `gitnexus_detect_changes()`；Java 侧禁用 `Optional.get()`，统一 `orElseThrow()`。
