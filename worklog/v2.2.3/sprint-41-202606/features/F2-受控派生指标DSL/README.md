# F2: 受控派生指标 DSL（ControlledMetricDslCompiler + 委托）

**优先级**: P0
**状态**: READY
**依赖**: F1

## 目标
把 dts-metrics 的受控派生指标 DSL 移植为平台独立组件 `ControlledMetricDslCompiler`：白名单函数、默认拒绝 raw SQL、三层 SQL 注入防御、方言感知标识符 quote；受控模式下 `buildMetricExpression` 委托它。语义须与 dts-metrics 字节级一致（黄金 SQL 守护）。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | ControlledMetricDslCompiler 组件移植（白名单 + 三层防御 + 方言 quote） | P0 | DONE | F1-T01 |
| T02 | 单测（每函数 postgres+doris、raw 拒、注入拒、quote、默认拒绝） | P0 | DONE | T01 |
| T03 | buildMetricExpression 受控委托；PERMISSIVE 保留现状 | P0 | DONE | T01, F1-T02 |

## 完成标准
- [ ] 受控模式仅允许 sum/count/count_distinct/avg/min/max/ratio/date_trunc/count_if/sum_if/case_when；其余 → 422。
- [ ] raw/custom SQL 表达式在受控模式被拒（与 PERMISSIVE 的 `safeMetricExpression` 放行形成对比）。
- [ ] 标识符按方言 quote（postgres `"` / doris `` ` ``）；危险串（`;`/注释/DDL/DML 关键字）被拒。
- [ ] 与 dts-metrics 黄金 SQL 语义一致。
