# F7: 质量预检与增量治理

**优先级**: P1
**状态**: IN_PROGRESS

## 目标

把连接、权限、结构、数据量、主键、增量字段和目标写入校验前置，并建立增量任务的 watermark 状态和重跑补数规则。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 接入前 Precheck 规则框架 | P0 | DONE | F3-F5 |
| T02 | 连接、权限、目标写入与类型兼容校验 | P0 | IN_PROGRESS | T01 |
| T03 | 主键唯一性、增量字段非空率与样本校验 | P1 | DONE | T01 |
| T04 | watermark 状态模型与失败不推进规则 | P1 | DONE | F5 |
| T05 | schema drift 与数据量波动检测 | P1 | IN_PROGRESS | T01-T04 |

## 完成标准

- [x] 建任务前可执行 dry-run/precheck。
- [x] 预检结果有 PASS/WARN/FAIL 和修复建议。
- [x] 增量任务有可查询 watermark 状态。
- [x] 失败执行不推进 watermark。
- [x] schema drift 产生事件，可在任务和数据资产侧查看。

## 当前落地

- 任务中心新增列表级“预检”入口，调用现有 `/ingestion/tasks/{id}/pre-check`，展示总行数、通过/失败行数和规则失败摘要。
- 任务列表展示 `preCheckStatus`，便于运行前识别未预检、PASS、WARN、FAIL。
- 数据源页新增“提交前预检”，调用 `POST /api/infra/data-sources/{id}/ods-precheck`，在生成同步任务前输出 PASS/WARN/FAIL、规则明细和修复建议；FAIL 阻断提交，WARN 需要二次确认。
- 平台侧 `OdsGenerationService.buildSyncTaskDraft` 会强制执行同一套 precheck，避免绕过前端直接创建存在基础规则失败的任务草稿。
- 当前规则覆盖 JDBC 数据源、连接测试状态、源表/字段完整性、ODS 目标映射冲突、重复目标表、增量字段可用性和类型转换警告。
- `OdsPrecheckProbeService` 新增源端只读探测：实际连接源端并校验源表 SELECT 权限、当前行数、主键空值/重复分组、增量字段空值；源表查询权限失败会阻断，质量探测失败或超时降级为 WARN。
- 数据源可通过 `props.precheckQueryTimeoutSeconds` 调整深度探测超时时间，也可用 `props.precheckProbeDisabled=true` 跳过源端深度探测。
- 增量 watermark 状态和审计沿用现有 `/incremental-states`、`/incremental-audits`，详情页和执行历史页已可查看。
- Schema Discover 缓存刷新会产生 drift 摘要，支持新增/删除表和字段类型/nullable 变化识别。

## 待补

- 目标端写入权限和深度类型兼容需要接入统一规则框架。
- 数据量波动检测需要接入执行行数基线和告警阈值。
