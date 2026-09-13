# F5: 实现发布与资产交接

**优先级**: P0  
**状态**: DRAFT（等待 F0/F4）

## 目标

维度表只有在来源、属性映射和实现策略全部通过后才能进入发布；发布成功后按统一资产键进入资产台账，并保留可恢复证据。

## 契约定义

- Stage gate：来源/上游/生成策略 inclusive OR；属性映射完整；命名/SCD/保留校验通过。
- 发布：复用 Sprint-69 ReleaseCandidate，不新增 publish API。
- 资产：复用 `CatalogAssetType/CatalogAssetKey`，锚点 `modelSpecId/revision`。

## UI/UX 规格

- 模型详情 blocker panel 给出准确修复 Tab。
- 发布工作台显示模型 revision、definition revision、dataMart、physicalName 和来源 revision。
- 资产注册 PARTIAL 时提供幂等重试，不把发布显示为完全成功。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 收敛实现门禁与资产注册交接 | P0 | DRAFT | F4 全部 |
| T02 | 完成迁移发布运维与端到端验收 | P0 | DRAFT | T01、F0 |

## Definition of Ready

- [x] 发布和资产 owner 已明确复用
- [x] IT-01～IT-06 已定义
- [ ] F0/F4 完成

## 完成标准

- [ ] 草稿与已发布资产状态不混淆
- [ ] 发布/注册失败可恢复
- [ ] G3/G4 与全部 DoD 证据通过
