import { useQuery } from "@tanstack/react-query";
import { Empty, Select, Spin } from "antd";
import EChartsReact from "echarts-for-react";
import { type FC, useMemo, useState } from "react";
import { getExecutionMeta, getExecutionPage } from "../api/sqlIdeExecution";
import {
  type ChartConfig,
  type ChartType,
  buildEChartsOption,
  recommendChart,
} from "./chartConfig";

export interface ResultChartProps {
  executionId: string;
}

const CHART_TYPE_OPTIONS: Array<{ value: ChartType; label: string }> = [
  { value: "bar", label: "柱状图" },
  { value: "line", label: "折线图" },
  { value: "area", label: "面积图" },
  { value: "pie", label: "饼图" },
  { value: "scatter", label: "散点图" },
];

export const ResultChart: FC<ResultChartProps> = ({ executionId }) => {
  const { data: meta, isLoading: metaLoading } = useQuery({
    queryKey: ["sqlide", "execution", "meta", executionId],
    queryFn: () => getExecutionMeta(executionId),
    staleTime: 60_000,
  });
  const { data: page, isLoading: pageLoading } = useQuery({
    queryKey: ["sqlide", "execution", "chart-page", executionId],
    queryFn: () => getExecutionPage(executionId, 1, 5000),
    enabled: !!meta,
    staleTime: 60_000,
  });

  const initial = useMemo(() => (meta ? recommendChart(meta.columns) : null), [meta]);
  const [config, setConfig] = useState<ChartConfig | null>(null);
  const effectiveConfig = config ?? initial;

  if (metaLoading || pageLoading) {
    return (
      <div style={{ padding: 24, textAlign: "center" }}>
        <Spin />
      </div>
    );
  }
  if (!meta || !page || meta.columns.length === 0) {
    return <Empty description="无数据" />;
  }
  if (!effectiveConfig) return <Empty description="正在准备图表配置" />;

  const option = useMemo(
    () => buildEChartsOption(effectiveConfig, page.rows),
    [effectiveConfig, page.rows],
  );
  const colOptions = meta.columns.map((c) => ({ value: c.name, label: c.name }));

  return (
    <div style={{ display: "flex", height: "100%" }}>
      <div style={{ flex: 1, minWidth: 0, padding: 8 }}>
        <EChartsReact
          option={option}
          style={{ height: "100%", minHeight: 240 }}
          notMerge
          lazyUpdate
        />
      </div>
      <div
        style={{
          width: 220,
          padding: "8px 12px",
          borderLeft: "1px solid var(--ant-color-border-secondary)",
          overflow: "auto",
        }}
      >
        <div style={{ marginBottom: 8 }}>
          <div
            style={{ fontSize: 11, color: "var(--ant-color-text-secondary)", marginBottom: 4 }}
          >
            图表类型
          </div>
          <Select
            size="small"
            style={{ width: "100%" }}
            value={effectiveConfig.type}
            options={CHART_TYPE_OPTIONS}
            onChange={(v) => setConfig({ ...effectiveConfig, type: v })}
          />
        </div>
        <div style={{ marginBottom: 8 }}>
          <div
            style={{ fontSize: 11, color: "var(--ant-color-text-secondary)", marginBottom: 4 }}
          >
            X 轴
          </div>
          <Select
            size="small"
            style={{ width: "100%" }}
            value={effectiveConfig.xAxis}
            options={colOptions}
            onChange={(v) => setConfig({ ...effectiveConfig, xAxis: v })}
          />
        </div>
        <div style={{ marginBottom: 8 }}>
          <div
            style={{ fontSize: 11, color: "var(--ant-color-text-secondary)", marginBottom: 4 }}
          >
            Y 轴
          </div>
          <Select
            size="small"
            style={{ width: "100%" }}
            mode="multiple"
            value={effectiveConfig.yAxis}
            options={colOptions}
            onChange={(v) => setConfig({ ...effectiveConfig, yAxis: v })}
          />
        </div>
      </div>
    </div>
  );
};
