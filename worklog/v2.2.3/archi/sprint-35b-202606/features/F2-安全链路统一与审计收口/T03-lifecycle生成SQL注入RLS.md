# T03: lifecycle 生成 SQL 注入 RLS/masking（对齐 pack 链路）

**优先级**: P0
**状态**: DONE（RLS WHERE + masking 注入与 pack 对等；masked 列作度量→422 已测）
**依赖**: T02

## 目标

lifecycle 生成的 dbt model SQL 与 pack 链路一样，按 RLS predicate 注入 WHERE、按 maskedColumns 注入 masking 表达式。

## 技术设计

- `MetricModelLifecycleService.dbtModelSql` 复用 T01 抽出的 `appendRlsWhere` 与 masking 辅助。
- masked 列不得作为可聚合 metric 直接输出（复用 pack 链路 `validateMaskedMetricInputs` 校验）。
- 保持现有 SQL 注入防御（函数白名单、标识符 quote）不变。

## 影响范围
- `MetricModelLifecycleService.dbtModelSql` / artifacts。

## 验证
- [ ] 给定带 RLS predicate 的 policy，生成 SQL 含对应 WHERE。
- [ ] masked 列在输出中被 masking 包裹；masked 列做 metric 时报错。

## 完成标准
- [ ] 两条链路生成 SQL 的 RLS/masking 行为对等。
