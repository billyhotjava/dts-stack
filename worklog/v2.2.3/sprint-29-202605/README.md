# Sprint-29: Dify 风格工作流编辑器（reactflow 抄 dify 架构）(202605)

**时间**: 2026-05
**状态**: READY
**类型**: Feature / Frontend Architecture（dts-platform-webapp + dts-platform 后端 schema）
**目标**: 把当前 ETL/数据入湖任务的"表单式配置"升级为画布式可视化编排，参照 Dify `web/app/components/workflow/` 整套架构（reactflow 之上自建 BlockSelector/CandidateNode/CustomEdge/HelpLine/Panel/DSL 等子系统），全程严格遵守 Chrome 95 兼容性约束。

## 背景

### 起因

- 当前数据入湖/ETL 编排页 (`OrchestrationPage.tsx`) 仅是 Airflow DAG 列表 + 外链跳转，**无可视化编排能力**。
- 已有 `@xyflow/react` v12 画布（SemanticModelCanvas、VisualFlowCanvas、QueryPlanView）作为只读/简单编辑用途，但缺少节点库面板、拖拽虚影、对齐参考线、节点配置抽屉、DSL 序列化等"工业级编辑器"必备能力。
- 调研结论：Dify workflow 模块用的是老 `reactflow`（catalog 版本管理，未升级到 `@xyflow/react` v12），但其"强大"不在底层库，而在 30+ 节点类型 + 10+ 子系统的工程化沉淀。
- 决策：**底层保留 `@xyflow/react` v12（与 SemanticModelCanvas 同栈），抄 Dify 在其之上的工程架构**。

### 隐藏 bug

- `@xyflow/react@12.10.2` 内部使用 `structuredClone()`（Chrome 98+ 才支持），**Chrome 95 上一旦触发 connection drop 即崩**。
- 当前 SemanticModelCanvas / VisualFlowCanvas 用户量小未爆出，但新画布将成主流入口，**必须先在 F0 修掉**。

### Chrome 95 硬约束

| ❌ 禁用 | ✅ 替代方案 |
|---|---|
| `structuredClone()` | `@ungap/structured-clone` polyfill (F0-T01) |
| `Array.toReversed/.toSorted` | `[...arr].reverse()` / `[...arr].sort()` |
| `Object.hasOwn` | `Object.prototype.hasOwnProperty.call` |
| CSS `:has()` | JS 计算 className |
| CSS `@layer` | 已有 `unwrapCssLayers` postcss |
| `oklch()` / `color-mix()` | hex / hsl + sass mix |
| View Transitions / `inert` | 直接换页 / `pointer-events:none + aria-hidden` |

每个 Feature 在落地前必须 grep 检查上述禁用 API。

## Feature 列表

| ID | Feature | 优先级 | Task 数 | 状态 | 依赖 |
|----|---------|--------|---------|------|------|
| F0 | Chrome 95 前置修复（structuredClone polyfill）| P0 | 1 | READY | - |
| F1 | 基础画布与 zustand store | P0 | 7 | READY | F0 |
| F2 | BlockSelector 节点库 + CandidateNode 拖拽体验 | P0 | 4 | READY | F1 |
| F3 | ETL 最小节点集（6 类节点）| P0 | 7 | READY | F1, F2 |
| F4 | Panel 配置抽屉 + DSL 序列化 + 接入点 | P0 | 6 | READY | F3 |
| F5 | 高级特性（iteration/loop subflow + 右键菜单 + 快捷键 + 撤销重做 + 便签）| **P0** | 6 | READY | F4 |

**总计**: 6 个 Feature，31 个 Task。

## 接入点策略（已确认）

✅ **方案 A**：**替代** `OrchestrationPage.tsx`，直接作为新版数据入湖任务编排页（保留原 Airflow DAG 列表 Tab 作为"运行实例"视图，新画布作为"编排"主视图）。

- 路由不变（`/explore/etl/orchestration`），降低用户迁移成本。
- F4-T06 落地：将原页面拆为「编排画布 Tab」+「运行实例列表 Tab」，老链接不破。

## 后端 DSL 字段策略（已确认）

✅ **方案 A**：dts-platform 后端在 `IngestionTask` 实体上新增 `graph_dsl: jsonb` 字段。

- 与任务 1:1 绑定，最小侵入；含 schema 版本号 `dslVersion`，未来字段演化用 migration。
- 不另建 `workflow_graph` 表（暂不支持版本历史；如有需要后续单独 sprint）。
- F4-T05 落地：Liquibase changelog 新增列 + Hibernate 字段 + GET/PUT API 扩展。

## Phase 5 范围裁剪（已确认）

✅ **保留** iteration（多表批量同步）+ loop（增量同步直到追上）+ 右键菜单 + 快捷键 + 撤销重做 + 便签。

❌ **本期不做**：嵌套子流程模板库复用（用户场景下尚不需要"模板共享中心"）。

> 用户场景：「有增量同步和多张表批量处理需求」 → iteration 与 loop 是核心而非可选。

## 完成标准

- [ ] F0-F4 全部 DONE，画布在 Chrome 95 / Chrome 109 / Firefox 102 / Safari 15.4 真机 + Lighthouse 模拟全部冒烟通过
- [ ] 用户能在新画布上：从节点库拖出 6 类节点 → 拖拽自动连边 → 配置每节点参数 → 保存为 DSL → 后端持久化 → 重新加载完整复现
- [ ] DSL JSON schema 文档 + 反序列化幂等性测试 + 节点交互 e2e 测试覆盖率 ≥ 80%
- [ ] `OrchestrationPage` / `TransformCreatePage` 接入或独立路由可达
- [ ] F5 视情况推迟到下一个 Sprint，不阻塞主流程交付
- [ ] Chrome 95 兼容性 checklist 在每个 Feature 完成前 grep 验证（无 `structuredClone` 直接调用、无 ES2023 数组方法、无现代 CSS 函数）

## 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| xyflow v12 后续版本继续引入 ES2023+ API | F0 polyfill 不够 | 锁定 12.10.2，新增依赖前 grep 扫禁用 API |
| Dify zustand store 体量大，全盘抄过来认知负担重 | F1 工时超出 | 只抄 nodes/edges/ui 三层 slice，collaboration/datasets-detail-store 等领域无关切片不抄 |
| Tailwind 4 在 chrome 95 编译产物可能用 `@layer` | 样式失效 | 现有 `legacyCssFallbacks` postcss 已处理，新写 CSS 仍要克制 |
| 后端 graph_dsl schema 设计不当导致 F5 嵌套节点无法存储 | 重构成本高 | F4-T05 设计时预留 `children: WorkflowNode[]` 嵌套结构 |
| 用户实际不需要 iteration/loop（ETL 一般是 DAG）| F5 浪费工时 | F5 标 P1，等 F0-F4 上线收用户反馈再决定是否启动 |

## 参考资料

- Dify 工作流源码: <https://github.com/langgenius/dify/tree/main/web/app/components/workflow>
- xyflow v12 文档: <https://reactflow.dev>
- 项目 vite.config.ts 中 `legacySupportedBrowsers` 配置: `chrome >= 95`
- `@ungap/structured-clone` polyfill: <https://github.com/ungap/structured-clone>

## 相关文档

- 集成测试: `worklog/v2.2.3/sprint-29-202605/it/README.md`
- 设计资产: `worklog/v2.2.3/sprint-29-202605/assets/`
- 评审记录: `worklog/v2.2.3/sprint-29-202605/review/`
