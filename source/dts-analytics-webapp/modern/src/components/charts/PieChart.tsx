import React, { useCallback, useMemo, useState } from 'react';
import { getChartColor, formatChartValue, getContrastingTextColor } from './chartColors';
import { ChartLegend, type LegendItem } from './ChartLegend';
import { ChartTooltip, useChartTooltip } from './ChartTooltip';

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

export function PieChart({
  data,
  labelIndex = 0,
  valueIndex = 1,
  showLabels = true,
  showPercentages = true,
  donut = false,
  colors
}: PieChartProps) {
  const [hoveredSlice, setHoveredSlice] = useState<number | null>(null);
  const [hiddenSlices, setHiddenSlices] = useState<Set<string>>(new Set());
  const tooltip = useChartTooltip();

  const chartData = useMemo(() => {
    if (!data?.rows?.length || !data?.cols?.length) {
      return { slices: [], total: 0 };
    }

    const slices = data.rows
      .map((row, i) => ({
        key: `slice-${i}`,
        label: String(row[labelIndex] ?? ''),
        value: Math.max(0, Number(row[valueIndex]) || 0),
        color: colors?.[i % (colors?.length || 1)] || getChartColor(i),
      }))
      .filter(s => s.value > 0 && !hiddenSlices.has(s.key));

    const total = slices.reduce((sum, s) => sum + s.value, 0);

    return { slices, total };
  }, [data, labelIndex, valueIndex, colors, hiddenSlices]);

  const allSlices = useMemo(() => {
    if (!data?.rows?.length) return [];
    return data.rows
      .map((row, i) => ({
        key: `slice-${i}`,
        label: String(row[labelIndex] ?? ''),
        value: Math.max(0, Number(row[valueIndex]) || 0),
        color: colors?.[i % (colors?.length || 1)] || getChartColor(i),
      }))
      .filter(s => s.value > 0);
  }, [data, labelIndex, valueIndex, colors]);

  const toggleSlice = useCallback((key: string) => {
    setHiddenSlices(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }, []);

  if (chartData.slices.length === 0 && allSlices.length === 0) {
    return <div className="chart-container__empty" style={{ minHeight: 200 }}>No data to display</div>;
  }

  const size = 300;
  const center = size / 2;
  const outerRadius = size * 0.38;
  const innerRadius = donut ? outerRadius * 0.6 : 0;
  const hoverExpand = 6;

  const { slices, total } = chartData;

  // Calculate slice paths
  let currentAngle = -Math.PI / 2; // Start from top
  const slicesWithAngles = slices.map((slice, i) => {
    const angle = (slice.value / total) * 2 * Math.PI;
    const startAngle = currentAngle;
    const endAngle = currentAngle + angle;
    currentAngle = endAngle;

    const midAngle = (startAngle + endAngle) / 2;
    const percentage = ((slice.value / total) * 100).toFixed(1);

    // Label position (slightly outside for non-donut, center of arc for donut)
    const labelR = donut ? (outerRadius + innerRadius) / 2 : outerRadius * 0.7;
    const labelX = center + Math.cos(midAngle) * labelR;
    const labelY = center + Math.sin(midAngle) * labelR;

    return {
      ...slice,
      startAngle,
      endAngle,
      midAngle,
      labelX,
      labelY,
      percentage,
      index: i,
    };
  });

  const createArcPath = (
    startAngle: number,
    endAngle: number,
    innerR: number,
    outerR: number,
    cx = center,
    cy = center,
  ): string => {
    const largeArcFlag = endAngle - startAngle > Math.PI ? 1 : 0;

    const startOuterX = cx + Math.cos(startAngle) * outerR;
    const startOuterY = cy + Math.sin(startAngle) * outerR;
    const endOuterX = cx + Math.cos(endAngle) * outerR;
    const endOuterY = cy + Math.sin(endAngle) * outerR;

    if (innerR === 0) {
      return `M ${cx} ${cy} L ${startOuterX} ${startOuterY} A ${outerR} ${outerR} 0 ${largeArcFlag} 1 ${endOuterX} ${endOuterY} Z`;
    }

    const startInnerX = cx + Math.cos(startAngle) * innerR;
    const startInnerY = cy + Math.sin(startAngle) * innerR;
    const endInnerX = cx + Math.cos(endAngle) * innerR;
    const endInnerY = cy + Math.sin(endAngle) * innerR;

    return `M ${startOuterX} ${startOuterY} A ${outerR} ${outerR} 0 ${largeArcFlag} 1 ${endOuterX} ${endOuterY} L ${endInnerX} ${endInnerY} A ${innerR} ${innerR} 0 ${largeArcFlag} 0 ${startInnerX} ${startInnerY} Z`;
  };

  const handleSliceHover = (slice: typeof slicesWithAngles[0], event: React.MouseEvent) => {
    setHoveredSlice(slice.index);
    tooltip.showTooltip(
      {
        items: [{
          label: slice.label,
          value: `${formatChartValue(slice.value, { compact: true })} (${slice.percentage}%)`,
          color: slice.color,
        }],
      },
      { x: event.clientX, y: event.clientY }
    );
  };

  const handleSliceLeave = () => {
    setHoveredSlice(null);
    tooltip.hideTooltip();
  };

  const legendItems: LegendItem[] = allSlices.map(s => ({
    key: s.key,
    label: s.label,
    color: s.color,
    value: showPercentages ? `${((s.value / (total || 1)) * 100).toFixed(1)}%` : undefined,
  }));

  return (
    <div style={{ width: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <svg
        width="100%"
        height="100%"
        viewBox={`0 0 ${size} ${size}`}
        preserveAspectRatio="xMidYMid meet"
        style={{ maxWidth: size, minHeight: 240 }}
      >
        {/* Drop shadow filter */}
        <defs>
          <filter id="pie-shadow" x="-20%" y="-20%" width="140%" height="140%">
            <feDropShadow dx="0" dy="2" stdDeviation="3" floodOpacity="0.15" />
          </filter>
        </defs>

        {slicesWithAngles.map((slice) => {
          const isHovered = hoveredSlice === slice.index;
          const expandedOuter = isHovered ? outerRadius + hoverExpand : outerRadius;
          const expandedInner = donut ? (isHovered ? innerRadius - 2 : innerRadius) : 0;

          // Move slice outward on hover
          const offsetX = isHovered ? Math.cos(slice.midAngle) * 4 : 0;
          const offsetY = isHovered ? Math.sin(slice.midAngle) * 4 : 0;

          return (
            <g key={slice.index}>
              <path
                d={createArcPath(
                  slice.startAngle,
                  slice.endAngle,
                  expandedInner,
                  expandedOuter,
                  center + offsetX,
                  center + offsetY,
                )}
                fill={slice.color}
                stroke="var(--color-bg-primary, #fff)"
                strokeWidth={2}
                style={{
                  cursor: 'pointer',
                  transition: 'all 0.2s ease',
                  filter: isHovered ? 'url(#pie-shadow)' : undefined,
                }}
                onMouseEnter={(e) => handleSliceHover(slice, e)}
                onMouseMove={(e) => tooltip.updatePosition({ x: e.clientX, y: e.clientY })}
                onMouseLeave={handleSliceLeave}
              />
              {/* Percentage label inside slice (for slices > 8%) */}
              {showPercentages && parseFloat(slice.percentage) > 8 && (
                <text
                  x={slice.labelX + offsetX}
                  y={slice.labelY + offsetY}
                  textAnchor="middle"
                  dominantBaseline="middle"
                  fontSize={11}
                  fontWeight={600}
                  fill={getContrastingTextColor(slice.color)}
                  style={{ pointerEvents: 'none' }}
                >
                  {slice.percentage}%
                </text>
              )}
            </g>
          );
        })}

        {/* Center label for donut */}
        {donut && (
          <g>
            <text
              x={center}
              y={center - 6}
              textAnchor="middle"
              dominantBaseline="middle"
              fontSize={22}
              fontWeight={700}
              fill="var(--color-text-primary, #333)"
            >
              {formatChartValue(total, { compact: true })}
            </text>
            <text
              x={center}
              y={center + 14}
              textAnchor="middle"
              dominantBaseline="middle"
              fontSize={11}
              fill="var(--color-text-tertiary, #999)"
            >
              Total
            </text>
          </g>
        )}
      </svg>

      {/* Legend */}
      {showLabels && (
        <ChartLegend
          items={legendItems}
          hiddenItems={hiddenSlices}
          onItemClick={toggleSlice}
          orientation="horizontal"
          position="bottom"
        />
      )}

      <ChartTooltip
        data={tooltip.tooltipData}
        position={tooltip.tooltipPosition}
        visible={tooltip.isVisible}
      />
    </div>
  );
}

export default PieChart;
