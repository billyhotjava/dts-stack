// Chrome 95 兼容:@xyflow/react v12 内部使用 structuredClone,polyfill 通过入口已注入。
import "@/polyfills/legacy-browser";
import { Background, Controls, ReactFlow, type Edge, type Node } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useQuery } from "@tanstack/react-query";
import { Button, Spin, Tabs, message } from "antd";
import { type FC, useCallback, useMemo, useState } from "react";
import { getExecutionLog } from "../api/sqlIdeLog";
import { postExplain, type PlanResult } from "../api/sqlIdePlan";
import { layoutPlanNodes, type PlanNode } from "./planLayout";

export interface QueryPlanViewProps {
  executionId: string | null;
  fallbackSql: string;
  engine: string;
  datasourceId: string | null;
  catalog: string | null;
}

export const QueryPlanView: FC<QueryPlanViewProps> = ({ executionId, fallbackSql, engine, datasourceId, catalog }) => {
  const [result, setResult] = useState<PlanResult | null>(null);
  const [loading, setLoading] = useState(false);

  const { data: logData } = useQuery({
    queryKey: ["sqlide", "execution", "log", executionId],
    queryFn: () => getExecutionLog(executionId!),
    enabled: !!executionId,
    staleTime: 60_000,
  });
  const effectiveSql = logData?.originalSql ?? fallbackSql;

  const handleExplain = useCallback(async () => {
    if (!effectiveSql.trim()) {
      void message.warning("请先输入 SQL");
      return;
    }
    setLoading(true);
    try {
      const r = await postExplain({ sql: effectiveSql, engine, datasourceId, catalog });
      setResult(r);
    } catch {
      void message.error("EXPLAIN 失败");
    } finally {
      setLoading(false);
    }
  }, [effectiveSql, engine, datasourceId, catalog]);

  const flow = useMemo(() => {
    if (!result) return { nodes: [] as Node[], edges: [] as Edge[] };
    const { nodes, edges } = layoutPlanNodes(result.root);
    return {
      nodes: nodes.map<Node>((n) => ({
        id: n.id,
        position: n.position,
        data: { label: nodeLabel(n.data.node) },
        style: {
          padding: 6,
          fontSize: 11,
          border: `1px solid ${costColor(n.data.node.estimatedCost)}`,
          whiteSpace: "pre-line",
        },
      })),
      edges: edges.map<Edge>((e) => ({ id: e.id, source: e.source, target: e.target })),
    };
  }, [result]);

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%" }}>
      <div style={{ padding: 6, borderBottom: "1px solid var(--ant-color-border-secondary)" }}>
        <Button size="small" type="primary" onClick={handleExplain} loading={loading}>
          运行 EXPLAIN
        </Button>
        {result && (
          <span
            style={{ marginLeft: 12, fontSize: 11, color: "var(--ant-color-text-secondary)" }}
          >
            {result.engine} · {result.explainTimeMs} ms
          </span>
        )}
      </div>
      <div style={{ flex: 1, minHeight: 0 }}>
        {loading ? (
          <div style={{ textAlign: "center", padding: 24 }}>
            <Spin />
          </div>
        ) : result ? (
          <Tabs
            defaultActiveKey="tree"
            items={[
              {
                key: "tree",
                label: "Tree",
                children: (
                  <div style={{ height: "100%", minHeight: 280 }}>
                    <ReactFlow nodes={flow.nodes} edges={flow.edges} fitView>
                      <Background />
                      <Controls />
                    </ReactFlow>
                  </div>
                ),
              },
              {
                key: "raw",
                label: "Raw",
                children: (
                  <pre
                    style={{ padding: 12, fontSize: 11, overflow: "auto", height: "100%" }}
                  >
                    {result.rawText}
                  </pre>
                ),
              },
            ]}
          />
        ) : (
          <div style={{ padding: 24, color: "var(--ant-color-text-tertiary)" }}>
            点击"运行 EXPLAIN"分析查询计划
          </div>
        )}
      </div>
    </div>
  );
};

function nodeLabel(n: PlanNode): string {
  const cost = n.estimatedCost != null ? ` · cost=${n.estimatedCost.toFixed(0)}` : "";
  const rows = n.estimatedRows != null ? ` · rows=${n.estimatedRows.toFixed(0)}` : "";
  const tbl = n.table ? `\n${n.table}` : "";
  return `${n.operator}${cost}${rows}${tbl}`;
}

function costColor(cost: number | null | undefined): string {
  if (cost == null) return "var(--ant-color-border)";
  if (cost > 10000) return "var(--ant-color-error)";
  if (cost > 1000) return "var(--ant-color-warning)";
  return "var(--ant-color-success)";
}
