# Sprint-31A F2/T04 治理缺口 API

## 目标

平台统一识别资产治理缺口，给资产门户、发布门禁和最终 IT 验收脚本使用。前端和增值服务不再各自硬编码“缺字段、缺血缘、缺 schema”的判断。

## 当前落地

新增端点：

```text
GET /api/catalog/assets-v2/governance-gaps
```

支持沿用资产列表筛选参数：

- `keyword`
- `service`
- `type`
- `database`
- `schema`
- `classification`
- `warehouseLayer`
- `ownerDept`
- `governanceStatus`
- `matchStatus`
- `domainId`
- `domainUnassigned`
- `page`
- `size`

## 缺口分级

| 等级 | 规则 | 用途 |
|------|------|------|
| `BLOCKING` | 缺 owner、classification、warehouseLayer、sourceSystem、domain，或生命周期不是 `ACTIVE`，或资产不可消费 | 发布门禁、企业级验收 |
| `WARNING` | 缺 schema contract、缺 lineage、同步状态非 `SYNCED/ACTIVE` | 治理提醒、资产门户提示 |
| `READY` | 无 blocking 和 warning 缺口 | 可消费资产 |

## 返回契约

| 字段 | 说明 |
|------|------|
| `content` | 非 READY 资产列表 |
| `severityCounts` | 按 `READY/WARNING/BLOCKING` 统计 |
| `gapCounts` | 按 `blocking:{field}` / `warning:{field}` 统计 |
| `inspected` | 本页实际检查资产数 |
| `skipped` | 因可见性或数据漂移跳过的资产数 |
| `totalCandidates` | 列表查询候选资产总数 |

## 后续依赖

- Sprint-31 发布门禁读取该 API，阻断治理缺口未补齐的 DWS/ADS/BI Dataset 发布。
- F5 资产门户按该 API 展示治理缺口和整改入口。
- F6 IT 脚本把该 API 作为最终验收证据。
