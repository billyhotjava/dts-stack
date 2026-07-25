# Sprint-71：数据标签体系与资产打标闭环

**时间**: 2026-07
**状态**: READY
**类型**: Data Governance / Catalog Capability / Frontend-first Implementation
**目标**: 把当前的自由文本 `tags` 字段升级为「标签目录 + 预置标签库 + 结构化打标 + 按标签检索」的数据标签管理体系，闭合协议 2.3.2.4 数据管理模块的 P1 缺口。

## 背景

技术协议（`docs/req/dts.pdf` 2.3.2.4「数据管理」）明列**数据标签管理**子能力：标签与标签目录、预置常用标签、支持自定义、对数据资源打标。

2026-06-08《协议 gap 分析 v3》将其判定为 **🔴 真缺口 / P1**，证据见
`worklog/v2.2.3/sprint-36-202606/assets/gap-evidence/M04-数据管理.md` §3：

- 全仓无 `DataTag` / `CatalogTag` / `TagCatalog` / `TagDefinition` 任何 domain 或 service
- 现状仅 `CatalogDataset.tags`（`domain/catalog/CatalogDataset.java:48`，`@Column(length=1024)` 自由文本）
- `CatalogAssetPortalService:465` 仅对该自由文本做 `like` 模糊查询
- 前端无标签管理页；`ui/label.tsx` 是通用 UI 组件，`ClassificationTag.tsx` 是密级标记，均非数据标签
- 无标签目录、无预置标签库、无标签 CRUD、无 tag↔asset 结构化关联

**自由文本 tags 字段 ≠ 协议要求的标签管理体系。** 该缺口自 2026-04 首次识别以来，历经 sprint-32~70 从未被触达。

## 设计决策

以下决策已确定，实施时不再重新讨论：

| 决策 | 选择 | 理由 |
|------|------|------|
| 菜单落点 | **不新增菜单**，标签目录管理挂载到既有「数据资产 → 元数据管理」页（`MetadataManagementPage.tsx`）新增 Tab | 遵循 Sprint-48/49 确立的「以现有页面为第一事实源，默认不新增菜单或页面」治理规则 |
| 打标锚点 | 复用 `CatalogAssetType`（20 类）+ `CatalogAssetKey` 通用资产标识 | 不为 dataset 单造关系表；一套关联表覆盖 dataset/dbt_model/metric/data_product 等全部资产类型 |
| 标签与密级的关系 | **严格分离**，标签不承载密级语义 | `classification` 字段与 `SecurityLevelCatalog` 是独立的合规控制面，标签混入会污染密级判定 |
| 预置标签交付方式 | 走 Sprint-68 标准内容库管道（可版本化、可安全升级） | 避免硬编码 seed 与客户本地自定义标签冲突 |
| 存量 `tags` 字段 | **保留不删**，新增结构化标签并行；F4 提供迁移工具 | 该字段被 `CatalogMetadataService:191/238/253` 透传消费，直接删除会破坏既有契约 |

## Feature 列表

| ID | Feature | Task 数 | 优先级 | 状态 |
|----|---------|---------|--------|------|
| F1 | 标签领域模型与目录 | 3 | P1 | READY |
| F2 | 打标与检索契约 | 4 | P1 | READY |
| F3 | 前端标签管理与打标交互 | 3 | P1 | READY |
| F4 | 存量标签迁移与兼容 | 2 | P2 | READY |

**依赖顺序**: F1 → F2 → F3；F4 依赖 F2 完成后执行。

## 完成标准

- [ ] 标签目录支持分类树 / 分组，标签支持自定义 CRUD，全部动作接入审计
- [ ] 预置常用标签库可安装、可版本化升级，且不覆盖客户本地自定义标签
- [ ] 任意 `CatalogAssetType` 资产可打标 / 取消 / 批量打标，关联关系结构化落库
- [ ] 资产门户与数据搜索支持按标签精确检索（非 `like` 模糊匹配）
- [ ] 前端标签管理 Tab + 资产详情页打标交互 + 按标签筛选三处可见可用
- [ ] 存量 `CatalogDataset.tags` 自由文本可迁移为结构化标签，且迁移可回滚
- [ ] `it/` 下有真实后端 IT 与页面 smoke 证据，非空占位

## 非目标

明确不在本 sprint 范围：

- 元数据审核-发布工作流（M04 另一 P1 缺口，另立 sprint）
- 标签驱动的权限控制 / 行级过滤（属 M05 数据安全域）
- 标签自动推荐 / AI 打标（协议未要求）
