import { useQuery } from "@tanstack/react-query";
import { Empty, Select, Spin } from "antd";
import { type FC, useMemo, useState } from "react";
import { getExecutionMeta, getExecutionPage } from "../api/sqlIdeExecution";

export interface ResultPivotProps {
  executionId: string;
}

type AggFn = "SUM" | "COUNT" | "AVG" | "MIN" | "MAX" | "DISTINCT_COUNT";

const AGG_OPTIONS: Array<{ value: AggFn; label: string }> = [
  { value: "SUM", label: "SUM" },
  { value: "COUNT", label: "COUNT" },
  { value: "AVG", label: "AVG" },
  { value: "MIN", label: "MIN" },
  { value: "MAX", label: "MAX" },
  { value: "DISTINCT_COUNT", label: "DISTINCT COUNT" },
];

const MAX_ROWS = 10_000;

function aggregate(values: unknown[], fn: AggFn): number | string {
  const nums = values.filter((v) => typeof v === "number") as number[];
  switch (fn) {
    case "SUM":
      return nums.reduce((a, b) => a + b, 0);
    case "COUNT":
      return values.filter((v) => v !== null && v !== undefined).length;
    case "AVG":
      return nums.length === 0 ? 0 : nums.reduce((a, b) => a + b, 0) / nums.length;
    case "MIN":
      return nums.length === 0 ? "" : Math.min(...nums);
    case "MAX":
      return nums.length === 0 ? "" : Math.max(...nums);
    case "DISTINCT_COUNT":
      return new Set(values).size;
  }
}

export const ResultPivot: FC<ResultPivotProps> = ({ executionId }) => {
  const { data: meta, isLoading: metaLoading } = useQuery({
    queryKey: ["sqlide", "execution", "meta", executionId],
    queryFn: () => getExecutionMeta(executionId),
    staleTime: 60_000,
  });
  const { data: page, isLoading: pageLoading } = useQuery({
    queryKey: ["sqlide", "execution", "pivot-page", executionId],
    queryFn: () => getExecutionPage(executionId, 1, MAX_ROWS),
    enabled: !!meta,
    staleTime: 60_000,
  });

  const [rowField, setRowField] = useState<string | null>(null);
  const [colField, setColField] = useState<string | null>(null);
  const [valueField, setValueField] = useState<string | null>(null);
  const [aggFn, setAggFn] = useState<AggFn>("SUM");

  const tooLarge = (page?.total ?? 0) > MAX_ROWS;

  const pivot = useMemo(() => {
    if (!page || !rowField || !valueField) return null;
    const rowKeys = new Set<string>();
    const colKeys = new Set<string>();
    const cell = new Map<string, Map<string, unknown[]>>();

    for (const r of page.rows) {
      const rk = String(r[rowField] ?? "");
      const ck = colField ? String(r[colField] ?? "") : "TOTAL";
      const v = r[valueField];
      rowKeys.add(rk);
      colKeys.add(ck);
      if (!cell.has(rk)) cell.set(rk, new Map());
      const inner = cell.get(rk)!;
      if (!inner.has(ck)) inner.set(ck, []);
      inner.get(ck)!.push(v);
    }
    const sortedRows = [...rowKeys].sort();
    const sortedCols = [...colKeys].sort();
    return { rows: sortedRows, cols: sortedCols, cell };
  }, [page, rowField, colField, valueField]);

  if (metaLoading || pageLoading) {
    return <div style={{ padding: 24, textAlign: "center" }}><Spin /></div>;
  }
  if (!meta || !page) return <Empty description="无数据" />;
  if (tooLarge) {
    return <Empty description={`数据量 ${page.total} 行 > ${MAX_ROWS}，请在 SQL 中先聚合`} />;
  }

  const colOptions = meta.columns.map((c) => ({ value: c.name, label: c.name }));

  return (
    <div style={{ display: "flex", height: "100%" }}>
      <div style={{ flex: 1, padding: 8, overflow: "auto" }}>
        {pivot ? (
          <table style={{ borderCollapse: "collapse", fontSize: 12 }}>
            <thead>
              <tr>
                <th style={{ padding: "4px 8px", border: "1px solid var(--ant-color-border)" }}>{rowField}</th>
                {pivot.cols.map((c) => (
                  <th key={c} style={{ padding: "4px 8px", border: "1px solid var(--ant-color-border)" }}>{c}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {pivot.rows.map((rk) => (
                <tr key={rk}>
                  <td style={{ padding: "4px 8px", border: "1px solid var(--ant-color-border)" }}>{rk}</td>
                  {pivot.cols.map((ck) => {
                    const vals = pivot.cell.get(rk)?.get(ck) ?? [];
                    return (
                      <td key={ck} style={{ padding: "4px 8px", border: "1px solid var(--ant-color-border)", textAlign: "right" }}>
                        {String(aggregate(vals, aggFn))}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <Empty description="请配置行/列/值字段" />
        )}
      </div>
      <div style={{ width: 220, padding: "8px 12px", borderLeft: "1px solid var(--ant-color-border-secondary)" }}>
        <div style={{ marginBottom: 8 }}>
          <div style={{ fontSize: 11, color: "var(--ant-color-text-secondary)" }}>行</div>
          <Select size="small" style={{ width: "100%" }} options={colOptions} value={rowField ?? undefined} onChange={setRowField} allowClear />
        </div>
        <div style={{ marginBottom: 8 }}>
          <div style={{ fontSize: 11, color: "var(--ant-color-text-secondary)" }}>列（可选）</div>
          <Select size="small" style={{ width: "100%" }} options={colOptions} value={colField ?? undefined} onChange={setColField} allowClear />
        </div>
        <div style={{ marginBottom: 8 }}>
          <div style={{ fontSize: 11, color: "var(--ant-color-text-secondary)" }}>值</div>
          <Select size="small" style={{ width: "100%" }} options={colOptions} value={valueField ?? undefined} onChange={setValueField} allowClear />
        </div>
        <div style={{ marginBottom: 8 }}>
          <div style={{ fontSize: 11, color: "var(--ant-color-text-secondary)" }}>聚合</div>
          <Select size="small" style={{ width: "100%" }} options={AGG_OPTIONS} value={aggFn} onChange={setAggFn} />
        </div>
      </div>
    </div>
  );
};
