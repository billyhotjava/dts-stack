# T04: 评审 / 发布 / runs 流

**优先级**: P0
**状态**: READY
**依赖**: T03

## 目标
原生实现模型生命周期：提交评审/审批/驳回、发布 dbt、runs、生成工件查看，消费 `/api/semantic`。

## 技术设计
- 评审：`submit-review`/`approve-review`/`reject-review` + `review-logs` 展示。
- 发布：`generate-artifacts`/`preview-data`/`publish-dbt`（+ register-bi-dataset/register-lineage 入口）。
- runs：`models/{id}/runs`（列表 + 触发 + 状态）。
- generated-artifacts：查看生成的 dbt 工件。
- 流程态可视（DRAFT→IN_REVIEW→APPROVED→PUBLISHED）；危险动作二次确认。

## 影响范围
- `dts-platform-webapp`：publish/runs section + 模型详情生命周期区 + `semanticModelingApi`。

## 验证
- [ ] 评审/发布/runs 端到端贯通 /api/semantic。
- [ ] 受控模型提交评审触发后端分层闸（F1-T03 展示诊断）。

## 完成标准
- [ ] 生命周期流可操作、贯通后端、构建绿。
