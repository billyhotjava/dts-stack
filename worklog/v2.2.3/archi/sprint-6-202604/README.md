# Sprint-6: Airflow 运维对接体系重构 (202604)

**状态**: IN_PROGRESS
**类型**: Implementation（实施型）
**目标**: 完善 platform 与 Airflow 的对接体系，覆盖任务编排、运行概览、任务实例监控三个页面，统一日志查看入口

## 背景

当前 Airflow 后端集成已完善（AirflowClient、DbtDagService、OpsRunSyncScheduler 等），但前端存在以下不足：
1. 任务编排页面无法查看执行日志
2. 运行概览页面缺少 Airflow 实时运行信息
3. 任务实例监控页面只有 DAG Run 级别，无法展开到 Task Instance
4. 日志查看分散在各页面，没有统一入口
5. 昨天新增的日志显示功能被回退（存在 git stash 中）

## 设计决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 日志入口 | 运维中心统一入口 + 各页面抽屉预览 | 避免多套日志 UI，后续可扩展其他微服务日志 |
| 交互形态 | 独立页面 + 快捷抽屉 (方案 C) | 日志中心完整功能，其他页面轻量预览 |
| 数据源范围 | Airflow + Ingestion | ExternalRunLog 已有两种数据，零成本接入 |
| Task Instance | 可展开到 Task 级别 | 需后端新增代理接口 |
| 运行概览增强 | 现有 Tab 嵌入 2 个卡片 | 避免与实例监控页重复 |

---

## F1: 日志中心页面

### F1/T01: 创建 OpsLogCenterPage 页面骨架

**文件**: `src/pages/ops/OpsLogCenterPage.tsx`
**路由**: `/ops/logs`

页面结构:
- 顶部筛选栏: 入口类型 Select (AIRFLOW_DAG / INGESTION_TASK / DBT_RUN) + 状态 Select + 关键词 Input + 时间范围 RangePicker
- 主表格: artifactName, entryKey(Tag), status(Tag 带颜色), dagId, startedAt, finishedAt, durationMs, 操作列
- URL 参数驱动: `?entryKey=&runId=&status=` 支持其他页面跳转定位

数据源: `GET /infra/external-runs` (已有)

### F1/T02: 实现日志内容展示区

点击表格行的「查看日志」按钮后:
- 表格下方展开日志面板（或行内展开）
- 暗色背景 (#1e1e1e) + monospace 字体
- 支持关键词搜索高亮
- 自动滚动到底部
- Airflow 日志: 调用 `GET /etl/dbt/runs/{dagRunId}/logs`
- Ingestion 日志: 调用现有 ingestion 执行日志接口

### F1/T03: 注册路由和菜单

- 在运维中心模块下注册 `/ops/logs` 路由
- 菜单项: 「日志查看」

---

## F2: 日志预览抽屉

### F2/T01: 创建 LogPreviewDrawer 组件

**文件**: `src/components/log-preview/LogPreviewDrawer.tsx`

全局 API:
```ts
interface LogPreviewParams {
  entryKey: 'AIRFLOW_DAG' | 'INGESTION_TASK' | 'DBT_RUN';
  dagId?: string;
  dagRunId?: string;
  taskId?: string;
  tryNumber?: number;
  ingestionTaskId?: number;
  executionId?: number;
  title?: string;
}
```

抽屉内容:
- 标题: `[entryKey] dagId / taskId`
- 日志文本: 暗色 pre 区域, 最多 500 行
- Loading/Error 状态处理
- 底部: 「在日志中心打开」链接 → `/ops/logs?entryKey=...&runId=...`

### F2/T02: 全局注册与 Context Provider

**文件**: `src/components/log-preview/LogPreviewContext.tsx`

- 创建 `LogPreviewProvider` 包裹 App
- 暴露 `useLogPreview()` hook → `{ openLogPreview(params) }`
- 在 Layout 层挂载 `<LogPreviewDrawer />`

---

## F3: 任务编排增强

### F3/T01: DAG Runs 表格增加日志链接

在 OrchestrationPage 的 DAG Runs 表格操作列增加「日志」按钮:
- 点击调用 `openLogPreview({ entryKey: 'AIRFLOW_DAG', dagId, dagRunId: run.runId })`
- 按钮样式: 文字链接风格，和现有「重试」按钮并列

### F3/T02: 恢复 stash 中的 OrchestrationPage 改动

从 `git stash@{0}` 提取 OrchestrationPage 的日志相关代码:
- 移除页面内 Modal 实现 → 改为调用 LogPreviewDrawer
- 保留其他改动（如状态管理优化）

---

## F4: 运行概览增强

### F4/T01: 添加「当前运行中 DAG」卡片

在 OpsOverviewPage 的 devCenterTab 告警概览下方新增卡片:
- 小表格: dagId, artifactName, startedAt, duration(实时), 操作(日志链接)
- 数据源: `GET /infra/external-runs?entryKey=AIRFLOW_DAG&status=RUNNING`
- 空状态: "当前没有运行中的 DAG"

### F4/T02: 添加「最近失败 DAG Run」卡片

紧跟运行中卡片下方:
- 小表格: dagId, artifactName, finishedAt, durationMs, message(截断), 操作(日志链接)
- 数据源: `GET /infra/external-runs?entryKey=AIRFLOW_DAG&status=FAILED&limit=10`
- 空状态: "最近没有失败的 DAG 运行"

---

## F5: 任务实例监控增强

### F5/T01: 表格增加可展开行

OpsInstancesPage 表格增加 `expandable` 配置:
- 仅 entryKey=AIRFLOW_DAG 的行可展开
- 展开时调用 `GET /etl/airflow/jobs/{dagId}/runs/{dagRunId}/tasks`
- 子表格列: taskId, state(Tag), startDate, endDate, duration, tryNumber, 操作(日志链接)

### F5/T02: 子表格日志链接

每个 Task Instance 行操作列:
- 「日志」链接 → `openLogPreview({ entryKey: 'AIRFLOW_DAG', dagId, dagRunId, taskId, tryNumber })`
- 「在日志中心打开」文字链接 → `/ops/logs?...`

### F5/T03: 主表格 DAG Run 级别日志链接

DAG Run 级别行操作列的「日志」链接:
- **仅对 dbt DAG 显示**（通过 dagId 包含 `_dbt_` 或 dagId 前缀匹配判断）
- 固定查看 `taskId=dbt_run`，tryNumber 取 Task Instance 展开后的最大值；若未展开则默认 tryNumber=1
- 非 dbt DAG 不显示「日志」链接，只显示展开箭头查看 Task Instance 列表

---

## F6: 后端接口补全

> **⚠️ Code Review 问题修复（必须先于其他 Feature 落地）**

### F6/T01: 修复 getDbtRunLog 的 ApiResponse 包装（HIGH 级别）

**问题**: `EtlResource.getDbtRunLog()` 成功分支返回 `ResponseEntity<Map<String,Object>>`，
但前端 `apiClient.ts` 只按标准 `ApiResponse` 格式解包，导致 200 成功被前端当失败处理。
SqlModelingPage 和任何新日志入口都受影响。

**修复** (`EtlResource.java:303`):
```java
// 原始返回（错误）:
return ResponseEntity.ok(result);

// 修复后:
@GetMapping("/dbt/runs/{dagRunId}/logs")
public ApiResponse<Map<String, Object>> getDbtRunLog(...)
    // 成功: return ApiResponses.ok(result);
    // 失败: return ApiResponses.error("...");
    // 不可用: throw 或 return ApiResponses.error("Airflow integration is not enabled");
```

### F6/T02: 新增通用 Airflow Task Log 代理接口（MEDIUM 级别）

**问题**: 现有 `/dbt/runs/{dagRunId}/logs` 是 dbt 专用接口（默认 taskId=dbt_run），
非 dbt DAG 调用此接口会查到不存在的 task，表现为假失败。

**新增通用接口** (`EtlResource.java`):
```java
@GetMapping("/airflow/jobs/{dagId}/runs/{dagRunId}/task-logs")
public ApiResponse<Map<String, Object>> getAirflowTaskLog(
    @PathVariable String dagId,
    @PathVariable String dagRunId,
    @RequestParam String taskId,
    @RequestParam(defaultValue = "1") int tryNumber)
```
- 无默认 taskId（必填），前端必须显式传入
- tryNumber 必须从 Task Instance 数据取实际值

**AirflowClient.java** 新增 listTaskInstances:
```java
public JsonNode listTaskInstances(String dagId, String dagRunId)
// GET /api/v1/dags/{dagId}/dagRuns/{dagRunId}/taskInstances
```

**EtlResource.java** 新增 Task Instance 列表接口:
```java
@GetMapping("/etl/airflow/jobs/{dagId}/runs/{dagRunId}/tasks")
public ApiResponse<JsonNode> listTaskInstances(
    @PathVariable String dagId,
    @PathVariable String dagRunId)
```

### F6/T03: 前端日志调用统一使用 tryNumber（MEDIUM 级别）

**问题**: stash 代码调用日志接口时未传 tryNumber，retry 场景看到的是第一次尝试的日志。

**规则**:
- LogPreviewDrawer 的 `LogPreviewParams` 中 `tryNumber` 字段必填（不传则默认 1，但调用方应明确传入）
- Task Instance 展开后，取 `try_number` 字段（Airflow 返回值）传给 LogPreviewDrawer
- OrchestrationPage DAG Runs 表格：无 task instance 数据时 tryNumber=1，并在 UI 上注明「重试任务请展开查看最新日志」
- 日志中心页面：提供 tryNumber 输入框（默认 1，可手动改）

---

## Feature 列表汇总

| ID | Feature | Task 数 | 优先级 |
|----|---------|---------|--------|
| F1 | 日志中心页面 | 3 | P0 |
| F2 | 日志预览抽屉 | 2 | P0 |
| F3 | 任务编排增强 | 2 | P0 |
| F4 | 运行概览增强 | 2 | P1 |
| F5 | 任务实例监控增强 | 3 | P1 |
| F6 | 后端接口补全 | 2 | P0 |

**共计 14 个 Task**

## 依赖链

```
F6(后端接口) ──→ F2(日志抽屉) ──→ F1(日志中心)
                                ──→ F3(编排增强)
                                ──→ F4(概览增强)
                                ──→ F5(实例监控增强)
```

F6 先行（后端接口就绪），F2 次之（全局组件就绪），然后 F1/F3/F4/F5 可并行。

## 并行策略

```
第一批: F6（后端接口补全）
第二批: F2（日志抽屉组件）
第三批（并行）: F1 + F3 + F4 + F5
```
