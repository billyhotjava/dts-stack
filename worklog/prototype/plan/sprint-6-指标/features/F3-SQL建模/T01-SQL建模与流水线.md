# T01: SQL 建模 + 模板 + ModelPipeline

**优先级**: P1
**状态**: READY
**依赖**: S4

## 目标

在阶段④渲染 `SqlModelingPage`（SQL 编辑建模）、`ModelTemplatesPage`（SQL 建模模板）、`ModelPipelinePage`（建模流水线列表/流程视图），接 mock。

## 技术设计

- 文件：`app/src/stages/metrics/SqlModelingPage.tsx`、`ModelTemplatesPage.tsx`、`ModelPipelinePage.tsx`（命名对齐现网 `pages/modeling/SqlModelingPage.tsx` 等）。
- SQL 建模页：SQL 编辑面（现网同款编辑器组件或轻量占位 `textarea` + 高亮），数据来源选 S5 发布的数据集；「保存为模型」落 `sqlModelingService.saveModel`；可「预览结果」走 `olapService`（轻量占位即可）。
- 模板页：列出 SQL 建模模板（如增量/全量/视图），「使用模板」预填 SQL 骨架。
- ModelPipeline 页：以列表 + 流程占位视图呈现建模流水线（编排/依赖占位）；列表用 `CompactTable`，默认 10 条/页、`pageSize` 收敛、切 size 重置第 1 页。状态列用 Swiss 状态点 token。
- mock service：`sqlModelingService`（`listModels` / `saveModel` / `listTemplates` / `listPipeline`，均返回 `Promise<Result<T>>`，复刻现网契约形状）。
- **dbt 隐藏（关键）**：`ModelPipelinePage` 在现网原是裸 dbt 流水线入口——本原型里它只呈现**业务语义的建模流水线**（模型→依赖→运行状态），底层编译为 dbt 但**不暴露 dbt 文件树、不提供 dbt 文件编辑/直接运行**。要查底层 dbt 产物只能去 F4 T01 只读抽屉。

## 影响范围

- 新增 `app/src/stages/metrics/SqlModelingPage.tsx`、`ModelTemplatesPage.tsx`、`ModelPipelinePage.tsx`
- 新增/扩展 `app/src/mock/services/sqlModelingService.ts`（`listModels`/`saveModel`/`listTemplates`/`listPipeline`）
- mock fixtures：基于 ODS 宽表数据集的 SQL 模型样例 + 模板若干 + 流水线一条
- 阶段④ 路由 slot 注册（依赖 S1 外壳）

## 验证

- [ ] SQL 建模页可编辑并「保存为模型」，经 `sqlModelingService.saveModel` 落 mock。
- [ ] 模板页「使用模板」预填 SQL 骨架；ModelPipeline 列表 CompactTable 默认 10 条/页、切条数回第 1 页。
- [ ] `ModelPipelinePage` **不出现 dbt 文件树、不提供 dbt 文件编辑/直接运行入口**——仅业务语义流水线视图。
- [ ] Chrome 95：无 oklch/`:has()`/容器查询/subgrid；legacy 构建产物可加载。

## 完成标准

- [ ] 三页可路由可访问，SQL 建模→保存模型、模板复用、流水线展示闭环成立。
- [ ] 全部经 `sqlModelingService`（取数预览可借 `olapService`）取数，无硬编码业务数据散落组件内。
- [ ] 高级 SQL 建模路径仍守住「不暴露 dbt 文件树编辑」的隐藏约束。
