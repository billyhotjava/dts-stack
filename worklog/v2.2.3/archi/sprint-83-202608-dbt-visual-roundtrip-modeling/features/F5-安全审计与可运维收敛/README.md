# F5：安全、审计与可运维收敛

**优先级**：P0
**状态**：CODE_COMPLETE / RUNTIME_RETIREMENT_PENDING
**依赖**：F0；从 F1 起并行守卫

## 目标

把外部 ZIP、SQL 草稿和数据预览纳入 DTS 的租户、权限、密级、公共审计、临时存储和运行指标约束。P0 安全与审计不得因退役工作延期；旧建模尾巴仅作为 P2 独立任务在 caller=0 后物理删除。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 加固 ZIP、临时目录、依赖和非受信代码边界 | P0 | CODE_COMPLETE | F0/T02 |
| T02 | 补齐公共审计动作、outbox、correlation 与保留策略 | P0 | CODE_COMPLETE | F1/T01 |
| T03 | 固化权限、租户、密级与脱敏门禁 | P0 | CODE_COMPLETE | 当前切片对应 F2/F3 合同 |
| T04 | 建立解析、导入、预览与物化可观测性 | P1 | CODE_COMPLETE | F1/T02、对应交付切片 |
| T05 | 退役旧 Bash DAG、不安全 preview、共享文件写与重复 parser 尾巴 | P2 | CODE_COMPLETE / RUNTIME_EVIDENCE_PENDING | T04、全部待退役调用方迁移完成 |

## 安全/审计契约

- `read` 查看业务结构；SQL 仅在显式高级 dbt 实现中按现有 read + 建模维护者边界查看，`write` 编辑、提交和导入。Sprint-83 不新增权限 action。
- 审计动作至少覆盖 INSPECT、PREVIEW、APPLY、RETRY、FORWARD_UNDO、CHECKPOINT、COMMIT、CONFLICT、CATALOG_PROJECTION、PHYSICAL_PREVIEW，并全部登记 dts-admin 动作字典。
- 审计 payload 只含身份、修订、checksum、资产/candidate/evidence 引用、请求与返回行列数量、mask/deny 数量、结果、错误码和 correlationId；SQL、ZIP、变量、凭据、样例值正文必须为 0。
- 样例行默认 100/最大 500，必须显式加载并返回 `private, no-store`；不增加分页、导出或持久缓存。

## 完成标准

- [ ] 恶意 ZIP、跨租户、越权、密级、脱敏和日志泄露测试全部通过。
- [ ] inspect/preview 无网络依赖下载和模型 SQL 执行。
- [ ] `R-DBT-LEGACY-DAG`、`R-DBT-LEGACY-PREVIEW` 和其他旧尾巴有具名 owner、可观测性与删除门禁；物理删除由 P2/T05 独立验收，不反向阻断 P0 安全能力。
