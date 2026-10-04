# T05: 血缘失败告警和阻断报告

**优先级**: P0
**状态**: DONE
**依赖**: T01-T04

## 目标

把血缘解析失败、资产解析失败和治理缺口从 best-effort 日志提升为可观测报告。

## 技术设计

- 新增 `/api/catalog/assets-v2/lineage-failures`，复用治理缺口报告生成血缘失败和发布阻断报告。
- 记录失败原因、来源系统、相关资产、是否阻断、建议动作。
- Sprint-31 发布门禁可引用 `BLOCKING` / `WARNING` 统计，血缘缺失默认为 warning，治理阻断优先级更高。

## 影响范围

- Platform event / audit
- Catalog governance report
- Release governance page

## 验证

- [x] 失败报告有独立审计事件 `CATALOG_LINEAGE_FAILURE_REPORT_VIEW`。
- [x] 报告可定位到具体资产，并返回 `assetKey`、授权资产类型和授权资产 ID。
- [x] 单测覆盖血缘缺失 warning、治理阻断 blocking 和 ready 资产过滤。

## 完成标准

- [x] 血缘不再是不可验证的旁路能力。

## 交付物

- `CatalogLineageFailureReportBuilder`
- `CatalogLineageFailureReport`
- `CatalogLineageFailureItem`
- `GET /api/catalog/assets-v2/lineage-failures`
- `worklog/v2.2.3/sprint-31a-202605/assets/lineage-failure-report.md`
