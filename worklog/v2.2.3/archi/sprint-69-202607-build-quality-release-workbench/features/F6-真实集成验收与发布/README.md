# F6：真实集成验收与发布

**优先级**：P0
**状态**：READY

## 目标

用真实 Spring Security、PostgreSQL、dbt target 和 Chrome 95 证明 canonical ModelLifecycle 主线可运行、可失败、可恢复和可回滚，并给出可复核的 Go/No-Go。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 补齐后端契约 API 与安全回归 | P0 | READY | F1-F4 |
| T02 | 建立 PostgreSQL canonical lifecycle 全链路 IT | P0 | READY | T01 |
| T03 | 完成真实 dbt 与 Chrome 95 主旅程 | P0 | READY | F5、T02 |
| T04 | 完成构建回滚与最终 Go/No-Go | P0 | READY | T03 |

## 完成标准

- [ ] 后端单测、资源层、租户隔离、权限、ETag、幂等和错误码回归通过。
- [ ] canonical API 在真实 PostgreSQL 完成候选到发布、部分重试和回滚。
- [ ] 最小 dbt 项目在真实 target 执行 compile/build/test，失败证据不能越过门禁。
- [ ] Chrome 95 桌面与 390px 完成主旅程和关键负向旅程。
- [ ] 最终报告区分代码、测试、迁移、部署、浏览器和回滚六层结论。
