# BI 重构任务清单（2026-02-14）

> 范围：`dts-analytics` + `dts-analytics-webapp/modern`  
> 原则：借鉴 Metabase / Superset / DataEase 设计思想，不复制源码。  
> 状态定义：`planned` / `in-progress` / `done` / `blocked`

## 1. 阶段总览

| 阶段 | 目标 | 预计周期 | 状态 |
|---|---|---:|---|
| P0 | 补齐商用最小闭环（可设计、可发布、可排障） | 3-5 周 | done |
| P1 | 形成成熟产品能力（可复用、可扩展、可交付） | 4-6 周 | in-progress |
| P2 | 形成差异化竞争力（AI + 行业化 + 企业级） | 6-10 周 | in-progress |

## 2. 任务顺序（强制）

1. `P0-01` 视觉属性与表格绑定补齐（先解决“看得见、配得准”）
2. `P0-02` 统一内核协议 Spec v2 冻结
3. `P0-03` 设计器生产力工具（对齐/分布/分组/撤销）
4. `P0-04` 发布、权限、审计、安全分享闭环
5. `P0-05` 可观测、兼容、性能基线
6. `P1-01` 数据源统一与 SQL 模式（Metabase+Superset 思路融合）
7. `P1-02` 全局筛选器与联动引擎（Superset Native Filter 思路）
8. `P1-03` 插件化渲染体系与组件市场（Superset + DataEase 思路）
9. `P1-04` 模板/主题/资产中心（DataEase 思路）
10. `P1-05` 高级可视化组件集（地图/静态表/富文本/容器）
11. `P2-01` AI 大屏 Copilot（NL2SQL -> NL2Viz -> ScreenSpec）
12. `P2-02` 企业协作与多端适配
13. `P2-03` 行业包与硬件一体化交付

## 3. 文件索引

- `P0-01-visual-property-table-binding.md`
- `P0-02-spec-v2-kernel-freeze.md`
- `P0-03-designer-productivity-tools.md`
- `P0-04-release-acl-audit-sharing.md`
- `P0-05-observability-compat-performance.md`
- `P1-01-datasource-unification-sql-mode.md`
- `P1-02-global-filter-interaction-engine.md`
- `P1-03-plugin-system-renderer-runtime.md`
- `P1-04-asset-center-template-theme.md`
- `P1-05-advanced-visual-components.md`
- `P2-01-ai-screen-copilot.md`
- `P2-02-collaboration-export-mobile.md`
- `P2-03-industry-pack-hardware-bundle.md`

## 4. 管理规则

- 每个任务卡必须包含：范围、子任务、验收标准、风险与回滚。
- 每个任务卡完工后必须补：代码路径、测试命令、截图证据、现场验证记录。
- 禁止跳过前置任务直接进入后续阶段（除非单独审批）。
