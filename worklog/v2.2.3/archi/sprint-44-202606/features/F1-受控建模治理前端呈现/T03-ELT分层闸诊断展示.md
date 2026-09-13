# T03: ELT 分层闸诊断展示（400 分层码）

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
受控模型**提交评审**失败时，把后端 400 分层码（`invalid_layer`/`grain_mismatch`/`standard_code_required`）渲染为**定位到模型/层的可读诊断 + 修复指引**，而非裸 toast。

## 技术设计
- **落点**：`src/pages/modeling/SemanticModelsPage.tsx` / `ModelPipeline.tsx` 的"提交评审"动作（调 `/api/semantic/models/{id}/submit-review`）。
- 捕获 400 + body `code ∈ {invalid_layer, grain_mismatch, standard_code_required}` → 诊断展示组件（Alert/列表）：
  - 码→中文 + 修复指引映射：
    - `invalid_layer`：该模型输出层不可用于建模（ODS/STG 仅血缘；或 DWD 不可直连发布）。
    - `grain_mismatch`：DWD 高级建模必须声明 grain/主键 → 引导去模型编辑填 grain。
    - `standard_code_required`：DWD 必须绑定标准码 → 引导（SP-2 列族就绪后联动字段）。
  - 复用后端诊断 message（含定位信息）。
- permissive 模型：提交评审不过闸，行为不变。

## 影响范围
- `dts-platform-webapp`：`SemanticModelsPage.tsx` / `ModelPipeline.tsx` + 诊断展示组件。

## 验证
- [ ] 受控模型提交（ODS/STG 输出 / 无 grain 的 DWD）→ 可读诊断 + 指引。
- [ ] 受控合规模型提交 → 成功进入评审。
- [ ] permissive 模型提交不受闸影响。

## 完成标准
- [ ] 分层码引导式渲染、码→指引映射齐、permissive 不变；构建通过。
