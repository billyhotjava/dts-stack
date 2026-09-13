# Sprint-97：企业数据资产目录与标签闭环

**时间**：2026-08-21 ～ 2026-09-04  
**状态**：DONE  
**类型**：Architecture / Full-stack / Data Governance / UI Productization  
**Owner / Reviewer**：xiezm  
**目标**：在不复制业务 owner 数据、不新建第二套资产身份的前提下，让数据表、数据模型、指标、分析数据集、看板、数据产品和数据服务进入统一资产目录，并恢复可发现的数据标签入口，贯通筛选、关联、关系查看和质量事实。

## 背景与问题

- 数据标签的分类、标签、关联和权限后端仍然存在，但资产目录只保留了隐藏深链，用户无法发现。
- 当前资产目录主要展示数据表，运行库中模型、指标、分析数据集、看板、数据产品和 API 服务已有真实记录，却不能从同一目录检索。
- `CatalogAssetType + CatalogAssetKey` 已是统一身份，但目录、标签关联和详情导航没有共同使用这条身份链。
- 数据模型是资产的一种来源，不等于全部数据资产；资产目录也不能成为模型、指标或服务的新写 owner。

## 架构决策记录

| 决策 | 选择 | 约束 |
|---|---|---|
| ADR-97-01 统一身份 | 继续使用 `(CatalogAssetType, CatalogAssetKey)` | 不新增并行资产 ID 或标签关系表 |
| ADR-97-02 owner 边界 | 目录是只读聚合；各业务模块继续拥有名称、状态和生命周期 | 目录不得反写模型、指标、BI、产品或 API 主数据 |
| ADR-97-03 资产范围 | DATASET、SEMANTIC_MODEL、GOV_INDICATOR、BI_DATASET、SCREEN、DATA_PRODUCT、API_SERVICE | 本 Sprint 不纳入治理字典、任务实例或临时执行记录 |
| ADR-97-04 标签入口 | 在“数据资产目录”页内提供“资产目录 / 数据标签”显式页签 | 不新增重复的全局菜单项；保留旧 `?tab=catalog-tags` 深链 |
| ADR-97-05 标签与密级 | 业务数据标签和合规密级分区展示、独立写入 | 标签不能替代密级或权限判定 |
| ADR-97-06 目录查询 | 扩展 `GET /api/catalog/assets-v2` 的可选 `assetFamily` 参数 | 未传参数时保持 DATASET 旧行为；`ALL` 才启用统一聚合 |
| ADR-97-07 关系事实 | 使用现有物化、指标来源、BI 数据集、看板和服务绑定事实生成只读关系 | 不把执行记录或候选版本登记为新资产 |
| ADR-97-08 质量口径 | 只展示 owner 已有的验证/质量事实；缺证据返回 UNKNOWN | 不从发布、同步或物化成功推断质量通过 |
| ADR-97-09 验收节奏 | 全部 Feature 完成后只执行一次集中 E2E | 开发中只跑单元、契约和模块构建 |

## 端到端契约链

| 层 | 契约 | 责任 |
|---|---|---|
| 菜单 | 数据治理 → 数据资产 → 数据资产目录 | 唯一全局入口，不增加“数据标签”菜单 |
| UI | `DataSearchPage` | 显式页签、资产家族筛选、统一表格、详情/关系抽屉 |
| 标签 UI | `AssetTagsWorkspace` + `GovernedAssetTagPanel` | 标签字典、资产关联、单资产增删标签 |
| API | `GET /api/catalog/assets-v2?assetFamily=ALL` | 兼容旧 DATASET 查询并返回统一目录页 |
| 聚合服务 | `CatalogUnifiedAssetDirectoryService` | 有界加载、权限过滤、稳定排序、标签和关系批量水合 |
| 身份/权限 | `CatalogAssetType`、`CatalogAssetKey`、现有 tag guard | 唯一身份和唯一写权限链 |
| owner 数据 | catalog/modeling/governance/BI/product/service 表 | 各模块继续拥有资产事实 |

## 真实数据基线（2026-08-21）

| 资产家族 | 运行库记录 |
|---|---:|
| 数据表 | 367 |
| 数据模型 | 45 |
| 指标 | 77 |
| 分析数据集 | 25 |
| 看板 | 2 |
| 数据产品 | 0 |
| API 服务 | 1 |

运行库当前没有数据产品 owner 记录，目录必须如实返回空结果，不能补造演示资产。标签分类、标签和资产标签关系当前均为 0；内置标签包及显式安装能力仍在。

## Feature 列表

| ID | Feature | 状态 |
|---|---|---|
| F0 | 交付基线与领域契约 | DONE |
| F1 | 数据标签可发现入口 | DONE |
| F2 | 多类型统一资产目录 | DONE |
| F3 | 资产关系与质量事实 | DONE |
| F4 | 统一详情与标签联动 | DONE |
| F5 | 集中验证、发布安全与运维 | DONE |

**执行顺序**：F0 → F1 → F2 → F3 → F4 → F5；F5 才允许执行 E2E。

## Gate Registry

| Gate | 状态 | 证据 |
|---|---|---|
| G0 运行与登录路径 | PASS | `it/baseline.md`；F5 已使用 xiezm 完成真实菜单登录 |
| G0 领域与数据画像 | PASS | `assets/domain-profile.md` |
| G1 契约与 UI 规格 | PASS | 本文、各 Feature README |
| G1 非功能预算 | PASS | `assets/nfr-budget.md` |
| G2 测试先行 | PASS | 各 Task 登记 RED/GREEN；后端 20 条、前端 46 条聚焦测试通过 |
| G3 发布与回滚 | PASS | `assets/release-plan.md`；已实演旧镜像回滚与新镜像恢复 |
| G4 运维与集中 E2E | PASS_WITH_ENV_NOTE | `assets/runbook.md`、`it/README.md`、`it/evidence/README.md`；真实浏览器为本机 Chrome 150，Chrome 95 以源码约束和生产构建验证 |

## 完成标准

- [x] 数据资产目录内可显式切换“资产目录 / 数据标签”，刷新及旧深链状态稳定。
- [x] 统一表格支持七类资产家族，owner 无记录时如实为空，默认旧 API 行为不变。
- [x] 业务标签可跨资产类型筛选、批量关联和单资产维护，密级仍独立展示。
- [x] 关系仅来自真实绑定；质量缺证据时明确显示“暂无证据”。
- [x] 部门角色继续受部门/密级约束；所级数据管理员沿用现有全局治理权限。
- [x] 全部 Feature 完成后完成一次真实 Chrome 集中 E2E，控制台和关键网络请求无异常。

## 非目标

- 不实现 Lakehouse、Iceberg/Hudi/Delta、快照版本或相关 UI/数据库字段。
- 不引入多租户目录、搜索引擎或新的资产主数据表。
- 不把数据模型等同于数据资产全集，也不把目录升级为业务对象写 owner。
- 不重构数据标准、主数据或 OpenMetadata 同步任务。
