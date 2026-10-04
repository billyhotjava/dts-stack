# F1：统一创作草稿与版本边界

**优先级**：P0  
**状态**：IMPLEMENTED（自动化通过；实时环境基线与 E2E 待集中验收）

## 目标

把既有 dbt implementation draft 扩展为同一 ModelSpec 的组合创作草稿，使 visual/code 共用 pins、ETag、保存、校验与 commit receipt，并为 PUBLISHED 提供显式 fork 而非原地修改。

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| 数据 Expand | `modeling_dbt_implementation_draft` | 可空 `model_spec_snapshot jsonb`、`projection_summary jsonb`、`authoring_origin varchar(32)`；不改旧状态/checksum 约束 |
| Create/Fork | `POST /api/modeling/model-specs/{id}/authoring-drafts` | `intent,baseModelRevision/baseModelChecksum,baseImplementationRevision/checksum?,idempotencyKey` |
| Save | `PUT .../authoring-drafts/{draftId}` | `expectedEtag,modelSpecSnapshot,files[],activeView`；响应新 ETag |
| Read | `GET .../{id}/authoring-context` | pins、provenance、projection、openDraft、allowedActions、publishedForkRequired |
| 复用 | `DbtImplementationDraftService`/repository + ModelSpec validator/repository | facade 是唯一新 public command owner，不复制持久化/validator |

## UI/UX 规格

- PUBLISHED：表单和代码只读，只显示一个“创建新草稿版本”。
- DRAFT：打开或幂等续编活动 authoring draft；两个视图共享状态。
- 创建/续编 loading 时保留标题；409/412 时保留本地内容并提供刷新。
- 不显示 implementation ownership 选择器。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | Expand 既有草稿 schema 与 repository | P0 | IMPLEMENTED | F0/T01、F0/T02 |
| T02 | 建立 authoring context/create/save facade | P0 | IMPLEMENTED | T01 |
| T03 | 实现 PUBLISHED fork 与幂等续编 | P0 | IMPLEMENTED | T02 |

## 实施证据（2026-08-20）

- 草稿表已以 Expand 方式增加 ModelSpec 快照、投影摘要和来源字段，旧列和旧路由保留。
- 已提供 source-neutral authoring context/create/save facade，并复用原有 draft repository、ETag、pins 和 receipt。
- `PUBLISHED` 只能通过 `FORK_PUBLISHED` 派生同 ModelSpec 的新 `DRAFT`，幂等与 CAS 用例已通过。
- 自动化详见 `../../it/evidence/20260820-automated.md`。

## Definition of Ready

- [x] 路由、请求/响应、列和兼容边界已钉死。
- [x] 不新建模型/依赖/发布台账。
- [x] PUBLISHED 与 DRAFT 状态行为明确。
- [ ] F0 当前 schema、异常草稿和样本画像尚未刷新；不阻断已完成的 Expand 实现，但阻断真实迁移验收。

## 完成标准

- [ ] old/new backend/frontend 组合可兼容运行。
- [ ] visual/code save 返回同一 draftId 和单调 ETag。
- [ ] PUBLISHED fork 保留旧 revision，产生同身份 DRAFT，并支持幂等重放。
- [ ] 过期、冲突、越权、容量上限和事务失败均 fail closed。
