import React, { useState } from 'react';
import { LineChart } from './LineChart';
import { BarChart } from './BarChart';
import { PieChart } from './PieChart';
import { AreaChart } from './AreaChart';
import { ScalarChart } from './ScalarChart';
import { DataTable } from '../DataTable';
import { Spinner } from '../../ui/Loading/Spinner';
import './ChartComponents.css';

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
  | 'map'
  | 'pivot';

export interface VisualizationSettings {
  // Common settings
  'graph.x_axis.axis_enabled'?: boolean;
  'graph.y_axis.axis_enabled'?: boolean;
  'graph.x_axis.title_text'?: string;
  'graph.y_axis.title_text'?: string;
  'graph.show_values'?: boolean;
  'graph.label_value_frequency'?: string;

  // Line/Area specific
  'graph.show_dots'?: boolean;
  'graph.show_area'?: boolean;
  'graph.smooth'?: boolean;
  'stackable.stack_type'?: 'stacked' | 'normalized' | null;

  // Pie specific
  'pie.show_legend'?: boolean;
  'pie.show_total'?: boolean;
  'pie.percent_visibility'?: 'off' | 'legend' | 'inside';
  'pie.slice_threshold'?: number;

  // Scalar specific
  'scalar.prefix'?: string;
  'scalar.suffix'?: string;
  'scalar.compact_primary_number'?: boolean;

  // Series colors
  'graph.colors'?: string[];
  'series_settings'?: Record<string, { color?: string; display?: string }>;

  // Column mappings
  'graph.dimensions'?: string[];
  'graph.metrics'?: string[];

  // Table specific
  'table.pivot'?: boolean;
  'table.pivot_column'?: string;
  'table.cell_column'?: string;

  // Any other settings
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

export function ChartRenderer({
  data,
  display,
  settings = {},
  loading = false,
  error,
  className,
  style
}: ChartRendererProps) {
  // Loading state
  if (loading) {
    return (
      <div className={`chart-container ${className || ''}`} style={style}>
        <div className="chart-container__loading">
          <Spinner size="lg" />
        </div>
      </div>
    );
  }

  // Error state
  if (error) {
    const message = error instanceof Error ? error.message : String(error);
    return (
      <div className={`chart-container ${className || ''}`} style={style}>
        <div className="chart-container__error">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
          <div style={{ fontSize: 'var(--font-size-sm)' }}>{message}</div>
        </div>
      </div>
    );
  }

  // Empty state
  if (!data || !data.rows || !data.cols || data.rows.length === 0) {
    return (
      <div className={`chart-container ${className || ''}`} style={style}>
        <div className="chart-container__empty">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M3 3v18h18" />
            <path d="M18 17V9" />
            <path d="M13 17V5" />
            <path d="M8 17v-3" />
          </svg>
          <div style={{ fontSize: 'var(--font-size-sm)' }}>No data available</div>
        </div>
      </div>
    );
  }

  const colors = settings['graph.colors'];

  // Find dimension and metric column indices
  const dimensionNames = settings['graph.dimensions'] || [];
  const metricNames = settings['graph.metrics'] || [];

  const xAxisIndex = dimensionNames.length > 0
    ? data.cols.findIndex(c => c.name === dimensionNames[0] || c.display_name === dimensionNames[0])
    : 0;

  const yAxisIndices = metricNames.length > 0
    ? metricNames.map(name =>
        data.cols.findIndex(c => c.name === name || c.display_name === name)
      ).filter(i => i >= 0)
    : data.cols
        .map((c, i) => i)
        .filter(i => i !== (xAxisIndex >= 0 ? xAxisIndex : 0))
        .slice(0, 5);

  const content = (() => {
    switch (display) {
      case 'line':
        return (
          <LineChart
            data={data}
            xAxisIndex={xAxisIndex >= 0 ? xAxisIndex : 0}
            yAxisIndices={yAxisIndices.length > 0 ? yAxisIndices : [1]}
            showDots={settings['graph.show_dots'] !== false}
            showArea={settings['graph.show_area'] === true}
            smooth={settings['graph.smooth'] !== false}
            colors={colors}
          />
        );

      case 'bar':
        return (
          <BarChart
            data={data}
            xAxisIndex={xAxisIndex >= 0 ? xAxisIndex : 0}
            yAxisIndex={yAxisIndices[0] ?? 1}
            orientation="vertical"
            showValues={settings['graph.show_values'] === true}
            stacked={settings['stackable.stack_type'] === 'stacked'}
            colors={colors}
          />
        );

      case 'row':
        return (
          <BarChart
            data={data}
            xAxisIndex={xAxisIndex >= 0 ? xAxisIndex : 0}
            yAxisIndex={yAxisIndices[0] ?? 1}
            orientation="horizontal"
            showValues={settings['graph.show_values'] === true}
            colors={colors}
          />
        );

      case 'pie':
        return (
          <PieChart
            data={data}
            labelIndex={xAxisIndex >= 0 ? xAxisIndex : 0}
            valueIndex={yAxisIndices[0] ?? 1}
            showLabels={settings['pie.show_legend'] !== false}
            showPercentages={settings['pie.percent_visibility'] !== 'off'}
            donut={settings['pie.show_total'] === true}
            colors={colors}
          />
        );

      case 'area':
        return (
          <AreaChart
            data={data}
            xAxisIndex={xAxisIndex >= 0 ? xAxisIndex : 0}
            yAxisIndices={yAxisIndices.length > 0 ? yAxisIndices : [1]}
            stacked={settings['stackable.stack_type'] === 'stacked'}
            smooth={settings['graph.smooth'] !== false}
            colors={colors}
          />
        );

      case 'scalar':
      case 'number':
        return (
          <ScalarChart
            data={data}
            valueIndex={yAxisIndices[0] ?? 0}
            prefix={settings['scalar.prefix']}
            suffix={settings['scalar.suffix']}
            compact={settings['scalar.compact_primary_number'] === true}
          />
        );

      case 'progress':
      case 'gauge':
        return (
          <ScalarChart
            data={data}
            valueIndex={yAxisIndices[0] ?? 0}
            prefix={settings['scalar.prefix']}
            suffix={settings['scalar.suffix'] || '%'}
            compact={settings['scalar.compact_primary_number'] === true}
          />
        );

      case 'table':
      case 'pivot':
      default:
        return (
          <DataTable
            cols={data.cols as any[]}
            rows={data.rows}
          />
        );

      // Unsupported visualization types - fall back to table
      case 'combo':
      case 'waterfall':
      case 'funnel':
      case 'scatter':
      case 'map':
        return (
          <div className="chart-container__empty">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M3 3v18h18" />
              <path d="M18 17V9" />
              <path d="M13 17V5" />
              <path d="M8 17v-3" />
            </svg>
            <div style={{ fontSize: 'var(--font-size-sm)', marginBottom: 'var(--spacing-md)' }}>
              {display} visualization is not yet supported
            </div>
            <DataTable cols={data.cols as any[]} rows={data.rows} />
          </div>
        );
    }
  })();

  return (
    <div className={`chart-container ${className || ''}`} style={style}>
      {content}
    </div>
  );
}

export default ChartRenderer;
