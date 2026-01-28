import React, { useMemo, useRef, useState } from 'react';
import { getChartColor, formatChartValue } from './chartColors';
import { ChartTooltip, useChartTooltip } from './ChartTooltip';

interface BarChartProps {
  data: {
    rows: any[][];
    cols: { name: string; display_name?: string; base_type?: string }[];
  };
  xAxisIndex?: number;
  yAxisIndex?: number;
  yAxisIndices?: number[];
  orientation?: 'vertical' | 'horizontal';
  showValues?: boolean;
  stacked?: boolean;
  colors?: string[];
}

export function BarChart({
  data,
  xAxisIndex = 0,
  yAxisIndex = 1,
  yAxisIndices,
  orientation = 'vertical',
  showValues = false,
  stacked = false,
  colors
}: BarChartProps) {
  const tooltip = useChartTooltip();
  const [hoveredBar, setHoveredBar] = useState<{ groupIdx: number; barIdx: number } | null>(null);

  const effectiveYIndices = yAxisIndices || [yAxisIndex];

  const { groups, maxValue, labels, seriesNames } = useMemo(() => {
    if (!data?.rows?.length || !data?.cols?.length) {
      return { groups: [], maxValue: 0, labels: [], seriesNames: [] };
    }

    const labels = data.rows.map(row => String(row[xAxisIndex] ?? ''));
    const seriesNames = effectiveYIndices.map(idx =>
      data.cols[idx]?.display_name || data.cols[idx]?.name || `Series`
    );

    const groups = data.rows.map((row, rowIdx) => {
      const values = effectiveYIndices.map(idx => Number(row[idx]) || 0);
      return { label: labels[rowIdx], values };
    });

    let max: number;
    if (stacked && effectiveYIndices.length > 1) {
      max = Math.max(...groups.map(g => g.values.reduce((a, b) => a + b, 0)), 1);
    } else {
      max = Math.max(...groups.flatMap(g => g.values), 1);
    }

    return { groups, maxValue: max, labels, seriesNames };
  }, [data, xAxisIndex, effectiveYIndices, stacked]);

  if (groups.length === 0) {
    return <div className="chart-container__empty" style={{ minHeight: 200 }}>No data to display</div>;
  }

  const xLabel = data.cols[xAxisIndex]?.display_name || data.cols[xAxisIndex]?.name || '';
  const yLabel = effectiveYIndices.length === 1
    ? (data.cols[effectiveYIndices[0]]?.display_name || data.cols[effectiveYIndices[0]]?.name || '')
    : '';

  const handleBarHover = (groupIdx: number, barIdx: number, event: React.MouseEvent) => {
    setHoveredBar({ groupIdx, barIdx });
    const group = groups[groupIdx];
    tooltip.showTooltip(
      {
        title: group.label,
        items: group.values.map((v, i) => ({
          label: seriesNames[i],
          value: v,
          color: colors?.[i % (colors?.length || 1)] || getChartColor(i),
        })),
      },
      { x: event.clientX, y: event.clientY }
    );
  };

  const handleBarLeave = () => {
    setHoveredBar(null);
    tooltip.hideTooltip();
  };

  // Horizontal bar chart
  if (orientation === 'horizontal') {
    return (
      <div style={{ width: '100%', display: 'flex', flexDirection: 'column', minHeight: Math.max(200, groups.length * 36 + 40) }}>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 'var(--spacing-xs, 4px)', padding: 'var(--spacing-md, 16px)' }}>
          {groups.map((group, groupIdx) => (
            <div
              key={groupIdx}
              style={{ display: 'flex', alignItems: 'center', gap: 'var(--spacing-sm, 8px)' }}
              onMouseEnter={(e) => handleBarHover(groupIdx, 0, e)}
              onMouseMove={(e) => tooltip.updatePosition({ x: e.clientX, y: e.clientY })}
              onMouseLeave={handleBarLeave}
            >
              <div style={{
                width: 120,
                fontSize: 'var(--font-size-sm, 12px)',
                color: 'var(--color-text-secondary, #666)',
                textAlign: 'right',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                flexShrink: 0,
              }} title={group.label}>
                {group.label.length > 18 ? group.label.slice(0, 18) + '...' : group.label}
              </div>
              <div style={{
                flex: 1,
                height: 28,
                backgroundColor: 'var(--color-bg-tertiary, #f5f5f5)',
                borderRadius: 'var(--radius-sm, 4px)',
                overflow: 'hidden',
                display: 'flex',
              }}>
                {stacked && effectiveYIndices.length > 1 ? (
                  // Stacked bars
                  group.values.map((v, barIdx) => {
                    const pct = (v / maxValue) * 100;
                    const color = colors?.[barIdx % (colors?.length || 1)] || getChartColor(barIdx);
                    return (
                      <div
                        key={barIdx}
                        style={{
                          width: `${pct}%`,
                          height: '100%',
                          backgroundColor: color,
                          opacity: hoveredBar?.groupIdx === groupIdx && hoveredBar?.barIdx !== barIdx ? 0.6 : 1,
                          transition: 'width 0.3s ease, opacity 0.15s ease',
                        }}
                      />
                    );
                  })
                ) : (
                  <div
                    style={{
                      width: `${(group.values[0] / maxValue) * 100}%`,
                      height: '100%',
                      backgroundColor: colors?.[groupIdx % (colors?.length || 1)] || getChartColor(groupIdx),
                      borderRadius: 'var(--radius-sm, 4px)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'flex-end',
                      paddingRight: 'var(--spacing-sm, 8px)',
                      transition: 'width 0.3s ease',
                      minWidth: 4,
                    }}
                  >
                    {showValues && (
                      <span style={{
                        fontSize: 'var(--font-size-xs, 11px)',
                        color: '#fff',
                        fontWeight: 500,
                      }}>
                        {formatChartValue(group.values[0], { compact: true })}
                      </span>
                    )}
                  </div>
                )}
              </div>
              {showValues && stacked && (
                <span style={{
                  fontSize: 'var(--font-size-xs, 11px)',
                  color: 'var(--color-text-secondary, #666)',
                  fontWeight: 500,
                  minWidth: 40,
                }}>
                  {formatChartValue(group.values.reduce((a, b) => a + b, 0), { compact: true })}
                </span>
              )}
            </div>
          ))}
        </div>

        {/* Legend for stacked */}
        {stacked && effectiveYIndices.length > 1 && (
          <div style={{ display: 'flex', justifyContent: 'center', gap: 'var(--spacing-md, 16px)', padding: 'var(--spacing-sm, 8px) 0', flexWrap: 'wrap' }}>
            {seriesNames.map((name, i) => (
              <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 'var(--spacing-xs, 4px)' }}>
                <div style={{
                  width: 12, height: 12,
                  borderRadius: 'var(--radius-xs, 2px)',
                  backgroundColor: colors?.[i % (colors?.length || 1)] || getChartColor(i),
                }} />
                <span style={{ fontSize: 'var(--font-size-sm, 12px)', color: 'var(--color-text-secondary, #666)' }}>{name}</span>
              </div>
            ))}
          </div>
        )}

        <ChartTooltip data={tooltip.tooltipData} position={tooltip.tooltipPosition} visible={tooltip.isVisible} />
      </div>
    );
  }

  // Vertical bars - SVG-based
  const width = 600;
  const height = 300;
  const pad = { top: 20, right: 20, bottom: 50, left: 60 };
  const cw = width - pad.left - pad.right;
  const ch = height - pad.top - pad.bottom;
  const groupWidth = cw / groups.length;
  const barPadding = Math.max(4, groupWidth * 0.15);
  const barsPerGroup = stacked ? 1 : effectiveYIndices.length;
  const barWidth = Math.min(60, (groupWidth - barPadding * 2) / barsPerGroup);

  const getBarY = (value: number) => pad.top + ch - (value / maxValue) * ch;

  return (
    <div style={{ width: '100%', display: 'flex', flexDirection: 'column' }}>
      <svg width="100%" height="100%" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="xMidYMid meet" style={{ minHeight: 260 }}>
        {/* Grid lines */}
        {[0, 0.25, 0.5, 0.75, 1].map((ratio, i) => {
          const y = pad.top + ch * (1 - ratio);
          const value = maxValue * ratio;
          return (
            <g key={i}>
              <line x1={pad.left} y1={y} x2={width - pad.right} y2={y} stroke="var(--color-border, #eee)" strokeWidth={1} />
              <text x={pad.left - 8} y={y + 4} fontSize={10} fill="var(--color-text-tertiary, #888)" textAnchor="end">
                {formatChartValue(value, { compact: true })}
              </text>
            </g>
          );
        })}

        {/* Bars */}
        {groups.map((group, groupIdx) => {
          const groupX = pad.left + groupIdx * groupWidth;

          if (stacked && effectiveYIndices.length > 1) {
            // Stacked bars
            let stackY = 0;
            return (
              <g key={groupIdx}>
                {group.values.map((v, barIdx) => {
                  const barH = (v / maxValue) * ch;
                  const y = pad.top + ch - stackY - barH;
                  stackY += barH;
                  const color = colors?.[barIdx % (colors?.length || 1)] || getChartColor(barIdx);
                  const isHovered = hoveredBar?.groupIdx === groupIdx;
                  return (
                    <rect
                      key={barIdx}
                      x={groupX + barPadding}
                      y={y}
                      width={groupWidth - barPadding * 2}
                      height={Math.max(1, barH)}
                      fill={color}
                      rx={barIdx === group.values.length - 1 ? 4 : 0}
                      opacity={isHovered && hoveredBar?.barIdx !== barIdx ? 0.7 : 1}
                      style={{ cursor: 'pointer', transition: 'opacity 0.15s ease' }}
                      onMouseEnter={(e) => handleBarHover(groupIdx, barIdx, e)}
                      onMouseMove={(e) => tooltip.updatePosition({ x: e.clientX, y: e.clientY })}
                      onMouseLeave={handleBarLeave}
                    />
                  );
                })}
                {/* X label */}
                <text
                  x={groupX + groupWidth / 2}
                  y={height - pad.bottom + 16}
                  fontSize={10}
                  fill="var(--color-text-tertiary, #888)"
                  textAnchor="middle"
                >
                  {group.label.length > 10 ? group.label.slice(0, 10) + '...' : group.label}
                </text>
              </g>
            );
          }

          // Grouped or single bars
          return (
            <g key={groupIdx}>
              {group.values.map((v, barIdx) => {
                const barH = (v / maxValue) * ch;
                const y = getBarY(v);
                const x = groupX + barPadding + barIdx * barWidth;
                const color = effectiveYIndices.length > 1
                  ? (colors?.[barIdx % (colors?.length || 1)] || getChartColor(barIdx))
                  : (colors?.[groupIdx % (colors?.length || 1)] || getChartColor(groupIdx));
                const isHovered = hoveredBar?.groupIdx === groupIdx && hoveredBar?.barIdx === barIdx;

                return (
                  <g key={barIdx}>
                    <rect
                      x={x}
                      y={y}
                      width={barWidth}
                      height={Math.max(1, barH)}
                      fill={color}
                      rx={4}
                      opacity={isHovered ? 1 : 0.85}
                      style={{ cursor: 'pointer', transition: 'opacity 0.15s ease' }}
                      onMouseEnter={(e) => handleBarHover(groupIdx, barIdx, e)}
                      onMouseMove={(e) => tooltip.updatePosition({ x: e.clientX, y: e.clientY })}
                      onMouseLeave={handleBarLeave}
                    />
                    {showValues && (
                      <text
                        x={x + barWidth / 2}
                        y={y - 6}
                        fontSize={10}
                        fill="var(--color-text-primary, #333)"
                        textAnchor="middle"
                        fontWeight={500}
                      >
                        {formatChartValue(v, { compact: true })}
                      </text>
                    )}
                  </g>
                );
              })}
              {/* X label */}
              <text
                x={groupX + groupWidth / 2}
                y={height - pad.bottom + 16}
                fontSize={10}
                fill="var(--color-text-tertiary, #888)"
                textAnchor="middle"
              >
                {group.label.length > 10 ? group.label.slice(0, 10) + '...' : group.label}
              </text>
            </g>
          );
        })}

        {/* Axes */}
        <line x1={pad.left} y1={pad.top} x2={pad.left} y2={pad.top + ch} stroke="var(--color-border, #ccc)" strokeWidth={1} />
        <line x1={pad.left} y1={pad.top + ch} x2={width - pad.right} y2={pad.top + ch} stroke="var(--color-border, #ccc)" strokeWidth={1} />

        {/* Axis labels */}
        {xLabel && (
          <text x={width / 2} y={height - 4} fontSize={11} fill="var(--color-text-secondary, #666)" textAnchor="middle" fontWeight={500}>
            {xLabel}
          </text>
        )}
      </svg>

      {/* Legend for grouped/stacked */}
      {effectiveYIndices.length > 1 && (
        <div style={{ display: 'flex', justifyContent: 'center', gap: 'var(--spacing-md, 16px)', padding: 'var(--spacing-sm, 8px) 0', flexWrap: 'wrap' }}>
          {seriesNames.map((name, i) => (
            <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 'var(--spacing-xs, 4px)' }}>
              <div style={{
                width: 12, height: 12,
                borderRadius: 'var(--radius-xs, 2px)',
                backgroundColor: colors?.[i % (colors?.length || 1)] || getChartColor(i),
              }} />
              <span style={{ fontSize: 'var(--font-size-sm, 12px)', color: 'var(--color-text-secondary, #666)' }}>{name}</span>
            </div>
          ))}
        </div>
      )}

      <ChartTooltip data={tooltip.tooltipData} position={tooltip.tooltipPosition} visible={tooltip.isVisible} />
    </div>
  );
}

export default BarChart;
