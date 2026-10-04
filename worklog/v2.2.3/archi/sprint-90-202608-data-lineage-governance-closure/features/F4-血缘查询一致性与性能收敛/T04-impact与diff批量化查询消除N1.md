# T04: impact 与 diff 批量化查询消除 N+1

**优先级**: P1
**状态**: DRAFT（阻塞于 G0 的 Q1——无真实规模基线则无法判断"优化到什么程度算够"）
**依赖**: F0/T01（性能基线）；F1/T01（字段血缘查询已改，避免两次改同一方法）

## 目标

把 `impact` 单次请求的 SQL 条数从 `O(节点数 × depth)` 降到 `≤ depth + 3`，使 depth=5 的影响分析满足 `assets/nfr-budget.md` 的 P95 ≤ 4s 预算，接口契约完全不变。

## 技术设计 (Contract-first)

### 缺陷定位（账本#18）

`CatalogLineageResource.java`：

| 位置 | 问题 | 量级 |
|---|---|---|
| `:181` BFS 内层 | 每个 frontier 节点单独 `lineageRepo.findByEitherSideAt(current, snapshotAt)` | `O(节点数)` 次查询，共 `depth` 轮 |
| `:216` 节点物化 | `visited` 中每个 id 单独 `datasetRepo.findById(id)` | `O(节点数)` 次查询 |
| `edgeMapAt:430-487` | diff 跑两次完整 BFS | 上述成本 ×2 |

### 输出契约

**接口响应完全不变**——`nodes`、`edges`、`columnLineages`、`impactStats` 的字段、顺序语义、过滤规则均不得改变。本 Task 是纯内部优化，靠"改造前后响应体逐字段相等"来证明无回归。

### 实现方案

1. **BFS 批量化**：新增

```java
@Query("""
    select l from CatalogDatasetLineage l
    where (l.upstreamDatasetId in :ids or l.downstreamDatasetId in :ids)
      and (l.validFrom is null or l.validFrom <= :at)
      and (l.validTo is null or l.validTo > :at)
    """)
List<CatalogDatasetLineage> findByEitherSideInAt(@Param("ids") Collection<UUID> ids, @Param("at") Instant at);
```

   每层用一次 `IN` 查询替代 `frontier.size()` 次单点查询 → 查询数降为 `depth` 次。
   `IN` 集合上限保护：单批 > 1000 时按 1000 分片（Postgres 参数上限保护），分片数计入 SQL 预算。

2. **节点批量物化**：`visited` 一次 `datasetRepo.findAllById(visited)` 替代逐个 `findById` → 1 次查询。

3. **diff 复用**：`edgeMapAt` 改调同一批量 BFS；两次快照的 BFS 无法合并（不同 `at`），但各自成本已降。

4. **字段血缘**：已是 `IN` 查询（F1/T01 改造后仍是），无需再动。

**总计**：`depth`（BFS）+ 1（节点物化）+ 1（字段血缘）+ 1（根节点校验）= `depth + 3`，与 NFR 预算一致。

### 不做

- **不加缓存**。血缘是治理数据，用户刚登记完就要看到；引入服务端缓存会带来失效复杂度，收益不明确。前端 TanStack Query 的 30s staleTime（F4/T01）已覆盖"切页不重查"的主要场景。
- **不改 BFS 语义**（visited 去重、方向判定、层级过滤顺序均不动）。
- **不加索引**——除非 Q1 基线显示 `upstream_dataset_id` / `downstream_dataset_id` 缺索引。若确缺，另开子任务并走 Expand-only 迁移（`CREATE INDEX CONCURRENTLY`）。

## UI 交互规格

无。用户可感知：depth=5 的查询变快。

## 影响范围

| 文件 | 改动 |
|---|---|
| `dts-platform/.../repository/catalog/CatalogDatasetLineageRepository.java` | 新增 `findByEitherSideInAt`；保留既有 `findByEitherSideAt`（其他调用方仍在用） |
| `dts-platform/.../web/rest/CatalogLineageResource.java` | `impact` BFS 与节点物化批量化；`edgeMapAt` 同步 |
| `dts-platform/src/test/.../web/rest/CatalogLineageResourceImpactTest.java` | 扩展（F1/T01 已建） |

无迁移（除非需补索引）。

## 验证 (RED→GREEN)

- [ ] `impact_responseUnchangedAfterBatching`：**黄金对比**——同一数据集、同一参数，改造前响应 JSON 快照与改造后逐字段相等（含 nodes/edges 顺序）
- [ ] `impact_sqlCountWithinBudget`：以查询计数器断言 SQL 条数 ≤ `depth + 3`
- [ ] `impact_handlesIdBatchOver1000`：造 >1000 节点的图，断言分片正确且结果完整
- [ ] `impact_respectsDepthAndDirection`：BFS 语义未变（既有行为回归）
- [ ] `diff_responseUnchangedAfterBatching`：diff 黄金对比
- [ ] `impact_visibilityFilterStillApplied`：跨部门节点仍被剔除（鉴权零回归——**这是最关键的一条**，批量化最容易漏掉逐节点鉴权）

### 性能验收

- [ ] depth=3 P95 ≤ 1.5s；depth=5 P95 ≤ 4s；diff P95 ≤ 3s（`assets/nfr-budget.md`）
- [ ] 与 F0/T01 记录的基线对比，记录改善倍数

## Definition of Done

- [ ] 架构：6 条契约测试绿，其中黄金对比与鉴权回归为**阻断项**
- [ ] UI：不适用
- [ ] 切片：IT-08 在真实规模数据上达标；未达标则记 GAP 并说明剩余瓶颈
- [ ] `assets/nfr-budget.md` 的「当前」列更新为优化后实测值
- [ ] 无占位证据
