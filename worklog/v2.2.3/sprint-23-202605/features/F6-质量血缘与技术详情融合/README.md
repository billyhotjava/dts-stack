# F6: 质量、血缘与技术详情融合

**优先级**: P0  
**状态**: DONE

## 目标

统一质量、血缘和技术详情的资产身份，让 OpenMetadata 技术血缘成为主输入，DTS 业务血缘和运行节点作为 overlay 叠加。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 统一资产身份解析器：`om_entity_id` / `fqn` / `legacy_dataset_id` | P0 | DONE | F3 |
| T02 | OpenMetadata table/column lineage cache 同步与去重 | P0 | DONE | F1,T01 |
| T03 | DTS 接入任务、运行批次、dbt、指标、报表 overlay 节点 | P0 | DONE | T01 |
| T04 | 血缘图 API 标注 edge source 和 edge type | P0 | DONE | T02,T03 |
| T05 | 质量报告统一 DTS 治理运行与 OM test case | P0 | DONE | T01 |
| T06 | 前端血缘/质量/详情来源标识与缺失态 | P1 | DONE | T04,T05 |

## 完成标准

- [x] 血缘和质量不再各自拼 FQN。
- [x] 技术血缘可来自 OpenMetadata cache。
- [x] DTS 本地业务节点可以和 OM table/column 节点叠加展示。
- [x] 血缘边显示来源：`openmetadata`、`dts-ingestion`、`dbt`、`manual` 等。
- [x] 质量页能同时说明 DTS 治理运行和 OM test case 的来源、时间和缺失原因。
- [x] 资产存在但血缘/质量缺失时，页面给出可定位原因。
