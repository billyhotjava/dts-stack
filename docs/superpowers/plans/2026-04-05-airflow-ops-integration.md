# Airflow 运维对接体系重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一 Airflow 运维对接体系——修复日志接口 Bug、新增通用日志代理接口与 Task Instance 接口、构建全局日志预览抽屉、日志中心页面，增强任务编排/运行概览/任务实例监控三个页面。

**Architecture:** 后端先行（F6）修复 getDbtRunLog 响应格式并补充通用接口；前端构建全局 LogPreviewContext + LogPreviewDrawer（F2）作为各页面的日志预览底座；日志中心页面（F1）作为统一入口；三个现有页面（F3/F4/F5）各自增强并通过 openLogPreview() 调用抽屉。

**Tech Stack:** Spring Boot 3 / Java 21 (后端)、React 18 / TypeScript / antd / Vite (前端)、Sonner (toast)、react-router v6

---

## File Map

### Backend（新增/修改）
| 文件 | 操作 | 说明 |
|------|------|------|
| `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/AirflowClient.java` | Modify | 新增 `listTaskInstances()` 方法 |
| `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java` | Modify | 修复 `getDbtRunLog` 返回类型；新增 `listTaskInstances`、`getAirflowTaskLog` 端点 |

### Frontend（新增/修改）
| 文件 | 操作 | 说明 |
|------|------|------|
| `source/dts-platform-webapp/src/components/log-preview/LogPreviewContext.tsx` | Create | Context + Provider + `useLogPreview()` hook |
| `source/dts-platform-webapp/src/components/log-preview/LogPreviewDrawer.tsx` | Create | 全局日志预览抽屉，调用日志 API |
| `source/dts-platform-webapp/src/App.tsx` | Modify | 包裹 `<LogPreviewProvider>` |
| `source/dts-platform-webapp/src/api/platformApi.ts` | Modify | 新增 `listAirflowTaskInstances`、`getAirflowTaskLog` |
| `source/dts-platform-webapp/src/api/services/opsService.ts` | Modify | 新增 `ExternalRun` 类型、`externalRuns` 查询方法 |
| `source/dts-platform-webapp/src/pages/ops/OpsLogCenterPage.tsx` | Create | 日志中心页面 |
| `source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx` | Modify | 注册 `/ops/logs` → `OpsLogCenterPage` |
| `source/dts-platform-webapp/src/pages/explore/etl/OrchestrationPage.tsx` | Modify | DAG Runs 表格加「日志」按钮 |
| `source/dts-platform-webapp/src/pages/ops/OpsOverviewPage.tsx` | Modify | 加「运行中 DAG」和「最近失败」两张卡片 |
| `source/dts-platform-webapp/src/pages/ops/OpsInstancesPage.tsx` | Modify | 表格加可展开 Task Instance 行 |

---

## Task 1: 修复 getDbtRunLog — ApiResponse 包装（HIGH 级别）

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java:303-323`

- [ ] **Step 1: 确认当前问题**

  查看当前实现：
  ```bash
  sed -n '303,325p' source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java
  ```
  预期看到：方法签名返回 `ResponseEntity<Map<String,Object>>`，成功时 `return ResponseEntity.ok(result)`。

- [ ] **Step 2: 修改方法签名和返回类型**

  将 `EtlResource.java` 中 `getDbtRunLog` 方法替换为：
  ```java
  @GetMapping("/dbt/runs/{dagRunId}/logs")
  public ApiResponse<Map<String, Object>> getDbtRunLog(
      @PathVariable String dagRunId,
      @RequestParam(defaultValue = "dbt_load") String dagId,
      @RequestParam(defaultValue = "dbt_run") String taskId,
      @RequestParam(defaultValue = "1") int tryNumber
  ) {
      if (!airflowProperties.isEnabled()) {
          return ApiResponses.error("Airflow integration is not enabled");
      }
      String log = airflowClient.getTaskInstanceLog(dagId, dagRunId, taskId, tryNumber);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("dagId", dagId);
      result.put("dagRunId", dagRunId);
      result.put("taskId", taskId);
      result.put("tryNumber", tryNumber);
      result.put("log", log != null ? log : "");
      auditService.audit("READ", "etl.dbt.logs", dagRunId);
      return ApiResponses.ok(result);
  }
  ```

  同时删除 `import org.springframework.http.ResponseEntity;` 这行（若只有该方法使用它）。

  > 检查：`grep "ResponseEntity" source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`，如还有其他用法则保留 import。

- [ ] **Step 3: 编译验证**

  ```bash
  cd source/dts-platform && mvn compile -q 2>&1 | tail -20
  ```
  预期: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

  ```bash
  git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java
  git commit -m "fix(etl): getDbtRunLog return ApiResponse instead of ResponseEntity"
  ```

---

## Task 2: 新增 AirflowClient.listTaskInstances()

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/AirflowClient.java`

- [ ] **Step 1: 查看现有方法结构**

  ```bash
  sed -n '140,175p' source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/AirflowClient.java
  ```
  确认 `getTaskInstanceLog()` 方法后的位置，作为插入点。

- [ ] **Step 2: 在 getTaskInstanceLog 方法后添加 listTaskInstances**

  在 `AirflowClient.java` 中 `getTaskInstanceLog` 方法结束后（`return "";` 后的 `}`）添加：

  ```java
  /**
   * List task instances for a DAG run.
   * GET /api/v1/dags/{dagId}/dagRuns/{dagRunId}/taskInstances
   */
  public com.fasterxml.jackson.databind.JsonNode listTaskInstances(String dagId, String dagRunId) {
      if (!properties.isEnabled()) {
          return objectMapper.createObjectNode();
      }
      if (!org.springframework.util.StringUtils.hasText(dagId) || !org.springframework.util.StringUtils.hasText(dagRunId)) {
          return objectMapper.createObjectNode();
      }
      String path = "/dags/" + dagId + "/dagRuns/" + dagRunId + "/taskInstances";
      URI uri = buildUri(path);
      try {
          HttpHeaders headers = defaultHeaders();
          headers.setAccept(java.util.List.of(org.springframework.http.MediaType.APPLICATION_JSON));
          HttpEntity<Void> entity = new HttpEntity<>(headers);
          ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
          return objectMapper.readTree(response.getBody() != null ? response.getBody() : "{}");
      } catch (HttpStatusCodeException ex) {
          LOG.warn("Airflow listTaskInstances failed status={}", ex.getStatusCode().value());
          return objectMapper.createObjectNode();
      } catch (Exception ex) {
          LOG.warn("Airflow listTaskInstances error: {}", ex.getMessage());
          return objectMapper.createObjectNode();
      }
  }
  ```

  注意：`AirflowClient` 需要有 `objectMapper` 字段。检查：
  ```bash
  grep "objectMapper\|ObjectMapper" source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/AirflowClient.java | head -5
  ```
  如果没有，在类的字段区加：
  ```java
  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
  ```

- [ ] **Step 3: 编译验证**

  ```bash
  cd source/dts-platform && mvn compile -q 2>&1 | tail -20
  ```
  预期: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

  ```bash
  git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/AirflowClient.java
  git commit -m "feat(etl): add AirflowClient.listTaskInstances()"
  ```

---

## Task 3: 新增 EtlResource 两个端点（Task Instance 列表 + 通用日志）

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`

- [ ] **Step 1: 在 triggerAirflowJob 方法后添加两个端点**

  找到 `triggerAirflowJob` 方法末尾（约 `line 489`），在其后插入：

  ```java
  @GetMapping("/airflow/jobs/{dagId}/runs/{dagRunId}/tasks")
  public ApiResponse<com.fasterxml.jackson.databind.JsonNode> listAirflowTaskInstances(
      @PathVariable String dagId,
      @PathVariable String dagRunId
  ) {
      if (!airflowProperties.isEnabled()) {
          return ApiResponses.error("Airflow integration is not enabled");
      }
      com.fasterxml.jackson.databind.JsonNode result = airflowClient.listTaskInstances(dagId, dagRunId);
      auditService.audit("READ", "etl.airflow.task-instances", dagId + "/" + dagRunId);
      return ApiResponses.ok(result);
  }

  @GetMapping("/airflow/jobs/{dagId}/runs/{dagRunId}/task-logs")
  public ApiResponse<Map<String, Object>> getAirflowTaskLog(
      @PathVariable String dagId,
      @PathVariable String dagRunId,
      @RequestParam String taskId,
      @RequestParam(defaultValue = "1") int tryNumber
  ) {
      if (!airflowProperties.isEnabled()) {
          return ApiResponses.error("Airflow integration is not enabled");
      }
      if (!org.springframework.util.StringUtils.hasText(taskId)) {
          return ApiResponses.error("taskId is required");
      }
      String log = airflowClient.getTaskInstanceLog(dagId, dagRunId, taskId, Math.max(1, tryNumber));
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("dagId", dagId);
      result.put("dagRunId", dagRunId);
      result.put("taskId", taskId);
      result.put("tryNumber", tryNumber);
      result.put("log", log != null ? log : "");
      auditService.audit("READ", "etl.airflow.task-logs", dagId + "/" + dagRunId + "/" + taskId);
      return ApiResponses.ok(result);
  }
  ```

- [ ] **Step 2: 编译验证**

  ```bash
  cd source/dts-platform && mvn compile -q 2>&1 | tail -20
  ```
  预期: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

  ```bash
  git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java
  git commit -m "feat(etl): add task-instances and generic task-logs Airflow proxy endpoints"
  ```

---

## Task 4: 前端 API — 新增 platformApi 方法 + opsService.externalRuns

**Files:**
- Modify: `source/dts-platform-webapp/src/api/platformApi.ts`
- Modify: `source/dts-platform-webapp/src/api/services/opsService.ts`

- [ ] **Step 1: 在 platformApi.ts 的 getDbtRunLog 后添加两个新方法**

  ```typescript
  // Airflow generic task log (non-dbt DAGs)
  export const getAirflowTaskLog = (
    dagId: string,
    dagRunId: string,
    taskId: string,
    tryNumber = 1,
  ) =>
    api.get<{ dagId: string; dagRunId: string; taskId: string; tryNumber: number; log: string }>({
      url: `/etl/airflow/jobs/${encodeURIComponent(dagId)}/runs/${encodeURIComponent(dagRunId)}/task-logs`,
      params: { taskId, tryNumber },
    });

  // Airflow task instances for a DAG run
  export const listAirflowTaskInstances = (dagId: string, dagRunId: string) =>
    api.get<{ task_instances: AirflowTaskInstance[] }>({
      url: `/etl/airflow/jobs/${encodeURIComponent(dagId)}/runs/${encodeURIComponent(dagRunId)}/tasks`,
    });

  export type AirflowTaskInstance = {
    task_id: string;
    dag_id: string;
    dag_run_id: string;
    state?: string;
    start_date?: string;
    end_date?: string;
    duration?: number;
    try_number?: number;
    operator?: string;
  };
  ```

- [ ] **Step 2: 在 opsService.ts 新增 ExternalRun 类型和查询方法**

  在 `opsService.ts` 的 `OpsInstance` 类型后添加：
  ```typescript
  export type ExternalRun = {
    id: string;
    entryKey?: string;
    artifactName?: string;
    artifactType?: string;
    externalRunId?: string;
    status?: string;
    startedAt?: string;
    finishedAt?: string;
    durationMs?: number;
    message?: string;
    dagId?: string;
    metricsJson?: string;
  };
  ```

  在 `export default { ... }` 对象中添加：
  ```typescript
  externalRuns: (params?: {
    entryKey?: string;
    status?: string;
    keyword?: string;
    limit?: number;
    startedAfter?: string;
    startedBefore?: string;
  }) => apiClient.get<ExternalRun[]>({ url: "/infra/external-runs", params }),
  ```

- [ ] **Step 3: 验证 TypeScript 类型检查**

  ```bash
  cd source/dts-platform-webapp && npx tsc --noEmit --skipLibCheck 2>&1 | head -30
  ```
  预期: 无新增错误

- [ ] **Step 4: Commit**

  ```bash
  git add source/dts-platform-webapp/src/api/platformApi.ts \
          source/dts-platform-webapp/src/api/services/opsService.ts
  git commit -m "feat(api): add getAirflowTaskLog, listAirflowTaskInstances, ExternalRun type"
  ```

---

## Task 5: 构建 LogPreviewContext（全局日志预览状态）

**Files:**
- Create: `source/dts-platform-webapp/src/components/log-preview/LogPreviewContext.tsx`

- [ ] **Step 1: 创建文件**

  ```typescript
  // source/dts-platform-webapp/src/components/log-preview/LogPreviewContext.tsx
  import { createContext, useCallback, useContext, useRef, useState } from "react";

  export type LogPreviewParams = {
    entryKey: "AIRFLOW_DAG" | "INGESTION_TASK" | "DBT_RUN";
    /** For AIRFLOW_DAG / DBT_RUN */
    dagId?: string;
    dagRunId?: string;
    taskId?: string;
    /** Must be passed when known; defaults to 1 only as last resort */
    tryNumber?: number;
    /** For INGESTION_TASK */
    ingestionTaskId?: number;
    executionId?: number;
    /** Display title override */
    title?: string;
  };

  type LogPreviewContextValue = {
    params: LogPreviewParams | null;
    open: boolean;
    openLogPreview: (p: LogPreviewParams) => void;
    closeLogPreview: () => void;
  };

  const LogPreviewContext = createContext<LogPreviewContextValue | null>(null);

  export function LogPreviewProvider({ children }: { children: React.ReactNode }) {
    const [params, setParams] = useState<LogPreviewParams | null>(null);
    const [open, setOpen] = useState(false);

    const openLogPreview = useCallback((p: LogPreviewParams) => {
      setParams(p);
      setOpen(true);
    }, []);

    const closeLogPreview = useCallback(() => {
      setOpen(false);
    }, []);

    return (
      <LogPreviewContext value={{ params, open, openLogPreview, closeLogPreview }}>
        {children}
      </LogPreviewContext>
    );
  }

  export function useLogPreview() {
    const ctx = useContext(LogPreviewContext);
    if (!ctx) throw new Error("useLogPreview must be used inside LogPreviewProvider");
    return ctx;
  }
  ```

  > 注意：React 19 的 `createContext` 直接接受 `value` prop，React 18 需用 `<LogPreviewContext.Provider value={...}>` 形式。检查项目 React 版本：
  > ```bash
  > grep '"react"' source/dts-platform-webapp/package.json | head -1
  > ```
  > 若是 React 18，改为 `<LogPreviewContext.Provider value={{ params, open, openLogPreview, closeLogPreview }}>`.

- [ ] **Step 2: 验证类型**

  ```bash
  cd source/dts-platform-webapp && npx tsc --noEmit --skipLibCheck 2>&1 | grep "LogPreviewContext" | head -10
  ```
  预期: 无报错

- [ ] **Step 3: Commit**

  ```bash
  git add source/dts-platform-webapp/src/components/log-preview/LogPreviewContext.tsx
  git commit -m "feat(log-preview): add LogPreviewContext and useLogPreview hook"
  ```

---

## Task 6: 构建 LogPreviewDrawer 组件

**Files:**
- Create: `source/dts-platform-webapp/src/components/log-preview/LogPreviewDrawer.tsx`

- [ ] **Step 1: 创建抽屉组件**

  ```typescript
  // source/dts-platform-webapp/src/components/log-preview/LogPreviewDrawer.tsx
  import { useEffect, useRef, useState } from "react";
  import { Button, Drawer, Space, Spin, Typography } from "antd";
  import { useNavigate } from "react-router";
  import { getDbtRunLog, getAirflowTaskLog } from "@/api/platformApi";
  import { useLogPreview } from "./LogPreviewContext";

  const { Text } = Typography;

  function buildLogCenterUrl(params: ReturnType<typeof useLogPreview>["params"]): string {
    if (!params) return "/ops/logs";
    const q = new URLSearchParams();
    q.set("entryKey", params.entryKey);
    if (params.dagRunId) q.set("runId", params.dagRunId);
    if (params.dagId) q.set("dagId", params.dagId);
    if (params.taskId) q.set("taskId", params.taskId);
    return `/ops/logs?${q.toString()}`;
  }

  export default function LogPreviewDrawer() {
    const { params, open, closeLogPreview } = useLogPreview();
    const navigate = useNavigate();
    const [log, setLog] = useState<string>("");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const logRef = useRef<HTMLPreElement>(null);

    useEffect(() => {
      if (!open || !params) return;
      setLog("");
      setError(null);
      setLoading(true);

      const fetch = async () => {
        try {
          let result: any;
          if (params.entryKey === "AIRFLOW_DAG" && params.dagId && params.taskId) {
            // Generic Airflow log (non-dbt or explicit taskId)
            result = await getAirflowTaskLog(
              params.dagId,
              params.dagRunId ?? "",
              params.taskId,
              params.tryNumber ?? 1,
            );
          } else if ((params.entryKey === "DBT_RUN" || params.entryKey === "AIRFLOW_DAG") && params.dagRunId) {
            // dbt default log (dbt_run task)
            result = await getDbtRunLog(params.dagRunId, {
              dagId: params.dagId,
              taskId: params.taskId ?? "dbt_run",
              tryNumber: params.tryNumber ?? 1,
            });
          } else {
            setError("不支持的日志类型或参数不完整");
            return;
          }
          const logText = typeof result === "string" ? result : (result?.log ?? "");
          setLog(logText);
          // scroll to bottom
          setTimeout(() => {
            if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight;
          }, 50);
        } catch (e: any) {
          setError(e?.message ?? "日志加载失败");
        } finally {
          setLoading(false);
        }
      };
      void fetch();
    }, [open, params]);

    const title = params?.title
      ?? `[${params?.entryKey ?? ""}] ${params?.dagId ?? ""}${params?.taskId ? ` / ${params.taskId}` : ""}`;

    return (
      <Drawer
        title={title}
        open={open}
        onClose={closeLogPreview}
        width={680}
        footer={
          <Space>
            <Button
              type="link"
              onClick={() => {
                closeLogPreview();
                navigate(buildLogCenterUrl(params));
              }}
            >
              在日志中心打开 →
            </Button>
            {params?.tryNumber && params.tryNumber > 1 && (
              <Text type="secondary">当前查看第 {params.tryNumber} 次尝试</Text>
            )}
          </Space>
        }
      >
        {loading && (
          <div className="flex h-40 items-center justify-center">
            <Spin tip="加载日志中..." />
          </div>
        )}
        {error && !loading && (
          <Text type="danger">{error}</Text>
        )}
        {!loading && !error && (
          <pre
            ref={logRef}
            style={{
              background: "#1e1e1e",
              color: "#d4d4d4",
              fontFamily: "monospace",
              fontSize: 12,
              lineHeight: 1.6,
              padding: 16,
              borderRadius: 6,
              maxHeight: "calc(100vh - 200px)",
              overflowY: "auto",
              overflowX: "auto",
              whiteSpace: "pre-wrap",
              wordBreak: "break-all",
            }}
          >
            {log || "（日志为空）"}
          </pre>
        )}
      </Drawer>
    );
  }
  ```

- [ ] **Step 2: 验证类型**

  ```bash
  cd source/dts-platform-webapp && npx tsc --noEmit --skipLibCheck 2>&1 | grep "LogPreviewDrawer\|log-preview" | head -10
  ```
  预期: 无报错

- [ ] **Step 3: Commit**

  ```bash
  git add source/dts-platform-webapp/src/components/log-preview/LogPreviewDrawer.tsx
  git commit -m "feat(log-preview): add LogPreviewDrawer component"
  ```

---

## Task 7: 注册 LogPreviewProvider + Drawer 到 App

**Files:**
- Modify: `source/dts-platform-webapp/src/App.tsx`

- [ ] **Step 1: 修改 App.tsx**

  将 `App.tsx` 的 import 区增加：
  ```typescript
  import { LogPreviewProvider } from "./components/log-preview/LogPreviewContext";
  import LogPreviewDrawer from "./components/log-preview/LogPreviewDrawer";
  ```

  将 `<MotionLazy>{children}</MotionLazy>` 改为：
  ```tsx
  <LogPreviewProvider>
    <MotionLazy>{children}</MotionLazy>
    <LogPreviewDrawer />
  </LogPreviewProvider>
  ```

  > `LogPreviewDrawer` 用了 `useNavigate`，必须在 Router 内部渲染。检查 App 是否在 Router 内部：
  > ```bash
  > grep -r "BrowserRouter\|RouterProvider\|createBrowserRouter" source/dts-platform-webapp/src/main.tsx
  > ```
  > 若 Router 在 `main.tsx` 包裹了 App，则 LogPreviewProvider 和 Drawer 在 App 内部没问题。
  > 若 Router 在 App 内部，则 LogPreviewProvider/Drawer 必须在 Router 标签**内**。

- [ ] **Step 2: 验证构建**

  ```bash
  cd source/dts-platform-webapp && npx vite build 2>&1 | tail -10
  ```
  预期: `✓ built in`

- [ ] **Step 3: Commit**

  ```bash
  git add source/dts-platform-webapp/src/App.tsx
  git commit -m "feat(log-preview): register LogPreviewProvider and LogPreviewDrawer in App"
  ```

---

## Task 8: 创建 OpsLogCenterPage（日志中心）

**Files:**
- Create: `source/dts-platform-webapp/src/pages/ops/OpsLogCenterPage.tsx`
- Modify: `source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx`

- [ ] **Step 1: 创建页面文件**

  ```typescript
  // source/dts-platform-webapp/src/pages/ops/OpsLogCenterPage.tsx
  import { useEffect, useRef, useState } from "react";
  import { useSearchParams } from "react-router";
  import { Button, Card, DatePicker, Input, Select, Space, Table, Tag, Typography } from "antd";
  import type { ColumnsType } from "antd/es/table";
  import dayjs from "dayjs";
  import { PageHeader } from "@/components/page-header";
  import { EmptyState } from "@/components/empty-state";
  import opsService, { type ExternalRun } from "@/api/services/opsService";
  import { getDbtRunLog, getAirflowTaskLog } from "@/api/platformApi";

  const { Text } = Typography;
  const { RangePicker } = DatePicker;

  const STATUS_TAG_COLOR: Record<string, string> = {
    SUCCESS: "green",
    FAILED: "red",
    RUNNING: "blue",
    SUBMITTED: "cyan",
  };

  const ENTRY_OPTIONS = [
    { label: "全部类型", value: "" },
    { label: "Airflow DAG", value: "AIRFLOW_DAG" },
    { label: "入湖任务", value: "INGESTION_TASK" },
    { label: "dbt 任务", value: "DBT_RUN" },
  ];

  const STATUS_OPTIONS = [
    { label: "全部状态", value: "" },
    { label: "运行中", value: "RUNNING" },
    { label: "成功", value: "SUCCESS" },
    { label: "失败", value: "FAILED" },
  ];

  export default function OpsLogCenterPage() {
    const [searchParams] = useSearchParams();
    const [records, setRecords] = useState<ExternalRun[]>([]);
    const [loading, setLoading] = useState(false);
    const [keyword, setKeyword] = useState("");
    const [entryKey, setEntryKey] = useState(searchParams.get("entryKey") ?? "");
    const [status, setStatus] = useState(searchParams.get("status") ?? "");
    const [expandedRunId, setExpandedRunId] = useState<string | null>(searchParams.get("runId"));
    const [logContent, setLogContent] = useState<Record<string, string>>({});
    const [logLoading, setLogLoading] = useState<Record<string, boolean>>({});
    const [logTryNumber, setLogTryNumber] = useState<Record<string, number>>({});
    const logRef = useRef<HTMLPreElement>(null);

    const loadRecords = async () => {
      setLoading(true);
      try {
        const list = await opsService.externalRuns({
          entryKey: entryKey || undefined,
          status: status || undefined,
          keyword: keyword.trim() || undefined,
          limit: 100,
        });
        setRecords(Array.isArray(list) ? list : []);
      } catch {
        // handled by interceptor
      } finally {
        setLoading(false);
      }
    };

    useEffect(() => { void loadRecords(); }, [entryKey, status]);

    const loadLog = async (record: ExternalRun) => {
      const runId = record.externalRunId ?? record.id;
      const tryNum = logTryNumber[runId] ?? 1;
      setLogLoading((prev) => ({ ...prev, [runId]: true }));
      try {
        let logText = "";
        if (record.entryKey === "AIRFLOW_DAG" || record.entryKey === "DBT_RUN") {
          const result: any = await getDbtRunLog(runId, {
            dagId: record.dagId,
            taskId: "dbt_run",
            tryNumber: tryNum,
          });
          logText = typeof result === "string" ? result : (result?.log ?? "");
        } else {
          logText = "（该类型日志暂不支持直接查看，请前往对应任务详情页）";
        }
        setLogContent((prev) => ({ ...prev, [runId]: logText }));
        setTimeout(() => { if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight; }, 50);
      } catch (e: any) {
        setLogContent((prev) => ({ ...prev, [runId]: `[错误] ${e?.message ?? "日志加载失败"}` }));
      } finally {
        setLogLoading((prev) => ({ ...prev, [runId]: false }));
      }
    };

    const columns: ColumnsType<ExternalRun> = [
      { title: "任务名称", dataIndex: "artifactName", render: (v) => v || "-" },
      {
        title: "类型",
        dataIndex: "entryKey",
        width: 140,
        render: (v) => <Tag>{v || "-"}</Tag>,
      },
      {
        title: "状态",
        dataIndex: "status",
        width: 110,
        render: (v) => <Tag color={STATUS_TAG_COLOR[v] ?? "default"}>{v || "-"}</Tag>,
      },
      { title: "DAG ID", dataIndex: "dagId", width: 180, render: (v) => v || "-" },
      {
        title: "开始时间",
        dataIndex: "startedAt",
        width: 170,
        render: (v) => (v ? dayjs(v).format("MM-DD HH:mm:ss") : "-"),
      },
      {
        title: "结束时间",
        dataIndex: "finishedAt",
        width: 170,
        render: (v) => (v ? dayjs(v).format("MM-DD HH:mm:ss") : "-"),
      },
      {
        title: "耗时",
        dataIndex: "durationMs",
        width: 100,
        render: (v) => (v != null ? `${(v / 1000).toFixed(1)}s` : "-"),
      },
      {
        title: "操作",
        width: 160,
        render: (_, record) => {
          const runId = record.externalRunId ?? record.id;
          const isExpanded = expandedRunId === runId;
          const isLoading = logLoading[runId];
          return (
            <Space size="small">
              <Button
                type="link"
                size="small"
                loading={isLoading}
                onClick={() => {
                  if (isExpanded) {
                    setExpandedRunId(null);
                  } else {
                    setExpandedRunId(runId);
                    if (!logContent[runId]) void loadLog(record);
                  }
                }}
              >
                {isExpanded ? "收起日志" : "查看日志"}
              </Button>
            </Space>
          );
        },
      },
    ];

    return (
      <div className="space-y-6 px-6 py-6">
        <PageHeader title="日志查看" />
        <Card
          extra={
            <Space wrap>
              <Input
                placeholder="搜索任务名称..."
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                onPressEnter={() => void loadRecords()}
                style={{ width: 200 }}
              />
              <Select
                value={entryKey}
                options={ENTRY_OPTIONS}
                onChange={setEntryKey}
                style={{ width: 150 }}
              />
              <Select
                value={status}
                options={STATUS_OPTIONS}
                onChange={setStatus}
                style={{ width: 130 }}
              />
              <Button onClick={() => void loadRecords()}>刷新</Button>
            </Space>
          }
        >
          <Table
            rowKey={(r) => r.id}
            columns={columns}
            dataSource={records}
            loading={loading}
            locale={{ emptyText: <EmptyState title="暂无运行记录" description="当前筛选条件下没有日志记录。" /> }}
            expandable={{
              expandedRowKeys: expandedRunId ? [records.find((r) => (r.externalRunId ?? r.id) === expandedRunId)?.id ?? ""] : [],
              showExpandColumn: false,
              expandedRowRender: (record) => {
                const runId = record.externalRunId ?? record.id;
                const content = logContent[runId];
                const tryNum = logTryNumber[runId] ?? 1;
                return (
                  <div style={{ padding: "8px 0" }}>
                    <Space style={{ marginBottom: 8 }}>
                      <Text type="secondary">尝试次数:</Text>
                      <Input
                        type="number"
                        min={1}
                        max={10}
                        value={tryNum}
                        style={{ width: 70 }}
                        onChange={(e) => {
                          const n = Number(e.target.value);
                          if (n >= 1) setLogTryNumber((prev) => ({ ...prev, [runId]: n }));
                        }}
                      />
                      <Button size="small" onClick={() => void loadLog(record)}>
                        重新加载
                      </Button>
                    </Space>
                    <pre
                      ref={logRef}
                      style={{
                        background: "#1e1e1e",
                        color: "#d4d4d4",
                        fontFamily: "monospace",
                        fontSize: 12,
                        lineHeight: 1.6,
                        padding: 16,
                        borderRadius: 6,
                        maxHeight: 400,
                        overflowY: "auto",
                        overflowX: "auto",
                        whiteSpace: "pre-wrap",
                        wordBreak: "break-all",
                        margin: 0,
                      }}
                    >
                      {logLoading[runId] ? "加载中..." : (content ?? "点击「查看日志」加载")}
                    </pre>
                  </div>
                );
              },
            }}
          />
        </Card>
      </div>
    );
  }
  ```

- [ ] **Step 2: 注册路由覆盖**

  在 `dynamic-resolver.tsx` 的 `PATH_COMPONENT_OVERRIDES` 对象中添加：
  ```typescript
  "/ops/logs": "/pages/ops/OpsLogCenterPage",
  ```

- [ ] **Step 3: 验证构建**

  ```bash
  cd source/dts-platform-webapp && npx vite build 2>&1 | tail -10
  ```
  预期: `✓ built in`

- [ ] **Step 4: Commit**

  ```bash
  git add source/dts-platform-webapp/src/pages/ops/OpsLogCenterPage.tsx \
          source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx
  git commit -m "feat(ops): add OpsLogCenterPage and register /ops/logs route"
  ```

---

## Task 9: 增强 OrchestrationPage — DAG Runs 表格加日志按钮

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/explore/etl/OrchestrationPage.tsx`

- [ ] **Step 1: 查看 DAG Runs 表格的当前实现**

  ```bash
  grep -n "runColumns\|AirflowRun\|操作\|retry\|重试" source/dts-platform-webapp/src/pages/explore/etl/OrchestrationPage.tsx | head -20
  ```

- [ ] **Step 2: 在文件顶部 import 区添加**

  ```typescript
  import { useLogPreview } from "@/components/log-preview/LogPreviewContext";
  ```

- [ ] **Step 3: 在组件内部 hook 调用区添加**

  在 `OrchestrationPage` 函数体内（state 声明处）添加：
  ```typescript
  const { openLogPreview } = useLogPreview();
  ```

- [ ] **Step 4: 在 DAG Runs 的表格列定义中添加日志操作列**

  找到渲染 DAG runs 的表格（含 `AirflowRun` 数据），在操作列（或新建操作列）添加「日志」按钮：

  ```typescript
  {
    title: "操作",
    width: 160,
    render: (_: unknown, run: AirflowRun) => {
      const isDbt = Boolean(selectedDag?.dagId?.includes("_dbt_") || selectedDag?.dagId?.includes("dbt"));
      return (
        <Space size="small">
          {/* 日志：仅 dbt DAG 或已知 taskId 时显示，其他 DAG 展开 task instance 查看 */}
          {isDbt && run.runId && (
            <Button
              type="link"
              size="small"
              onClick={() =>
                openLogPreview({
                  entryKey: "AIRFLOW_DAG",
                  dagId: selectedDag?.dagId,
                  dagRunId: run.runId,
                  taskId: "dbt_run",
                  tryNumber: 1,
                  title: `日志 — ${selectedDag?.dagId} / ${run.runId}`,
                })
              }
            >
              日志
            </Button>
          )}
          {/* 已有的重试按钮保持不变 */}
        </Space>
      );
    },
  }
  ```

  > 变量名 `selectedDag` 可能与实际代码不同。查看：
  > ```bash
  > grep -n "selectedDag\|currentDag\|activeDag" source/dts-platform-webapp/src/pages/explore/etl/OrchestrationPage.tsx | head -10
  > ```
  > 根据实际变量名调整。

- [ ] **Step 5: 验证构建**

  ```bash
  cd source/dts-platform-webapp && npx vite build 2>&1 | tail -5
  ```

- [ ] **Step 6: Commit**

  ```bash
  git add source/dts-platform-webapp/src/pages/explore/etl/OrchestrationPage.tsx
  git commit -m "feat(orchestration): add log preview button for dbt DAG runs"
  ```

---

## Task 10: 增强 OpsOverviewPage — Airflow 运行卡片

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/ops/OpsOverviewPage.tsx`

- [ ] **Step 1: 在文件顶部 import 区添加**

  ```typescript
  import { useLogPreview } from "@/components/log-preview/LogPreviewContext";
  import opsService, { type ExternalRun, /* existing imports */ } from "@/api/services/opsService";
  import dayjs from "dayjs";
  ```

- [ ] **Step 2: 在 OpsOverviewPage 函数体内添加 state 和数据加载**

  ```typescript
  const { openLogPreview } = useLogPreview();
  const [runningDags, setRunningDags] = useState<ExternalRun[]>([]);
  const [failedDags, setFailedDags] = useState<ExternalRun[]>([]);
  const [dagCardsLoading, setDagCardsLoading] = useState(false);

  const loadDagCards = async () => {
    setDagCardsLoading(true);
    try {
      const [running, failed] = await Promise.all([
        opsService.externalRuns({ entryKey: "AIRFLOW_DAG", status: "RUNNING", limit: 10 }),
        opsService.externalRuns({ entryKey: "AIRFLOW_DAG", status: "FAILED", limit: 10 }),
      ]);
      setRunningDags(Array.isArray(running) ? running : []);
      setFailedDags(Array.isArray(failed) ? failed : []);
    } catch {
      // handled by interceptor
    } finally {
      setDagCardsLoading(false);
    }
  };
  ```

  在现有 `useEffect(() => { void loadData(); }, [...])` 旁边添加：
  ```typescript
  useEffect(() => { void loadDagCards(); }, []);
  ```

- [ ] **Step 3: 在 devCenterTab 的告警概览 Card 下方添加两张卡片**

  在 `devCenterTab` 的 JSX 中，`<Card title="告警概览">` 之后添加：

  ```tsx
  <Card
    title="当前运行中 DAG"
    size="small"
    loading={dagCardsLoading}
    extra={<Button size="small" onClick={() => void loadDagCards()}>刷新</Button>}
  >
    {runningDags.length > 0 ? (
      <Table
        size="small"
        pagination={false}
        rowKey="id"
        dataSource={runningDags}
        columns={[
          { title: "DAG", dataIndex: "dagId", render: (v) => v || "-" },
          { title: "任务", dataIndex: "artifactName", render: (v) => v || "-" },
          {
            title: "开始时间",
            dataIndex: "startedAt",
            render: (v) => (v ? dayjs(v).format("MM-DD HH:mm:ss") : "-"),
          },
          {
            title: "操作",
            width: 80,
            render: (_: unknown, r: ExternalRun) => (
              <Button
                type="link"
                size="small"
                onClick={() =>
                  openLogPreview({
                    entryKey: "AIRFLOW_DAG",
                    dagId: r.dagId,
                    dagRunId: r.externalRunId ?? undefined,
                    taskId: "dbt_run",
                    tryNumber: 1,
                  })
                }
              >
                日志
              </Button>
            ),
          },
        ]}
      />
    ) : (
      <EmptyState title="当前没有运行中的 DAG" description="所有 DAG 均已完成或未触发。" />
    )}
  </Card>

  <Card
    title="最近失败 DAG Run"
    size="small"
    loading={dagCardsLoading}
  >
    {failedDags.length > 0 ? (
      <Table
        size="small"
        pagination={false}
        rowKey="id"
        dataSource={failedDags}
        columns={[
          { title: "DAG", dataIndex: "dagId", render: (v) => v || "-" },
          { title: "任务", dataIndex: "artifactName", render: (v) => v || "-" },
          {
            title: "结束时间",
            dataIndex: "finishedAt",
            render: (v) => (v ? dayjs(v).format("MM-DD HH:mm:ss") : "-"),
          },
          {
            title: "耗时",
            dataIndex: "durationMs",
            render: (v) => (v != null ? `${(v / 1000).toFixed(1)}s` : "-"),
          },
          {
            title: "操作",
            width: 80,
            render: (_: unknown, r: ExternalRun) => (
              <Button
                type="link"
                size="small"
                onClick={() =>
                  openLogPreview({
                    entryKey: "AIRFLOW_DAG",
                    dagId: r.dagId,
                    dagRunId: r.externalRunId ?? undefined,
                    taskId: "dbt_run",
                    tryNumber: 1,
                  })
                }
              >
                日志
              </Button>
            ),
          },
        ]}
      />
    ) : (
      <EmptyState title="最近没有失败的 DAG 运行" description="当前时间窗口内无失败记录。" />
    )}
  </Card>
  ```

- [ ] **Step 4: 验证构建**

  ```bash
  cd source/dts-platform-webapp && npx vite build 2>&1 | tail -5
  ```

- [ ] **Step 5: Commit**

  ```bash
  git add source/dts-platform-webapp/src/pages/ops/OpsOverviewPage.tsx
  git commit -m "feat(ops-overview): add running and failed Airflow DAG cards"
  ```

---

## Task 11: 增强 OpsInstancesPage — Task Instance 展开行

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/ops/OpsInstancesPage.tsx`

- [ ] **Step 1: 在文件顶部添加 imports**

  ```typescript
  import { useLogPreview } from "@/components/log-preview/LogPreviewContext";
  import { listAirflowTaskInstances, type AirflowTaskInstance } from "@/api/platformApi";
  import { useNavigate } from "react-router";
  import dayjs from "dayjs";
  ```

- [ ] **Step 2: 在组件内添加 hook 调用和 task instance 状态**

  ```typescript
  const { openLogPreview } = useLogPreview();
  const navigate = useNavigate();
  const [taskInstances, setTaskInstances] = useState<Record<string, AirflowTaskInstance[]>>({});
  const [taskLoading, setTaskLoading] = useState<Record<string, boolean>>({});
  ```

- [ ] **Step 3: 添加 loadTaskInstances 函数**

  ```typescript
  const loadTaskInstances = async (record: OpsInstance) => {
    const runId = record.id;
    if (!record.dagId || !record.externalRunId) return;
    setTaskLoading((prev) => ({ ...prev, [runId]: true }));
    try {
      const result: any = await listAirflowTaskInstances(record.dagId, record.externalRunId);
      const instances: AirflowTaskInstance[] = Array.isArray(result?.task_instances)
        ? result.task_instances
        : [];
      setTaskInstances((prev) => ({ ...prev, [runId]: instances }));
    } catch {
      setTaskInstances((prev) => ({ ...prev, [runId]: [] }));
    } finally {
      setTaskLoading((prev) => ({ ...prev, [runId]: false }));
    }
  };
  ```

- [ ] **Step 4: 更新主表格的「日志/备注」列，加入日志和日志中心链接**

  将现有 `日志/备注` 列替换为：
  ```typescript
  {
    title: "操作",
    width: 180,
    render: (_: unknown, record: OpsInstance) => {
      const isDbt = record.entryKey === "DBT_RUN" ||
        (record.entryKey === "AIRFLOW_DAG" && record.dagId?.includes("dbt"));
      return (
        <Space size="small">
          {isDbt && record.externalRunId && (
            <Button
              type="link"
              size="small"
              onClick={() =>
                openLogPreview({
                  entryKey: "AIRFLOW_DAG",
                  dagId: record.dagId,
                  dagRunId: record.externalRunId ?? undefined,
                  taskId: "dbt_run",
                  tryNumber: 1,
                })
              }
            >
              日志
            </Button>
          )}
          <Button
            type="link"
            size="small"
            onClick={() =>
              navigate(
                `/ops/logs?entryKey=${record.entryKey ?? ""}&runId=${record.externalRunId ?? record.id}`,
              )
            }
          >
            日志中心
          </Button>
        </Space>
      );
    },
  },
  ```

- [ ] **Step 5: 给 Table 添加 expandable 配置**

  将 `<Table ... />` 改为：
  ```tsx
  <Table
    rowKey={(record) => record.id}
    columns={columns}
    dataSource={records}
    loading={loading}
    expandable={{
      rowExpandable: (record) =>
        record.entryKey === "AIRFLOW_DAG" && Boolean(record.dagId) && Boolean(record.externalRunId),
      onExpand: (expanded, record) => {
        if (expanded && !taskInstances[record.id]) {
          void loadTaskInstances(record);
        }
      },
      expandedRowRender: (record) => {
        const instances = taskInstances[record.id] ?? [];
        const isLoading = taskLoading[record.id];
        return (
          <Table
            size="small"
            rowKey="task_id"
            loading={isLoading}
            pagination={false}
            dataSource={instances}
            locale={{ emptyText: "暂无 Task Instance 数据" }}
            columns={[
              { title: "Task ID", dataIndex: "task_id", render: (v) => v || "-" },
              {
                title: "状态",
                dataIndex: "state",
                width: 110,
                render: (v) => (
                  <Tag
                    color={
                      v === "success" ? "green"
                      : v === "failed" ? "red"
                      : v === "running" ? "blue"
                      : "default"
                    }
                  >
                    {v || "-"}
                  </Tag>
                ),
              },
              {
                title: "开始时间",
                dataIndex: "start_date",
                render: (v) => (v ? dayjs(v).format("MM-DD HH:mm:ss") : "-"),
              },
              {
                title: "耗时",
                dataIndex: "duration",
                width: 100,
                render: (v) => (v != null ? `${Number(v).toFixed(1)}s` : "-"),
              },
              {
                title: "尝试次数",
                dataIndex: "try_number",
                width: 90,
                render: (v) => v ?? 1,
              },
              {
                title: "操作",
                width: 140,
                render: (_: unknown, ti: AirflowTaskInstance) => (
                  <Space size="small">
                    <Button
                      type="link"
                      size="small"
                      onClick={() =>
                        openLogPreview({
                          entryKey: "AIRFLOW_DAG",
                          dagId: record.dagId,
                          dagRunId: record.externalRunId ?? undefined,
                          taskId: ti.task_id,
                          tryNumber: ti.try_number ?? 1,
                          title: `${ti.task_id} — 第 ${ti.try_number ?? 1} 次`,
                        })
                      }
                    >
                      日志
                    </Button>
                    <Button
                      type="link"
                      size="small"
                      onClick={() =>
                        navigate(
                          `/ops/logs?entryKey=AIRFLOW_DAG&runId=${record.externalRunId ?? ""}&taskId=${ti.task_id}`,
                        )
                      }
                    >
                      日志中心
                    </Button>
                  </Space>
                ),
              },
            ]}
          />
        );
      },
    }}
  />
  ```

- [ ] **Step 6: 验证构建**

  ```bash
  cd source/dts-platform-webapp && npx vite build 2>&1 | tail -5
  ```

- [ ] **Step 7: Commit**

  ```bash
  git add source/dts-platform-webapp/src/pages/ops/OpsInstancesPage.tsx
  git commit -m "feat(ops-instances): add Task Instance expandable rows with log preview"
  ```

---

## 自检 Checklist

在所有任务完成后，执行以下验证：

- [ ] **后端编译**
  ```bash
  cd source/dts-platform && mvn compile -q && echo OK
  ```

- [ ] **前端构建**
  ```bash
  cd source/dts-platform-webapp && npx vite build 2>&1 | tail -5
  ```

- [ ] **接口修复验证**：`getDbtRunLog` 返回 `{"status":"SUCCESS","data":{...}}` 格式，不再是裸 Map

- [ ] **日志抽屉验证**：在任意页面调用 `openLogPreview({entryKey:"AIRFLOW_DAG", dagId:"dbt_load", dagRunId:"...", taskId:"dbt_run", tryNumber:1})` 能打开抽屉并加载日志

- [ ] **日志中心路由验证**：浏览器访问 `/ops/logs` 渲染 `OpsLogCenterPage`

- [ ] **Task Instance 展开验证**：OpsInstancesPage 中 AIRFLOW_DAG 类型行可展开，显示子任务列表

- [ ] **运行概览卡片验证**：OpsOverviewPage 的「任务运行概览」Tab 下方出现「当前运行中 DAG」和「最近失败 DAG Run」卡片
