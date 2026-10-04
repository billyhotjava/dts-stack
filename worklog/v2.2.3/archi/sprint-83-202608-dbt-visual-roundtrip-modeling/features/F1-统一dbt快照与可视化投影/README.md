# F1：统一 dbt 快照与可视化投影

**优先级**：P0
**状态**：CODE_COMPLETE
**依赖**：F0

## 目标

提供一套版本固定、按受众服务端裁剪的 `ModelRepresentationView`，让普通业务可视化、显式高级 dbt 实现、逆向预检、血缘、诊断和物化证据消费同一结构口径，而不新增模型 owner 或第二套 parser。

## 契约定义（提案）

```text
ModelRepresentationView {
  modelSpecId, modelRevision, modelChecksum,
  implementationRevision, implementationChecksum,
  ownershipMode, representationScope: BUSINESS | TECHNICAL,
  visualizationCapability, capabilityReasons[],
  logicalModel, dependencyProjection,
  latestPublishedRef?, servingRef?, runtimeObservation?, previewCapability?, drift,
  technicalImplementation? // 仅 TECHNICAL scope
}
```

`logicalModel` 来源为 ModelSpec；`dependencyProjection` 来源为固定 ModelSpec/artifact/manifest 的受控投影；`latestPublishedRef/servingRef` 来自既有发布与 Catalog 投影；`runtimeObservation` 来源为 serving 或显式成功 candidate 的固定 relation evidence。每个字段必须携带 `DECLARED | COMPILED | OBSERVED` provenance。`BUSINESS` 响应不得包含 SQL/Jinja、macro、project path、compiled SQL、dbt 文件树或完整技术 DAG；`TECHNICAL` 只供显式高级 dbt 实现并执行服务端权限门禁。样例行不嵌入表示读模型，只由显式 physical-preview 请求按 D12 获取。

dbt 包和运行时兼容由 [`assets/dbt-compatibility-and-source-only-contract.md`](../../assets/dbt-compatibility-and-source-only-contract.md) 的 `inspection/importProjection/materialization` 三轴派生；它不是新的模型状态。source-only 的 `STRUCTURE_VIEW_ONLY` 仅表示已有可信声明结构可读，不能绕过 apply 门禁或改变 ownership。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 冻结版本固定的 ModelRepresentationView 契约 | P0 | CODE_COMPLETE | F0/T04 |
| T02 | 收敛规范化 projection seam 与 parser 删除门禁 | P0 | CODE_COMPLETE（非建模 reader 作为 owner adapter 保留） | T01 |
| T03 | 实现逻辑、技术、运行三类 provenance 投影 | P0 | CODE_COMPLETE | T01～T02 |
| T04 | 实现可视化能力判定 | P0 | CODE_COMPLETE | T03 |
| T05 | 实现技术三方漂移与语义映射状态机 | P1 | CODE_COMPLETE | T03 |

## UI/UX 规格

本 Feature 不新增页面；为 F2/F3 提供统一 read model。普通页面必须请求 `BUSINESS` scope，不能依赖前端 CSS 隐藏 SQL；错误必须返回稳定 reason code，页面不得根据空字段猜测“完整可视化”。

## Definition of Ready

- [x] D01、D02、D05 已确认。
- [x] D09 兼容政策和 D10 source-only 行为已于 2026-08-02 确认。
- [ ] 当前 P0 表示切片所需 FX-01 与三轴基础结果已实测归档；materialization 仍由 RT-01/H83-01 独立阻断。
- [ ] parser 收敛矩阵列出保留 seam、消费者迁移和删除条件。

## 完成标准

- [ ] 相同 revision/checksum 重读结果稳定。
- [ ] provenance、capability 有 P0 契约及 fixture 测试；technical drift 由 P1/T05 独立验收。
- [ ] BUSINESS scope 的响应正文和序列化快照中 SQL/dbt 技术正文字段为 0。
- [ ] 未新增 dbt 项目、模型、发布或运行平行台账。
