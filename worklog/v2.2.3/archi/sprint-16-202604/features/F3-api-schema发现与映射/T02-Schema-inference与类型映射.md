# T02: Schema inference 与类型映射

**优先级**: P0
**状态**: DRAFT
**依赖**: T01

## 目标

从 preview records 推断字段类型，形成源端 schema snapshot；字段类型标准化和列名调整只输出到 stg mapping，不直接改变 ODS。

## 范围

- 推断 string、integer、decimal、boolean、date、timestamp、json。
- 处理 null、空字符串、混合类型、数组、对象。
- 生成置信度、样本值、冲突说明。
- 保存字段顺序、JSON path、nullable、样本和推断类型。
- 允许用户覆盖 stg 类型、stg 列名、nullable 和敏感标记。

## 完成标准

- [ ] 类型推断结果可解释。
- [ ] 混合类型默认降级为 string/json 并提示。
- [ ] ODS 不生成业务字段列，仅保存 `_dts_raw_record` 和 `_dts_*` 技术字段。
- [ ] 用户覆盖项持久化到 stg mapping version。
