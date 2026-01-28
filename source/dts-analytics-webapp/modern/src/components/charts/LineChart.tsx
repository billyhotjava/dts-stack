import React, { useCallback, useMemo, useRef, useState } from 'react';
import { getChartColor, formatChartValue } from './chartColors';
import { ChartLegend, type LegendItem } from './ChartLegend';
import { ChartTooltip, type TooltipData, useChartTooltip } from './ChartTooltip';

interface LineChartProps {
  data: {
    rows: any[][];
    cols: { name: string; display_name?: string; base_type?: string }[];
  };
  xAxisIndex?: number;
  yAxisIndices?: number[];
  showDots?: boolean;
  showArea?: boolean;
  smooth?: boolean;
  colors?: string[];
}

// Compute cubic bezier control points for smooth curve
function smoothPath(points: { x: number; y: number }[]): string {
  if (points.length < 2) return '';
  if (points.length === 2) {
    return `M ${points[0].x} ${points[0].y} L ${points[1].x} ${points[1].y}`;
  }

  let d = `M ${points[0].x} ${points[0].y}`;
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[Math.max(0, i - 1)];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[Math.min(points.length - 1, i + 2)];

    const tension = 0.3;
    const cp1x = p1.x + (p2.x - p0.x) * tension;
    const cp1y = p1.y + (p2.y - p0.y) * tension;
    const cp2x = p2.x - (p3.x - p1.x) * tension;
    const cp2y = p2.y - (p3.y - p1.y) * tension;

    d += ` C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${p2.x} ${p2.y}`;
  }
  return d;
}

function linearPath(points: { x: number; y: number }[]): string {
  return points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ');
}

export function LineChart({
  data,
  xAxisIndex = 0,
  yAxisIndices = [1],
  showDots = true,
  showArea = false,
  smooth = true,
  colors
}: LineChartProps) {
  const svgRef = useRef<SVGSVGElement>(null);
  const [hiddenSeries, setHiddenSeries] = useState<Set<string>>(new Set());
  const [hoveredPoint, setHoveredPoint] = useState<{ seriesIdx: number; pointIdx: number } | null>(null);
  const tooltip = useChartTooltip();

  const chartData = useMemo(() => {
    if (!data?.rows?.length || !data?.cols?.length) {
      return { series: [], maxValue: 0, minValue: 0, labels: [] };
    }

    const labels = data.rows.map(row => String(row[xAxisIndex] ?? ''));
    const series = yAxisIndices.map((yIdx, seriesIdx) => ({
      key: `series-${seriesIdx}`,
      name: data.cols[yIdx]?.display_name || data.cols[yIdx]?.name || `Series ${seriesIdx + 1}`,
      values: data.rows.map(row => Number(row[yIdx]) || 0),
      color: colors?.[seriesIdx % (colors?.length || 1)] || getChartColor(seriesIdx)
    }));

    const visibleSeries = series.filter(s => !hiddenSeries.has(s.key));
    const allValues = visibleSeries.flatMap(s => s.values);
    const max = allValues.length > 0 ? Math.max(...allValues, 1) : 1;
    const min = allValues.length > 0 ? Math.min(...allValues, 0) : 0;

    return { series, maxValue: max, minValue: min, labels };
  }, [data, xAxisIndex, yAxisIndices, colors, hiddenSeries]);

  const toggleSeries = useCallback((key: string) => {
    setHiddenSeries(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }, []);

  if (chartData.series.length === 0) {
    return <div className="chart-container__empty" style={{ minHeight: 200 }}>No data to display</div>;
  }

  const width = 600;
  const height = 300;
  const padding = { top: 20, right: 20, bottom: 40, left: 60 };
  const chartWidth = width - padding.left - padding.right;
  const chartHeight = height - padding.top - padding.bottom;

  const { maxValue, minValue, series, labels } = chartData;
  const valueRange = maxValue - minValue || 1;

  const xLabel = data.cols[xAxisIndex]?.display_name || data.cols[xAxisIndex]?.name || '';

  const getX = (i: number) => padding.left + (i / (labels.length - 1 || 1)) * chartWidth;
  const getY = (value: number) => padding.top + chartHeight - ((value - minValue) / valueRange) * chartHeight;

  const legendItems: LegendItem[] = series.map(s => ({
    key: s.key,
    label: s.name,
    color: s.color,
  }));

  const handlePointHover = (seriesIdx: number, pointIdx: number, event: React.MouseEvent) => {
    setHoveredPoint({ seriesIdx, pointIdx });
    const s = series[seriesIdx];
    tooltip.showTooltip(
      {
        title: labels[pointIdx],
        items: series
          .filter(ss => !hiddenSeries.has(ss.key))
          .map(ss => ({
            label: ss.name,
            value: ss.values[pointIdx],
            color: ss.color,
          })),
      },
      { x: event.clientX, y: event.clientY }
    );
  };

  const handlePointLeave = () => {
    setHoveredPoint(null);
    tooltip.hideTooltip();
  };

  return (
    <div style={{ width: '100%', display: 'flex', flexDirection: 'column' }}>
      <svg
        ref={svgRef}
        width="100%"
        height="100%"
        viewBox={`0 0 ${width} ${height}`}
        preserveAspectRatio="xMidYMid meet"
        style={{ minHeight: 260 }}
      >
        {/* Grid lines */}
        {[0, 0.25, 0.5, 0.75, 1].map((ratio, i) => {
          const y = padding.top + chartHeight * (1 - ratio);
          const value = minValue + valueRange * ratio;
          return (
            <g key={i}>
              <line
                x1={padding.left}
                y1={y}
                x2={width - padding.right}
                y2={y}
                stroke="var(--color-border, #eee)"
                strokeWidth={1}
              />
              <text x={padding.left - 8} y={y + 4} fontSize={10} fill="var(--color-text-tertiary, #888)" textAnchor="end">
                {formatChartValue(value, { compact: true })}
              </text>
            </g>
          );
        })}

        {/* X-axis labels */}
        {labels.map((label, i) => {
          const x = getX(i);
          const showLabel = labels.length <= 12 || i % Math.ceil(labels.length / 12) === 0;
          if (!showLabel) return null;
          return (
            <text
              key={i}
              x={x}
              y={height - padding.bottom + 16}
              fontSize={10}
              fill="var(--color-text-tertiary, #888)"
              textAnchor="middle"
            >
              {label.length > 12 ? label.slice(0, 12) + '...' : label}
            </text>
          );
        })}

        {/* X-axis label */}
        {xLabel && (
          <text
            x={width / 2}
            y={height - 4}
            fontSize={11}
            fill="var(--color-text-secondary, #666)"
            textAnchor="middle"
            fontWeight={500}
          >
            {xLabel}
          </text>
        )}

        {/* Hover crosshair */}
        {hoveredPoint !== null && (
          <line
            x1={getX(hoveredPoint.pointIdx)}
            y1={padding.top}
            x2={getX(hoveredPoint.pointIdx)}
            y2={padding.top + chartHeight}
            stroke="var(--color-border-hover, #ccc)"
            strokeWidth={1}
            strokeDasharray="4 4"
          />
        )}

        {/* Series */}
        {series.map((s, seriesIdx) => {
          if (hiddenSeries.has(s.key)) return null;
          const points = s.values.map((v, i) => ({ x: getX(i), y: getY(v) }));
          const pathD = smooth ? smoothPath(points) : linearPath(points);

          // Area path: close to bottom
          const areaD = showArea
            ? `${pathD} L ${points[points.length - 1].x} ${padding.top + chartHeight} L ${points[0].x} ${padding.top + chartHeight} Z`
            : '';

          return (
            <g key={seriesIdx}>
              {/* Gradient definition for area */}
              {showArea && (
                <defs>
                  <linearGradient id={`area-gradient-${seriesIdx}`} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={s.color} stopOpacity={0.3} />
                    <stop offset="100%" stopColor={s.color} stopOpacity={0.02} />
                  </linearGradient>
                </defs>
              )}

              {/* Area fill */}
              {showArea && (
                <path
                  d={areaD}
                  fill={`url(#area-gradient-${seriesIdx})`}
                />
              )}

              {/* Line */}
              <path
                d={pathD}
                fill="none"
                stroke={s.color}
                strokeWidth={2.5}
                strokeLinecap="round"
                strokeLinejoin="round"
              />

              {/* Dots */}
              {showDots && points.map((p, i) => (
                <circle
                  key={i}
                  cx={p.x}
                  cy={p.y}
                  r={hoveredPoint?.seriesIdx === seriesIdx && hoveredPoint?.pointIdx === i ? 6 : 4}
                  fill={s.color}
                  stroke="var(--color-bg-primary, #fff)"
                  strokeWidth={2}
                  style={{ cursor: 'pointer', transition: 'r 0.15s ease' }}
                  onMouseEnter={(e) => handlePointHover(seriesIdx, i, e)}
                  onMouseMove={(e) => tooltip.updatePosition({ x: e.clientX, y: e.clientY })}
                  onMouseLeave={handlePointLeave}
                />
              ))}

              {/* Invisible hit areas for tooltips when dots are hidden */}
              {!showDots && points.map((p, i) => (
                <circle
                  key={`hit-${i}`}
                  cx={p.x}
                  cy={p.y}
                  r={8}
                  fill="transparent"
                  style={{ cursor: 'pointer' }}
                  onMouseEnter={(e) => handlePointHover(seriesIdx, i, e)}
                  onMouseMove={(e) => tooltip.updatePosition({ x: e.clientX, y: e.clientY })}
                  onMouseLeave={handlePointLeave}
                />
              ))}
            </g>
          );
        })}

        {/* Axes */}
        <line
          x1={padding.left}
          y1={padding.top}
          x2={padding.left}
          y2={padding.top + chartHeight}
          stroke="var(--color-border, #ccc)"
          strokeWidth={1}
        />
        <line
          x1={padding.left}
          y1={padding.top + chartHeight}
          x2={width - padding.right}
          y2={padding.top + chartHeight}
          stroke="var(--color-border, #ccc)"
          strokeWidth={1}
        />
      </svg>

      {/* Legend */}
      {series.length > 1 && (
        <ChartLegend
          items={legendItems}
          hiddenItems={hiddenSeries}
          onItemClick={toggleSeries}
          orientation="horizontal"
          position="bottom"
        />
      )}

      {/* Tooltip */}
      <ChartTooltip
        data={tooltip.tooltipData}
        position={tooltip.tooltipPosition}
        visible={tooltip.isVisible}
      />
    </div>
  );
}

export default LineChart;
