# F1: 血缘事实正确性修复

**优先级**: P0
**状态**: READY（纯后端契约修复，不依赖 G0）

## 目标

让血缘查询返回的事实是对的：字段血缘不再返回已失效关系、快照时间对表级与字段级语义一致；dbt manifest 导入不再因表名撞车而错连，且"跳过了什么、为什么跳过"可被下游 UI 消费。

## 契约定义 (Contracts)

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| Repository | `CatalogColumnLineageRepository.findByDatasetLineageIdInAt(Collection<UUID> ids, Instant at)` | 语义与 `CatalogDatasetLineageRepository.findByEitherSideAt`（账本#8）严格同构：`(valid_from is null or valid_from <= :at) and (valid_to is null or valid_to > :at)` |
| REST（响应扩展） | `GET /api/catalog/lineage/impact` | `columnLineages[]` 元素不变；新增顶层 `columnLineageSnapshotAt`（string, ISO Instant），与既有 `snapshotAt` 同值，用于前端自证时间语义一致 |
| REST（响应扩展） | `POST /api/catalog/lineage/import-dbt-manifest` | 由 `{created,skipped,total,propagationEnqueued}` 扩展为 `{created,skipped,total,propagationEnqueued,skippedReasons:{notModel,malformedNode,unmatchedModel,unmatchedParent},unmatched:[{uniqueId,name,reason}],truncated:boolean}` |
| 数据 | `catalog_column_lineage` | 无 schema 变更；仅查询侧补有效期条件（列名见账本#24） |
| 数据 | `catalog_dataset_lineage` | 无 schema 变更 |

**兼容性**：两处均为**响应体增量扩展**，既有字段名与类型不变，旧前端不受影响。

## UI/UX 规格

本 Feature 无独立 UI 交付；其响应扩展由 F3/T01→F3/T02 消费。唯一的用户可见变化是：

- 字段血缘页在选择「快照时间」后，结果集与表级血缘处于同一时刻——用户看到的行数可能**减少**（此前多算了已失效关系），这是修复而非回归，需在 IT-01 中明确记录前后行数差。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 字段血缘按有效期与快照过滤 | P0 | READY | - |
| T02 | dbt 导入按 schema+表名匹配并返回跳过原因 | P0 | READY | - |

## Definition of Ready

- [x] 契约已钉死（Repository 方法签名、响应体字段名与类型均已写明）
- [x] 竖切片已画通（Repository → Resource → 响应体 → F3 消费）
- [x] UI 落点已命名（本 Feature 无新 UI；下游消费点为 F3/T02）
- [x] 依赖已就绪（账本#7/#8/#19/#24 提供全部前置事实，无需再勘察）
- [x] 验收可验证（每条标准对应一个接口测试）

## 完成标准

- [ ] `impact` 接口首次拥有测试（补账本#21 空白），断言：已失效字段血缘不出现在结果中
- [ ] 断言：同一请求的表级边与字段血缘取自同一 `at`
- [ ] `import-dbt-manifest` 契约测试断言四类 `skippedReasons` 分桶正确、`unmatched` 明细可定位
- [ ] `CatalogDbtLineageServiceTest`（既有）不回归
- [ ] 无 `lineageRepo.findAll()` 全表加载残留
