# F1：统计口径与可见性单一事实源

**优先级**：P0
**状态**：DRAFT

## 目标

修掉 `total` 的重复计数与 `truncated` 的误报，用既有批量路径消除 `overview` 的 N+1，并在聚合器上新增 `byDomain` rollup。可见性规则一行不改，只被复用。

## 契约定义

| 类型 | 契约 | 关键签名 |
|---|---|---|
| 分页总数 | `CatalogAssetPortalService.listAssetsWithoutTagFilter` | `legacyPage.total()` 不再每页无条件并入（L07） |
| 截断判定 | `CatalogAssetPortalService.overview` | `truncated = scanned >= SCAN_CAP`（L08） |
| 扫描上限 | 新常量 `ASSET_STATS_SCAN_CAP = 5000` | 与既有 `MAX_TAG_FILTER_CANDIDATES` 同档；N+1 消除后方可提升 |
| 批量加载 | 复用 `loadCandidateMetadata(List<OpenMetadataAssetCache>)` | `overview` 改走批量，替换逐行 `findFirstByOmAsset` + `findFirstByFqnIgnoreCase`（L09、L10） |
| 域级 rollup | `CatalogAssetOverviewAggregator.AssetOverview.byDomain` | `Map<String, DomainStats>`，由既有 `matrix` 的 domainId 维度归并，**不新增统计口径** |
| 服务出口 | `CatalogAssetPortalService.domainStats(String activeDept)` | 本 Feature 拥有并暴露；F2 只组合，不自行聚合 |
| 可见性 | `canRead` / `AccessChecker` | **零改动**（ADR-75-01） |

## 实现约束

- `byDomain` 必须从**已经过 `canRead` 过滤**的行集合派生。禁止新增任何绕过 `canRead` 的计数路径。
- `DomainStats` 复用 `attention` 的既有定义（未定密 ∨ 未归域 ∨ STALE ∨ `PENDING_*`/`DISABLED`），不得在此处引入第二套判定。
- `total` 修复要覆盖"OM 资产与 legacy 资产共存"的场景。仅有 OM 或仅有 legacy 时旧代码恰好正确，是这个缺陷长期未被发现的原因。
- 批量路径改造后须确认 `hydrateAssetTags` 与 `toSummary` 的行为不变——`overview` 只需要统计字段，但为保口径一致仍走同一 `AssetSummary` 构造。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 修复 listAssets 的 total 重复计数 | P0 | DRAFT | F0/T01 |
| T02 | 修正 truncated 判定与警告文案来源 | P0 | DRAFT | T01 |
| T03 | overview 切换批量元数据加载并提升扫描上限 | P0 | DRAFT | T01 |
| T04 | 聚合器新增 byDomain rollup | P0 | DRAFT | T03 |
| T05 | 暴露 domainStats 服务方法 | P0 | DRAFT | T04 |

## Definition of Ready

- [ ] 已运行 `gitnexus_impact` 于 `listAssetsWithoutTagFilter`、`overview`、`aggregate`，并向用户报告 blast radius
- [ ] 已确认 `listAssetsWithoutTagFilter` 的其他调用方不依赖当前（错误的）`total` 语义
- [ ] 已确认 `loadCandidateMetadata` 在 5000 条量级的实测耗时可接受

## Definition of Done

- [ ] G-75-01 通过：`byDomain[d].total == listAssets({domainId: d}).total`
- [ ] G-75-02 通过：`truncated` 在 4999/5000/5001 三个边界上判定正确
- [ ] `CatalogAssetOverviewAggregatorTest` 覆盖 byDomain（含 domainId 为 null 的分支）
- [ ] service 层测试覆盖 OM + legacy 共存下的 total 不重复计数
- [ ] `overview` 的查询次数从 O(n) 降至 O(1) 批次（以测试内查询计数或日志断言）
