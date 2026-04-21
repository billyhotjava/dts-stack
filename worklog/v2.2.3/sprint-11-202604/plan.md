# Sprint-11 设计文档：SQL IDE 重构

**版本**: 2026-04-12
**状态**: ARCHIVED（原始设计基线）
**对应 Sprint**: `worklog/v2.2.3/sprint-11-202604/`

> 说明：本文保留为 Sprint-11 启动时的设计快照，便于回看最初假设与范围边界。
> 最终实现状态、收尾修复和验收证据以 `README.md` 与 `it/` 目录为准。

## 1. 背景

当前数据开发模块的即席查询（`SqlWorkbenchExperimental.tsx`，963 行单文件）属 MVP 级别：原生 `<textarea>` + 原生 `<table>`，项目已装的 Monaco Editor 未使用；无多 Tab、无虚拟滚动、无导出、无 Query Plan、无审计全覆盖。目标：重写为对标 DataGrip / DataWorks / Superset SQL Lab 的专业级 SQL IDE。

## 2. 核心决策

| 维度 | 决策 |
|---|---|
| **用户角色** | 分析师 + 开发者分层：默认简洁模式，可切高级模式。单组件 + `mode` 字段条件渲染，不搞两套组件 |
| **引擎策略** | 通用 SQL 语法高亮 + 引擎标签；补全基于 catalog 元数据而非语法差异 |
| **结果交互** | 深度分析：专业表格 + 快速图表 + 透视表 + 二次查询（DuckDB 临时视图） |
| **Tab 持久化** | 用户级持久化到后端，跨设备恢复；resultSnapshot 不持久化，按 executionId 回拉 |
| **改造策略** | 全新重写 `components/sql-ide/`，老文件保留；Feature Flag `enableSqlIdeV2` 切换 |
| **AI 能力** | 本 Sprint 只预留 Copilot 插槽 + 接口契约，不实现 |

## 3. 整体布局

采用 VS Code 式**双栏 + 底部面板**布局：

```
┌────┬────────┬───────────────────────────────┐
│ A  │ Side   │  Top bar (DS / Run / Mode)    │
│ c  │ Panel  │  ─────────────────────────────│
│ t  │        │  Tab bar                      │
│ i  │ Schema │                               │
│ v  │ / Hist │  Monaco Editor                │
│ i  │ / Save │                               │
│ t  │ / AI   │  ─────────────────────────────│
│ y  │        │  Bottom Panel                 │
│    │        │  Results | Chart | Pivot |    │
│ B  │        │  Query Plan | Log             │
│ a  │        │                               │
│ r  │        │                               │
└────┴────────┴───────────────────────────────┘
```

三个方案对比（完整评估见 brainstorming 记录）：
- A 经典三栏（DataGrip）
- **B 双栏+底部（VS Code）★ 采用**
- C 浮动面板（Notion）

## 4. 目录结构

见 Sprint README §技术选型。

## 5. Feature 拆解

| Feature | Task 数 | 关键交付 |
|---|---|---|
| **F1 架构骨架与 Monaco 编辑器** | 6 | Monaco + 补全 + 快捷键 + 格式化 + Feature Flag |
| **F2 多 Tab 持久化** | 4 | sql_ide_tab 表 + CRUD + Zustand + 三层持久化 |
| **F3 Schema 浏览器与 Activity Bar** | 5 | 惰性 catalog API + 虚拟树 + History + Saved |
| **F4 专业结果表格与导出** | 5 | tanstack-table 虚拟滚动 + 流式导出 + 13 类审计 |
| **F5 Chart/Pivot/Plan/二次查询** | 5 | ECharts + Pivot + EXPLAIN 树 + DuckDB 临时视图 + Log |
| **F6 简洁高级模式与 Copilot 插槽** | 3 | 模式切换 + Copilot 插槽 + 灰度上线压测文档 |

**合计 28 个 Task，预计 6 个 Sprint-week（约 3 个月）**。

## 6. 数据变更

| 变更 | 说明 |
|---|---|
| **新增 `sql_ide_tab`** | 用户 Tab 持久化 |
| **新增 `query_execution_chunk`** | 结果集分块存储（100k 行上限） |
| **`saved_query.folder`** | 新增列，支持虚拟分组 |
| **`query_execution.rewritten_sql`** | 新增列（若尚无），审计与 Log 展示使用 |

所有变更走 Liquibase changelog。

## 7. API 变更

新增端点统一前缀 `/api/sql/v2/*`，老 `/api/sql/*` 不动。清单见各 Task。

## 8. 前端新增依赖

```
@tanstack/react-table ^8
@tanstack/react-virtual ^3
react-arborist ^3
sql-formatter ^15
```

## 9. 后端新增依赖

```xml
<dependency>
  <groupId>org.duckdb</groupId>
  <artifactId>duckdb_jdbc</artifactId>
  <version>1.0.0</version>
</dependency>
```
（含 Apache POI，项目若已有则复用）

## 10. 非功能性要求

### 10.1 性能

| 指标 | 目标 |
|---|---|
| 首次渲染 | ≤800ms |
| 切 Tab | ≤100ms |
| Schema 树展开 | ≤300ms（命中缓存 ≤50ms） |
| 10k 行首屏 | ≤200ms |
| 100k 行滚动 | 60fps |
| Tab 防抖同步 | 2s |
| 轮询退避 | 500ms → 1.5s → 3s → 5s |

### 10.2 安全

- 复用 `SecuritySqlRewriter` 行列级安全
- 禁写操作（DROP/DELETE/UPDATE/INSERT）
- 导出频控 5 次 / 10 分钟 / 用户
- 二次查询 DuckDB 临时视图跨用户隔离
- SQL 参数化：`:param` 占位符 + PreparedStatement（替代字符串拼接）

### 10.3 审计（13 类动作）

见 F4/T20 清单。关键：**重写前/后 SQL 双记录**，含 `userId`, `deptCode`, `dataLevel`。

### 10.4 回滚

Feature Flag 一键切回老页面；新增表/API 独立存在，回滚不需跑数据迁移。

## 11. 风险

| 风险 | 缓解 |
|---|---|
| DuckDB JNI 在目标 Linux 环境兼容性 | 回退 PostgreSQL 临时表 |
| 5000 → 100k 行后 JVM 内存压力 | 流式写 chunk + SXSSFWorkbook 流式导出 |
| Monaco bundle size | 路由级 code-split + webpack 插件只打 SQL 语言 |
| Tab 同步冲突 | updatedAt 乐观锁 + server-wins + 前端提示 |
| 审计日志量增长 | 容量评估 + 审计表分区策略（交付运维） |

## 12. 原始交付里程碑（设计时假设）

| Sprint Week | 范围 |
|---|---|
| W1-2 | F1 骨架 + Monaco + Tab 持久化（F2） |
| W3-4 | F3 Schema / Activity Bar / History / Saved |
| W5-6 | F4 ResultGrid + 流式导出 + 全量审计 |
| W7-8 | F5 Chart + Pivot + Query Plan + DuckDB 二次查询 + Log |
| W9-10 | F6 简洁/高级模式 + Copilot 插槽 |
| W11-12 | 灰度 + 压测 + 文档 + GA |

## 13. 参考

- 老实现：`source/dts-platform-webapp/src/components/sql/SqlWorkbenchExperimental.tsx`
- 后端基准：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlExecutionService.java`
- 安全：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/security/SecuritySqlRewriter.java`
- 审计：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/audit/AuditService.java`
