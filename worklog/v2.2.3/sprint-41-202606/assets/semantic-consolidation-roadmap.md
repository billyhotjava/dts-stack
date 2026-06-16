# 语义层整合路线图（平台侧权威）

**决策日**: 2026-06-16

## 决策
dts-metrics 与 dts-platform `SemanticModelingResource` 是两套并行语义建模层。经评审，**以 dts-platform 为唯一权威语义层**，dts-metrics 亮点移植进平台后逐步退役。

理由：
- 平台 `SemanticModelingResource` 是更成熟系统：完整评审工作流（submit/approve/reject + review-logs）、runs、业务对象 table-mappings、**可用的** publish-dbt / register-bi-dataset / register-lineage、持久化（2 个 Liquibase changelog + 存量数据）、配套前端。
- 平台已具备**物化能力**：`generateArtifacts`→`DbtFileService` 写 dbt 项目树 + `{{ ref }}`（`SemanticModelingService.java:904,1534-1567,1890-1893`），dts-metrics 苦于的"物化桥断裂"在平台侧本不存在。
- dts-metrics（尤其 v2.2.3 基线）仍是较薄原型；其价值在治理"亮点"逻辑（受控 DSL、ELT 分层准入、图形工作台 UX），而非基础设施。

## 生态约束（不可脱离）
dts-metrics/语义层坐落在 ELT + OpenMetadata 生态之上（详见记忆 `dts-metrics-elt-ecosystem`）：
- **Addax**(dts-ingestion) 入湖 → ODS；**dbt**(dts-platform `services/dts-dbt/`) 转换 STG/DWD/DWS/ADS；**Airflow** 编排；**OpenMetadata**(已部署) 结构元数据真源（平台只读镜像 `om_asset_cache`）。
- 治理（分类/owner/域/术语/标准）= 平台本地 DB；血缘双库割裂（ingestion 写 OM、平台 OpenLineage 落本地）。
- 整合**不重造**目录/血缘/编排/转换——这些生态已有；只把"语义建模治理"收口到平台。

## 阶段
| 阶段 | 内容 | 价值 | 风险 |
|------|------|------|------|
| **SP-1** | 受控建模逻辑移植（受控 DSL + ELT 分层准入） | 治理亮点不丢 | 低（后端、绞杀者并存） |
| SP-2 | 语义富化契约（schema.yml meta 角色 + catalog 透出） | 填"语义角色缺失"根因 | 中（跨 dbt + catalog） |
| SP-3 | React Flow 图形工作台移植进平台前端 | 保留分析师建模 UX | 中高（前端工作量大） |
| SP-4 | dts-metrics 服务/webapp 退役切流 | 消除双语义层 | 中（切流 + 数据/路由处置） |

## 架构边界决策（2026-06-16）
用户提出："dts-platform 单体功能越来越多，不分拆 metrics 架构师会不会有很多问题？" —— 正当关切。结论：
- **消重复 ≠ 解决巨石过载**，是两个独立问题。当前 dts-metrics 是"坏的分拆"（瘦 UI + 重复语义层 + chatty 调回平台 = distributed monolith），合并它**减少**架构债。
- **SP-1 升级为"模块化、可抽取"标准**：受控建模逻辑落进独立 `modeling` 子包/组件（`ControlledMetricDslCompiler`、`EltLayerGate`，显式接口、`semantic_*` schema 命名隔离），**不堆进 2000+ 行 `SemanticModelingService`**——今天是模块，将来可抽取为服务（modular monolith with extraction-ready seam）。
- **dts-platform 按真实领域缝解耦（元数据&目录 / ETL&编排 / 语义建模&服务 / 治理）= v2.3 大版本独立架构议题**，不在本 sprint。metrics 的最终归宿是其中"语义建模&服务"模块，但前提是先合并干净、有清晰契约。
- 抽取判据（满足才独立成服务）：独立有界上下文 + 独立生命周期 + 独立数据 owner + 独立扩展需求 + 独立团队 owner。

## 遗留处置
- **dts-metrics sprint-35b 硬化**（F1 持久化 / F2 安全对等 / F5 拆分 / F6 IT，在 `feat/sprint-35b-dts-metrics-hardening` + `main`）：dts-metrics 既定退役，**不再合入 v2.2.3、不再继续硬化**；移植取其**逻辑**（DSL/分层/图形），非其基础设施。
- 相关记忆：`dts-metrics-branch-baseline`、`dts-metrics-elt-ecosystem`、`dts-metrics-architecture-review`。
