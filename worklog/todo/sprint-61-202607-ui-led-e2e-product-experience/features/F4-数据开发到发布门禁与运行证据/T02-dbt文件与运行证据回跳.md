# T02: dbt 文件与运行证据回跳

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

dbt 文件浏览、SQL 建模和运维实例之间形成可回跳链路。

## 技术设计

- `DbtFileBrowserPage.tsx` 增加“返回 SQL 建模发布门禁”入口。
- `SqlModelingPage.tsx` 的运行结果链接到 `/ops/instances?journey=e2e-data-product&modelId=...`。
- `OpsInstancesPage.tsx` 能显示来自模型的上下文。

## 影响范围

- `DbtFileBrowserPage.tsx`
- `SqlModelingPage.tsx`
- `OpsInstancesPage.tsx`

## 验证

- [ ] source-contract 断言 dbt 文件页回跳 SQL 建模。
- [ ] 浏览器 smoke 覆盖 SQL -> Ops -> SQL 回跳。

## 完成标准

- [ ] 开发人员能从模型运行结果直接查看任务实例和日志。
