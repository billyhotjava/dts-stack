# F1：统一 dbt 快照与可视化投影

**优先级**：P0  
**状态**：DRAFT  
**依赖**：F0

## 目标

提供一套版本固定的 `ModelRepresentationView`，让高级模式、逆向预检、血缘、诊断和物化证据消费同一 dbt 结构口径，而不新增模型 owner 或第二套 parser。

## 契约定义（提案）

```text
ModelRepresentationView {
  modelSpecId, modelRevision, modelChecksum,
  implementationRevision, implementationChecksum,
  ownershipMode, visualizationCapability, capabilityReasons[],
  logicalModel, dbtStructure, runtimeObservation?, drift
}
```

`logicalModel` 来源为 ModelSpec；`dbtStructure` 来源为固定 artifact/manifest；`runtimeObservation` 来源为固定 candidate/relation evidence。每个字段必须携带 `DECLARED | COMPILED | OBSERVED` provenance。

## Task 列表

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 冻结版本固定的 ModelRepresentationView 契约 | DRAFT | F0/T04 |
| T02 | 收敛 manifest/catalog/source parser 与删除矩阵 | DRAFT | T01 |
| T03 | 实现逻辑、技术、运行三类 provenance 投影 | DRAFT | T01～T02 |
| T04 | 实现可视化能力和三方漂移状态机 | DRAFT | T03 |

## UI/UX 规格

本 Feature 不新增页面；为 F2/F3 提供统一 read model。错误必须返回稳定 reason code，页面不得根据空字段猜测“完整可视化”。

## Definition of Ready

- [ ] D01、D02、D05、D09、D10 已确认。
- [ ] parser 收敛矩阵列出保留 seam、消费者迁移和删除条件。

## 完成标准

- [ ] 相同 revision/checksum 重读结果稳定。
- [ ] provenance、capability 和 drift 均有契约及 fixture 测试。
- [ ] 未新增 dbt 项目、模型、发布或运行平行台账。
