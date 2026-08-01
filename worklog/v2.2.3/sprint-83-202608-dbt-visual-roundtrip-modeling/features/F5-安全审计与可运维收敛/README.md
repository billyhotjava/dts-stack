# F5：安全、审计与可运维收敛

**优先级**：P0  
**状态**：DRAFT  
**依赖**：F0；从 F1 起并行守卫

## 目标

把外部 ZIP、SQL 草稿和数据预览纳入 DTS 的租户、权限、密级、公共审计、临时存储和运行指标约束，并删除无消费者的旧建模尾巴。

## Task 列表

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 加固 ZIP、临时目录、依赖和非受信代码边界 | DRAFT | F0/T02 |
| T02 | 补齐公共审计动作、outbox、correlation 与保留策略 | DRAFT | F1/T01 |
| T03 | 固化权限、租户、密级与脱敏门禁 | DRAFT | F2/F3 合同 |
| T04 | 建立可观测性并退役建模侧旧文件/导入/parser 尾巴 | DRAFT | F1/T02、F2/F3完成 |

## 安全/审计契约

- `read` 查看 SQL/结构，`write` 编辑、提交和导入；写操作同时沿用现有建模维护者 authority。Sprint-83 不新增权限 action。
- 审计动作至少覆盖 INSPECT、PREVIEW、APPLY、RETRY、CHECKPOINT、COMMIT、CONFLICT。
- 审计 payload 只含身份、修订、checksum、数量、结果、错误码；SQL、ZIP、变量、凭据正文必须为 0。

## 完成标准

- [ ] 恶意 ZIP、跨租户、越权、密级、脱敏和日志泄露测试全部通过。
- [ ] inspect/preview 无网络依赖下载和模型 SQL 执行。
- [ ] 旧尾巴仅在 caller=0 与迁移证据通过后物理删除，不留长期双写/隐藏开关。
