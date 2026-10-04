# Sprint-92 backlog：DBT 转回可视化维护

本文件是 Sprint-91 的范围移交资产，不属于 Sprint-91 Task，不计入状态统计。

> **2026-08-19 已承接**：该 backlog 已升级为“来源无关的统一模型创作”，不再实现一次性的 DBT→DESIGNER 回切按钮。正式 Sprint 位于 `../../sprint-92-202608-unified-model-authoring-convergence/`；本文件保留为当时为何不能安全回切的历史输入。

## 为什么不在 Sprint-91 实施

1. `DbtCompatibilityEvaluator` 只评估导入包的 runtime/adapter 兼容性，不负责 SQL 到字段映射的可逆性判断。
2. `ModelConversionClassifier` 要求来自 `dts_semantic.yml` 的 `SemanticMetadata`，而现网语义位于 ModelSpec。
3. 平台生成 SQL 固有包含 `{{ ref(...) }}`、`with` 和类型转换，现有 classifier 会将其判为复杂；刚接管且未修改的模型也无法安全转回。

## 重启 DoR

- [ ] 明确从 ModelSpec 构造回切语义的单一适配 owner。
- [ ] 决定扩展现有 classifier 还是采用不可逆长期产品形态；不得维护两套相互漂移的安全子集规则。
- [ ] 定义双 ETag、previewChecksum、服务端生成 field mappings 和原子审计契约。
- [ ] 决定旧 `POST .../convert-to-designer-generated` 的加固或下线策略。

## Sprint-91 固定边界

- 不实现、不展示、不置灰任何回切动作。
- 不修改旧转换端点。
- DBT_MANAGED 的可视化模式仅提供可信只读投影；用户文案为“本模型由代码维护，请在代码模式修改实现。”
