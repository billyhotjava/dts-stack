# T01: 现有资产表和调用面盘点

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标

盘点 `dts-platform` 当前资产相关表、服务、API、前端页面和消费方，明确哪些属于事实源，哪些只是缓存、兼容或展示。

## 技术设计

- 梳理 Catalog、OpenMetadata cache、dbt 同步、OpenLineage、Addax writeback、asset_grant、QueryDataset、Screen/Report 相关资产路径。
- 输出资产类型清单和调用关系。
- 标注重复事实源和历史 fallback。

## 影响范围

- `source/dts-platform`
- `source/dts-platform-webapp`
- `source/dts-analytics`
- `worklog/v2.2.3/sprint-31a-202605/assets`

## 验证

- [x] 产出资产表和 API 盘点文档：`worklog/v2.2.3/sprint-31a-202605/assets/asset-surface-inventory.md`。
- [x] 每个资产来源都有事实源定位和 fallback 风险标记。

## 完成标准

- [x] 后续任务能基于盘点决定迁移和兼容策略。
