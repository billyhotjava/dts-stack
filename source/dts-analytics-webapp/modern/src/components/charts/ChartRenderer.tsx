import React, { useState, useEffect, lazy, Suspense } from 'react';
import { DataTable } from '../DataTable';
import { Spinner } from '../../ui/Loading/Spinner';
import type { ReferenceLine } from './LineChart';
import './ChartComponents.css';

// Lazy load ECharts to avoid blocking initial bundle
const EChartsRuntime = lazy(() => import('./EChartsRuntime'));

export type VisualizationType =
  | 'table'
  | 'line'
  | 'bar'
  | 'row'  // horizontal bar
  | 'pie'
  | 'area'
  | 'scalar'
  | 'number'
  | 'progress'
  | 'gauge'
  | 'combo'
  | 'waterfall'
  | 'funnel'
  | 'scatter'
  | 'map';

export interface VisualizationSettings {
  'graph.dimensions'?: string[];
  'graph.metrics'?: string[];
  'graph.colors'?: string[];
  'graph.x_axis.axis_enabled'?: boolean;
  'graph.y_axis.axis_enabled'?: boolean;
  'graph.x_axis.title_text'?: string;
  'graph.y_axis.title_text'?: string;
  'graph.x_axis.label_rotate'?: number;
  'graph.show_values'?: boolean;
  'graph.label_value_frequency'?: string;
  'graph.show_dots'?: boolean;
  'graph.show_area'?: boolean;
  'graph.smooth'?: boolean;
  'stackable.stack_type'?: 'stacked' | 'normalized' | null;
  'pie.show_legend'?: boolean;
  'pie.show_total'?: boolean;
  'pie.percent_visibility'?: 'off' | 'legend' | 'inside';
  'pie.slice_threshold'?: number;
  'scalar.prefix'?: string;
  'scalar.suffix'?: string;
  'scalar.compact_primary_number'?: boolean;
  'series_settings'?: Record<string, { color?: string; display?: string }>;
  'graph.reference_lines'?: ReferenceLine[];
  'table.pivot'?: boolean;
  'table.pivot_column'?: string;
  'table.cell_column'?: string;
  [key: string]: any;
}

interface ChartRendererProps {
  data: {
    rows: any[][];
    cols: { name: string; display_name?: string; base_type?: string }[];
  } | null;
  display: VisualizationType;
  settings?: VisualizationSettings;
  loading?: boolean;
  error?: unknown;
  className?: string;
  style?: React.CSSProperties;
}

const DEFAULT_COLORS = ['#509EE3', '#88BF4D', '#F9D45C', '#F2A86F', '#EF8C8C', '#98D9D9'];

export function ChartRenderer({
  data,
  display,
  settings = {},
  loading = false,
  error,
  className,
  style
}: ChartRendererProps) {
  if (loading) {
    return (
      <div className={`chart-container ${className || ''}`} style={style}>
        <div className="chart-container__loading"><Spinner size="lg" /></div>
      </div>
    );
  }

  if (error) {
    const message = error instanceof Error ? error.message : String(error);
    return (
      <div className={`chart-container ${className || ''}`} style={style}>
        <div className="chart-container__error">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" /></svg>
          <div style={{ fontSize: 'var(--font-size-sm)' }}>{message}</div>
        </div>
      </div>
    );
  }

  if (!data || !data.rows || !data.cols || data.rows.length === 0) {
    return (
      <div className={`chart-container ${className || ''}`} style={style}>
        <div className="chart-container__empty" style={{ minHeight: 200 }}>No data to display</div>
      </div>
    );
  }

  const colors = settings['graph.colors'] || DEFAULT_COLORS;

  // Resolve dimension (x-axis) and metric (y-axis) indices
  const dimensionNames = settings['graph.dimensions'] || [];
  const metricNames = settings['graph.metrics'] || [];
  const xAxisIndex = dimensionNames.length > 0
    ? data.cols.findIndex(c => c.name === dimensionNames[0] || c.display_name === dimensionNames[0])
    : 0;
  const effectiveXIdx = xAxisIndex >= 0 ? xAxisIndex : 0;
  const yAxisIndices = metricNames.length > 0
    ? metricNames.map(name => data.cols.findIndex(c => c.name === name || c.display_name === name)).filter(i => i >= 0)
    : data.cols.map((_, i) => i).filter(i => i !== effectiveXIdx).slice(0, 5);

  const labels = data.rows.map(row => String(row[effectiveXIdx] ?? ''));

  const content = (() => {
    switch (display) {
      case 'line':
      case 'area': {
        const series = yAxisIndices.map((yIdx, si) => ({
          name: data.cols[yIdx]?.display_name || data.cols[yIdx]?.name || `Series ${si + 1}`,
          type: 'line' as const,
          data: data.rows.map(row => Number(row[yIdx]) || 0),
          smooth: settings['graph.smooth'] !== false,
          showSymbol: settings['graph.show_dots'] !== false,
          symbolSize: 6,
          areaStyle: (display === 'area' || settings['graph.show_area']) ? { opacity: 0.15 } : undefined,
          ...(settings['stackable.stack_type'] === 'stacked' ? { stack: 'total' } : {}),
          itemStyle: { color: colors[si % colors.length] },
        }));

        const refLines = settings['graph.reference_lines'] || [];
        const yAxisMarkLines = refLines.map(ref => ({
          yAxis: ref.y,
          label: { show: !!ref.label, formatter: ref.label || '', position: 'end' as const },
          lineStyle: { color: ref.color || '#dc2626', type: 'dashed' as const },
        }));

        if (yAxisMarkLines.length > 0 && series.length > 0) {
          (series[0] as any).markLine = {
            silent: true,
            symbol: 'none',
            data: yAxisMarkLines,
          };
        }

        const option = {
          color: colors,
          tooltip: { trigger: 'axis' },
          legend: { data: series.map(s => s.name), bottom: 0, textStyle: { fontSize: 12 } },
          grid: { left: 50, right: 20, top: 20, bottom: 40 },
          xAxis: { type: 'category', data: labels, axisLabel: { rotate: settings['graph.x_axis.label_rotate'] || 0, fontSize: 11 } },
          yAxis: { type: 'value', axisLabel: { fontSize: 11 } },
          series,
        };
        return <Suspense fallback={<Spinner size="md" />}><EChartsRuntime option={option} style={{ width: '100%', height: '100%', minHeight: 260 }} /></Suspense>;
      }

      case 'bar':
      case 'row': {
        const isHorizontal = display === 'row';
        const series = yAxisIndices.map((yIdx, si) => ({
          name: data.cols[yIdx]?.display_name || data.cols[yIdx]?.name || `Series ${si + 1}`,
          type: 'bar' as const,
          data: data.rows.map(row => Number(row[yIdx]) || 0),
          ...(settings['stackable.stack_type'] === 'stacked' ? { stack: 'total' } : {}),
          label: settings['graph.show_values'] ? { show: true, position: 'top' as const, fontSize: 11 } : undefined,
          itemStyle: { color: colors[si % colors.length] },
        }));

        const categoryAxis = { type: 'category' as const, data: labels, axisLabel: { fontSize: 11 } };
        const valueAxis = { type: 'value' as const, axisLabel: { fontSize: 11 } };

        const option = {
          color: colors,
          tooltip: { trigger: 'axis' },
          legend: { data: series.map(s => s.name), bottom: 0, textStyle: { fontSize: 12 } },
          grid: { left: isHorizontal ? 80 : 50, right: 20, top: 20, bottom: 40 },
          xAxis: isHorizontal ? valueAxis : categoryAxis,
          yAxis: isHorizontal ? categoryAxis : valueAxis,
          series,
        };
        return <Suspense fallback={<Spinner size="md" />}><EChartsRuntime option={option} style={{ width: '100%', height: '100%', minHeight: 260 }} /></Suspense>;
      }

      case 'pie': {
        const labelIdx = effectiveXIdx;
        const valueIdx = yAxisIndices[0] ?? 1;
        const pieData = data.rows.map((row, i) => ({
          name: String(row[labelIdx] ?? ''),
          value: Number(row[valueIdx]) || 0,
          itemStyle: { color: colors[i % colors.length] },
        }));

        const isDonut = settings['pie.show_total'] === true;
        const option = {
          color: colors,
          tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
          legend: settings['pie.show_legend'] !== false ? { bottom: 0, textStyle: { fontSize: 12 } } : undefined,
          series: [{
            type: 'pie',
            radius: isDonut ? ['40%', '65%'] : ['0%', '65%'],
            data: pieData,
            label: {
              show: settings['pie.percent_visibility'] !== 'off',
              formatter: settings['pie.percent_visibility'] === 'inside' ? '{d}%' : '{b}\n{d}%',
              fontSize: 11,
            },
            emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.3)' } },
          }],
        };
        return <Suspense fallback={<Spinner size="md" />}><EChartsRuntime option={option} style={{ width: '100%', height: '100%', minHeight: 260 }} /></Suspense>;
      }

      case 'scalar':
      case 'number':
      case 'progress':
      case 'gauge': {
        const valueIdx = yAxisIndices[0] ?? 0;
        const lastRow = data.rows[data.rows.length - 1];
        const rawValue = lastRow?.[valueIdx];
        const numValue = Number(rawValue) || 0;
        const prefix = settings['scalar.prefix'] || '';
        const suffix = settings['scalar.suffix'] || (display === 'progress' || display === 'gauge' ? '%' : '');
        const compact = settings['scalar.compact_primary_number'] === true;
        const displayValue = compact && numValue >= 1000
          ? `${(numValue / 1000).toFixed(1)}k`
          : numValue.toLocaleString('zh-CN');

        return (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', width: '100%', height: '100%', minHeight: 120 }}>
            <div style={{ fontSize: 48, fontWeight: 800, color: 'var(--color-text-primary, #15243a)', lineHeight: 1.2 }}>
              {prefix}{displayValue}{suffix}
            </div>
            {data.cols[valueIdx] && (
              <div style={{ fontSize: 14, color: 'var(--color-text-secondary, #6c7a90)', marginTop: 8 }}>
                {data.cols[valueIdx].display_name || data.cols[valueIdx].name}
              </div>
            )}
          </div>
        );
      }

      case 'table':
        return <DataTable cols={data.cols as any[]} rows={data.rows} />;

      case 'combo':
      case 'waterfall':
      case 'funnel':
      case 'scatter':
      case 'map':
      default:
        return <DataTable cols={data.cols as any[]} rows={data.rows} />;
    }
  })();

  return (
    <div className={`chart-container ${className || ''}`} style={style}>
      {content}
    </div>
  );
}
