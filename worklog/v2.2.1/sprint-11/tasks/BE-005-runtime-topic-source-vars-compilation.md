# BE-005：运行时编译专题 sources/vars

## 目标

在 dbt `compile/test/build` 前，根据当前绑定关系生成运行时 `sources/vars`。

## 范围

- `services/dts-dbt`
- `source/dts-platform`

## 交付

- source 编译逻辑
- vars 编译逻辑
- 缺失绑定检测

## 验收

- 模型只依赖逻辑 source，仍能读到现场真实 ODS 表

## 状态

DONE
