# T02: Schema inference 与类型映射

**优先级**: P0
**状态**: DRAFT
**依赖**: T01

## 目标

从 preview records 推断字段类型，并映射到 ODS 可落地的数据类型。

## 范围

- 推断 string、integer、decimal、boolean、date、timestamp、json。
- 处理 null、空字符串、混合类型、数组、对象。
- 生成置信度、样本值、冲突说明。
- 允许用户覆盖类型、列名、nullable 和敏感标记。

## 完成标准

- [ ] 类型推断结果可解释。
- [ ] 混合类型默认降级为 string/json 并提示。
- [ ] ODS 列名经过统一规范化。
- [ ] 用户覆盖项持久化到 mapping version。

