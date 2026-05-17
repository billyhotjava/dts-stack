# T04: 字段级血缘和时间窗口

**优先级**: P1
**状态**: DONE
**依赖**: T01-T03

## 目标

为重要链路补充字段级血缘和时间窗口，避免血缘图只停留在表级静态关系。

## 技术设计

- 字段级血缘先覆盖 dbt manifest 可解析字段。
- `catalog_column_lineage` 增加 `valid_from / valid_to`，并回填历史 `valid_from`。
- dbt 字段血缘写入 `validFrom / validTo / lastObservedAt`；关系消失时不再物理删除。
- 血缘 API 字段级 DTO 返回 `validFrom / validTo`。

## 影响范围

- CatalogDatasetLineage
- LineagePage
- dbt manifest import
- `source/dts-platform/src/main/resources/config/liquibase/changelog/20260517_01_catalog_column_lineage_time_window.xml`
- `worklog/v2.2.3/sprint-31a-202605/assets/column-lineage-time-window.md`
- Backfill strategy: `worklog/v2.2.3/sprint-31b-202605/assets/column-lineage-backfill-strategy.md`

## 验证

- [x] 字段级关系缺失时不影响表级展示。
- [x] 时间窗口不会物理删除历史关系。
- [x] 历史 backfill 策略和 dry-run scaffold 已转入 Sprint-31B F4/T03。
- [ ] 统一测试在 Sprint-31A/31/32 完成后执行。

## 完成标准

- [x] 为企业级影响分析打基础。
