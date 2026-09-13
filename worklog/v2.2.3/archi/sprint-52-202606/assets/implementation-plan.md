# Sprint-52 指标工作台实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建 React Flow 三栏指标工作台 `/modeling/metric-workbench`，替换 6 个 SemanticXxxPage 重定向壳为真实页面，实现 DWS/ADS 建模→指标可视化→消费看板端到端闭环。

**Architecture:** 新建 `MetricWorkbenchPage`（三栏）及 `metric-workbench/` 子组件（SubjectBrowserPanel + MetricCanvas + MetricDetailPanel），复用已有 `@xyflow/react` 和 `semanticModelingApi.ts`；6 个 SemanticXxxPage 从重定向壳扩展为真实 Ant Design 页面，各自注册独立路由。

**Tech Stack:** React 18, @xyflow/react ^12.10.2, Ant Design 5, semanticModelingApi.ts (34 functions), VisualFlowCanvas (existing), node:test source-contract

## Global Constraints

- Chrome 95: 颜色用 `hsl()` 或 `#rrggbb`，禁 `oklch()` / `:has()` / `@container` / `subgrid`
- 不触碰 `services/dts-airflow/dags/addax-env-runner.jar`
- 不新增 `/v2` 路由命名空间
- `/modeling/semantic-center` 路由（MetricsServiceFrame iframe）保持不动
- 每个 Task 完成后运行 `pnpm exec tsc --noEmit`，零报错才提交
- 工作目录：`source/dts-platform-webapp/`
- source-contract baseline：10 个失败，不得增加

---

## File Map

| 操作 | 文件 |
|------|------|
| 新建 | `src/pages/modeling/MetricWorkbenchPage.tsx` |
| 新建 | `src/pages/modeling/metric-workbench/SubjectBrowserPanel.tsx` |
| 新建 | `src/pages/modeling/metric-workbench/MetricCanvas.tsx` |
| 新建 | `src/pages/modeling/metric-workbench/MetricDetailPanel.tsx` |
| 新建 | `src/pages/modeling/metric-workbench/nodes/BizObjectNode.tsx` |
| 新建 | `src/pages/modeling/metric-workbench/nodes/MetricNode.tsx` |
| 新建 | `src/pages/modeling/metric-workbench/edges/MetricBindingEdge.tsx` |
| 新建 | `src/pages/modeling/metricWorkbench.source-contract.test.ts` |
| 覆盖 | `src/pages/modeling/SemanticSubjectsPage.tsx` |
| 覆盖 | `src/pages/modeling/SemanticObjectsPage.tsx` |
| 覆盖 | `src/pages/modeling/SemanticMetricsPage.tsx` |
| 覆盖 | `src/pages/modeling/SemanticModelsPage.tsx` |
| 覆盖 | `src/pages/modeling/SemanticPublishPage.tsx` |
| 覆盖 | `src/pages/modeling/SemanticRunsPage.tsx` |
| 修改 | `src/routes/sections/dashboard/static-routes.tsx` |
| 修改 | `src/routes/sections/dashboard/dynamic-resolver.tsx` |
| 修改 | `src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts` |

---

## Task 1: 三栏骨架 + 路由注册（F1/T01）

**Files:**
- Create: `src/pages/modeling/MetricWorkbenchPage.tsx`
- Modify: `src/routes/sections/dashboard/static-routes.tsx`
- Modify: `src/routes/sections/dashboard/dynamic-resolver.tsx`
- Modify: `src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts`

**Interfaces:**
- Produces: `default export function MetricWorkbenchPage()` — 供路由使用
- Produces: `selectedId: string | null`，`setSelectedId: (id: string | null) => void` — 供 Task 2/3/4 使用

- [ ] **Step 1: 在 `dataDevelopmentWorkbench.source-contract.test.ts` 末尾追加路由断言（先让测试红）**

在文件末尾已有的最后一个 `test(...)` 块之后追加：

```typescript
test("metric workbench route is registered in static routes and dynamic resolver", () => {
    assert.match(staticRoutes, /path: "modeling\/metric-workbench"/);
    assert.match(dynamicResolver, /"\/modeling\/metric-workbench"/);
    assert.match(staticRoutes, /path: "modeling\/semantic\/subjects"/);
    assert.match(staticRoutes, /path: "modeling\/semantic\/objects"/);
    assert.match(staticRoutes, /path: "modeling\/semantic\/metrics"/);
    assert.match(staticRoutes, /path: "modeling\/semantic\/models"/);
    assert.match(staticRoutes, /path: "modeling\/semantic\/publish"/);
    assert.match(staticRoutes, /path: "modeling\/semantic\/runs"/);
});
```

- [ ] **Step 2: 运行，确认测试红**

```bash
node --test src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts 2>&1 | tail -10
```

预期：`not ok` 出现（metric workbench route is registered 失败）

- [ ] **Step 3: 新建 `src/pages/modeling/MetricWorkbenchPage.tsx`**

```tsx
import { useEffect, useState } from "react";
import { PageHeader } from "@/components/page-header";
import {
    listSemanticSubjectDomains,
    listSemanticBusinessObjects,
    listSemanticMetrics,
    type SemanticSubjectDomain,
    type SemanticBusinessObject,
    type SemanticMetric,
} from "@/api/semanticModelingApi";

export default function MetricWorkbenchPage() {
    const [domains, setDomains] = useState<SemanticSubjectDomain[]>([]);
    const [objects, setObjects] = useState<SemanticBusinessObject[]>([]);
    const [metrics, setMetrics] = useState<SemanticMetric[]>([]);
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        void (async () => {
            setLoading(true);
            const [d, o, m] = await Promise.allSettled([
                listSemanticSubjectDomains(),
                listSemanticBusinessObjects(),
                listSemanticMetrics(),
            ]);
            if (d.status === "fulfilled") setDomains(Array.isArray(d.value) ? (d.value as SemanticSubjectDomain[]) : []);
            if (o.status === "fulfilled") setObjects(Array.isArray(o.value) ? (o.value as SemanticBusinessObject[]) : []);
            if (m.status === "fulfilled") setMetrics(Array.isArray(m.value) ? (m.value as SemanticMetric[]) : []);
            setLoading(false);
        })();
    }, []);

    return (
        <div className="flex h-full flex-col" data-testid="metric-workbench-page">
            <PageHeader title="指标工作台" />
            <div className="flex flex-1 overflow-hidden">
                <div
                    style={{ width: 240, borderRight: "1px solid #e5e7eb" }}
                    className="overflow-y-auto p-2 text-sm text-gray-500"
                >
                    {loading ? "加载中..." : `主题域 ${domains.length} · 对象 ${objects.length}`}
                </div>
                <div className="flex flex-1 items-center justify-center bg-gray-50">
                    <span className="text-gray-400">
                        {loading ? "画布加载中..." : `${objects.length} 个业务对象，${metrics.length} 个指标`}
                    </span>
                </div>
                <div
                    style={{ width: 360, borderLeft: "1px solid #e5e7eb" }}
                    className="overflow-y-auto p-4 text-gray-400 text-sm"
                >
                    请在画布中选择节点
                </div>
            </div>
        </div>
    );
}
```

- [ ] **Step 4: 在 `static-routes.tsx` 第 28 行后（DbtFileBrowserPage 导入之后）追加 lazy 导入，并在路由数组中注册**

在 `const DbtFileBrowserPage = ...` 之后追加：
```tsx
const MetricWorkbenchPage = lazy(() => import("@/pages/modeling/MetricWorkbenchPage"));
const SemanticSubjectsPage = lazy(() => import("@/pages/modeling/SemanticSubjectsPage"));
const SemanticObjectsPage = lazy(() => import("@/pages/modeling/SemanticObjectsPage"));
const SemanticMetricsPage = lazy(() => import("@/pages/modeling/SemanticMetricsPage"));
const SemanticModelsPage = lazy(() => import("@/pages/modeling/SemanticModelsPage"));
const SemanticPublishPage = lazy(() => import("@/pages/modeling/SemanticPublishPage"));
const SemanticRunsPage = lazy(() => import("@/pages/modeling/SemanticRunsPage"));
```

在路由数组中 `{ path: "modeling/dbt-files", ... }` 之后追加：
```tsx
{ path: "modeling/metric-workbench", element: <S><MetricWorkbenchPage /></S> },
{ path: "modeling/semantic/subjects", element: <S><SemanticSubjectsPage /></S> },
{ path: "modeling/semantic/objects", element: <S><SemanticObjectsPage /></S> },
{ path: "modeling/semantic/metrics", element: <S><SemanticMetricsPage /></S> },
{ path: "modeling/semantic/models", element: <S><SemanticModelsPage /></S> },
{ path: "modeling/semantic/publish", element: <S><SemanticPublishPage /></S> },
{ path: "modeling/semantic/runs", element: <S><SemanticRunsPage /></S> },
```

- [ ] **Step 5: 在 `dynamic-resolver.tsx` 的 `PATH_COMPONENT_OVERRIDES` 中追加**

在 `"/modeling/dbt-files": "/pages/modeling/DbtFileBrowserPage",` 之后追加：
```typescript
"/modeling/metric-workbench": "/pages/modeling/MetricWorkbenchPage",
"/modeling/semantic/subjects": "/pages/modeling/SemanticSubjectsPage",
"/modeling/semantic/objects": "/pages/modeling/SemanticObjectsPage",
"/modeling/semantic/metrics": "/pages/modeling/SemanticMetricsPage",
"/modeling/semantic/models": "/pages/modeling/SemanticModelsPage",
"/modeling/semantic/publish": "/pages/modeling/SemanticPublishPage",
"/modeling/semantic/runs": "/pages/modeling/SemanticRunsPage",
```

- [ ] **Step 6: tsc 验证**

```bash
pnpm exec tsc --noEmit 2>&1 | tail -5
```

预期：0 errors（若报 SemanticXxxPage 导入缺失，先用临时壳：`export default function SemanticSubjectsPage() { return null; }` 解决，后续 Task 覆盖）

- [ ] **Step 7: source-contract 验证（现在应为绿）**

```bash
node --test src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts 2>&1 | tail -5
```

预期：4 tests pass（包括新增的路由断言）

- [ ] **Step 8: Commit**

```bash
git add src/pages/modeling/MetricWorkbenchPage.tsx \
        src/routes/sections/dashboard/static-routes.tsx \
        src/routes/sections/dashboard/dynamic-resolver.tsx \
        src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts
git commit -m "feat(F1/T01): 三栏指标工作台骨架 + 7条路由注册"
```

---

## Task 2: React Flow 画布节点与边（F1/T03）

**Files:**
- Create: `src/pages/modeling/metric-workbench/nodes/BizObjectNode.tsx`
- Create: `src/pages/modeling/metric-workbench/nodes/MetricNode.tsx`
- Create: `src/pages/modeling/metric-workbench/edges/MetricBindingEdge.tsx`
- Create: `src/pages/modeling/metric-workbench/MetricCanvas.tsx`
- Modify: `src/pages/modeling/MetricWorkbenchPage.tsx`

**Interfaces:**
- Consumes: `SemanticBusinessObject`, `SemanticMetric` from `@/api/semanticModelingApi`
- Produces: `<MetricCanvas objects={} metrics={} selectedId={} onNodeSelect={} />`

- [ ] **Step 1: 新建 `src/pages/modeling/metric-workbench/nodes/BizObjectNode.tsx`**

```tsx
import { Handle, Position, type NodeProps } from "@xyflow/react";

export type BizObjectNodeData = {
    objectId: string;
    name: string;
    code: string;
    tableCount: number;
};

export function BizObjectNode({ data, selected }: NodeProps) {
    const d = data as BizObjectNodeData;
    return (
        <div
            style={{
                border: selected ? "2px solid hsl(220,80%,40%)" : "2px solid hsl(220,80%,55%)",
                background: "hsl(220,95%,97%)",
                borderRadius: 8,
                padding: "10px 14px",
                minWidth: 160,
                fontSize: 13,
            }}
        >
            <div style={{ fontWeight: 600, color: "hsl(220,30%,25%)" }}>{d.name}</div>
            <div style={{ color: "hsl(220,20%,55%)", fontSize: 11, marginTop: 2 }}>
                {d.code} · {d.tableCount} 张表
            </div>
            <Handle type="source" position={Position.Right} />
            <Handle type="target" position={Position.Left} />
        </div>
    );
}
```

- [ ] **Step 2: 新建 `src/pages/modeling/metric-workbench/nodes/MetricNode.tsx`**

```tsx
import { Handle, Position, type NodeProps } from "@xyflow/react";

export type MetricNodeData = {
    metricId: string;
    name: string;
    formulaType?: string;
    status?: string;
};

const FORMULA_COLOR: Record<string, string> = {
    "aggregation/sum": "hsl(220,80%,55%)",
    "aggregation/count_distinct": "hsl(142,60%,45%)",
    "aggregation/avg": "hsl(35,85%,50%)",
};

export function MetricNode({ data, selected }: NodeProps) {
    const d = data as MetricNodeData;
    const isDraft = !d.status || d.status === "DRAFT";
    const borderColor = isDraft ? "hsl(0,0%,70%)" : "hsl(142,60%,45%)";
    const bg = isDraft ? "hsl(0,0%,98%)" : "hsl(142,80%,96%)";
    const formulaColor = d.formulaType ? (FORMULA_COLOR[d.formulaType] ?? "hsl(0,0%,60%)") : "hsl(0,0%,60%)";

    return (
        <div
            style={{
                border: selected ? `2px solid hsl(142,60%,30%)` : `2px solid ${borderColor}`,
                background: bg,
                borderRadius: 8,
                padding: "10px 14px",
                minWidth: 160,
                fontSize: 13,
            }}
        >
            <div style={{ fontWeight: 600, color: "hsl(142,30%,20%)" }}>📈 {d.name}</div>
            {d.formulaType && (
                <div
                    style={{
                        marginTop: 4,
                        fontSize: 10,
                        color: formulaColor,
                        background: "rgba(0,0,0,0.04)",
                        borderRadius: 4,
                        padding: "1px 6px",
                        display: "inline-block",
                    }}
                >
                    {d.formulaType}
                </div>
            )}
            {isDraft && (
                <div style={{ fontSize: 10, color: "hsl(0,0%,60%)", marginTop: 2 }}>DRAFT</div>
            )}
            <Handle type="target" position={Position.Left} />
        </div>
    );
}
```

- [ ] **Step 3: 新建 `src/pages/modeling/metric-workbench/edges/MetricBindingEdge.tsx`**

```tsx
import { BaseEdge, getStraightPath, type EdgeProps } from "@xyflow/react";

export function MetricBindingEdge({
    sourceX, sourceY, targetX, targetY,
}: EdgeProps) {
    const [edgePath] = getStraightPath({ sourceX, sourceY, targetX, targetY });
    return (
        <BaseEdge
            path={edgePath}
            style={{
                stroke: "hsl(142,50%,50%)",
                strokeWidth: 1.5,
                strokeDasharray: "5,3",
            }}
        />
    );
}
```

- [ ] **Step 4: 新建 `src/pages/modeling/metric-workbench/MetricCanvas.tsx`**

```tsx
import {
    ReactFlow,
    ReactFlowProvider,
    Background,
    Controls,
    MiniMap,
    type Node,
    type Edge,
    type NodeTypes,
    type EdgeTypes,
    type OnSelectionChangeFunc,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useCallback, useMemo } from "react";
import type { SemanticBusinessObject, SemanticMetric } from "@/api/semanticModelingApi";
import { BizObjectNode, type BizObjectNodeData } from "./nodes/BizObjectNode";
import { MetricNode, type MetricNodeData } from "./nodes/MetricNode";
import { MetricBindingEdge } from "./edges/MetricBindingEdge";

const NODE_TYPES: NodeTypes = {
    bizObject: BizObjectNode,
    metric: MetricNode,
};
const EDGE_TYPES: EdgeTypes = { binding: MetricBindingEdge };

interface MetricCanvasProps {
    objects: SemanticBusinessObject[];
    metrics: SemanticMetric[];
    selectedId: string | null;
    onNodeSelect: (id: string | null) => void;
}

function buildNodes(
    objects: SemanticBusinessObject[],
    metrics: SemanticMetric[],
    selectedId: string | null,
): Node[] {
    const objectNodes: Node[] = objects.map((o, i) => ({
        id: `obj-${o.id}`,
        type: "bizObject",
        position: { x: 0, y: i * 180 },
        selected: selectedId === `obj-${o.id}`,
        data: {
            objectId: o.id,
            name: o.name,
            code: o.code,
            tableCount: 0,
        } satisfies BizObjectNodeData,
    }));
    const metricNodes: Node[] = metrics.map((m, i) => ({
        id: `metric-${m.id}`,
        type: "metric",
        position: { x: 380, y: i * 140 },
        selected: selectedId === `metric-${m.id}`,
        data: {
            metricId: m.id,
            name: m.name,
            formulaType: m.formulaType,
            status: m.status,
        } satisfies MetricNodeData,
    }));
    return [...objectNodes, ...metricNodes];
}

function buildEdges(metrics: SemanticMetric[]): Edge[] {
    return metrics
        .filter((m) => m.objectId)
        .map((m) => ({
            id: `edge-${m.id}`,
            source: `obj-${m.objectId}`,
            target: `metric-${m.id}`,
            type: "binding",
        }));
}

function MetricCanvasInner({ objects, metrics, selectedId, onNodeSelect }: MetricCanvasProps) {
    const nodes = useMemo(() => buildNodes(objects, metrics, selectedId), [objects, metrics, selectedId]);
    const edges = useMemo(() => buildEdges(metrics), [metrics]);

    const handleSelectionChange: OnSelectionChangeFunc = useCallback(
        ({ nodes: selected }) => {
            onNodeSelect(selected.length > 0 ? selected[0].id : null);
        },
        [onNodeSelect],
    );

    if (objects.length === 0 && metrics.length === 0) {
        return (
            <div className="flex h-full items-center justify-center text-gray-400 text-sm">
                暂无数据，请先在主题域页创建业务对象
            </div>
        );
    }

    return (
        <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={NODE_TYPES}
            edgeTypes={EDGE_TYPES}
            fitView
            onSelectionChange={handleSelectionChange}
        >
            <Background />
            <Controls />
            <MiniMap />
        </ReactFlow>
    );
}

export function MetricCanvas(props: MetricCanvasProps) {
    return (
        <ReactFlowProvider>
            <MetricCanvasInner {...props} />
        </ReactFlowProvider>
    );
}
```

- [ ] **Step 5: 将 MetricCanvas 接入 MetricWorkbenchPage.tsx，替换画布占位区**

将 `MetricWorkbenchPage.tsx` 中的中栏占位 `<div className="flex flex-1 items-center justify-center bg-gray-50">` 替换为：

```tsx
import { MetricCanvas } from "./metric-workbench/MetricCanvas";

// 中栏:
<div className="flex-1 overflow-hidden" style={{ minHeight: 0 }}>
    <MetricCanvas
        objects={objects}
        metrics={metrics}
        selectedId={selectedId}
        onNodeSelect={setSelectedId}
    />
</div>
```

- [ ] **Step 6: tsc 验证**

```bash
pnpm exec tsc --noEmit 2>&1 | grep -c "error TS" || echo "0 errors"
```

预期：0 errors

- [ ] **Step 7: Commit**

```bash
git add src/pages/modeling/metric-workbench/
git add src/pages/modeling/MetricWorkbenchPage.tsx
git commit -m "feat(F1/T03): React Flow 画布 — BizObjectNode/MetricNode/MetricBindingEdge"
```

---

## Task 3: SubjectBrowserPanel 左栏（F1/T02）

**Files:**
- Create: `src/pages/modeling/metric-workbench/SubjectBrowserPanel.tsx`
- Modify: `src/pages/modeling/MetricWorkbenchPage.tsx`

**Interfaces:**
- Consumes: `domains: SemanticSubjectDomain[]`, `objects: SemanticBusinessObject[]`, `metrics: SemanticMetric[]`, `selectedId: string | null`, `onSelect: (id: string) => void`

- [ ] **Step 1: 新建 `src/pages/modeling/metric-workbench/SubjectBrowserPanel.tsx`**

```tsx
import { Tree, Button, Empty } from "antd";
import type { DataNode } from "antd/es/tree";
import { useRouter } from "@/routes/hooks";
import type { SemanticSubjectDomain, SemanticBusinessObject, SemanticMetric } from "@/api/semanticModelingApi";

interface SubjectBrowserPanelProps {
    domains: SemanticSubjectDomain[];
    objects: SemanticBusinessObject[];
    metrics: SemanticMetric[];
    selectedId: string | null;
    onSelect: (id: string) => void;
}

function buildTreeData(
    domains: SemanticSubjectDomain[],
    objects: SemanticBusinessObject[],
    metrics: SemanticMetric[],
): DataNode[] {
    return domains.map((domain) => {
        const domainObjects = objects.filter((o) => o.domainId === domain.id);
        return {
            key: `domain-${domain.id}`,
            title: `📁 ${domain.name}`,
            selectable: false,
            children: domainObjects.map((obj) => {
                const objMetrics = metrics.filter((m) => m.objectId === obj.id);
                return {
                    key: `obj-${obj.id}`,
                    title: `◎ ${obj.name}`,
                    children: objMetrics.map((m) => ({
                        key: `metric-${m.id}`,
                        title: `📈 ${m.name}`,
                        isLeaf: true,
                    })),
                };
            }),
        };
    });
}

export function SubjectBrowserPanel({
    domains, objects, metrics, selectedId, onSelect,
}: SubjectBrowserPanelProps) {
    const router = useRouter();
    const treeData = buildTreeData(domains, objects, metrics);

    const handleSelect = (keys: React.Key[]) => {
        if (keys.length > 0) {
            onSelect(String(keys[0]));
        }
    };

    return (
        <div className="flex h-full flex-col">
            <div className="flex-1 overflow-y-auto p-2">
                {treeData.length === 0 ? (
                    <Empty description="暂无主题域" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                ) : (
                    <Tree
                        treeData={treeData}
                        selectedKeys={selectedId ? [selectedId] : []}
                        onSelect={handleSelect}
                        defaultExpandAll
                        blockNode
                        style={{ fontSize: 12 }}
                    />
                )}
            </div>
            <div className="border-t border-gray-200 p-2 space-y-1">
                <Button
                    size="small"
                    block
                    onClick={() => router.push("/modeling/semantic/subjects")}
                >
                    管理主题域
                </Button>
                <Button
                    size="small"
                    block
                    onClick={() => router.push("/modeling/semantic/objects")}
                >
                    管理业务对象
                </Button>
            </div>
        </div>
    );
}
```

- [ ] **Step 2: 替换 MetricWorkbenchPage.tsx 左栏占位**

```tsx
import { SubjectBrowserPanel } from "./metric-workbench/SubjectBrowserPanel";

// 左栏替换为:
<div style={{ width: 240, borderRight: "1px solid #e5e7eb" }} className="overflow-hidden">
    <SubjectBrowserPanel
        domains={domains}
        objects={objects}
        metrics={metrics}
        selectedId={selectedId}
        onSelect={setSelectedId}
    />
</div>
```

- [ ] **Step 3: tsc + commit**

```bash
pnpm exec tsc --noEmit 2>&1 | grep -c "error TS" || echo "0 errors"
git add src/pages/modeling/metric-workbench/SubjectBrowserPanel.tsx \
        src/pages/modeling/MetricWorkbenchPage.tsx
git commit -m "feat(F1/T02): SubjectBrowserPanel 主题域树左栏"
```

---

## Task 4: MetricDetailPanel 右栏（F1/T04）

**Files:**
- Create: `src/pages/modeling/metric-workbench/MetricDetailPanel.tsx`
- Modify: `src/pages/modeling/MetricWorkbenchPage.tsx`

**Interfaces:**
- Consumes: `selectedId: string | null`, `objects: SemanticBusinessObject[]`, `metrics: SemanticMetric[]`, `onMetricUpdated: () => void`

- [ ] **Step 1: 新建 `src/pages/modeling/metric-workbench/MetricDetailPanel.tsx`**

```tsx
import { useState, useEffect } from "react";
import { Tabs, Select, Input, Button, Space, Tag, Empty } from "antd";
import { toast } from "sonner";
import {
    updateSemanticMetric,
    listSemanticModelRuns,
    triggerSemanticModelRun,
    publishSemanticModelToDbt,
    registerSemanticBiDataset,
    registerSemanticLineage,
    type SemanticBusinessObject,
    type SemanticMetric,
    type SemanticModelRun,
} from "@/api/semanticModelingApi";

const FORMULA_TYPE_OPTIONS = [
    { label: "aggregation/sum", value: "aggregation/sum" },
    { label: "aggregation/count_distinct", value: "aggregation/count_distinct" },
    { label: "aggregation/avg", value: "aggregation/avg" },
    { label: "aggregation/max", value: "aggregation/max" },
    { label: "aggregation/min", value: "aggregation/min" },
];

const RUN_STATUS_COLOR: Record<string, string> = {
    PENDING: "default",
    RUNNING: "processing",
    SUCCESS: "success",
    FAILED: "error",
    CANCELLED: "warning",
};

interface MetricDetailPanelProps {
    selectedId: string | null;
    objects: SemanticBusinessObject[];
    metrics: SemanticMetric[];
    onMetricUpdated: () => void;
}

function MetricFormulaTab({
    metric,
    onSaved,
}: {
    metric: SemanticMetric;
    onSaved: () => void;
}) {
    const [formulaType, setFormulaType] = useState(metric.formulaType ?? "");
    const [formulaJson, setFormulaJson] = useState(metric.formulaJson ?? "");
    const [saving, setSaving] = useState(false);

    const handleSave = async () => {
        setSaving(true);
        try {
            await updateSemanticMetric(metric.id, { formulaType, formulaJson });
            toast.success("指标已保存");
            onSaved();
        } catch {
            /* global interceptor */
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="space-y-3 p-2">
            <div>
                <div className="text-xs text-gray-500 mb-1">公式类型</div>
                <Select
                    options={FORMULA_TYPE_OPTIONS}
                    value={formulaType || undefined}
                    onChange={setFormulaType}
                    style={{ width: "100%" }}
                    placeholder="选择公式类型"
                />
            </div>
            <div>
                <div className="text-xs text-gray-500 mb-1">公式 JSON</div>
                <Input.TextArea
                    value={formulaJson}
                    onChange={(e) => setFormulaJson(e.target.value)}
                    rows={4}
                    placeholder='{"field": "amount", "type": "sum"}'
                    style={{ fontFamily: "monospace", fontSize: 12 }}
                />
            </div>
            <Button type="primary" size="small" loading={saving} onClick={handleSave} block>
                保存指标
            </Button>
        </div>
    );
}

function RunsTab({ metricId }: { metricId: string }) {
    const [runs, setRuns] = useState<SemanticModelRun[]>([]);
    const [loading, setLoading] = useState(false);
    const [triggering, setTriggering] = useState(false);
    const [publishing, setPublishing] = useState(false);

    useEffect(() => {
        void (async () => {
            setLoading(true);
            try {
                const list = await listSemanticModelRuns(metricId);
                setRuns(Array.isArray(list) ? (list as SemanticModelRun[]) : []);
            } catch {
                setRuns([]);
            } finally {
                setLoading(false);
            }
        })();
    }, [metricId]);

    const handleTrigger = async () => {
        setTriggering(true);
        try {
            await triggerSemanticModelRun(metricId, { runType: "MANUAL" });
            toast.success("运行已触发");
        } catch {
            /* global interceptor */
        } finally {
            setTriggering(false);
        }
    };

    const handlePublish = async () => {
        setPublishing(true);
        try {
            await publishSemanticModelToDbt(metricId);
            await Promise.allSettled([
                registerSemanticBiDataset(metricId),
                registerSemanticLineage(metricId),
            ]);
            toast.success("已发布 dbt 并注册 BI 数据集 + 血缘");
        } catch {
            /* global interceptor */
        } finally {
            setPublishing(false);
        }
    };

    if (loading) return <div className="p-2 text-sm text-gray-400">加载中...</div>;

    return (
        <div className="p-2 space-y-2">
            <Space>
                <Button size="small" loading={triggering} onClick={handleTrigger}>
                    触发运行
                </Button>
                <Button size="small" type="primary" loading={publishing} onClick={handlePublish}>
                    发布 dbt
                </Button>
            </Space>
            {runs.length === 0 ? (
                <Empty description="暂无运行记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
                <div className="space-y-1">
                    {runs.slice(0, 10).map((run) => (
                        <div key={run.id} className="flex items-center justify-between text-xs py-1">
                            <span className="text-gray-500">{run.startedAt?.slice(0, 16) ?? "-"}</span>
                            <Tag color={RUN_STATUS_COLOR[run.status ?? ""] ?? "default"}>
                                {run.status ?? "UNKNOWN"}
                            </Tag>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}

export function MetricDetailPanel({
    selectedId,
    objects,
    metrics,
    onMetricUpdated,
}: MetricDetailPanelProps) {
    if (!selectedId) {
        return (
            <div className="flex h-full items-center justify-center text-gray-400 text-sm p-4 text-center">
                请在画布中选择节点
            </div>
        );
    }

    if (selectedId.startsWith("obj-")) {
        const objectId = selectedId.replace("obj-", "");
        const obj = objects.find((o) => o.id === objectId);
        if (!obj) return null;
        return (
            <div className="p-4 space-y-2">
                <div className="font-semibold text-gray-700">{obj.name}</div>
                <div className="text-xs text-gray-400">{obj.code}</div>
                <div className="text-xs text-gray-500">主表: {obj.mainTable ?? "未配置"}</div>
                <div className="text-xs text-gray-500">主键: {obj.primaryKey ?? "未配置"}</div>
            </div>
        );
    }

    if (selectedId.startsWith("metric-")) {
        const metricId = selectedId.replace("metric-", "");
        const metric = metrics.find((m) => m.id === metricId);
        if (!metric) return null;
        return (
            <Tabs
                size="small"
                items={[
                    {
                        key: "formula",
                        label: "公式/维度",
                        children: <MetricFormulaTab metric={metric} onSaved={onMetricUpdated} />,
                    },
                    {
                        key: "runs",
                        label: "消费数据",
                        children: <RunsTab metricId={metricId} />,
                    },
                ]}
            />
        );
    }

    return null;
}
```

- [ ] **Step 2: 替换 MetricWorkbenchPage.tsx 右栏占位**

```tsx
import { MetricDetailPanel } from "./metric-workbench/MetricDetailPanel";

// 右栏替换为：
<div style={{ width: 360, borderLeft: "1px solid #e5e7eb" }} className="overflow-y-auto">
    <MetricDetailPanel
        selectedId={selectedId}
        objects={objects}
        metrics={metrics}
        onMetricUpdated={() => {
            // 重新加载指标列表
            void listSemanticMetrics().then((m) => {
                setMetrics(Array.isArray(m) ? (m as SemanticMetric[]) : []);
            });
        }}
    />
</div>
```

- [ ] **Step 3: tsc + commit**

```bash
pnpm exec tsc --noEmit 2>&1 | grep -c "error TS" || echo "0 errors"
git add src/pages/modeling/metric-workbench/MetricDetailPanel.tsx \
        src/pages/modeling/MetricWorkbenchPage.tsx
git commit -m "feat(F1/T04): MetricDetailPanel 右栏 Tabs（公式/消费数据）"
```

---

## Task 5: SemanticSubjectsPage 真实实现（F2/T01）

**Files:**
- Overwrite: `src/pages/modeling/SemanticSubjectsPage.tsx`

- [ ] **Step 1: 覆盖 `src/pages/modeling/SemanticSubjectsPage.tsx`**

```tsx
import { useCallback, useEffect, useState } from "react";
import { Button, Form, Input, Modal, Space, Tag } from "antd";
import { toast } from "sonner";
import { CompactTable } from "@/components/table";
import { PageHeader } from "@/components/page-header";
import type { ColumnsType } from "antd/es/table";
import {
    listSemanticSubjectDomains,
    createSemanticSubjectDomain,
    updateSemanticSubjectDomain,
    type SemanticSubjectDomain,
} from "@/api/semanticModelingApi";

const columns = (onEdit: (row: SemanticSubjectDomain) => void): ColumnsType<SemanticSubjectDomain> => [
    { title: "编码", dataIndex: "code", key: "code", width: 120 },
    { title: "名称", dataIndex: "name", key: "name" },
    { title: "描述", dataIndex: "description", key: "description", ellipsis: true },
    {
        title: "治理域", dataIndex: "governanceDomainName", key: "governanceDomainName",
        render: (v?: string) => v ? <Tag>{v}</Tag> : <span style={{ color: "#aaa" }}>未映射</span>,
    },
    {
        title: "操作", key: "actions", width: 80,
        render: (_: unknown, row: SemanticSubjectDomain) => (
            <Button type="link" size="small" onClick={() => onEdit(row)}>编辑</Button>
        ),
    },
];

export default function SemanticSubjectsPage() {
    const [domains, setDomains] = useState<SemanticSubjectDomain[]>([]);
    const [loading, setLoading] = useState(false);
    const [modalOpen, setModalOpen] = useState(false);
    const [editRow, setEditRow] = useState<SemanticSubjectDomain | null>(null);
    const [form] = Form.useForm();

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const list = await listSemanticSubjectDomains();
            setDomains(Array.isArray(list) ? (list as SemanticSubjectDomain[]) : []);
        } catch {
            /* global interceptor */
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { void load(); }, [load]);

    const handleOpen = (row?: SemanticSubjectDomain) => {
        setEditRow(row ?? null);
        form.setFieldsValue(row ?? { code: "", name: "", description: "" });
        setModalOpen(true);
    };

    const handleSubmit = async () => {
        try {
            const values = await form.validateFields();
            if (editRow) {
                await updateSemanticSubjectDomain(editRow.id, values);
                toast.success("主题域已更新");
            } else {
                await createSemanticSubjectDomain(values);
                toast.success("主题域已创建");
            }
            setModalOpen(false);
            void load();
        } catch (err: unknown) {
            if (err && typeof err === "object" && "errorFields" in err) return;
            /* global interceptor */
        }
    };

    return (
        <div className="space-y-4" data-testid="semantic-subjects-page">
            <PageHeader
                title="语义建模 · 主题域"
                action={
                    <Button
                        type="primary"
                        data-testid="semantic-subjects-create"
                        onClick={() => handleOpen()}
                    >
                        + 新建主题域
                    </Button>
                }
            />
            <CompactTable<SemanticSubjectDomain>
                rowKey="id"
                columns={columns(handleOpen)}
                dataSource={domains}
                loading={loading}
            />
            <Modal
                title={editRow ? "编辑主题域" : "新建主题域"}
                open={modalOpen}
                onCancel={() => setModalOpen(false)}
                onOk={handleSubmit}
                destroyOnClose
            >
                <Form form={form} layout="vertical" className="pt-4">
                    <Form.Item name="code" label="编码" rules={[{ required: true, message: "必填" }]}>
                        <Input placeholder="SALES" disabled={!!editRow} />
                    </Form.Item>
                    <Form.Item name="name" label="名称" rules={[{ required: true, message: "必填" }]}>
                        <Input placeholder="销售域" />
                    </Form.Item>
                    <Form.Item name="description" label="描述">
                        <Input.TextArea rows={2} />
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
}
```

- [ ] **Step 2: tsc + commit**

```bash
pnpm exec tsc --noEmit 2>&1 | grep -c "error TS" || echo "0 errors"
git add src/pages/modeling/SemanticSubjectsPage.tsx
git commit -m "feat(F2/T01): SemanticSubjectsPage 真实实现（替换重定向壳）"
```

---

## Task 6: SemanticObjectsPage 真实实现（F2/T02）

**Files:**
- Overwrite: `src/pages/modeling/SemanticObjectsPage.tsx`

- [ ] **Step 1: 覆盖 `src/pages/modeling/SemanticObjectsPage.tsx`**

```tsx
import { useCallback, useEffect, useState } from "react";
import { Button, Drawer, Form, Input, Select, Space, Tag } from "antd";
import { toast } from "sonner";
import { CompactTable } from "@/components/table";
import { PageHeader } from "@/components/page-header";
import { VisualFlowCanvas } from "@/components/visual-canvas/VisualFlowCanvas";
import type { ColumnsType } from "antd/es/table";
import type { Node, Edge } from "@xyflow/react";
import {
    listSemanticBusinessObjects,
    listSemanticObjectTableMappings,
    saveSemanticObjectTableMappings,
    createSemanticBusinessObject,
    type SemanticBusinessObject,
    type SemanticObjectTableMapping,
} from "@/api/semanticModelingApi";

function buildJoinGraph(mappings: SemanticObjectTableMapping[]): { nodes: Node[]; edges: Edge[] } {
    const nodes: Node[] = mappings.map((m, i) => ({
        id: m.id ?? `tmp-${i}`,
        position: { x: i * 220, y: 60 },
        data: { label: `${m.tableName}\n[${m.tableRole ?? "main"}]` },
    }));
    const mainNode = nodes.find((_, i) => mappings[i]?.tableRole === "main" || i === 0);
    const edges: Edge[] = mappings
        .filter((m) => m.joinExpression && mainNode && m.id !== mainNode.id)
        .map((m) => ({
            id: `edge-${m.id}`,
            source: mainNode!.id,
            target: m.id ?? "",
            label: m.joinExpression?.slice(0, 20),
        }));
    return { nodes, edges };
}

export default function SemanticObjectsPage() {
    const [objects, setObjects] = useState<SemanticBusinessObject[]>([]);
    const [loading, setLoading] = useState(false);
    const [selected, setSelected] = useState<SemanticBusinessObject | null>(null);
    const [mappings, setMappings] = useState<SemanticObjectTableMapping[]>([]);
    const [createOpen, setCreateOpen] = useState(false);
    const [form] = Form.useForm();

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const list = await listSemanticBusinessObjects();
            setObjects(Array.isArray(list) ? (list as SemanticBusinessObject[]) : []);
        } catch {
            /* global interceptor */
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { void load(); }, [load]);

    const handleSelect = async (obj: SemanticBusinessObject) => {
        setSelected(obj);
        try {
            const maps = await listSemanticObjectTableMappings(obj.id);
            setMappings(Array.isArray(maps) ? (maps as SemanticObjectTableMapping[]) : []);
        } catch {
            setMappings([]);
        }
    };

    const handleCreate = async () => {
        try {
            const values = await form.validateFields();
            await createSemanticBusinessObject(values);
            toast.success("业务对象已创建");
            setCreateOpen(false);
            void load();
        } catch (err: unknown) {
            if (err && typeof err === "object" && "errorFields" in err) return;
        }
    };

    const columns: ColumnsType<SemanticBusinessObject> = [
        { title: "编码", dataIndex: "code", width: 120 },
        { title: "名称", dataIndex: "name" },
        { title: "主表", dataIndex: "mainTable", render: (v?: string) => v ?? <span style={{ color: "#aaa" }}>-</span> },
        {
            title: "操作", key: "actions", width: 80,
            render: (_: unknown, row: SemanticBusinessObject) => (
                <Button type="link" size="small" onClick={() => handleSelect(row)}>查看 join 图</Button>
            ),
        },
    ];

    const { nodes, edges } = buildJoinGraph(mappings);

    return (
        <div className="space-y-4" data-testid="semantic-objects-page">
            <PageHeader
                title="语义建模 · 业务对象"
                action={
                    <Button
                        type="primary"
                        data-testid="semantic-objects-create"
                        onClick={() => { form.resetFields(); setCreateOpen(true); }}
                    >
                        + 新建业务对象
                    </Button>
                }
            />
            <div className="flex gap-4">
                <div style={{ flex: 1 }}>
                    <CompactTable<SemanticBusinessObject>
                        rowKey="id"
                        columns={columns}
                        dataSource={objects}
                        loading={loading}
                    />
                </div>
                {selected && (
                    <div style={{ width: 480, border: "1px solid #e5e7eb", borderRadius: 6, overflow: "hidden" }}>
                        <div className="p-2 text-sm font-medium text-gray-600 border-b border-gray-200">
                            {selected.name} — join 关系图
                        </div>
                        <VisualFlowCanvas nodes={nodes} edges={edges} height={300} />
                    </div>
                )}
            </div>
            <Drawer
                title="新建业务对象"
                open={createOpen}
                onClose={() => setCreateOpen(false)}
                footer={
                    <Button type="primary" onClick={handleCreate} block>创建</Button>
                }
            >
                <Form form={form} layout="vertical">
                    <Form.Item name="code" label="编码" rules={[{ required: true }]}>
                        <Input placeholder="ORDER" />
                    </Form.Item>
                    <Form.Item name="name" label="名称" rules={[{ required: true }]}>
                        <Input placeholder="订单" />
                    </Form.Item>
                    <Form.Item name="mainTable" label="主表">
                        <Input placeholder="dwd_order_detail" />
                    </Form.Item>
                    <Form.Item name="primaryKey" label="主键">
                        <Input placeholder="order_id" />
                    </Form.Item>
                </Form>
            </Drawer>
        </div>
    );
}
```

- [ ] **Step 2: tsc + commit**

```bash
pnpm exec tsc --noEmit 2>&1 | grep -c "error TS" || echo "0 errors"
git add src/pages/modeling/SemanticObjectsPage.tsx
git commit -m "feat(F2/T02): SemanticObjectsPage 真实实现（含 VisualFlowCanvas join 图）"
```

---

## Task 7: SemanticMetricsPage 真实实现（F3/T01）

**Files:**
- Overwrite: `src/pages/modeling/SemanticMetricsPage.tsx`

- [ ] **Step 1: 覆盖 `src/pages/modeling/SemanticMetricsPage.tsx`**

```tsx
import { useCallback, useEffect, useState } from "react";
import { Button, Drawer, Form, Input, Select, Tag } from "antd";
import { toast } from "sonner";
import { CompactTable } from "@/components/table";
import { PageHeader } from "@/components/page-header";
import type { ColumnsType } from "antd/es/table";
import {
    listSemanticMetrics,
    createSemanticMetric,
    updateSemanticMetric,
    listSemanticBusinessObjects,
    type SemanticMetric,
    type SemanticBusinessObject,
} from "@/api/semanticModelingApi";

const FORMULA_TYPE_COLOR: Record<string, string> = {
    "aggregation/sum": "blue",
    "aggregation/count_distinct": "green",
    "aggregation/avg": "orange",
};

const FORMULA_OPTIONS = [
    { label: "aggregation/sum", value: "aggregation/sum" },
    { label: "aggregation/count_distinct", value: "aggregation/count_distinct" },
    { label: "aggregation/avg", value: "aggregation/avg" },
    { label: "aggregation/max", value: "aggregation/max" },
    { label: "aggregation/min", value: "aggregation/min" },
];

export default function SemanticMetricsPage() {
    const [metrics, setMetrics] = useState<SemanticMetric[]>([]);
    const [objects, setObjects] = useState<SemanticBusinessObject[]>([]);
    const [loading, setLoading] = useState(false);
    const [editRow, setEditRow] = useState<SemanticMetric | null>(null);
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [createOpen, setCreateOpen] = useState(false);
    const [form] = Form.useForm();
    const [createForm] = Form.useForm();

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const [m, o] = await Promise.allSettled([listSemanticMetrics(), listSemanticBusinessObjects()]);
            if (m.status === "fulfilled") setMetrics(Array.isArray(m.value) ? (m.value as SemanticMetric[]) : []);
            if (o.status === "fulfilled") setObjects(Array.isArray(o.value) ? (o.value as SemanticBusinessObject[]) : []);
        } catch {
            /* global interceptor */
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { void load(); }, [load]);

    const handleEdit = (row: SemanticMetric) => {
        setEditRow(row);
        form.setFieldsValue({ formulaType: row.formulaType, formulaJson: row.formulaJson, unit: row.unit });
        setDrawerOpen(true);
    };

    const handleSave = async () => {
        try {
            const values = await form.validateFields();
            await updateSemanticMetric(editRow!.id, values);
            toast.success("指标已保存");
            setDrawerOpen(false);
            void load();
        } catch (err: unknown) {
            if (err && typeof err === "object" && "errorFields" in err) return;
        }
    };

    const handleCreate = async () => {
        try {
            const values = await createForm.validateFields();
            await createSemanticMetric(values);
            toast.success("指标已创建");
            setCreateOpen(false);
            void load();
        } catch (err: unknown) {
            if (err && typeof err === "object" && "errorFields" in err) return;
        }
    };

    const columns: ColumnsType<SemanticMetric> = [
        { title: "编码", dataIndex: "code", width: 140 },
        { title: "名称", dataIndex: "name" },
        {
            title: "公式类型", dataIndex: "formulaType", width: 200,
            render: (v?: string) => v
                ? <Tag color={FORMULA_TYPE_COLOR[v] ?? "default"}>{v}</Tag>
                : <span style={{ color: "#aaa" }}>-</span>,
        },
        { title: "单位", dataIndex: "unit", width: 80 },
        {
            title: "状态", dataIndex: "status", width: 100,
            render: (v?: string) => <Tag color={v === "ACTIVE" ? "success" : "default"}>{v ?? "DRAFT"}</Tag>,
        },
        {
            title: "操作", key: "actions", width: 100,
            render: (_: unknown, row: SemanticMetric) => (
                <Button type="link" size="small" onClick={() => handleEdit(row)}>编辑公式</Button>
            ),
        },
    ];

    return (
        <div className="space-y-4" data-testid="semantic-metrics-page">
            <PageHeader
                title="语义建模 · 指标"
                action={
                    <Button
                        type="primary"
                        data-testid="semantic-metrics-create"
                        onClick={() => { createForm.resetFields(); setCreateOpen(true); }}
                    >
                        + 新建指标
                    </Button>
                }
            />
            <CompactTable<SemanticMetric>
                rowKey="id"
                columns={columns}
                dataSource={metrics}
                loading={loading}
            />
            <Drawer
                title="编辑公式"
                open={drawerOpen}
                onClose={() => setDrawerOpen(false)}
                footer={<Button type="primary" block onClick={handleSave}>保存</Button>}
            >
                <Form form={form} layout="vertical">
                    <Form.Item name="formulaType" label="公式类型">
                        <Select options={FORMULA_OPTIONS} placeholder="选择类型" />
                    </Form.Item>
                    <Form.Item name="formulaJson" label="公式 JSON">
                        <Input.TextArea rows={5} style={{ fontFamily: "monospace", fontSize: 12 }} />
                    </Form.Item>
                    <Form.Item name="unit" label="单位">
                        <Input placeholder="万元 / 次 / %" />
                    </Form.Item>
                </Form>
            </Drawer>
            <Drawer
                title="新建指标"
                open={createOpen}
                onClose={() => setCreateOpen(false)}
                footer={<Button type="primary" block onClick={handleCreate}>创建</Button>}
            >
                <Form form={createForm} layout="vertical">
                    <Form.Item name="code" label="编码" rules={[{ required: true }]}>
                        <Input placeholder="GMV" />
                    </Form.Item>
                    <Form.Item name="name" label="名称" rules={[{ required: true }]}>
                        <Input placeholder="成交总额" />
                    </Form.Item>
                    <Form.Item name="objectId" label="关联业务对象">
                        <Select
                            options={objects.map((o) => ({ label: o.name, value: o.id }))}
                            placeholder="选择业务对象"
                        />
                    </Form.Item>
                    <Form.Item name="formulaType" label="公式类型">
                        <Select options={FORMULA_OPTIONS} />
                    </Form.Item>
                    <Form.Item name="unit" label="单位">
                        <Input placeholder="万元" />
                    </Form.Item>
                </Form>
            </Drawer>
        </div>
    );
}
```

- [ ] **Step 2: tsc + commit**

```bash
pnpm exec tsc --noEmit 2>&1 | grep -c "error TS" || echo "0 errors"
git add src/pages/modeling/SemanticMetricsPage.tsx
git commit -m "feat(F3/T01): SemanticMetricsPage 真实实现（指标设计器）"
```

---

## Task 8: SemanticModelsPage 真实实现（F3/T02）

**Files:**
- Overwrite: `src/pages/modeling/SemanticModelsPage.tsx`

- [ ] **Step 1: 覆盖 `src/pages/modeling/SemanticModelsPage.tsx`**

```tsx
import { useCallback, useEffect, useState } from "react";
import { Button, Form, Input, Modal, Popconfirm, Radio, Select, Space, Tag } from "antd";
import { toast } from "sonner";
import { CompactTable } from "@/components/table";
import { PageHeader } from "@/components/page-header";
import type { ColumnsType } from "antd/es/table";
import {
    listSemanticModels,
    createSemanticModel,
    previewSemanticModelData,
    generateSemanticModelArtifacts,
    triggerSemanticModelRun,
    submitSemanticModelReview,
    type SemanticModel,
    type SemanticModelPreview,
} from "@/api/semanticModelingApi";

type ModelType = "DWS" | "ADS" | "ALL";

export default function SemanticModelsPage() {
    const [models, setModels] = useState<SemanticModel[]>([]);
    const [loading, setLoading] = useState(false);
    const [typeFilter, setTypeFilter] = useState<ModelType>("ALL");
    const [createOpen, setCreateOpen] = useState(false);
    const [previewData, setPreviewData] = useState<SemanticModelPreview | null>(null);
    const [form] = Form.useForm();

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const list = await listSemanticModels(typeFilter === "ALL" ? undefined : { type: typeFilter });
            setModels(Array.isArray(list) ? (list as SemanticModel[]) : []);
        } catch {
            /* global interceptor */
        } finally {
            setLoading(false);
        }
    }, [typeFilter]);

    useEffect(() => { void load(); }, [load]);

    const handlePreview = async (modelId: string) => {
        try {
            const data = await previewSemanticModelData(modelId, 50);
            setPreviewData(data as SemanticModelPreview);
        } catch {
            /* global interceptor */
        }
    };

    const handleGenerate = async (modelId: string) => {
        try {
            const result = await generateSemanticModelArtifacts(modelId);
            const paths = (result as { artifacts?: { path?: string }[] })?.artifacts?.map((a) => a.path).filter(Boolean) ?? [];
            toast.success(`已生成 ${paths.length} 个制品: ${paths.join(", ")}`);
        } catch {
            /* global interceptor */
        }
    };

    const handleTrigger = async (modelId: string) => {
        try {
            await triggerSemanticModelRun(modelId);
            toast.success("运行已触发");
        } catch {
            /* global interceptor */
        }
    };

    const handleSubmitReview = async (modelId: string) => {
        try {
            await submitSemanticModelReview(modelId);
            toast.success("已提交审核");
            void load();
        } catch {
            /* global interceptor */
        }
    };

    const handleCreate = async () => {
        try {
            const values = await form.validateFields();
            await createSemanticModel(values);
            toast.success("模型已创建");
            setCreateOpen(false);
            void load();
        } catch (err: unknown) {
            if (err && typeof err === "object" && "errorFields" in err) return;
        }
    };

    const previewColumns = previewData?.headers?.map((h: string) => ({
        title: h, dataIndex: h, key: h, ellipsis: true, width: 120,
    })) ?? [];

    const columns: ColumnsType<SemanticModel> = [
        { title: "名称", dataIndex: "name" },
        { title: "表名", dataIndex: "tableName", width: 160 },
        {
            title: "类型", dataIndex: "type", width: 80,
            render: (v?: string) => <Tag color={v === "DWS" ? "blue" : "green"}>{v ?? "-"}</Tag>,
        },
        { title: "粒度", dataIndex: "grain", width: 100 },
        {
            title: "状态", dataIndex: "reviewStatus", width: 100,
            render: (v?: string) => {
                const colorMap: Record<string, string> = {
                    DRAFT: "default", SUBMITTED: "processing", APPROVED: "success", REJECTED: "error",
                };
                return <Tag color={colorMap[v ?? ""] ?? "default"}>{v ?? "DRAFT"}</Tag>;
            },
        },
        {
            title: "操作", key: "actions", width: 280,
            render: (_: unknown, row: SemanticModel) => (
                <Space size="small">
                    <Button type="link" size="small" onClick={() => handlePreview(row.id)}>预览</Button>
                    <Button type="link" size="small" onClick={() => handleGenerate(row.id)}>生成制品</Button>
                    <Button type="link" size="small" onClick={() => handleTrigger(row.id)}>触发运行</Button>
                    <Popconfirm title="确认提交审核？" onConfirm={() => handleSubmitReview(row.id)}>
                        <Button type="link" size="small">提交审核</Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    return (
        <div className="space-y-4" data-testid="semantic-models-page">
            <PageHeader
                title="语义建模 · DWS/ADS 模型"
                action={
                    <Button
                        type="primary"
                        data-testid="semantic-models-create"
                        onClick={() => { form.resetFields(); setCreateOpen(true); }}
                    >
                        + 新建模型
                    </Button>
                }
            />
            <Radio.Group
                value={typeFilter}
                onChange={(e) => setTypeFilter(e.target.value as ModelType)}
                optionType="button"
                buttonStyle="solid"
                options={[
                    { label: "全部", value: "ALL" },
                    { label: "DWS", value: "DWS" },
                    { label: "ADS", value: "ADS" },
                ]}
            />
            <CompactTable<SemanticModel>
                rowKey="id"
                columns={columns}
                dataSource={models}
                loading={loading}
            />
            <Modal
                title="数据预览"
                open={previewData !== null}
                onCancel={() => setPreviewData(null)}
                footer={null}
                width={800}
            >
                {previewData?.success ? (
                    <CompactTable
                        rowKey={(_, i) => String(i)}
                        columns={previewColumns}
                        dataSource={previewData.rows ?? []}
                        pagination={false}
                        scroll={{ x: true }}
                    />
                ) : (
                    <p className="text-red-500">{previewData?.errorMessage ?? "预览失败"}</p>
                )}
            </Modal>
            <Modal
                title="新建模型"
                open={createOpen}
                onCancel={() => setCreateOpen(false)}
                onOk={handleCreate}
                destroyOnClose
            >
                <Form form={form} layout="vertical" className="pt-4">
                    <Form.Item name="name" label="名称" rules={[{ required: true }]}>
                        <Input placeholder="月销售汇总" />
                    </Form.Item>
                    <Form.Item name="tableName" label="表名" rules={[{ required: true }]}>
                        <Input placeholder="dws_sales_monthly" />
                    </Form.Item>
                    <Form.Item name="type" label="类型" initialValue="DWS">
                        <Radio.Group options={[{ label: "DWS", value: "DWS" }, { label: "ADS", value: "ADS" }]} />
                    </Form.Item>
                    <Form.Item name="grain" label="粒度">
                        <Input placeholder="DAY / MONTH" />
                    </Form.Item>
                    <Form.Item name="materialization" label="物化方式">
                        <Select
                            options={[
                                { label: "table", value: "table" },
                                { label: "incremental", value: "incremental" },
                                { label: "view", value: "view" },
                            ]}
                        />
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
}
```

- [ ] **Step 2: tsc + commit**

```bash
pnpm exec tsc --noEmit 2>&1 | grep -c "error TS" || echo "0 errors"
git add src/pages/modeling/SemanticModelsPage.tsx
git commit -m "feat(F3/T02): SemanticModelsPage 真实实现（预览/生成/触发/审核）"
```

---

## Task 9: SemanticPublishPage 真实实现（F4/T01）

**Files:**
- Overwrite: `src/pages/modeling/SemanticPublishPage.tsx`

- [ ] **Step 1: 覆盖 `src/pages/modeling/SemanticPublishPage.tsx`**

```tsx
import { useCallback, useEffect, useState } from "react";
import { Button, Drawer, Input, Space, Tag } from "antd";
import { toast } from "sonner";
import { CompactTable } from "@/components/table";
import { PageHeader } from "@/components/page-header";
import type { ColumnsType } from "antd/es/table";
import {
    listSemanticModels,
    approveSemanticModelReview,
    rejectSemanticModelReview,
    publishSemanticModelToDbt,
    registerSemanticBiDataset,
    registerSemanticLineage,
    listSemanticModelReviewLogs,
    listSemanticGeneratedArtifacts,
    type SemanticModel,
    type SemanticModelReviewLog,
    type SemanticGeneratedArtifact,
} from "@/api/semanticModelingApi";

const REVIEW_STATUS_COLOR: Record<string, string> = {
    DRAFT: "default", SUBMITTED: "processing", APPROVED: "success", REJECTED: "error",
};

export default function SemanticPublishPage() {
    const [models, setModels] = useState<SemanticModel[]>([]);
    const [loading, setLoading] = useState(false);
    const [publishing, setPublishing] = useState<string | null>(null);
    const [logDrawerModel, setLogDrawerModel] = useState<string | null>(null);
    const [logs, setLogs] = useState<SemanticModelReviewLog[]>([]);
    const [artifactDrawerModel, setArtifactDrawerModel] = useState<string | null>(null);
    const [artifacts, setArtifacts] = useState<SemanticGeneratedArtifact[]>([]);
    const [rejectComment, setRejectComment] = useState("");

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const list = await listSemanticModels({ type: "ADS" });
            setModels(Array.isArray(list) ? (list as SemanticModel[]) : []);
        } catch {
            /* global interceptor */
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { void load(); }, [load]);

    const handleApprove = async (modelId: string) => {
        try {
            await approveSemanticModelReview(modelId);
            toast.success("审核已通过");
            void load();
        } catch {
            /* global interceptor */
        }
    };

    const handleReject = async (modelId: string) => {
        if (!rejectComment.trim()) { toast.error("拒绝原因不能为空"); return; }
        try {
            await rejectSemanticModelReview(modelId, rejectComment);
            toast.success("审核已拒绝");
            setRejectComment("");
            void load();
        } catch {
            /* global interceptor */
        }
    };

    const handlePublish = async (modelId: string) => {
        setPublishing(modelId);
        try {
            await publishSemanticModelToDbt(modelId);
            await Promise.allSettled([
                registerSemanticBiDataset(modelId),
                registerSemanticLineage(modelId),
            ]);
            toast.success("发布成功：dbt 发布 + BI 数据集 + 血缘已注册");
            void load();
        } catch {
            /* global interceptor */
        } finally {
            setPublishing(null);
        }
    };

    const openLogs = async (modelId: string) => {
        setLogDrawerModel(modelId);
        try {
            const list = await listSemanticModelReviewLogs(modelId);
            setLogs(Array.isArray(list) ? (list as SemanticModelReviewLog[]) : []);
        } catch {
            setLogs([]);
        }
    };

    const openArtifacts = async (modelId: string) => {
        setArtifactDrawerModel(modelId);
        try {
            const list = await listSemanticGeneratedArtifacts({ modelId });
            setArtifacts(Array.isArray(list) ? (list as SemanticGeneratedArtifact[]) : []);
        } catch {
            setArtifacts([]);
        }
    };

    const columns: ColumnsType<SemanticModel> = [
        { title: "名称", dataIndex: "name" },
        { title: "表名", dataIndex: "tableName", width: 180, ellipsis: true },
        {
            title: "审核状态", dataIndex: "reviewStatus", width: 120,
            render: (v?: string) => <Tag color={REVIEW_STATUS_COLOR[v ?? ""] ?? "default"}>{v ?? "DRAFT"}</Tag>,
        },
        { title: "提交人", dataIndex: "submittedBy", width: 100 },
        {
            title: "操作", key: "actions", width: 340,
            render: (_: unknown, row: SemanticModel) => (
                <Space size="small" wrap>
                    <Button type="link" size="small" onClick={() => handleApprove(row.id)}>通过</Button>
                    <Button type="link" size="small" danger onClick={() => handleReject(row.id)}>拒绝</Button>
                    <Button
                        type="link" size="small"
                        loading={publishing === row.id}
                        onClick={() => handlePublish(row.id)}
                    >
                        发布 dbt
                    </Button>
                    <Button type="link" size="small" onClick={() => openLogs(row.id)}>审核日志</Button>
                    <Button type="link" size="small" onClick={() => openArtifacts(row.id)}>查看制品</Button>
                </Space>
            ),
        },
    ];

    return (
        <div className="space-y-4" data-testid="semantic-publish-page">
            <PageHeader title="语义建模 · 审核发布" />
            {publishing && (
                <div style={{ color: "hsl(220,80%,55%)", padding: "4px 0", fontSize: 13 }}>
                    正在发布 dbt 并注册血缘，请稍候...
                </div>
            )}
            <div className="flex gap-2 items-center">
                <span className="text-sm text-gray-500">拒绝原因:</span>
                <Input
                    style={{ width: 240 }}
                    size="small"
                    value={rejectComment}
                    onChange={(e) => setRejectComment(e.target.value)}
                    placeholder="填写后点击拒绝"
                />
            </div>
            <CompactTable<SemanticModel>
                rowKey="id"
                columns={columns}
                dataSource={models}
                loading={loading}
            />
            <Drawer
                title="审核日志"
                open={logDrawerModel !== null}
                onClose={() => setLogDrawerModel(null)}
            >
                {logs.length === 0 ? (
                    <p className="text-gray-400 text-sm">暂无审核记录</p>
                ) : (
                    <div className="space-y-2">
                        {logs.map((log) => (
                            <div key={log.id} className="text-sm border-b border-gray-100 pb-2">
                                <span className="font-medium">{log.action}</span>
                                <span className="text-gray-500 ml-2">{log.actor}</span>
                                {log.comment && <p className="text-gray-600 mt-1">{log.comment}</p>}
                                <p className="text-gray-400 text-xs">{log.createdDate}</p>
                            </div>
                        ))}
                    </div>
                )}
            </Drawer>
            <Drawer
                title="生成制品"
                open={artifactDrawerModel !== null}
                onClose={() => setArtifactDrawerModel(null)}
            >
                {artifacts.map((a) => (
                    <div key={a.id} className="mb-3">
                        <div className="text-xs text-gray-500 mb-1">{a.path}</div>
                        <pre
                            style={{
                                background: "#f5f5f5",
                                borderRadius: 4,
                                padding: "8px",
                                fontSize: 11,
                                overflow: "auto",
                                maxHeight: 200,
                            }}
                        >
                            {a.content ?? "（无内容）"}
                        </pre>
                    </div>
                ))}
            </Drawer>
        </div>
    );
}
```

- [ ] **Step 2: tsc + commit**

```bash
pnpm exec tsc --noEmit 2>&1 | grep -c "error TS" || echo "0 errors"
git add src/pages/modeling/SemanticPublishPage.tsx
git commit -m "feat(F4/T01): SemanticPublishPage 审核发布流（approve/reject/publish dbt + BI注册）"
```

---

## Task 10: SemanticRunsPage 真实实现（F4/T02）

**Files:**
- Overwrite: `src/pages/modeling/SemanticRunsPage.tsx`

- [ ] **Step 1: 覆盖 `src/pages/modeling/SemanticRunsPage.tsx`**

```tsx
import { useCallback, useEffect, useRef, useState } from "react";
import { Button, Drawer, Select, Tag } from "antd";
import { toast } from "sonner";
import { CompactTable } from "@/components/table";
import { PageHeader } from "@/components/page-header";
import type { ColumnsType } from "antd/es/table";
import {
    listSemanticModels,
    listSemanticModelRuns,
    triggerSemanticModelRun,
    type SemanticModel,
    type SemanticModelRun,
} from "@/api/semanticModelingApi";

const RUN_STATUS_COLOR: Record<string, string> = {
    PENDING: "default", RUNNING: "processing", SUCCESS: "success",
    FAILED: "error", CANCELLED: "warning",
};

const POLL_INTERVAL_MS = 10_000;

export default function SemanticRunsPage() {
    const [models, setModels] = useState<SemanticModel[]>([]);
    const [selectedModelId, setSelectedModelId] = useState<string | null>(null);
    const [runs, setRuns] = useState<SemanticModelRun[]>([]);
    const [loading, setLoading] = useState(false);
    const [triggering, setTriggering] = useState(false);
    const [logRun, setLogRun] = useState<SemanticModelRun | null>(null);
    const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

    useEffect(() => {
        void listSemanticModels().then((list) => {
            setModels(Array.isArray(list) ? (list as SemanticModel[]) : []);
        });
    }, []);

    const loadRuns = useCallback(async (modelId: string) => {
        setLoading(true);
        try {
            const list = await listSemanticModelRuns(modelId);
            setRuns(Array.isArray(list) ? (list as SemanticModelRun[]) : []);
        } catch {
            setRuns([]);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (intervalRef.current) clearInterval(intervalRef.current);
        if (!selectedModelId) return;
        void loadRuns(selectedModelId);
        return () => {
            if (intervalRef.current) clearInterval(intervalRef.current);
        };
    }, [selectedModelId, loadRuns]);

    // 有 RUNNING 时开启轮询
    useEffect(() => {
        if (intervalRef.current) clearInterval(intervalRef.current);
        const hasRunning = runs.some((r) => r.status === "RUNNING" || r.status === "PENDING");
        if (hasRunning && selectedModelId) {
            intervalRef.current = setInterval(() => {
                void loadRuns(selectedModelId);
            }, POLL_INTERVAL_MS);
        }
        return () => {
            if (intervalRef.current) clearInterval(intervalRef.current);
        };
    }, [runs, selectedModelId, loadRuns]);

    const handleTrigger = async () => {
        if (!selectedModelId) return;
        setTriggering(true);
        try {
            await triggerSemanticModelRun(selectedModelId, { runType: "MANUAL" });
            toast.success("运行已触发");
            void loadRuns(selectedModelId);
        } catch {
            /* global interceptor */
        } finally {
            setTriggering(false);
        }
    };

    const columns: ColumnsType<SemanticModelRun> = [
        { title: "运行类型", dataIndex: "runType", width: 100 },
        {
            title: "状态", dataIndex: "status", width: 120,
            render: (v?: string) => <Tag color={RUN_STATUS_COLOR[v ?? ""] ?? "default"}>{v ?? "-"}</Tag>,
        },
        { title: "触发人", dataIndex: "triggeredBy", width: 120 },
        { title: "开始时间", dataIndex: "startedAt", width: 160, render: (v?: string) => v?.slice(0, 16) ?? "-" },
        { title: "耗时(ms)", dataIndex: "durationMs", width: 100 },
        {
            title: "操作", key: "actions", width: 80,
            render: (_: unknown, row: SemanticModelRun) => (
                <Button type="link" size="small" onClick={() => setLogRun(row)}>日志</Button>
            ),
        },
    ];

    return (
        <div className="space-y-4" data-testid="semantic-runs-page">
            <PageHeader title="语义建模 · 运行监控" />
            <div className="flex items-center gap-3">
                <Select
                    style={{ width: 280 }}
                    placeholder="选择模型"
                    value={selectedModelId ?? undefined}
                    onChange={(v) => setSelectedModelId(v)}
                    options={models.map((m) => ({ label: `[${m.type ?? "-"}] ${m.name}`, value: m.id }))}
                />
                {selectedModelId && (
                    <Button loading={triggering} onClick={handleTrigger}>手动触发</Button>
                )}
            </div>
            {selectedModelId && (
                <CompactTable<SemanticModelRun>
                    rowKey="id"
                    columns={columns}
                    dataSource={runs}
                    loading={loading}
                />
            )}
            <Drawer
                title="运行日志"
                open={logRun !== null}
                onClose={() => setLogRun(null)}
            >
                {logRun && (
                    <div className="space-y-2 text-sm">
                        <div><span className="text-gray-500">状态: </span>
                            <Tag color={RUN_STATUS_COLOR[logRun.status ?? ""] ?? "default"}>
                                {logRun.status}
                            </Tag>
                        </div>
                        {logRun.message && (
                            <pre
                                style={{
                                    background: "#f5f5f5",
                                    borderRadius: 4,
                                    padding: 8,
                                    fontSize: 12,
                                    whiteSpace: "pre-wrap",
                                    wordBreak: "break-all",
                                }}
                            >
                                {logRun.message}
                            </pre>
                        )}
                    </div>
                )}
            </Drawer>
        </div>
    );
}
```

- [ ] **Step 2: tsc + commit**

```bash
pnpm exec tsc --noEmit 2>&1 | grep -c "error TS" || echo "0 errors"
git add src/pages/modeling/SemanticRunsPage.tsx
git commit -m "feat(F4/T02): SemanticRunsPage 运行监控（轮询 RUNNING + 日志 Drawer）"
```

---

## Task 11: source-contract 测试（F5/T01）

**Files:**
- Create: `src/pages/modeling/metricWorkbench.source-contract.test.ts`

- [ ] **Step 1: 新建 `src/pages/modeling/metricWorkbench.source-contract.test.ts`**

```typescript
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const WORKBENCH = readFileSync(
    new URL("./MetricWorkbenchPage.tsx", import.meta.url),
    "utf8",
);
const CANVAS = readFileSync(
    new URL("./metric-workbench/MetricCanvas.tsx", import.meta.url),
    "utf8",
);
const SUBJECTS = readFileSync(
    new URL("./SemanticSubjectsPage.tsx", import.meta.url),
    "utf8",
);
const OBJECTS = readFileSync(
    new URL("./SemanticObjectsPage.tsx", import.meta.url),
    "utf8",
);
const METRICS_PAGE = readFileSync(
    new URL("./SemanticMetricsPage.tsx", import.meta.url),
    "utf8",
);
const MODELS_PAGE = readFileSync(
    new URL("./SemanticModelsPage.tsx", import.meta.url),
    "utf8",
);
const PUBLISH = readFileSync(
    new URL("./SemanticPublishPage.tsx", import.meta.url),
    "utf8",
);
const RUNS = readFileSync(
    new URL("./SemanticRunsPage.tsx", import.meta.url),
    "utf8",
);

test("MetricWorkbenchPage uses React Flow canvas and semantic API", () => {
    assert.match(WORKBENCH, /listSemanticSubjectDomains/);
    assert.match(WORKBENCH, /listSemanticBusinessObjects/);
    assert.match(WORKBENCH, /listSemanticMetrics/);
    assert.match(WORKBENCH, /metric-workbench-page/);
    assert.doesNotMatch(WORKBENCH, /window\.location\.replace/);
    assert.doesNotMatch(WORKBENCH, /oklch|:has\(|@container/);
});

test("MetricCanvas uses @xyflow/react with custom nodes and edges", () => {
    assert.match(CANVAS, /ReactFlow/);
    assert.match(CANVAS, /BizObjectNode/);
    assert.match(CANVAS, /MetricNode/);
    assert.match(CANVAS, /MetricBindingEdge/);
    assert.doesNotMatch(CANVAS, /oklch|:has\(|@container/);
});

test("all SemanticXxxPages are real implementations (no redirect shells)", () => {
    for (const [name, src] of [
        ["SemanticSubjectsPage", SUBJECTS],
        ["SemanticObjectsPage", OBJECTS],
        ["SemanticMetricsPage", METRICS_PAGE],
        ["SemanticModelsPage", MODELS_PAGE],
        ["SemanticPublishPage", PUBLISH],
        ["SemanticRunsPage", RUNS],
    ]) {
        assert.doesNotMatch(src, /window\.location\.replace/, `${name} should not be a redirect shell`);
        assert.doesNotMatch(src, /SemanticModelingCenterPage/, `${name} should not import SemanticModelingCenterPage`);
    }
});

test("publish page calls dbt + BI registration + lineage in sequence", () => {
    assert.match(PUBLISH, /publishSemanticModelToDbt/);
    assert.match(PUBLISH, /registerSemanticBiDataset/);
    assert.match(PUBLISH, /registerSemanticLineage/);
    assert.match(PUBLISH, /semantic-publish-page/);
});

test("runs page polls RUNNING status", () => {
    assert.match(RUNS, /POLL_INTERVAL_MS/);
    assert.match(RUNS, /setInterval/);
    assert.match(RUNS, /clearInterval/);
    assert.match(RUNS, /semantic-runs-page/);
});
```

- [ ] **Step 2: 运行，确认全绿**

```bash
node --test src/pages/modeling/metricWorkbench.source-contract.test.ts 2>&1 | tail -10
```

预期：5 tests pass, 0 fail

- [ ] **Step 3: 运行全量 source-contract，确认 baseline 未增加**

```bash
node --test 'src/**/*.source-contract.test.ts' 2>&1 | grep -E "^#|pass|fail" | tail -5
```

预期：通过数 ≥ 138（原133 + 新5），失败数 ≤ 10（baseline 不增加）

- [ ] **Step 4: commit**

```bash
git add src/pages/modeling/metricWorkbench.source-contract.test.ts
git commit -m "test(F5/T01): metricWorkbench source-contract（5 tests）"
```

---

## Task 12: 全量验证与 IT 证据（F5/T02）

- [ ] **Step 1: TypeScript 检查**

```bash
cd source/dts-platform-webapp
pnpm exec tsc --noEmit 2>&1 | tail -5
```

预期：0 errors。若有错误，按错误信息修复类型问题后重新运行。

- [ ] **Step 2: Chrome 95 关键字检查**

```bash
grep -rn "oklch\|:has(\|@container" src/pages/modeling/metric-workbench/ 2>/dev/null | wc -l
```

预期：0（无匹配）

- [ ] **Step 3: 壳替换验证**

```bash
grep -l "window.location.replace\|SemanticModelingCenterPage" \
    src/pages/modeling/SemanticSubjectsPage.tsx \
    src/pages/modeling/SemanticObjectsPage.tsx \
    src/pages/modeling/SemanticMetricsPage.tsx \
    src/pages/modeling/SemanticModelsPage.tsx \
    src/pages/modeling/SemanticPublishPage.tsx \
    src/pages/modeling/SemanticRunsPage.tsx 2>/dev/null
```

预期：无输出（所有壳已替换）

- [ ] **Step 4: 生产构建**

```bash
pnpm build 2>&1 | tail -10
```

预期：`built in Xs`，无构建错误

- [ ] **Step 5: 记录 IT 证据**

将以下内容填入 `worklog/v2.2.3/sprint-52-202606/it/README.md`：

```markdown
## IT 证据区

tsc: EXIT:0, 0 errors
source-contract: X pass, ≤10 fail（baseline）
build: built in Xs
Chrome95 关键字: 0 匹配
壳替换: 0 文件残留
```

- [ ] **Step 6: 最终 commit**

```bash
git add worklog/v2.2.3/sprint-52-202606/it/README.md
git commit -m "docs(sprint-52): IT 验证证据记录"
```
