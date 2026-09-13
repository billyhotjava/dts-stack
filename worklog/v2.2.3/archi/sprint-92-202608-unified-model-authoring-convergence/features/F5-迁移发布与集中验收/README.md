# F5：迁移发布与集中验收

**优先级**：P0  
**状态**：IN_PROGRESS（迁移命令与自动化完成；E2E、发布和回滚演练待执行）

## 目标

以可预览、可分批、可回滚方式补齐存量 provenance/projection，完成兼容发布和回滚演练，并在所有 Feature 完成后执行一次真实纵向 E2E。

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| Migration preview | 只读报告 | counts、draftId、status、beforeChecksum、provenance、coverage、reason；无 SQL 正文 |
| Apply | 每批 ≤100，游标 `(last_modified_date,id)` | batchId、applied/skipped/conflict；pins 漂移 fail closed |
| Rollback | batchId + before checksum | 后续被用户修改的行拒绝盲回滚 |
| Release | `assets/release-plan.md` | schema → backend → frontend → migrate；无 Contract |
| Operability | `assets/runbook.md` | health、阈值、日志字段、故障处置 |
| E2E | `it/README.md` IT-01～11 | 现代/Chrome95、双视口、真实账号、HTTP/DB/audit/资产证据 |

## UI/UX 规格

本 Feature 不新增运维页面；用户验收仍在模型工作台、发布弹窗、模型列表和资产/血缘/质量现有页面完成。迁移 preview/apply/rollback 由受控运维命令执行并留报告。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 实现存量 preview/apply/rollback 与兼容盘点 | P0 | IMPLEMENTED | F4/T02、F0/T01 |
| T02 | 完成聚焦自动化与一次集中纵向 E2E | P0 | IN_PROGRESS | F1～F4 全部完成、T01 |
| T03 | 执行分阶段发布回滚演练并闭合运维材料 | P0 | PENDING | T01、T02 |

## 实施证据（2026-08-20）

- 已实现受控 migration preview/apply/rollback，使用持久化 batch/item ledger、CAS 和漂移保护。
- 后端聚焦测试、生命周期回归、前端聚焦测试、TypeScript、Biome、Spotless 和生产构建已通过。
- 按用户约定，本轮未执行浏览器 E2E，也未执行实时迁移、部署或回滚演练；Sprint 不标记 DONE。
- 自动化详见 `../../it/evidence/20260820-automated.md`。

## Definition of Ready

- [x] preview/apply/rollback、发布顺序和 E2E 场景已定义。
- [x] Feature 未完成前不执行 E2E 的纪律明确。
- [x] 不删除旧 schema/API 的 Contract 边界明确。
- [x] F1～F4 实现已完成并通过聚焦自动化。

## 完成标准

- [ ] dry-run/apply/rollback/re-apply 对漂移、过期、COMMITTED、缺 bundle 均有证据。
- [ ] 聚焦测试、build、旧 REST、现代 Chrome、Chrome95 和纵向 E2E 全部通过。
- [ ] 回滚 frontend/backend 后旧页面可读，历史 revision/candidate/asset 不丢失。
- [ ] runbook 可由未参与开发的人员定位 commit、projection、冲突和依赖故障。
