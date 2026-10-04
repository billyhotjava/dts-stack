# SP-2 设计底稿：语义富化贯通

**产出**: 2026-06-16
**范围**: dbt 约定 + dts-platform catalog + 消费端（dts-metrics visual-assets / 平台建模）。

## 1. 问题与发现
- **缺口**：建模/可视化需要列级语义角色（dimension/metric/time）+ grain + 标准码，但 `CatalogAssetSchemaContract` 只透 `columns/columnCount/schemaSource`，不透角色；`MetricVisualAssetResource` 列族硬编码 `List.of()`；Sprint-41 F3 源表 gating 拿不到源表层级/标准码。
- **关键发现**：dbt schema.yml **已有** `meta.semantic_type` 约定。证据 `services/dts-dbt/models/dws/semantic/schema.yml`：
  ```yaml
  columns:
    - name: customer_region_...
      meta: { semantic_type: dimension }
    - name: order_amount_...
      meta: { semantic_type: metric }
  ```
- **结论**：契约已存在于 dbt，只是没贯通到 catalog 与消费端。SP-2 = **贯通 + 小幅扩展**，非发明。

## 2. 贯通链路（目标）
```
dbt schema.yml meta (semantic_type/grain/standard_code/time)
  │  F1 标准化契约
  ▼
OM dbt ingestion  ──(F2-T00 spike: 是否已抓 meta?)──┐
  │  已抓→OM tag/property                          │ 未抓→直 parse dbt schema.yml
  ▼                                                ▼
catalog 列同步 (CatalogColumnSyncService / om_column_cache)  ── F2 捕获
  │
  ▼
CatalogAssetColumnContract (+ semanticType/standardCode)     ── F2 扩字段
  │
  ▼
schema-contract + assets-v2 (派生 dimensionColumns/metricColumns/timeColumns/grain/standardCodes)  ── F3 透出
  │
  ▼
消费端：① Sprint-41 F3 enforceLayerGate 源表层级+标准码  ② dts-metrics visual-assets 列族  ── F4 接通
```

## 3. Feature/Task 概要
- **F1 dbt meta 契约标准化**：`semantic_type ∈ {dimension, metric, time}`、model 级 `meta.grain: [col,...]`、列级 `meta.standard_code`。文档 + 样例模型补全。
- **F2 catalog 捕获 meta**：
  - **T00 spike（前置）**：验证 OM dbt ingestion 是否抓 `meta`。
  - T01：`CatalogAssetColumnContract` 加 `semanticType`/`standardCode`（record 加字段，注意构造点）。
  - T02：列同步（OM 镜像或直读 dbt）填这两字段。
- **F3 透出列族**：`CatalogAssetSchemaContract` + assets-v2 由列 meta 派生 `dimensionColumns/metricColumns/timeColumns/grain/standardCodes`。
- **F4 接通消费端**：
  - 填 Sprint-41 `SemanticModelingService.enforceLayerGate` 的源表层级（catalog 查源表 warehouseLayer）+ 标准码（hasStandardCode 从列 meta），把当前置 true/false 的占位换成真实输入；解锁源表"禁 ODS/STG 建模" + DWD 标准码强制。
  - 修 `MetricVisualAssetResource` 列族（从 schema-contract 列 meta 派生，替换 `List.of()`）。

## 4. 关键决策点（实施时定）
- F2 走 OM 镜像 vs 直读 dbt——由 T00 spike 决定。
- grain 的载体：model 级 dbt meta（`meta.grain`）vs 平台 `semantic_model.grain`（Sprint-41 已有列）——倾向复用平台 grain，dbt meta 作来源同步。
- 列族派生放在 service（CatalogAssetPortalService）还是消费端——倾向 service 统一派生，消费端只读。

## 5. 验收
- 单测：列 meta 解析/派生（dimension/metric/time 分类、grain、standard_code）。
- 集成：schema-contract 返回非空列族；enforceLayerGate 用真实源表层级阻断 ODS/STG 源；visual-assets 列族非空。
- 绞杀者：无 meta 的存量列回退（不破现有 catalog 行为）。
