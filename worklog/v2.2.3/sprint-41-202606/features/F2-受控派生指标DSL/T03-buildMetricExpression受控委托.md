# T03: buildMetricExpression 受控委托

**优先级**: P0
**状态**: READY
**依赖**: T01, F1-T02

## 目标
受控模式下 `SemanticModelingService.buildMetricExpression` 委托 `ControlledMetricDslCompiler`；PERMISSIVE 保留现有 switch + `safeMetricExpression` 路径不变。

## 技术设计
- 注入 `ControlledMetricDslCompiler`。
- 在 F1-T02 的分流点：`isControlled(model)` → `compiler.compile(metric.formulaType(), metric.formulaJson(), dialect)`；否则原路径。
- dialect 来源：模型/目标方言（确认平台是否有 dialect 字段；若无，先按 postgres，doris 留待与 SP-2 一并确定）。
- 受控编译抛 `IllegalArgumentException` → 在 service/controller 边界映射 422 `unsafe_expression`/`graph_validation_failed`（对齐 dts-metrics 错误码）。

## 影响范围
- `dts-platform`：`SemanticModelingService.buildMetricExpression` + 异常映射；可能涉及 controller 的 422 映射。

## 验证
- [ ] CONTROLLED 模型：派生指标走受控编译，非法表达式 → 422。
- [ ] PERMISSIVE 模型：generate-artifacts SQL 与改造前字节一致（既有测试守护）。
- [ ] 与 dts-metrics 黄金 SQL 语义比对一致（IT）。

## 完成标准
- [ ] 委托接通、错误码对齐、PERMISSIVE 不破、`clean test` 全绿。
