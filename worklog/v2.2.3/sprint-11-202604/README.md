# Sprint-11: 数据开发模块 SQL IDE 重构

**时间**: 2026-04
**状态**: DONE
**类型**: Implementation（实施型，全新重写）
**目标**: 将数据开发模块的即席查询从 MVP 原生 textarea 升级为专业级 SQL IDE，对标 DataGrip / DataWorks / Superset SQL Lab，支撑分析师与数据开发工程师双角色场景。

## 背景

### 现状问题

当前即席查询模块（`source/dts-platform-webapp/src/components/sql/SqlWorkbenchExperimental.tsx`，963 行单文件）属于 MVP 级别，相比专业 SQL IDE 明显落后：

- **编辑器**：原生 `<textarea>` + 手工行号侧栏，**完全未使用项目已安装的 Monaco Editor**；无语法高亮、无补全、无快捷键
- **多查询**：不支持多 Tab，同时只能编辑一条 SQL
- **结果展示**：原生 HTML `<table>`，无虚拟滚动（行数多卡顿）、无列宽拖拽、无排序/过滤/冻结列
- **导出**：仅支持剪贴板复制，无 CSV/Excel/JSON 导出
- **执行反馈**：1500ms 硬编码轮询，无适应性退避
- **深度分析能力缺失**：无结果可视化、无透视表、无 Query Plan、无二次查询
- **后端限制**：5000 行硬限制、无 EXPLAIN 实现、无参数化查询（SQL 拼接）

### 业务诉求

1. **双角色分层**：默认简洁模式服务分析师，高级模式服务数据开发工程师，单组件 + `mode` 字段控制条件渲染
2. **引擎策略**：通用 SQL 语法高亮 + 引擎标签（不做方言语法区分），补全基于 catalog 元数据
3. **深度分析**：结果集支持专业表格 + 快速图表 + 透视表 + 二次查询（结果集即临时视图）
4. **跨设备恢复**：Tab 状态（SQL 文本、光标位置、关联执行 ID）持久化到后端，跨设备可恢复
5. **AI 插槽预留**：Activity Bar 预留 Copilot 入口，本 Sprint 不实现具体功能
6. **全量审计**：所有 SQL 执行、分页、导出、复制、二次查询、EXPLAIN 必须审计，记录重写前/后 SQL

### 改造策略

- **全新重写**：新建 `components/sql-ide/` 目录，老 `SqlWorkbenchExperimental.tsx` 保留不动
- **Feature Flag 切换**：`GLOBAL_CONFIG.enableSqlIdeV2`（前端）+ `dts.sql-ide.v2.enabled`（后端）
- **API 隔离**：新接口 `/api/sql/v2/*`，老接口 `/api/sql/*` 不动
- **回滚零成本**：关闭 flag 即回到老页面，新增表 `sql_ide_tab` 独立存在

### 布局决策

经 brainstorming 评估三种布局方案（DataGrip 三栏 / VS Code 双栏+底部 / Notion 浮动），最终采用 **VS Code 双栏+底部面板**：
- 最左 44px Activity Bar（Schema/History/Saved/Search/Copilot 图标）
- 可展开的左侧面板（默认 Schema 树）
- 中央多 Tab 编辑器（Monaco）
- 底部可拖拽高度面板（Results/Chart/Pivot/Query Plan/Log）
- 顶部轻量工具栏（数据源选择、Run 按钮、模式切换）

## Feature 列表

| ID | Feature | Task 数 | 状态 | 优先级 |
|----|---------|---------|------|--------|
| F1 | 架构骨架与 Monaco 编辑器 | 6 | DONE | P0 |
| F2 | 多 Tab 持久化 | 4 | DONE | P0 |
| F3 | Schema 浏览器与 Activity Bar | 5 | DONE | P0 |
| F4 | 专业结果表格与导出 | 5 | DONE | P0 |
| F5 | Chart / Pivot / Plan / 二次查询 | 5 | DONE | P1 |
| F6 | 简洁-高级模式与 Copilot 插槽 | 3 | DONE | P1 |

**统计**: READY=0, IN_PROGRESS=0, DONE=28, BLOCKED=0

## 技术选型

### 前端新增依赖

```
@tanstack/react-table ^8      （表格框架）
@tanstack/react-virtual ^3    （虚拟滚动）
react-arborist ^3             （虚拟树）
sql-formatter ^15             （SQL 格式化）
```
（Monaco、React Flow、ECharts、Zustand、React Query 项目已有）

### 后端新增依赖

```xml
<dependency>
  <groupId>org.duckdb</groupId>
  <artifactId>duckdb_jdbc</artifactId>
  <version>1.0.0</version>
</dependency>
```
用于二次查询（结果集即临时视图）。

### 目录结构

```
source/dts-platform-webapp/src/
├── pages/explore/
│   ├── QueryWorkbenchPage.tsx          （保留，旧版）
│   └── SqlIdePage.tsx                   （新，通过 feature flag 路由切换）
└── components/sql-ide/                  ★ 新增
    ├── SqlIde.tsx
    ├── layout/{ActivityBar,SidePanel,BottomPanel}.tsx
    ├── editor/{SqlEditor,formatter,dialects}.{ts,tsx}
    ├── editor/completion/{catalogProvider,keywordProvider}.ts
    ├── tabs/{TabBar,useTabStore}.{ts,tsx}
    ├── schema/{SchemaTree,TableDetail}.tsx
    ├── result/{ResultGrid,ResultChart,ResultPivot,ResultSubQuery,QueryPlanView,ExportMenu}.tsx
    ├── history/HistoryPanel.tsx
    ├── copilot/CopilotSlot.tsx          ★ 预留插槽
    └── hooks/{useSqlExecution,useCatalog,useResultSet}.ts

source/dts-platform/src/main/java/com/yuzhi/dts/platform/
├── web/rest/sql/
│   ├── SqlWorkbenchResource.java        （保留）
│   └── SqlIdeResource.java              ★ 新增 /api/sql/v2/*
├── service/sql/
│   ├── SqlIdeTabService.java            ★ Tab 持久化
│   ├── SqlResultStreamService.java      ★ 流式导出 + 二次查询
│   └── SqlPlanService.java              ★ EXPLAIN 封装
└── domain/sql/SqlIdeTab.java            ★ 新增实体
```

## 完成标准

- [ ] 所有 6 个 Feature 的 Task 全部 DONE
- [ ] 老 `SqlWorkbenchExperimental` 功能 100% 在新版中可用（回归基线通过）
- [ ] Feature Flag 切换可用，关闭时无任何功能变化
- [ ] 所有新增 API 覆盖单元测试与集成测试
- [ ] 关键路径 E2E 通过：打开 → 写 SQL → 执行 → 看结果 → 导出 → 关闭/恢复 Tab
- [ ] 审计日志覆盖所有要求的动作点（见 F4/T05）
- [ ] 性能指标达标：首次渲染 ≤800ms、切 Tab ≤100ms、10 万行滚动 60fps
- [ ] 内部灰度 1 周无 P0/P1 问题
- [ ] `it/README.md` 包含集成测试与性能压测证据

## 风险点

| 风险 | 影响 | 缓解 |
|---|---|---|
| DuckDB JNI 稳定性（F5） | 二次查询功能不可用 | 回退方案：用 PostgreSQL 临时表实现 |
| 大结果集内存（F4） | 5000 → 100k 行需动 `ResultSet.previewColumns` schema | Liquibase 兼容变更，老数据保持不变 |
| Monaco bundle size | 首屏 JS 体积增加 | 路由级 code-split + `monaco-editor-webpack-plugin` 按需打包 |
| Tab 同步冲突 | 多设备数据覆盖 | 以 `updatedAt` 比较，server-wins 策略 + 前端冲突提示 |

## 参考

- 设计文档（本 Sprint）: `worklog/v2.2.3/sprint-11-202604/plan.md`
- 老实现: `source/dts-platform-webapp/src/components/sql/SqlWorkbenchExperimental.tsx`
- 后端基准: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlExecutionService.java`
- 安全基准: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/security/SecuritySqlRewriter.java`
