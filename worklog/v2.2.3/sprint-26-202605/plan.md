# Sprint-26 Implementation Plan

## Phase 1: 指标模块所有权迁移

1. 新建 `source/dts-platform-webapp/src/pages/metrics/`。
2. 将 `/metrics/center`、`/metrics/dictionary` 的路由目标迁到 `pages/metrics`。
3. 将 `/metrics/semantic*` 的路由目标迁到 `pages/metrics/semantic`。
4. 保留旧治理和建模路径兼容，不改变菜单路径。
5. 补 `metrics-module-smoke.sh` 验证 route ownership。

## Phase 2: 语义建模真实拆页

1. 从 `SemanticModelingCenterPage` 中提取 `SemanticModelingLayout`。
2. 提取 `useSemanticModelingData` 负责基础数据加载。
3. 按子页拆分：
   - `SemanticOverviewPage`
   - `SemanticSubjectsPage`
   - `SemanticObjectsPage`
   - `SemanticMetricDesignerPage`
   - `SemanticDatasetsPage`
   - `SemanticPublishPage`
   - `SemanticRunsPage`
4. 旧 `pages/modeling/Semantic*` 只作为兼容 wrapper。

## Phase 3: 血缘工作台真实拆页

1. 从 `LineagePage` 中提取 `LineageWorkbenchLayout`。
2. 提取 `useLineageImpact`、`useLineageDiff`、`useLineageExport`、`useLineageGraphLayout`。
3. 按子页拆分：
   - `LineageImpactPage`
   - `LineageGraphPage`
   - `LineageColumnsPage`
   - `LineageImportPage`
   - `LineageDiffPage`
4. 旧 `catalog/lineage` 作为默认 impact 入口。

## Phase 4: 约束与验收

1. DWD/DWS/ADS 选择器加语义层过滤和提示。
2. 发布前校验模型类型，业务发布只允许 DWS/ADS。
3. 补真实 API smoke：语义主题域、业务对象、模型生成、血缘写入。
4. 保留截图 smoke，确保客户可见路径稳定。

