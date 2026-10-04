# DTS Metric Visualization Center Refactor Design

## Objective

Refactor the DTS Platform "语义与指标中心" around the new `dts-metric-visualization-development` skill. The refactor must improve the product flow from DWD 明细数据 to metric definitions, DWS/ADS datasets, dbt artifacts, BI registration, lineage, and run monitoring. Frontend work comes first; backend changes follow only after the UI flow and contracts are stable.

## Current State

The active UI is under `source/dts-platform-webapp/src/pages/metrics/semantic`. It already has separate pages for overview, subject domains, business objects, metric design, DWS/ADS datasets, publish, and runs. Legacy `/modeling/semantic-center/*` routes redirect into `/metrics/semantic/*`.

There is also an unused 2215-line `SemanticWorkspacePage.tsx` that duplicates much of the newer page flow. It should not receive new product behavior. If no references remain, it can be removed after the new pages share the missing logic.

The API client `semanticModelingApi.ts` already covers subject domains, objects, mappings, dimensions, metrics, models, bindings, generated artifacts, review, publish, BI registration, lineage, and runs.

## Product Flow

The frontend should present one operational workflow:

1. 主题域映射: reuse governance domains first, but allow developer-created domains.
2. 业务对象 Join: build the business object from DWD detail models and explicit join mappings.
3. 指标可视化配置: define dimensions and metrics in business terms, with formula JSON generated from field choices.
4. DWS/ADS 数据集: combine dimensions and metrics into either reusable DWS summaries or page/API-oriented ADS datasets.
5. 审核发布与血缘: submit for engineer review, publish dbt, register BI dataset, write lineage.
6. 模型运行监控: run and observe published DWS/ADS models.

## Frontend Refactor Design

Create a small shared semantic modeling layer for code that is repeated across pages:

- Dataset normalization from catalog/platform API payloads.
- DWD input detection.
- Governance domain flattening.
- Safe metric/dimension code generation.
- Numeric field detection.
- Default formula JSON generation aligned with the skill reference:
  - Numeric fields become `aggregation/sum`.
  - Non-numeric fields become `aggregation/count_distinct`.
- Model layer labels and descriptions for DWS versus ADS.

Keep page components focused on their workflow step:

- `SemanticSubjectsPage`: domain mapping and DWD inventory.
- `SemanticObjectsPage`: object table mapping and Join canvas.
- `SemanticMetricDesignerPage`: field pool, dimension draft, metric draft, formula preview.
- `SemanticDatasetsPage`: DWS/ADS composition and generated artifacts.
- `SemanticPublishPage`: review, publish, BI registration, lineage.
- `SemanticRunsPage`: run triggering and status updates.

Do not introduce a new visual language. Use existing Ant Design, `PageHeader`, `CompactTable`, and `VisualFlowCanvas` patterns.

## Backend Follow-Up Design

After the frontend refactor, backend changes should focus on contracts that improve generated model quality:

- Validate formula JSON shape and reject malformed formulas.
- Persist richer metric口径 fields when schema supports them or introduce migrations when needed.
- Strengthen DWS/ADS generation rules around grain, null handling, zero division, and duplicate-count risk.
- Add API-level tests for model generation, publish gating, and formula validation.

## Validation Strategy

Frontend-first validation:

- Unit tests for semantic helper functions.
- Targeted Vitest run for the new helper tests.
- `pnpm build` in `source/dts-platform-webapp` after page changes.

Backend follow-up validation:

- GitNexus impact before editing Java symbols.
- `source/dts-platform/mvnw -q -Dmaven.repo.local=/tmp/codex-m2 test` or targeted Maven tests for touched services/resources.

## Out Of Scope For First Frontend Pass

- Replacing Superset or building a full BI designer.
- Changing the backend schema before the frontend contract is clarified.
- Large visual redesign of the analytics shell.
- Reworking unrelated governance indicator pages.
