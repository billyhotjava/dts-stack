# F1：统计口径与可见性单一事实源

**优先级**：P0
**状态**：DRAFT

## 目标

修掉 legacy 资产的分页不可达与 `truncated` 的误报，用既有批量路径消除 `overview` 的 N+1，并在聚合器上新增 `byDomain` rollup。可见性规则一行不改，只被复用。

## 契约定义

| 类型 | 契约 | 关键签名 |
|---|---|---|
| 分页组合 | `listAssetsWithoutTagFilter` + `listLegacyAssets` | legacy offset 改按 `max(0, page × size − openMetadataTotal)` 计算，不复用 OM 的 page 索引（L07）|
| 截断判定 | `CatalogAssetPortalService.overview` | `truncated = scanned >= SCAN_CAP`（L08） |
| 扫描上限 | 新常量 `ASSET_STATS_SCAN_CAP = 5000` | 与既有 `MAX_TAG_FILTER_CANDIDATES` 同档；N+1 消除后方可提升 |
| 批量加载 | 复用 `loadCandidateMetadata(List<OpenMetadataAssetCache>)` | `overview` 改走批量，替换逐行 `findFirstByOmAsset` + `findFirstByFqnIgnoreCase`（L09、L10） |
| 域级 rollup | `CatalogAssetOverviewAggregator.AssetOverview.byDomain` | `Map<String, DomainStats>`，由既有 `matrix` 的 domainId 维度归并，**不新增统计口径** |
| 服务出口 | `CatalogAssetPortalService.domainStats(String activeDept)` | 本 Feature 拥有并暴露；F2 只组合，不自行聚合 |
| 失效判定 | `rowStale` 的枚举比较 | `"STALE"` → `DEPRECATED \| ARCHIVED \| BLOCKED`（ADR-75-14） |
| 可见性 | `canRead` / `AccessChecker` | **零改动**（ADR-75-01） |

### 失效判定缺陷（L17）

`CatalogAssetLifecycleStatus` 的合法值为 `DISCOVERED / PENDING_GOVERNANCE / DRAFT_GOVERNANCE / TESTING / ACTIVE / DEPRECATED / ARCHIVED / BLOCKED / PENDING_REVIEW`，**不含 `STALE`，也不含 `DISABLED`**。全仓检索确认 `STALE` 作为 lifecycleStatus 从未被写入，只在三处比较中出现：

- `CatalogAssetOverviewAggregator.java:51`（`rowStale`）
- `assetPortalUx.helpers.ts:37`（判 `DISABLED`，该值属于治理状态而非生命周期）
- `DatasetsPage.tsx:341`（判 `STALE`）

后果：地图页"失效资产"指标**结构性恒为 0**，`attention` 中的 STALE 分支是死代码。截图里的"失效资产 0"不是事实陈述，是判定不可达。

修复后 `attention` 的数值会上升（此前被漏计的失效资产开始计入），这是**修正而非回归**，验收时不得按"数字变了=改坏了"判定。

## 实现约束

- `byDomain` 必须从**已经过 `canRead` 过滤**的行集合派生。禁止新增任何绕过 `canRead` 的计数路径。
- `DomainStats` 复用 `attention` 的既有定义（未定密 ∨ 未归域 ∨ 失效 ∨ 治理状态 `PENDING_*`/`DISABLED`），其中"失效"按 ADR-75-14 取 `DEPRECATED`/`ARCHIVED`/`BLOCKED`；不得在此处引入第二套判定。
- 修复要覆盖"OM 资产与 legacy 资产共存且 OM 超过一整页"的场景。仅有 OM、仅有 legacy、或 OM 不足一页时旧代码恰好正确，这是缺陷长期未被发现的原因。
- `total` 的算术（OM 总数 + legacy 总数）本身正确，**不要改它**；要改的是 legacy 内容的取数位置。
- 批量路径改造后须确认 `hydrateAssetTags` 与 `toSummary` 的行为不变——`overview` 只需要统计字段，但为保口径一致仍走同一 `AssetSummary` 构造。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 修复 legacy 资产的分页不可达 | P0 | DRAFT | F0/T01 |
| T02 | 修正 truncated 判定与警告文案来源 | P0 | DRAFT | T01 |
| T03 | overview 切换批量元数据加载并提升扫描上限 | P0 | DRAFT | T01 |
| T04 | 聚合器新增 byDomain rollup | P0 | DRAFT | T03 |
| T05 | 暴露 domainStats 服务方法 | P0 | DRAFT | T04 |
| T06 | 修正失效判定的不可达枚举 | P0 | DRAFT | F0/T01 |

## Definition of Ready

- [x] 已跑 `gitnexus_impact`（风险 LOW，7 个受影响符号）：`listAssets` → `overview` / `governanceGaps` / `CatalogAssetPortalResource.listAssets`；`governanceGaps` 把 `page.total()` 透传给 `GovernanceGapReport.total`，`lineageFailures` 再经其派生
- [ ] 已确认 `loadCandidateMetadata` 在 5000 条量级的实测耗时可接受

## Definition of Done

- [ ] G-75-01 通过：`byDomain[d].total == listAssets({domainId: d}).total`
- [ ] G-75-02 通过：`truncated` 在 4999/5000/5001 三个边界上判定正确
- [ ] `CatalogAssetOverviewAggregatorTest` 覆盖 byDomain（含 domainId 为 null 的分支）
- [ ] service 层测试覆盖 OM 满页 + legacy 共存时 legacy 行可达，且翻页无重复无遗漏
- [ ] `overview` 的查询次数从 O(n) 降至 O(1) 批次（以测试内查询计数或日志断言）
- [ ] G-75-09 通过：失效判定对 `DEPRECATED`/`ARCHIVED`/`BLOCKED` 三个值均命中，且不再比较不可达的 `STALE`/`DISABLED`
