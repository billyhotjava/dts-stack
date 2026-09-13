# T02: 受控派生指标 DSL 构造器（前端）

**优先级**: P1
**状态**: READY
**依赖**: T01

## 目标
把 dts-metrics 的受控派生指标构造器（白名单函数表单生成表达式，而非让用户写裸 SQL）移植/复用进平台治理建模指标编辑，前端即生成合法受控公式，减少后端 422 往返。

## 技术设计
- 参照 dts-metrics `SemanticDesignerPage.buildDerivedExpression`（白名单 sum/count/count_distinct/avg/min/max/ratio/date_trunc/count_if/sum_if/case_when → 结构化表单 → 表达式/formula）。
- 平台落点：`SemanticMetricsPage` 指标公式编辑（受控模型显示构造器，非裸 SQL 输入）。
- 产出与平台 `formulaType`/`formulaJson` 对齐的结构（与后端 `ControlledMetricDslCompiler` 同词汇），使前端构造的公式后端受控编译必过。
- 与 F1-T02 联动：构造器是"预防"，F1-T02 的 422 提示是"兜底"。
- permissive 模型保留原始表达式入口（绞杀者）。

## 影响范围
- `dts-platform-webapp`：`SemanticMetricsPage` + 受控构造器组件 + 类型。

## 验证
- [ ] 受控模型用构造器生成的公式，后端受控编译通过（无 422）。
- [ ] 覆盖白名单全函数；permissive 仍可裸表达式。

## 完成标准
- [ ] 构造器落地、formula 与后端词汇对齐、与 F1-T02 联动、构建通过。
