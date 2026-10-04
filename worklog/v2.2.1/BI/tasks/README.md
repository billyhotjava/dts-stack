# BI 重构任务清单（2026-02-14）

> 范围：`dts-analytics` + `dts-analytics-webapp/modern`  
> 原则：借鉴 Metabase / Superset / DataEase 设计思想，不复制源码。  
> 状态定义：`planned` / `in-progress` / `done` / `blocked`

## 1. 阶段总览

| 阶段 | 目标 | 预计周期 | 状态 |
|---|---|---:|---|
| P0 | 补齐商用最小闭环（含 NL2SQL 可评估、可执行基线） | 3-5 周 | done |
| P1 | 形成成熟产品能力（可复用、可扩展、可交付） | 4-6 周 | done |
| P2 | 形成差异化竞争力（AI + 行业化 + 企业级） | 6-10 周 | done |
| P3 | 对标竞品补齐差距 + Chrome 95 兼容性强化 | 8-12 周 | planned |

## 2. 任务顺序（强制）

1. `P0-01` 视觉属性与表格绑定补齐（先解决“看得见、配得准”）
2. `P0-02` 统一内核协议 Spec v2 冻结
3. `P0-03` 设计器生产力工具（对齐/分布/分组/撤销）
4. `P0-04` 发布、权限、审计、安全分享闭环
5. `P0-05` 可观测、兼容、性能基线
6. `P0-06` NL2SQL 评测集与失败回流闭环（先把质量量化）
7. `P0-07` NL2SQL 安全校验与自修复重试（先把可执行率做上去）
8. `P1-01` 数据源统一与 SQL 模式（Metabase+Superset 思路融合）
9. `P1-08` NL2SQL 语义召回（Schema RAG + 词典 + few-shot）
10. `P1-02` 全局筛选器与联动引擎（Superset Native Filter 思路）
11. `P1-03` 插件化渲染体系与组件市场（Superset + DataEase 思路）
12. `P1-04` 模板/主题/资产中心（DataEase 思路）
13. `P1-05` 高级可视化组件集（地图/静态表/富文本/容器）
14. `P1-06` 分析解释层（可解释查询、口径、过滤影响）
15. `P1-07` 自助分析会话（问题-探索-结论闭环）
16. `P2-01` AI 大屏 Copilot（NL2SQL -> NL2Viz -> ScreenSpec）
17. `P2-02` 企业协作与多端适配
18. `P2-03` 行业包与硬件一体化交付
19. `P2-04` 智能报告工厂（模板化生成与分发）
20. `P2-05` 指标语义透视台（版本、血缘、权限）

## 3. P3 任务顺序（竞品对标 + 兼容性）

> 来源：与 DataEase / DataV / GoView / AJ-Report / FlyFish 五款开源竞品全面对比后的差距改进清单。
> 约束：所有 P3 任务必须通过 Chrome 95 兼容性检查（P3-00）。

**Sprint 1 — 核心图表扩展**
21. `P3-00` Chrome 95 兼容性基线强化（跨切面，必须先完成）
22. `P3-01` 组合图与高级图表类型（柱线混合/堆叠/词云/矩形树/旭日图/瀑布图）
23. `P3-02` 地图可视化增强（气泡/热力/流向/散点 + 省级预设）
24. `P3-03` 行业模板库扩充（20+ 模板，覆盖 6 个行业）

**Sprint 2 — 体验优化**
25. `P3-04` 可视化字段映射面板（拖拽字段到 X/Y/颜色轴）
26. `P3-05` 图表标注与阈值线（辅助线/标记区域/条件着色）
27. `P3-06` 嵌入分享增强（iframe/密码/参数透传/自定义 URL）
28. `P3-07` 联动可视化配置简化（拓扑图/快捷向导/sourcePath 提示）
29. `P3-08` 富文本编辑器组件（Tiptap WYSIWYG）

**Sprint 3 — 企业能力**
30. `P3-09` 版本历史与配置 diff 对比
31. `P3-10` 定时快照与报告 API（自动截图/邮件分发）
32. `P3-11` 大屏多页轮播
33. `P3-12` 行列级数据权限（RLS + 列脱敏）

**Sprint 4 — 差异化**
34. `P3-13` AI 图表推荐与智能配色
35. `P3-14` 组件市场与社区共享
36. `P3-15` 3D 可视化（地球/柱状图/散点图）

## 4. 文件索引

### P0-P2（已完成）
- `P0-01-visual-property-table-binding.md`
- `P0-02-spec-v2-kernel-freeze.md`
- `P0-03-designer-productivity-tools.md`
- `P0-04-release-acl-audit-sharing.md`
- `P0-05-observability-compat-performance.md`
- `P0-06-nl2sql-eval-feedback-loop.md`
- `P0-07-nl2sql-safety-retry-pipeline.md`
- `P1-01-datasource-unification-sql-mode.md`
- `P1-02-global-filter-interaction-engine.md`
- `P1-03-plugin-system-renderer-runtime.md`
- `P1-04-asset-center-template-theme.md`
- `P1-05-advanced-visual-components.md`
- `P1-06-analysis-explainability-layer.md`
- `P1-07-selfservice-explore-session.md`
- `P1-08-nl2sql-semantic-rag-recall.md`
- `P2-01-ai-screen-copilot.md`
- `P2-02-collaboration-export-mobile.md`
- `P2-03-industry-pack-hardware-bundle.md`
- `P2-04-intelligent-report-factory.md`
- `P2-05-metric-lens-semantic-governance.md`

### P3（竞品对标，已规划）
- `P3-00-chrome95-compat-baseline.md`
- `P3-01-combo-chart-and-advanced-chart-types.md`
- `P3-02-map-visualization-enhancement.md`
- `P3-03-industry-template-library.md`
- `P3-04-visual-field-mapping-panel.md`
- `P3-05-chart-annotation-threshold.md`
- `P3-06-embed-share-enhancement.md`
- `P3-07-linkage-visual-config.md`
- `P3-08-richtext-editor-component.md`
- `P3-09-version-history-diff.md`
- `P3-10-scheduled-snapshot-api.md`
- `P3-11-multi-page-carousel.md`
- `P3-12-row-column-level-permission.md`
- `P3-13-ai-chart-recommend-autocolor.md`
- `P3-14-component-marketplace.md`
- `P3-15-3d-visualization.md`

### 其他
- `dataease-benchmark-implementation-checklist-2026-02-22.md`

## 5. 管理规则

- 每个任务卡必须包含：范围、子任务、验收标准、风险与回滚。
- 每个任务卡完工后必须补：代码路径、测试命令、截图证据、现场验证记录。
- 禁止跳过前置任务直接进入后续阶段（除非单独审批）。
