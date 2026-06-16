# SP-1 设计底稿：受控建模逻辑移植

**产出**: 2026-06-16 brainstorming 收敛
**范围**: 仅后端 dts-platform；绞杀者式，新受控路径与现有 permissive 路径并存。

## 1. 目标
平台 `SemanticModelingService` 成为权威后，不丢失 dts-metrics 的两项治理能力：
1. **受控派生指标 DSL（严格模式）**：拒绝 raw SQL、仅白名单函数、默认拒绝(422)、三层防御 + 方言感知 quote。
2. **ELT 分层准入**：DWS/ADS=入口、DWD=受控(需 grain+标准码)、ODS/STG=禁。

## 2. 现状（平台侧）
- `buildMetricExpression`（`SemanticModelingService.java:1628`）已支持 sum/count/count_distinct/avg/max/min/count_if/sum_if/ratio，并对 `sql/custom/expression`/default 走 `safeMetricExpression(rawExpression)` —— **允许原始 SQL**。
- `safeField`/`escapeSql`/`safeMetricExpression` 为现有清洗；需确认是否方言感知（疑非）。
- `warehouseLayer` 仅用于输出数据集（`findOrCreateCatalogDataset`），**无建模期分层准入**。

## 3. 来源（dts-metrics，移植参考）
- 受控 DSL：`MetricModelLifecycleService.compileDerivedExpression`（白名单 sum/count/count_distinct/avg/min/max/ratio/date_trunc/count_if/sum_if/case_when）+ `UNSAFE_EXPRESSION` 黑名单正则 + `safeIdentifier`/`quoteIdentifier`（postgres `"` / doris `` ` ``）+ `requiredArg`/`splitArgs`/`comparisonOperator`/`literalSql`。
- 分层准入：`MetricGraphDraftService.diagnostics`（invalid_layer/grain_mismatch/standard_code_required）+ `MetricVisualAssetResource.parseLayers`（ODS/STG 禁、DWD 需 includeDrilldown）。

## 4. 落点与组件
- **`ControlledMetricDslCompiler`**（新，`service/modeling`）：纯函数、可单测；输入 formula + dialect，输出受控 SQL；非白名单/raw/注入 → `IllegalArgumentException`（上层映射 422）。
- **`EltLayerGate`**（新，`service/modeling`）：输入资产 `warehouseLayer` + 模型节点的 grain/标准码，输出诊断；违规 → 400。
- **`SemanticModelingService`**：
  - `buildMetricExpression`：受控模式委托 `ControlledMetricDslCompiler`；PERMISSIVE 保留现状。
  - 模型/业务对象校验 + `validateModelForReview`：受控模式调用 `EltLayerGate`。
- **受控开关**：模型级属性 `governanceMode`（CONTROLLED|PERMISSIVE）；新模型默认 CONTROLLED，存量 PERMISSIVE（Liquibase 默认值保证兼容）。

## 5. 错误码（与 dts-metrics 对齐，便于前端复用）
| 场景 | HTTP | code |
|------|------|------|
| 非白名单/危险/raw 表达式 | 422 | `unsafe_expression` / `graph_validation_failed` |
| ODS/STG 作建模入口 | 400 | `invalid_layer` |
| DWD 缺 grain/主键 | 400 | `grain_mismatch` |
| DWD 缺标准码 | 400 | `standard_code_required` |

## 6. 测试
- `ControlledMetricDslCompiler` 单测：每函数 postgres+doris 编译输出、raw SQL 拒、注入串拒、标识符 quote、默认拒绝。
- `EltLayerGate` 单测：DWS/ADS 过、DWD 缺 grain/码拒、ODS/STG 拒。
- `SemanticModelingService` 集成：CONTROLLED 走严格、PERMISSIVE 字节不变。
- 黄金 SQL 比对：受控编译输出与 dts-metrics 现有黄金 SQL（`dts-metrics/src/test/resources/golden-sql/`）语义一致。

## 7. 风险/边界
- 不破坏存量 permissive 路径与既有测试（绞杀者并存）。
- 确认平台 `safeField` 方言感知性；受控编译器自带 dialect quote 以求确定性。
- 不动前端/退役（SP-3/SP-4）。
- DSL 语义须与 dts-metrics 完全一致，避免"移植漂移"（黄金 SQL 守护）。
