# F4：发布物化短流程

**优先级**：P0  
**状态**：DRAFT

## 目标

用户在模型编辑器中发起构建与提交上线，并在发布完成后衔接物化运行；复杂审核仍在同一 Candidate 工作台完成。

## 契约

| 动作 | 契约 | 边界 |
|---|---|---|
| 构建 | `POST /model-specs/{id}/build-intents` | 只创建/复用 SINGLE_MODEL Candidate |
| 提交上线 | `POST /model-specs/{id}/publish-intents` | 推进质量/送审，停在人工作用边界 |
| 审核/发布 | `/plans/{planId}/release-candidates/{id}/...` | reviewer/operator 独立命令 |
| 物化运行 | `/plans/{planId}/execution-bindings/{bindingId}/runs` | 仅 PUBLISHED + active binding |

## UI/UX

一个弹窗两步：“发布检查/提交上线”与“物化运行”。第二步在 PUBLISHED 前禁用并解释原因；批量 Candidate 冲突时进入完整交付工作台。

## Tasks

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 接入 Build/Publish Intent | DRAFT | F2/T01、Sprint-76 稳定契约 |
| T02 | 接入物化运行与发布结果 | DRAFT | T01、Sprint-76 DEV 可运行链 |

## Definition of Ready

- [x] 快捷入口与 Candidate owner 边界已冻结。
- [ ] Sprint-76 对应 DEV 契约和 F0 基线可运行。

## 完成标准

- [ ] 快捷弹窗不越权 approve/publish。
- [ ] build-only 不显示“已发布/已有物理资产”。
- [ ] relation EXISTS 和 Catalog 注册后才显示上线完成。
