# T03: DSL/SQL 安全与注入防护

**优先级**: P0
**状态**: DONE
**依赖**: F3,F4

## 目标

禁止默认任意 SQL，确保指标公式、过滤条件和生成 SQL 都来自受控 DSL 和 platform schema contract。

## 技术设计

- DSL 支持 sum、count、count_distinct、avg、min、max、count_if、sum_if、ratio、case_when、date_trunc。
- 字段引用必须来自 visual asset schema contract。
- 过滤条件只允许白名单操作符和值类型。
- ratio 分母为 0 返回 null 或显式诊断，不能静默给 0。
- SQL 生成器使用 identifier quoting 和方言适配，不拼接用户输入为 SQL 片段。

## 当前进展

- 前端派生指标已从自由 `textarea` 改为受控 DSL 构造器，支持 `sum`、`count`、`count_distinct`、`avg`、`count_if`、`sum_if`、`ratio`、`case_when`、`date_trunc`，表达式只读展示。
- 后端 `MetricGraphDraftService` 在 graph preflight 中拒绝 raw SQL、SQL 注入片段和未登记字段引用，并允许已登记字段上的条件 DSL。
- `MetricModelLifecycleService` 将 DSL 编译为带 identifier quoting 的 SQL；ratio 使用 `nullif(sum(denominator), 0)`，分母为 0 时返回 null。

## 影响范围

- `source/dts-metrics` formula parser / SQL generator
- `source/dts-metrics-webapp` formula editor
- `source/dts-metrics/src/test/**`

## 验证

- [x] raw SQL 字段被拒绝。
- [x] 未登记字段引用被拒绝。
- [x] SQL injection 字符串不能进入生成 SQL。
- [x] `count_if` / `sum_if` / `case_when` 操作和 ratio 零分母诊断补齐。
- [x] 方言级 SQL generator quoting 证据补齐。

## 完成标准

- [x] 合作方或普通用户不能绕过 DSL 直接注入 SQL。
