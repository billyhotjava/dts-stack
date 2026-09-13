# Sprint-43: 语义层整合 Phase 2 — 语义富化贯通

**时间**: 2026-06
**状态**: READY
**类型**: Architecture Consolidation / Implementation（dts-platform catalog + dbt 约定 + 消费端）
**实施分支**: 续 `feat/semantic-consolidation`（或新建 `feat/semantic-enrichment`）

## 目标

把 dbt 列级 `meta`（`semantic_type` + `grain` + `standard_code` + time 角色）**贯通** dbt → OM/catalog 列同步 → `CatalogAssetColumnContract` → schema-contract/assets-v2 → 建模消费，一举解锁：① Sprint-41 F3 的**源表层级 gating**、② dts-metrics **visual-assets 空列族**根因、③ DWD **标准码强制**。

## 背景

### 关键发现：契约已存在，只是没贯通
SP-2 不是"发明语义角色契约"——dbt schema.yml **已有** `meta.semantic_type` 约定（`services/dts-dbt/models/dws/semantic/schema.yml` 每列已声明 `dimension`/`metric`）。但 `CatalogAssetSchemaContract` 只透出 `columns/columnCount/schemaSource`，**不透 semantic_type**。所以根因是"**已有 meta 未贯通到 catalog 与消费端**"，SP-2 范围从"发明"收窄为"贯通 + 小幅扩展"。

### 这是整合大计划 SP-2
- ✅ SP-1（Sprint-41）受控建模逻辑移植（受控 DSL + ELT 分层闸）—— DONE。
- **SP-2（本 sprint）** 语义富化贯通 —— 解锁 SP-1 F3 的源表 gating + visual-assets 列族。
- SP-3 React Flow 工作台移植；SP-4 dts-metrics 退役；v2.3 dts-platform 领域解耦评审。

### 解锁的下游缺口
- **Sprint-41 F3-T02 落地说明**记录：源表层级 gating + DWD 标准码强制"依赖 SP-2"。本 sprint F4 接通。
- **dts-metrics review**（记忆 [[dts-metrics-elt-ecosystem]]）：`MetricVisualAssetResource` 列族硬编码 `List.of()`——因上游 catalog 不透列族；本 sprint F3 透出后即可填。

## Feature 列表

| ID | Feature | Task 数 | 状态 | 优先级 |
|----|---------|---------|------|--------|
| F1 | dbt meta 语义契约标准化（semantic_type/grain/standard_code/time） | 2 | READY | P1 |
| F2 | catalog 列同步捕获 meta（含 OM-meta 前置 spike） | 3 | READY | P0 |
| F3 | schema-contract + assets-v2 透出列族 | 2 | READY | P0 |
| F4 | 接通消费端（F3 源表 gating + visual-assets 列族） | 2 | READY | P0 |

**依赖**：F2 依赖 F1 契约定义；F3 依赖 F2 捕获；F4 依赖 F3 透出。**F2-T00 spike 是全 sprint 前置**。

## 完成标准
- [ ] dbt meta 契约规范文档化：`semantic_type ∈ dimension|metric|time`、model 级 `grain`、列级 `standard_code`。
- [ ] catalog 列同步捕获列 meta；`CatalogAssetColumnContract` 携 `semanticType`/`standardCode`。
- [ ] schema-contract + assets-v2 透出 `dimensionColumns/metricColumns/timeColumns/grain/standardCodes`（由列 meta 派生）。
- [ ] Sprint-41 F3 `enforceLayerGate` 接入真实源表层级 + 标准码（解锁源表"禁 ODS/STG"+ DWD 标准码强制）。
- [ ] dts-metrics visual-assets 列族不再空（或平台建模消费端拿到列族）。

## 非目标
- 不动 SP-1 已落的受控 DSL / 分层闸组件逻辑（只补它们的输入）。
- 不做 React Flow 工作台移植（SP-3）与 dts-metrics 退役（SP-4）。
- 不重写 OM ingestion（若 spike 发现 OM 已抓 meta 则直接读镜像）。

## 风险/前置
- **F2-T00 spike（前置）**：OM 的 dbt ingestion 是否已把 `meta.semantic_type` 抓进 OM（作 tag/custom property）？
  - 已抓 → F2 从 OM 镜像（`om_column_cache`）读，最省。
  - 未抓 → F2 直 parse dbt schema.yml（经 `DbtFileService`）或扩 OM ingestion（重，尽量避免）。
- 跨 dbt 约定 + OM 同步 + catalog 契约 + 消费端，属中等风险跨层改动——绞杀者/增量推进、每步 clean 验证。

## 相关材料
- SP-2 设计底稿: `assets/sp2-semantic-enrichment-design.md`
- 整合路线图: `worklog/v2.2.3/sprint-41-202606/assets/semantic-consolidation-roadmap.md`
- 上游 SP-1: `worklog/v2.2.3/sprint-41-202606/README.md`（F3 落地说明记录本 sprint 依赖）
- 集成测试: `it/README.md`
