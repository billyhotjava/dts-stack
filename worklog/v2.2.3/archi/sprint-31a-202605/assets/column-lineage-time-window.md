# Sprint-31A F3/T04 字段级血缘和时间窗口

## 目标

字段级血缘不再只是“当前静态关系”，而是具备 `validFrom/validTo/lastObservedAt` 时间证据，支持后续影响分析和历史解释。

## 当前落地

- `catalog_column_lineage` 增加 `valid_from`、`valid_to`。
- 历史数据回填 `valid_from = coalesce(created_date, last_observed_at, now())`。
- dbt 字段血缘写入时：
  - 首次出现写入 `validFrom`。
  - 每次观察更新 `lastObservedAt`。
  - 关系仍存在时清空 `validTo`。
  - 关系消失时写入 `validTo`，不再物理删除。
- 血缘 API 字段级 DTO 返回 `validFrom/validTo`。

## 边界

当前唯一约束仍按字段对唯一，因此同一字段关系重复出现时复用同一行并重新打开窗口；完整多段历史快照留到后续专门的历史表设计。

## 后续依赖

- F3/T05 血缘报告可识别“当前缺失但历史存在”的字段关系。
- 资产详情和血缘图可解释当前关系与历史关闭时间。
