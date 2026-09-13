# F6：集成验收与发布门禁

**优先级**：P0
**状态**：PLANNED

## 目标

在全部编码和物理退役完成后，一次回跑架构/NFR、升级恢复和真实认证 E2E，形成唯一 Go/No-Go 结论。

## 契约定义

| 类型 | 契约 | 要点 |
|---|---|---|
| 架构/NFR | `assets/nfr-budget.md` 全行 | 每条预算有可执行红/绿检查 |
| 升级恢复 | empty/existing upgrade + backup restore | historical changelog checksum 不变，manifest 一致 |
| E2E | IT-08 | 新 UI→canonical→quality→candidate→gateway→Airflow/dbt→Catalog/audit |
| 发布结论 | `GoNoGoDecision` | commit/environment/evidence/pass/fail/rollback/residualRisk |

## UI/UX 规格

最终走查使用 Sprint-80 正式 `/data-modeling/**` 页面，不恢复任何旧 UI。验证空/加载/错误/成功和冲突状态；未满足 StageGate、权限、执行或审计条件时必须明确失败关闭。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 回跑架构与 NFR 适应度函数 | P0 | PLANNED | F1～F5 完成编码 |
| T02 | 演练升级、迁移、回滚与恢复 | P0 | PLANNED | F0/T03、F5 完成 |
| T03 | 最终认证 E2E 与 Go/No-Go | P0 | PLANNED | T01～T02 全绿 |

## Definition of Ready

- [ ] F1～F5 全部代码、migration、定向测试和 review 完成。
- [ ] gitnexus_detect_changes 确认影响仅在预期流程。
- [ ] 当前环境/客户环境 pre-drop evidence 与恢复结果齐全。
- [ ] 一次性账号、隔离数据、Airflow/dbt/Catalog/dts-admin 验收路径可用。

## 完成标准

- [ ] IT-01～IT-08 真实证据齐全，无占位。
- [ ] 所有 NFR 预算绿；客户相关 GAP 已关闭或明确 NO_GO。
- [ ] 旧 route/table/code absent，outbox backlog=0，中央审计分类正确。
- [ ] 只有 T03 可把 Sprint 状态改为 DONE；此前保持 PLANNED/IN_PROGRESS。
