# F3: 操作权限矩阵

**优先级**: P0
**状态**: DONE

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
| T01 | AssetAction 动作枚举（8 动作）+ 审计 OperationType 语义对齐 | P0 | DONE | — |
| T02 | IamAssetActionPolicy 领域模型 + Liquibase changelog | P0 | DONE | T01 |
| T03 | AccessChecker.canPerform 实现 + 写动作入口接入（默认拒绝） | P0 | DONE | T02 |
| T04 | REST 资源 + 前端动作矩阵与审批 UI | P0 | DONE | T03 |
| T05 | 集成验证：8 动作 / 越权拦截 / 生效期 / 数据库约束 | P0 | DONE | T04 |

## 完成标准

- [x] `AssetAction` 完整枚举 CREATE/DELETE/UPDATE/COPY/IMPORT/EXPORT/ARCHIVE/DESTROY，并由 `OperationTypeNormalizer` 对齐中文/英文审计语义。
- [x] `IamAssetActionPolicy` 支持 subject(role/dept/user) × resource(catalog/table/dataset) × action × effect × validFrom/validTo，含 PostgreSQL 约束、索引与真实 migration 测试。
- [x] `AccessChecker.canPerform(resource, action)` 默认拒绝、DENY 优先、超管旁路；数据集 CRUD/导入、Schema 同步、生命周期副本/恢复/归档/回收站/永久销毁、SQL 结果导出均接入。
- [x] 平台数据安全页提供动作矩阵；变更只写 `PENDING` 申请，独立审批后原子写入生效策略，申请人不能自批。
- [x] 90 个后端聚焦测试与前端 source-contract/生产构建通过；既有 `canRead`、RLS/FIELD 读取链未被修改。

## 落地架构

- **唯一真值**：策略、审批请求、运行时判断均在 `dts-platform`；`dts-admin` 只提供角色/组织/用户目录，不复制策略到 `AdminRoleAssignment.operationsCsv`。
- **两层含义**：`IamAssetActionPolicy` 是已批准、可执行的运行时规则；`IamAssetActionPolicyRequest` 是待审批变更事实，二者不可互相替代。
- **资源层级**：同一次判断可匹配 DATASET、TABLE、CATALOG；任一当前主体命中 DENY 即拒绝，否则至少一个 ALLOW 才放行。
- **写入口映射**：数据集新增=CREATE、批量导入=IMPORT、修改/发布状态/下线/Schema 同步=UPDATE、删除/回收站=DELETE、留存副本/恢复=COPY、归档=ARCHIVE、永久销毁=DESTROY、执行结果导出=EXPORT。
- **并发安全**：数据库部分唯一索引保证同一 subject/resource 最多一条 PENDING；审批使用悲观锁并在单事务中应用全部动作差异。

## TDD 约定

- 每个 task RED→GREEN→REFACTOR，测试先行；覆盖率 ≥80%，鉴权/口令/会话路径要求分支覆盖。
- 改既有 symbol（`AccessChecker`、`PolicyService`、各 `*Resource` 写入口）前先 `gitnexus_impact`，提交前 `gitnexus_detect_changes()`；Java 侧禁用 `Optional.get()`，统一 `orElseThrow()`。
