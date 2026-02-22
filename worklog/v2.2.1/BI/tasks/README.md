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

## 3. 文件索引

- `dataease-benchmark-implementation-checklist-2026-02-22.md`
- `P0-01-visual-property-table-binding.md`
- `P0-02-spec-v2-kernel-freeze.md`
- `P0-03-designer-productivity-tools.md`
- `P0-04-release-acl-audit-sharing.md`
- `P0-05-observability-compat-performance.md`
- `P0-06-nl2sql-eval-feedback-loop.md`
- `P0-07-nl2sql-safety-retry-pipeline.md`
- `P1-01-datasource-unification-sql-mode.md`
- `P1-08-nl2sql-semantic-rag-recall.md`
- `P1-02-global-filter-interaction-engine.md`
- `P1-03-plugin-system-renderer-runtime.md`
- `P1-04-asset-center-template-theme.md`
- `P1-05-advanced-visual-components.md`
- `P1-06-analysis-explainability-layer.md`
- `P1-07-selfservice-explore-session.md`
- `P2-01-ai-screen-copilot.md`
- `P2-02-collaboration-export-mobile.md`
- `P2-03-industry-pack-hardware-bundle.md`
- `P2-04-intelligent-report-factory.md`
- `P2-05-metric-lens-semantic-governance.md`

## 4. 管理规则

- 每个任务卡必须包含：范围、子任务、验收标准、风险与回滚。
- 每个任务卡完工后必须补：代码路径、测试命令、截图证据、现场验证记录。
- 禁止跳过前置任务直接进入后续阶段（除非单独审批）。
