# F3：外部 dbt 包逆向建模产品化

**优先级**：P0  
**状态**：DRAFT  
**依赖**：F1

## 目标

用真实 ZIP inspect/preview/apply/retry 替换硬编码逆向原型，让用户看见依赖、结构、损失项与冲突，补齐必要业务语义后生成 canonical ModelSpec DRAFT。

## UI/UX 规格

- **入口**：`/data-modeling/dimensions/reverse?source=dbt`。
- **向导**：来源类型 → 上传检查 → 计划/域/来源映射 → 模型差异与语义补全 → 应用进度/结果。
- **结果分类**：CREATE、UPDATE、SKIP、CONFLICT、BLOCKED；每项显示 conversion capability 与 recoveryAction。
- **恢复**：刷新页面可凭 runId 恢复；部分成功逐项展示；retry 不重复成功项。
- **完成**：成功项可深链进入对应 ModelSpec 高级模式。

## Task 列表

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 增加 dbt ZIP 来源模式并接入安全 inspect | DRAFT | F0/T02、F1/T02 |
| T02 | 建立计划、域、来源映射和语义补全预检 | DRAFT | T01、F1/T03 |
| T03 | 展示依赖闭包、差异和冲突选择 | DRAFT | T02、F1/T04 |
| T04 | 接入 apply、进度恢复、结果、retry 与历史 | DRAFT | T03 |
| T05 | 建立重新导入、三方漂移和前向撤销 | DRAFT | T04、D05/D07 |

## Definition of Ready

- [ ] D04～D07、D09～D10 已确认。
- [ ] FX-01～05 均可用于 RED 测试。
- [ ] 不把数据库表逆向发现误写为本 Feature 已实现能力。

## 完成标准

- [ ] 前端不再使用 `DISCOVERED_MODELS` 作为真实结果。
- [ ] import run/apply attempt 现有台账被复用。
- [ ] 导入从不直接进入发布/物化，也不静默覆盖冲突。
