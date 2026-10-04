import type { ColumnMeta } from "../api/sqlIdeExecution";

export type ChartType = "bar" | "line" | "pie" | "scatter" | "area";
export type AxisType = "category" | "value" | "time";

export interface ChartConfig {
  type: ChartType;
  xAxis: string;
  xAxisType: AxisType;
  yAxis: string[];
  groupBy: string | null;
}

function normalizeSqlType(raw: string | undefined | null): string {
  if (!raw) return "";
  // Strip precision/scale and trailing modifiers: DECIMAL(10,2) → DECIMAL, INT(11) → INT, TIMESTAMP WITH TIME ZONE → TIMESTAMP
  return raw.trim().toUpperCase().split("(")[0].split(" ")[0];
}

function isNumeric(col: ColumnMeta): boolean {
  const t = normalizeSqlType(col.dataType);
  return /^(BIGINT|INT|INTEGER|SMALLINT|TINYINT|DOUBLE|FLOAT|REAL|DECIMAL|NUMERIC)$/.test(t);
}

function isTime(col: ColumnMeta): boolean {
  const t = normalizeSqlType(col.dataType);
  return /^(TIMESTAMP|DATE|TIME|DATETIME)$/.test(t);
}

export function recommendChart(columns: ColumnMeta[]): ChartConfig {
  if (columns.length === 0) {
    return { type: "bar", xAxis: "", xAxisType: "category", yAxis: [], groupBy: null };
  }
  const timeCol = columns.find(isTime);
  const numericCols = columns.filter(isNumeric);
  const stringCols = columns.filter((c) => !isNumeric(c) && !isTime(c));

  if (timeCol && numericCols.length >= 1) {
    return {
      type: "line",
      xAxis: timeCol.name,
      xAxisType: "time",
      yAxis: [numericCols[0].name],
      groupBy: null,
    };
  }
  if (numericCols.length >= 2) {
    return {
      type: "scatter",
      xAxis: numericCols[0].name,
      xAxisType: "value",
      yAxis: [numericCols[1].name],
      groupBy: null,
    };
  }
  if (stringCols.length >= 1 && numericCols.length >= 1) {
    return {
      type: "bar",
      xAxis: stringCols[0].name,
      xAxisType: "category",
      yAxis: [numericCols[0].name],
      groupBy: null,
    };
  }
  return { type: "bar", xAxis: columns[0].name, xAxisType: "category", yAxis: [], groupBy: null };
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
  if (cfg.type === "scatter") {
    const series = cfg.yAxis.map((y) => ({
      name: y,
      type: "scatter",
      data: rows.map((r) => [r[cfg.xAxis], r[y]]),
    }));
    return {
      tooltip: { trigger: "item" },
      xAxis: { type: "value" },
      yAxis: { type: "value" },
      series,
    };
  }
  const xAxisConfig = {
    type: cfg.xAxisType,
    data: cfg.xAxisType === "category" ? rows.map((r) => r[cfg.xAxis]) : undefined,
  };
  const series = cfg.yAxis.map((y) => ({
    name: y,
    type: cfg.type === "area" ? "line" : cfg.type,
    data: rows.map((r) => r[y]),
    areaStyle: cfg.type === "area" ? {} : undefined,
  }));
  return {
    tooltip: { trigger: "axis" },
    xAxis: xAxisConfig,
    yAxis: { type: "value" },
    series,
    legend: { top: 4 },
    grid: { top: 32, bottom: 32, left: 48, right: 16 },
  };
}
