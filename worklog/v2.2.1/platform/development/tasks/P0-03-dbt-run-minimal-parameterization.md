# P0-03 dbt 运行最小参数化

- 优先级：P0
- 状态：planned

## 范围

- 将 dbt 运行从固定 `models=all,target=dev` 升级为最小可配置。

## 子任务

- `DbtFileBrowserPage.tsx` 增加运行参数弹窗：selector、target、operation(run/test)。
- `EtlResource.java` 扩展请求结构与参数校验。
- `DbtDagService.java` 透传 selector/target，并记录审计字段。
- 运行记录页展示本次 selector/target。

## 验收标准

- 用户可在 UI 指定 selector 与 target。
- 触发后 Airflow conf 可见传入参数。
- run/test 至少两类命令可执行。

## 风险与回滚

- 风险：参数放开导致误跑全量。
- 回滚：默认值仍为安全配置，且新增确认提示。
