# P2-01 dbt DevOps 能力包

- 优先级：P2
- 状态：planned

## 范围

- 补齐 compile/test/docs build 与产物状态跟踪。

## 子任务

- 新增 API：`/dbt/compile`、`/dbt/test`、`/dbt/docs`。
- 统一 run artifact 状态与失败摘要（manifest/run_results）。
- 在 `DbtFileBrowserPage` 与建模页展示最近构建状态。

## 验收标准

- 支持一键 compile/test。
- 失败模型/测试可定位到具体节点。
- 产物同步状态可视化。

## 风险与回滚

- 风险：dbt 命令耗时长影响体验。
- 回滚：先提供异步触发 + 后台状态追踪，不阻塞页面。
