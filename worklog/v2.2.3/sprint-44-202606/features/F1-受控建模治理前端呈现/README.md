# F1: 受控建模治理前端呈现

**优先级**: P0
**状态**: READY
**对应后端**: Sprint-41 SP-1（`/api/semantic`，受控 DSL + ELT 分层闸 + governanceMode）

## 目标
让 SP-1 后端的受控建模治理在 dts-platform-webapp 原生语义建模页（`src/pages/modeling/`）**可见、可用、可理解**：用户能设受控模式、能看懂为什么被拒、能据诊断修复——把"后端默默校验"变成"前端引导式治理"。

## 范围与落点
- **后端已就绪**（无需改）：`governanceMode`（CONTROLLED 默认）、受控编译 422 `unsafe_expression`、分层闸 400 `invalid_layer`/`grain_mismatch`/`standard_code_required`。
- **前端落点**：`SemanticModelsPage`（模型列表）、`components/ModelEditDrawer`（模型创建/编辑表单）、`SemanticMetricsPage`（派生指标公式）、提交评审动作（`SemanticModelsPage`/`ModelPipeline`）、`src/api/semanticModelingApi.ts`（DTO/类型）。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | governanceMode 前端贯通与开关控件 | P0 | READY | - |
| T02 | 受控派生指标违规友好提示（422 unsafe_expression） | P0 | READY | T01 |
| T03 | ELT 分层闸诊断展示（400 分层码） | P0 | READY | T01 |
| T04 | 受控模式视觉区分 + 文案 + 前端契约测试 | P1 | READY | T01,T02,T03 |

## 完成标准
- [ ] 模型可设/显示 governanceMode；受控/permissive 视觉可辨。
- [ ] 受控 DSL 违规与分层违规均为引导式可读提示（非裸错误码/toast）。
- [ ] permissive 模型前端行为不变（绞杀者并存）。
- [ ] 前端契约/单测覆盖：governanceMode 透传、错误码渲染。
