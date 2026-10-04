# P0-03 治理状态枚举统一

`status`: `done`
`priority`: `P0`

## 目标

统一前后端状态字典，解决 `ARCHIVED`/`DEPRECATED` 混用。

## 范围

`source/dts-platform-webapp/src/pages/governance/IndicatorsPage.tsx`、`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/IndicatorService.java`、`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/DimensionService.java`。

## 子任务

1. 定义治理状态标准枚举与显示文案。
2. 统一后端写入值与前端选项值。
3. 补充历史数据迁移脚本（Liquibase）。

## 验收标准

- 前端状态筛选与后端存储一致。
- 历史数据查询不受影响。
- 相关 API 文档与页面文案同步更新。

## 完成记录

1. 指标与维度服务统一归档写入为 `ARCHIVED`，并兼容读取历史 `DEPRECATED`。
2. 依赖接口 `statusOptions` 统一为 `DRAFT/PUBLISHED/ARCHIVED`。
3. 前端指标页面归档选项统一为 `ARCHIVED`。
4. 新增 Liquibase 数据迁移：`20260222_01_governance_status_archived_unification.xml`，将历史 `DEPRECATED` 归一化为 `ARCHIVED`。

## 风险与回滚

- 风险：历史数据状态值导致筛选丢失。
- 回滚：兼容映射层保留一版。
