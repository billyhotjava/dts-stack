# Sprint-31A F3/T05 血缘失败和发布阻断报告

## 目标

把血缘缺失和治理阻断从旁路日志提升为可查询、可审计、可被发布门禁引用的报告。该报告不重新定义治理规则，而是复用 F2/T04 的资产治理缺口判定，避免资产门户、发布门禁和增值服务出现不同口径。

## 当前落地

新增端点：

```text
GET /api/catalog/assets-v2/lineage-failures
```

支持沿用资产列表筛选参数：

- `keyword`
- `service`
- `type`
- `database`
- `schema`
- `syncStatus`
- `classification`
- `warehouseLayer`
- `ownerDept`
- `governanceStatus`
- `matchStatus`
- `domainId`
- `domainUnassigned`
- `page`
- `size`

## 判定规则

| 类型 | 等级 | 说明 |
|------|------|------|
| `asset-governance-blocking` | `BLOCKING` | 资产存在 owner、classification、warehouseLayer、sourceSystem、domain、lifecycleStatus 或可消费状态阻断 |
| `lineage-missing` | `WARNING` | 资产治理可继续推进，但当前页检查未找到上游/下游血缘证据 |
| `asset-governance-blocking+lineage-missing` | `BLOCKING` | 资产同时存在治理阻断和血缘缺失，发布门禁按阻断处理 |

## 返回契约

| 字段 | 说明 |
|------|------|
| `content` | 存在血缘缺失或治理阻断的资产列表 |
| `severityCounts` | 按 `BLOCKING/WARNING` 汇总 |
| `reasonCounts` | 按报告原因汇总 |
| `inspected` | 本页实际检查资产数 |
| `skipped` | 因可见性或数据漂移跳过的资产数 |
| `totalCandidates` | 列表查询候选资产总数 |
| `sourceReport` | 当前为 `catalog-governance-gaps`，说明来源于统一治理缺口报告 |

## 审计

端点访问写入：

```text
CATALOG_LINEAGE_FAILURE_REPORT_VIEW
```

审计 payload 包含：

- `returned`
- `inspected`
- `skipped`
- `blocking`
- `warning`
- `activeDept`

## 后续依赖

- Sprint-31 发布门禁读取 `BLOCKING` 数量作为阻断条件。
- Sprint-31 发布门禁可把 `WARNING` 中的 `lineage-missing` 作为需确认项。
- F5 资产门户按 `reason` 和 `nextAction` 给出整改入口。
- F6 IT 脚本把该端点纳入最终验收证据。
