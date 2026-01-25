import React, { useMemo } from 'react';

interface LineChartProps {
  data: {
    rows: any[][];
    cols: { name: string; display_name?: string; base_type?: string }[];
  };
  xAxisIndex?: number;
  yAxisIndices?: number[];
  showDots?: boolean;
  showArea?: boolean;
  colors?: string[];
}

const DEFAULT_COLORS = [
  '#509EE3', '#88BF4D', '#F9D45C', '#F2A86F', '#EF8C8C',
  '#A989C5', '#98D9D9', '#7172AD', '#6450a2', '#4C5773'
];

export function LineChart({
  data,
  xAxisIndex = 0,
  yAxisIndices = [1],
  showDots = true,
  showArea = false,
  colors = DEFAULT_COLORS
}: LineChartProps) {
  const chartData = useMemo(() => {
    if (!data?.rows?.length || !data?.cols?.length) {
      return { series: [], maxValue: 0, minValue: 0, labels: [] };
    }

    const labels = data.rows.map(row => String(row[xAxisIndex] ?? ''));
    const series = yAxisIndices.map((yIdx, seriesIdx) => ({
      name: data.cols[yIdx]?.display_name || data.cols[yIdx]?.name || `Series ${seriesIdx + 1}`,
      values: data.rows.map(row => Number(row[yIdx]) || 0),
      color: colors[seriesIdx % colors.length]
    }));

    const allValues = series.flatMap(s => s.values);
    const max = Math.max(...allValues, 1);
    const min = Math.min(...allValues, 0);

    return { series, maxValue: max, minValue: min, labels };
  }, [data, xAxisIndex, yAxisIndices, colors]);

  if (chartData.series.length === 0) {
    return <div style={styles.empty}>No data to display</div>;
  }

  const width = 600;
  const height = 300;
  const padding = { top: 20, right: 20, bottom: 40, left: 60 };
  const chartWidth = width - padding.left - padding.right;
  const chartHeight = height - padding.top - padding.bottom;

  const { maxValue, minValue, series, labels } = chartData;
  const valueRange = maxValue - minValue || 1;

  const xLabel = data.cols[xAxisIndex]?.display_name || data.cols[xAxisIndex]?.name || 'X';

  const getX = (i: number) => padding.left + (i / (labels.length - 1 || 1)) * chartWidth;
  const getY = (value: number) => padding.top + chartHeight - ((value - minValue) / valueRange) * chartHeight;

  return (
    <div style={styles.container}>
      <svg width="100%" height="100%" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="xMidYMid meet">
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
                stroke="#eee"
                strokeWidth={1}
              />
              <text x={padding.left - 8} y={y + 4} fontSize={10} fill="#888" textAnchor="end">
                {formatNumber(value)}
              </text>
            </g>
          );
        })}

        {/* X-axis labels */}
        {labels.map((label, i) => {
          const x = getX(i);
          const showLabel = labels.length <= 10 || i % Math.ceil(labels.length / 10) === 0;
          if (!showLabel) return null;
          return (
            <text
              key={i}
              x={x}
              y={height - padding.bottom + 16}
              fontSize={10}
              fill="#888"
              textAnchor="middle"
            >
              {label.length > 10 ? label.slice(0, 10) + '...' : label}
            </text>
          );
        })}

        {/* X-axis label */}
        <text
          x={width / 2}
          y={height - 5}
          fontSize={11}
          fill="#666"
          textAnchor="middle"
          fontWeight={500}
        >
          {xLabel}
        </text>

        {/* Series */}
        {series.map((s, seriesIdx) => {
          const points = s.values.map((v, i) => ({ x: getX(i), y: getY(v) }));
          const pathD = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ');

          const areaD = showArea
            ? `${pathD} L ${points[points.length - 1].x} ${padding.top + chartHeight} L ${points[0].x} ${padding.top + chartHeight} Z`
            : '';

          return (
            <g key={seriesIdx}>
              {/* Area fill */}
              {showArea && (
                <path
                  d={areaD}
                  fill={s.color}
                  fillOpacity={0.1}
                />
              )}

              {/* Line */}
              <path
                d={pathD}
                fill="none"
                stroke={s.color}
                strokeWidth={2}
                strokeLinecap="round"
                strokeLinejoin="round"
              />

              {/* Dots */}
              {showDots && points.map((p, i) => (
                <circle
                  key={i}
                  cx={p.x}
                  cy={p.y}
                  r={4}
                  fill={s.color}
                  stroke="#fff"
                  strokeWidth={2}
                >
                  <title>{`${labels[i]}: ${formatNumber(s.values[i])}`}</title>
                </circle>
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
          stroke="#ccc"
          strokeWidth={1}
        />
        <line
          x1={padding.left}
          y1={padding.top + chartHeight}
          x2={width - padding.right}
          y2={padding.top + chartHeight}
          stroke="#ccc"
          strokeWidth={1}
        />
      </svg>

      {/* Legend */}
      {series.length > 1 && (
        <div style={styles.legend}>
          {series.map((s, i) => (
            <div key={i} style={styles.legendItem}>
              <div style={{ ...styles.legendColor, backgroundColor: s.color }} />
              <span style={styles.legendLabel}>{s.name}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function formatNumber(n: number): string {
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M';
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K';
  if (Number.isInteger(n)) return String(n);
  return n.toFixed(2);
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    width: '100%',
    height: '100%',
    minHeight: 300,
    display: 'flex',
    flexDirection: 'column'
  },
  legend: {
    display: 'flex',
    justifyContent: 'center',
    gap: 16,
    padding: '8px 0',
    flexWrap: 'wrap'
  },
  legendItem: {
    display: 'flex',
    alignItems: 'center',
    gap: 4
  },
  legendColor: {
    width: 12,
    height: 12,
    borderRadius: 2
  },
  legendLabel: {
    fontSize: 11,
    color: '#666'
  },
  empty: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    height: 200,
    color: '#888'
  }
};

export default LineChart;
