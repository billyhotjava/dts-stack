# F4: 韧性与契约对齐

**优先级**: P1（T01 低成本，已随 F1 落地）
**状态**: DONE（T01/T02/T03 均 DONE；受限重试拆为后续 followup）
**对应缺陷**: #5 同步依赖零韧性、#6 API 契约漂移

## 目标

给 platform 同步依赖配超时/重试，消除无界挂起；让 visual-assets 端点契约与实现一致。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | RestClient 连接/读超时（受限重试拆出后续） | P1 | DONE | - |
| T02 | visual-assets 契约对齐（端点 or 文档收口） | P1 | DONE | - |
| T03 | 逐资产 permissionDecision 富化 | P2 | DONE | T02 |

## 完成标准
- [x] RestClient 配置连接超时与读超时；platform 抖动快速失败为 503 而非挂起。
- [ ] 幂等只读调用（catalog/resolve）可受限重试；写调用不盲目重试。（拆为后续 followup，非本 sprint 阻断项）
- [x] `MetricVisualAssetResource` 与 API 契约文档对端点达成一致：取**文档收口**方案——契约承认现状 `/catalog/assets-v2`、把 `/internal/metrics/visual-assets` 记为中期跨服务目标（见 `sprint-35-202605/assets/dts-metrics-api-contract.md` "现状对齐"段）；并补逐资产 `permissionDecision` 透传（平台带则原样返回，缺失回退 `PLATFORM_FILTERED`）。

## 落地说明（Sprint-35b）
- **T02 决策**：按边界纪律不擅自把实现切到 platform 尚未提供的 `/internal/metrics/visual-assets`（对方职责），改为更新契约文档使其与现状一致，并明确中期目标。
- **T03 实现**：`MetricVisualAssetResource.toVisualAsset` 改为 `firstText(item.permissionDecision, item.permission_decision, "PLATFORM_FILTERED")`，前向兼容未来内部端点。
- **测试**：`MetricVisualAssetResourceTest` 新增 `perAssetPermissionDecisionFromPlatformIsSurfaced` + `permissionDecisionFallsBackToPlatformFilteredWhenAbsent`（6 例全绿）。
