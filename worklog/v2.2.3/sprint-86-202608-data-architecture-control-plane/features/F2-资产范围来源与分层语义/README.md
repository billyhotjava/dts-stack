# F2：资产范围、来源与分层语义

**优先级**：P0
**状态**：DONE（Architecture）

## 目标

冻结“什么进入资产台账”以及来源、数仓分层、业务归属、治理/消费状态的正交语义。

## 契约定义

| 类型 | 已批准契约 | 冻结内容 |
|---|---|---|
| 身份 | CatalogAssetType + CatalogAssetKey + physical locator | 表/视图/物化视图稳定身份与幂等登记 |
| 来源 | asset origin | SOURCE_SYSTEM/INGESTION/DBT/MANUAL/DISCOVERY 枚举 |
| 分层 | warehouse layer | SOURCE 迁移、null 规则、自定义层映射 |
| 状态 | governance + consumption | 发现、治理、发布、服务、过期/失败的最小状态机 |

## Task

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 定义物理资产纳管矩阵 | P0 | DONE | F1/T01、F1/T02 |

## Definition of Ready

- [x] F1/T01、F1/T02 的 owner、身份和关系契约已通过对应评审
- [x] 当前 SOURCE/DIM 数据、写入链、物化 observation 和 CatalogDataset 事实已登记到账本
- [x] ADR-86-04/05/13/16/18/19 的候选方案、反例和迁移约束已准备好进入评审
- [x] `assets/nfr-budget.md` 已登记统计、批量、幂等和审计 fitness function
- [x] IT-04、IT-07 已登记真实参与者、目标日期和预期决议
- [x] 纳管矩阵、来源/登记渠道、状态轴和兼容迁移是本 Task 的输出，不再错误写成 DoR 前置完成项

## Feature Definition of Done

- [x] T01 达到 DONE，ADR-86-04/05/13/16/18/19 均有明确结论
- [x] `CatalogAssetType + CatalogAssetKey` 仍是唯一资产身份，没有平行资产表或关系身份
- [x] ProducerRef、RegistrationEvidence、分层及五类状态轴正交且有迁移/回滚规则
- [x] 资产纳管与统计架构、规模预算同批冻结
- [x] IT-04、IT-07 有真实结论，下一实施 Sprint 可按后端迁移与资产 UI 竖切片拆分
