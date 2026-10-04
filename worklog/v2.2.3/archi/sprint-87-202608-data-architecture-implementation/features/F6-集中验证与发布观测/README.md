# F6：集中验证与发布观测

**优先级**：P0

**状态**：PARTIAL_LOCAL_VERIFY_PASS（E2E / DEPLOYMENT / OBSERVABILITY 未执行）

**证据**：`../../assets/implementation-evidence-20260810.md`

## 目标

在全部编码完成后执行一次集中验证，完成可回滚发布、运行观测和 Contract 准入判断。

## Task

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 完成集中验证、发布观测与 Contract 判定 | PARTIAL_LOCAL_VERIFY_PASS | F0 外部输入、E2E、部署与观测仍阻塞 |

## Feature DoD

- [ ] 代码、测试、构建、部署、fixture E2E 和真实验收证据分栏记录。
- [ ] 迁移、路由、API 和字段都具备演练过的批次级回滚路径。
- [ ] 观测窗口满足后才形成独立 Contract GO/NO-GO，不在本 Task 中默认删除兼容面。
