# F6：集中验证与发布观测

**优先级**：P0

**状态**：BLOCKED

## 目标

在全部编码完成后执行一次集中验证，完成可回滚发布、运行观测和 Contract 准入判断。

## Task

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 完成集中验证、发布观测与 Contract 判定 | BLOCKED | F0～F5 DONE |

## Feature DoD

- [ ] 代码、测试、构建、部署、fixture E2E 和真实验收证据分栏记录。
- [ ] 迁移、路由、API 和字段都具备演练过的批次级回滚路径。
- [ ] 观测窗口满足后才形成独立 Contract GO/NO-GO，不在本 Task 中默认删除兼容面。
