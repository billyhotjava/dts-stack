# T03: F3/T04 字段血缘 backfill 策略

**优先级**: P1
**状态**: READY
**依赖**: 无

## 目标

为 Sprint-31A F3/T04 "字段级血缘和时间窗口" 任务补一份明确的 backfill 策略：历史血缘要不要 backfill、按行版本还是事件流、回填到什么时间点。

## 背景

Sprint-31A F3/T04 已标 DONE，但任务描述里只有契约，没明确：
- 历史血缘是否需要 backfill？
- 时间窗口的存储是按行版本还是按事件流？
- 字段血缘第一次上线时如何 seed？

Sprint-32 的 metric artifact 已经开始想依赖字段血缘做 impact analysis（参见 RX X4），新 metric 上线时没有历史窗口可查，会得出错误结论。

## 技术设计

1. 决策表：

   | 资产类型 | backfill 策略 | 数据源 |
   |---|---|---|
   | dbt model | 一次性回填最近 90 天 manifest | `dbt artifacts` |
   | OpenLineage event | 不回填，从今天开始 | live event stream |
   | Addax run | 回填最近 30 天 | Airflow run log |
   | 手工声明血缘 | 不回填，按现有数据 | platform DB |

2. 写 `assets/column-lineage-backfill-strategy.md`，包含：
   - 决策表（同上）
   - 时间窗口 schema（events vs snapshots）
   - dry-run 脚本入口 `GET /api/internal/v1/lineage/backfill/dry-run?type=DBT_MODEL&since=...`
   - rollback 策略
3. 增加 backfill 任务的执行 owner、审批流程、回滚预案。
4. 在 Sprint-31A F3/T04 文档末尾追加 backfill 链接，关闭口径。

## 影响范围

- 新增 `worklog/v2.2.3/sprint-31b-202605/assets/column-lineage-backfill-strategy.md`
- `worklog/v2.2.3/sprint-31a-202605/features/F3-lineage-provenance/T04-column-lineage-time-window.md`（追加链接）
- 新增 dry-run endpoint scaffold（不实际跑回填）：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/internal/LineageBackfillResource.java`

## 验证

- [ ] backfill strategy 文档 review
- [ ] dry-run endpoint 返回结构化预估（不修改数据）

## 完成标准

- [ ] backfill 策略文档定稿。
- [ ] dry-run endpoint 占位可调用。
- [ ] Sprint-31A F3/T04 文档闭环。
