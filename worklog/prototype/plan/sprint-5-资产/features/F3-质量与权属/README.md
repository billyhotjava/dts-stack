# F3: 质量与权属

**优先级**: P1
**状态**: READY

## 目标

落地资产质量（质量看板/报告/规则）与权属（资产权属/授权/我的授权），让样例 `ODS.宽表` 有可见的质量规则与报告、可标注的权属与授权记录，补齐阶段③资产治理闭环。纯前端 mock。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| [T01](./T01-质量报告与规则.md) | 质量报告 + 质量规则（Quality/QualityReport/QualityRules） | P1 | READY | S1, F1-T02 |
| [T02](./T02-资产权属与授权.md) | 资产权属/授权/我的授权（AssetOwnership/AssetGrant/MyGrants） | P1 | READY | S1, F1-T02 |

## 完成标准

- [ ] `QualityPage`（看板）/ `QualityReportPage`（报告）/ `QualityRulesPage`（规则）可路由可访问并接 mock；列表用 CompactTable 默认 10 条/页。
- [ ] 样例 `ODS.宽表` 至少挂 1 条质量规则并有 1 份质量报告（通过/失败两态可呈现）。
- [ ] `AssetOwnershipPage`（权属）/ `AssetGrantPage`（授权）/ `MyGrantsPage`（我的授权）可路由可访问并接 mock。
- [ ] 样例 `ODS.宽表` 有负责人/权属记录与至少 1 条授权。
- [ ] 全部接 mock service，`VITE_USE_MOCK` 开关生效。
