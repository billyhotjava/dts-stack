import React, { useMemo } from 'react';

interface PieChartProps {
  data: {
    rows: any[][];
    cols: { name: string; display_name?: string; base_type?: string }[];
  };
  labelIndex?: number;
  valueIndex?: number;
  showLabels?: boolean;
  showPercentages?: boolean;
  donut?: boolean;
  colors?: string[];
}

const DEFAULT_COLORS = [
  '#509EE3', '#88BF4D', '#F9D45C', '#F2A86F', '#EF8C8C',
  '#A989C5', '#98D9D9', '#7172AD', '#6450a2', '#4C5773'
];

export function PieChart({
  data,
  labelIndex = 0,
  valueIndex = 1,
  showLabels = true,
  showPercentages = true,
  donut = false,
  colors = DEFAULT_COLORS
}: PieChartProps) {
  const chartData = useMemo(() => {
    if (!data?.rows?.length || !data?.cols?.length) {
      return { slices: [], total: 0 };
    }

    const slices = data.rows
      .map((row, i) => ({
        label: String(row[labelIndex] ?? ''),
        value: Math.max(0, Number(row[valueIndex]) || 0),
        color: colors[i % colors.length]
      }))
      .filter(s => s.value > 0);

    const total = slices.reduce((sum, s) => sum + s.value, 0);

    return { slices, total };
  }, [data, labelIndex, valueIndex, colors]);

  if (chartData.slices.length === 0) {
    return <div style={styles.empty}>No data to display</div>;
  }

  const size = 300;
  const center = size / 2;
  const outerRadius = size * 0.4;
  const innerRadius = donut ? outerRadius * 0.6 : 0;
  const labelRadius = outerRadius * 1.15;

  const { slices, total } = chartData;

  // Calculate slice paths
  let currentAngle = -Math.PI / 2; // Start from top
  const slicesWithAngles = slices.map(slice => {
    const angle = (slice.value / total) * 2 * Math.PI;
    const startAngle = currentAngle;
    const endAngle = currentAngle + angle;
    currentAngle = endAngle;

    const midAngle = (startAngle + endAngle) / 2;
    const labelX = center + Math.cos(midAngle) * labelRadius;
    const labelY = center + Math.sin(midAngle) * labelRadius;

    return {
      ...slice,
      startAngle,
      endAngle,
      midAngle,
      labelX,
      labelY,
      percentage: ((slice.value / total) * 100).toFixed(1)
    };
  });

  const createArcPath = (
    startAngle: number,
    endAngle: number,
    innerR: number,
    outerR: number
  ): string => {
    const largeArcFlag = endAngle - startAngle > Math.PI ? 1 : 0;

    const startOuterX = center + Math.cos(startAngle) * outerR;
    const startOuterY = center + Math.sin(startAngle) * outerR;
    const endOuterX = center + Math.cos(endAngle) * outerR;
    const endOuterY = center + Math.sin(endAngle) * outerR;

    if (innerR === 0) {
      // Pie slice (no hole)
      return `
        M ${center} ${center}
        L ${startOuterX} ${startOuterY}
        A ${outerR} ${outerR} 0 ${largeArcFlag} 1 ${endOuterX} ${endOuterY}
        Z
      `;
    }

    // Donut slice
    const startInnerX = center + Math.cos(startAngle) * innerR;
    const startInnerY = center + Math.sin(startAngle) * innerR;
    const endInnerX = center + Math.cos(endAngle) * innerR;
    const endInnerY = center + Math.sin(endAngle) * innerR;

    return `
      M ${startOuterX} ${startOuterY}
      A ${outerR} ${outerR} 0 ${largeArcFlag} 1 ${endOuterX} ${endOuterY}
      L ${endInnerX} ${endInnerY}
      A ${innerR} ${innerR} 0 ${largeArcFlag} 0 ${startInnerX} ${startInnerY}
      Z
    `;
  };

  return (
    <div style={styles.container}>
      <svg
        width="100%"
        height="100%"
        viewBox={`0 0 ${size} ${size}`}
        preserveAspectRatio="xMidYMid meet"
      >
        {slicesWithAngles.map((slice, i) => (
          <g key={i}>
            <path
              d={createArcPath(slice.startAngle, slice.endAngle, innerRadius, outerRadius)}
              fill={slice.color}
              stroke="#fff"
              strokeWidth={2}
            >
              <title>{`${slice.label}: ${formatNumber(slice.value)} (${slice.percentage}%)`}</title>
            </path>
          </g>
        ))}

        {/* Center label for donut */}
        {donut && (
          <text
            x={center}
            y={center}
            textAnchor="middle"
            dominantBaseline="middle"
            fontSize={20}
            fontWeight={600}
            fill="#333"
          >
            {formatNumber(total)}
          </text>
        )}
      </svg>

      {/* Legend */}
      <div style={styles.legend}>
        {slicesWithAngles.map((slice, i) => (
          <div key={i} style={styles.legendItem}>
            <div style={{ ...styles.legendColor, backgroundColor: slice.color }} />
            <span style={styles.legendLabel}>
              {slice.label}
              {showPercentages && <span style={styles.percentage}> ({slice.percentage}%)</span>}
            </span>
          </div>
        ))}
      </div>
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
    flexDirection: 'column',
    alignItems: 'center'
  },
  legend: {
    display: 'flex',
    flexWrap: 'wrap',
    justifyContent: 'center',
    gap: 12,
    padding: '12px 0',
    maxWidth: '100%'
  },
  legendItem: {
    display: 'flex',
    alignItems: 'center',
    gap: 6
  },
  legendColor: {
    width: 12,
    height: 12,
    borderRadius: 2,
    flexShrink: 0
  },
  legendLabel: {
    fontSize: 11,
    color: '#666',
    whiteSpace: 'nowrap'
  },
  percentage: {
    color: '#999'
  },
  empty: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    height: 200,
    color: '#888'
  }
};

export default PieChart;
