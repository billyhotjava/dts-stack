# F4: 韧性与契约对齐

**优先级**: P1（T01 低成本，已随 F1 落地）
**状态**: IN_PROGRESS（T01 DONE；T02/T03 待）
**对应缺陷**: #5 同步依赖零韧性、#6 API 契约漂移

## 目标

给 platform 同步依赖配超时/重试，消除无界挂起；让 visual-assets 端点契约与实现一致。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | RestClient 连接/读超时（受限重试拆出后续） | P1 | DONE | - |
| T02 | visual-assets 契约对齐（端点 or 文档收口） | P1 | READY | - |
| T03 | 逐资产 permissionDecision 富化 | P2 | READY | T02 |

## 完成标准
- [ ] RestClient 配置连接超时与读超时；platform 抖动快速失败为 503 而非挂起。
- [ ] 幂等只读调用（catalog/resolve）可受限重试；写调用不盲目重试。
- [ ] `MetricVisualAssetResource` 与 API 契约文档对端点达成一致（改实现走 `/internal/metrics/visual-assets`，或更新契约承认 `/catalog/assets-v2`），并补逐资产 permissionDecision。
