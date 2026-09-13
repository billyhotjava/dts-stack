# F0：交付基线与替代裁决

**优先级**：P0  
**状态**：READY

## 目标

在任何实现开始前，刷新可验收环境和存量数据画像，并把 Sprint-91 的单向接管/整页只读语义正式登记为被 Sprint-92 替代，确保后续 Task 不再按旧 ADR 继续扩展。

## 契约定义

| 类型 | 契约 | 关键字段/结果 |
|---|---|---|
| 基线 | `it/baseline.md` | P1～P8 实际结果、命令、日期、阻断 Task |
| 数据画像 | `assets/domain-profile.md` | implementationMode/status、draft 状态、bundle 完整性、三类 projection 样本 |
| 决策登记 | Sprint-91 README/F2/F3/F5 + handoff | 追加 `SUPERSEDED_BY_SPRINT_92`；不改写历史完成事实 |
| 队列 | `sprint-queue.md` | Sprint-92 目录、状态、Feature/Task 统计和执行顺序 |

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 刷新运行基线与真实数据画像 | P0 | READY | - |
| T02 | 固化 ADR 替代关系与三类 projection 样本 | P0 | READY | T01 的样本定位可并行准备 |

## Definition of Ready

- [x] 探针与只读 SQL 已写入基线/画像。
- [x] 不执行 E2E、不修改业务数据的边界明确。
- [x] 旧 Sprint 只追加替代说明，不篡改历史证据。
- [x] 关闭条件可由文档、HTTP、SQL 和 inspect 报告验证。

## 完成标准

- [ ] 当前 health/schema/login/harness 和真实数据分布有 2026-08-19 后证据。
- [ ] 平台生成、手工代码、ZIP 导入三个隔离样本能用于后续 RED 测试。
- [ ] Sprint-91/93/queue 对 Sprint-92 的描述一致，无“回切只是按钮”歧义。
- [ ] 受影响 Feature 的 G0 依赖解除后才改为 READY。

