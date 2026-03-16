# BE-006

## 标题

将“重建产出表”改成 `drop relation + dbt build`，替换当前 rollback/full-refresh 链路。

## 范围

- `SqlModelOutputService`
- `EtlResource`

## 目标

- 让重建动作回到 dbt 原生生命周期

## 交付

- rebuild 逻辑
- 与 `manifest/run_results` 同步接线

## 验收

- 重建后会触发当前 selector 的 `dbt build`
- 不再依赖 `/api/rollback/*`

## 当前进度

- 状态：TODO

## 风险

- 若 selector 解析不稳，可能重建范围过大或过小
