# BE-002

## 标题

在建模与 dbt 执行入口统一接入 workspace bootstrap，自愈空工作区。

## 范围

- `ModelingSqlModelService`
- `EtlResource`

## 目标

- `create/import/batch-import/compile/test/build/docs` 前自动保证工作区可运行

## 交付

- 入口级 bootstrap 接线
- 对应回归测试

## 验收

- 空目录下首次导入和首次 build 不再报工作区骨架缺失

## 当前进度

- 状态：TODO

## 风险

- 若入口接线不统一，仍可能出现个别按钮绕过 bootstrap
