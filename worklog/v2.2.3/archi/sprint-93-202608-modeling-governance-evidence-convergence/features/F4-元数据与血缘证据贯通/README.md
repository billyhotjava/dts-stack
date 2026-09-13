# F4：元数据与血缘证据贯通

**优先级**：P0
**状态**：IMPLEMENTATION_COMPLETE / IT-08_PASS / IT-07_AUTOMATED_PASS / FINAL_E2E_PENDING

## 目标

让模型物化形成的资产在同一 datasetId 下拥有可解释的技术元数据、表级和字段级血缘，并在 OpenMetadata 不可用时保持 DTS 资产和治理事实可用。

## 契约定义

| 类型 | 契约 | 关键字段 |
|---|---|---|
| 元数据身份 | Sprint-89 dataset/table/column stable ID + schema fingerprint | locator 与 AssetKey 分离、soft invalidation |
| 表级血缘 | `catalog_dataset_lineage` | source/target/relationType/verificationStatus/evidence/validity |
| 字段级血缘 | `catalog_column_lineage` | sourceColumn/targetColumn/confidence/evidence/validity |
| dbt 证据 | candidate-pinned MODEL/STG compiled SQL 与 run artifact | 复用唯一 shared parser；歧义不猜测 |
| OM 降级 | DTS 目录优先，OM cache 作为同步证据 | OM failure 不改变 asset identity/publication |

## UI/UX 规格

复用现有元数据和血缘页面。元数据管理入口明确“技术元数据采集/同步”和“业务元数据治理”两种视图；资产详情血缘 Tab 标注边的来源、验证状态和有效期。字段级缺失显示跳过原因，不显示空白成功态。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 将模型物化和 dbt 证据接入既有元数据血缘 seam | P0 | IMPLEMENTATION_COMPLETE / IT-08_PASS / IT-07_AUTOMATED_PASS | 最终真实 E2E |

## Definition of Ready

- [x] 表/字段血缘和 OM 边界已定义。
- [x] Sprint-90 字段有效期/匹配 seam 已复用，未新增第二 owner。
- [x] manifest 解析、skip reason 和物化链契约已实现；真实字段样本由 IT-07 验证。

## 完成标准

- [x] 同一 datasetId 的表/字段/模型证据写入契约通过自动化集成测试。
- [x] 重放幂等且不覆盖人工 VERIFIED。
- [x] OM 故障不删除资产或返回 5xx。
- [x] IT-08 通过；IT-07 自动化通过，最终真实 E2E 保持待执行。
