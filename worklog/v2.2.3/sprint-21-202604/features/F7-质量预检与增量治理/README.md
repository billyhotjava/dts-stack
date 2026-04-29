# F7: 质量预检与增量治理

**优先级**: P1
**状态**: IN_PROGRESS

## 目标

把连接、权限、结构、数据量、主键、增量字段和目标写入校验前置，并建立增量任务的 watermark 状态和重跑补数规则。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 接入前 Precheck 规则框架 | P0 | IN_PROGRESS | F3-F5 |
| T02 | 连接、权限、目标写入与类型兼容校验 | P0 | IN_PROGRESS | T01 |
| T03 | 主键唯一性、增量字段非空率与样本校验 | P1 | READY | T01 |
| T04 | watermark 状态模型与失败不推进规则 | P1 | DONE | F5 |
| T05 | schema drift 与数据量波动检测 | P1 | IN_PROGRESS | T01-T04 |

## 完成标准

- [ ] 建任务前可执行 dry-run/precheck。
- [x] 预检结果有 PASS/WARN/FAIL 和修复建议。
- [x] 增量任务有可查询 watermark 状态。
- [x] 失败执行不推进 watermark。
- [x] schema drift 产生事件，可在任务和数据资产侧查看。

## 当前落地

- 任务中心新增列表级“预检”入口，调用现有 `/ingestion/tasks/{id}/pre-check`，展示总行数、通过/失败行数和规则失败摘要。
- 任务列表展示 `preCheckStatus`，便于运行前识别未预检、PASS、WARN、FAIL。
- 增量 watermark 状态和审计沿用现有 `/incremental-states`、`/incremental-audits`，详情页和执行历史页已可查看。
- Schema Discover 缓存刷新会产生 drift 摘要，支持新增/删除表和字段类型/nullable 变化识别。

## 待补

- precheck 还需要前置到 F5 建任务向导提交前，而不只是任务创建后手动执行。
- 主键唯一性、增量字段非空率、目标端写入权限和类型兼容需要形成统一规则框架。
- 数据量波动检测需要接入执行行数基线和告警阈值。
