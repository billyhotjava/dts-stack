# F5: 前端建模与 Card Editor

**优先级**: P0
**状态**: READY
**依赖**: F1, F2, F3, F4

## 目标

建**独立路由**的新 Card Editor 页面，替代老 `IndicatorsPage.tsx` 的"新建指标 Modal"工作流。包含：
- 指标树 / 维度树（只读，来自 dbt 同步）
- 模型画布（ReactFlow，受控 join）
- 派生指标公式编辑器
- SQL 实时预览
- 图形选择 + 结果渲染

**这是业务用户/分析师能真正"用起来"自助 BI 的门面。** 前端做好做坏直接决定客户体验。

## 页面路由规划

| 路由 | 用途 | 谁用 |
|---|---|---|
| `/bi/explore` | 浏览所有可用指标 | 分析师 / 业务 |
| `/bi/card/new` | 新建 Card（核心） | 分析师 |
| `/bi/card/:id/edit` | 编辑 Card | 分析师 |
| `/bi/virtual-datasets` | 虚拟数据集列表 | 分析师 |
| `/bi/virtual-datasets/new` | 新建虚拟数据集（画布） | 分析师 |
| `/bi/virtual-datasets/:id` | 编辑虚拟数据集 | 分析师 |

**老路由处理**：
- `/governance/indicators/dictionary`（`IndicatorsPage.tsx`）保留，但"新建指标"按钮改为跳转 `/bi/card/new` + 提示"指标必须在 dbt 中声明"
- 其它 6 个孤岛页 `/governance/indicator-*` 的菜单项本 Sprint 不启用

## 技术栈

- React 18 + antd v5（现有）
- **ReactFlow** 作为模型画布引擎（开源，成熟，体积合理）
- ECharts 作为结果渲染（现有）
- `apache-arrow` npm 预留但本 Sprint 默认 JSON 返回
- 代码编辑器：`monaco-editor` 只做 SQL 预览只读视图（不给 SQL 编辑器）

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 新 Card Editor 路由骨架 + 菜单整理 | P0 | READY | F2/T02 |
| T02 | 指标树 / 维度树 + 字段拖拽 | P0 | READY | T01 |
| T03 | 模型画布（ReactFlow）+ 受控 join 连线 | P0 | READY | T01, F3/T01 |
| T04 | 派生指标公式编辑器 | P0 | READY | T02, F4/T03 |
| T05 | SQL 实时预览 + 图形选择 + 结果渲染 | P0 | READY | T02, T03, T04 |

## 完成标准

- [ ] 5 条新路由全部可访问，菜单正确显示
- [ ] `IndicatorsPage` 的新建按钮改造为跳转，Modal 禁用
- [ ] 从指标树拖字段到画布能创建查询并返回结果
- [ ] 画布只能连 JoinGraphRegistry 白名单 edge，非白名单拖不动（UX 提示）
- [ ] 派生指标公式编辑器能引用、函数自动补全、错误高亮
- [ ] SQL 预览实时更新，用户可见
- [ ] 保存 Card / 保存 VirtualDataset 全流程通
- [ ] E2E 测试 3 条主路径覆盖（Playwright）
