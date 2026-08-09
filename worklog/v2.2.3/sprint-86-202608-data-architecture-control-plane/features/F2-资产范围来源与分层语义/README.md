# F2：资产范围、来源与分层语义

**优先级**：P0
**状态**：DRAFT

## 目标

冻结“什么进入资产台账”以及来源、数仓分层、业务归属、治理/消费状态的正交语义。

## 契约定义

| 类型 | 候选契约 | 待冻结内容 |
|---|---|---|
| 身份 | CatalogAssetType + CatalogAssetKey + physical locator | 表/视图/物化视图稳定身份与幂等登记 |
| 来源 | asset origin | SOURCE_SYSTEM/INGESTION/DBT/MANUAL/DISCOVERY 枚举 |
| 分层 | warehouse layer | SOURCE 迁移、null 规则、自定义层映射 |
| 状态 | governance + consumption | 发现、治理、发布、服务、过期/失败的最小状态机 |

## Task

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 定义物理资产纳管矩阵 | DRAFT | F1/T01、F1/T02 |

## Definition of Ready

- [ ] 资产发现与验证/发布边界已确认
- [ ] SOURCE 兼容策略已确认
- [ ] 失败关系和持久化 STG 的处理规则已确认
- [ ] 状态机及展示/筛选含义已确认
- [ ] ModelSpec/observation 到语义资产与物理资产的两条映射已确认
- [ ] DIM 建模语义与资产兼容值的归一化策略已确认
