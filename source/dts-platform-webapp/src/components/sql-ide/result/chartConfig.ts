import type { ColumnMeta } from "../api/sqlIdeExecution";

export type ChartType = "bar" | "line" | "pie" | "scatter" | "area";

export interface ChartConfig {
  type: ChartType;
  xAxis: string;
  yAxis: string[];
  groupBy: string | null;
}

const NUMERIC_TYPES = /^(BIGINT|INT|INTEGER|SMALLINT|TINYINT|DOUBLE|FLOAT|REAL|DECIMAL|NUMERIC)$/i;
const TIME_TYPES = /^(TIMESTAMP|DATE|TIME|DATETIME)/i;

function isNumeric(col: ColumnMeta): boolean {
  return NUMERIC_TYPES.test(col.dataType ?? "");
}

function isTime(col: ColumnMeta): boolean {
  return TIME_TYPES.test(col.dataType ?? "");
}

export function recommendChart(columns: ColumnMeta[]): ChartConfig {
  if (columns.length === 0) {
    return { type: "bar", xAxis: "", yAxis: [], groupBy: null };
  }
  const timeCol = columns.find(isTime);
  const numericCols = columns.filter(isNumeric);
  const stringCols = columns.filter((c) => !isNumeric(c) && !isTime(c));

  if (timeCol && numericCols.length >= 1) {
    return { type: "line", xAxis: timeCol.name, yAxis: [numericCols[0].name], groupBy: null };
  }
  if (numericCols.length >= 2) {
    return {
      type: "scatter",
      xAxis: numericCols[0].name,
      yAxis: [numericCols[1].name],
      groupBy: null,
    };
  }
  if (stringCols.length >= 1 && numericCols.length >= 1) {
    return {
      type: "bar",
      xAxis: stringCols[0].name,
      yAxis: [numericCols[0].name],
      groupBy: null,
    };
  }
  return { type: "bar", xAxis: columns[0].name, yAxis: [], groupBy: null };
}

export function buildEChartsOption(
  cfg: ChartConfig,
  rows: Array<Record<string, unknown>>,
): Record<string, unknown> {
  if (!cfg.xAxis || cfg.yAxis.length === 0) {
    return { title: { text: "请选择 X / Y 轴", left: "center", top: "middle" } };
  }
  if (cfg.type === "pie") {
    return {
      tooltip: { trigger: "item" },
      series: [
        {
          type: "pie",
          radius: "55%",
          data: rows.map((r) => ({ name: String(r[cfg.xAxis]), value: r[cfg.yAxis[0]] })),
        },
      ],
    };
  }
  const xData = rows.map((r) => r[cfg.xAxis]);
  const series = cfg.yAxis.map((y) => ({
    name: y,
    type: cfg.type === "area" ? "line" : cfg.type,
    data: rows.map((r) => r[y]),
    areaStyle: cfg.type === "area" ? {} : undefined,
  }));
  return {
    tooltip: { trigger: "axis" },
    xAxis: { type: "category", data: xData },
    yAxis: { type: "value" },
    series,
    legend: { top: 4 },
    grid: { top: 32, bottom: 32, left: 48, right: 16 },
  };
}
