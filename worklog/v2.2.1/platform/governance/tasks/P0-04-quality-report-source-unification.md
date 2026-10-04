# P0-04 质量报告数据源统一（治理事实优先）

`status`: `done`
`priority`: `P0`

## 目标

让质量报告优先使用治理运行事实（`gov_quality_run`），避免与执行结果割裂。

## 范围

`source/dts-platform-webapp/src/pages/catalog/QualityPage.tsx`、`source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/CatalogResource.java`、治理运行查询服务。

## 子任务

1. 新增治理质量运行聚合接口。
2. 质量报告页面接入治理聚合，并保留 OpenMetadata 兜底。
3. 页面标注数据来源（governance/openmetadata）。

## 验收标准

- 执行质量规则后，报告页可实时看到对应运行结果。
- 无治理运行数据时自动回退至 OpenMetadata。
- 同一数据集两种来源的统计口径一致。

## 完成记录

1. 质量报告页优先读取治理运行记录：`GET /api/governance/quality/runs?datasetId=...`。
2. 无治理运行记录时自动回退到 `GET /api/catalog/datasets/{id}/quality`（OpenMetadata）。
3. 页面新增数据来源标注（`governance` / `openmetadata`）。
4. 治理运行 `metrics` 字段映射已按后端 DTO 对齐，避免使用不存在字段。

## 风险与回滚

- 风险：双来源口径差异引发误判。
- 回滚：增加来源切换开关，默认保持旧逻辑。
