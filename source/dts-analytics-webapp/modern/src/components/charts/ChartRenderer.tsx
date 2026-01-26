import React from 'react';
import { LineChart } from './LineChart';
import { BarChart } from './BarChart';
import { PieChart } from './PieChart';
import { AreaChart } from './AreaChart';
import { ScalarChart } from './ScalarChart';
import { DataTable } from '../DataTable';

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
  };
  display: VisualizationType;
  settings?: VisualizationSettings;
  className?: string;
  style?: React.CSSProperties;
}

export function ChartRenderer({
  data,
  display,
  settings = {},
  className,
  style
}: ChartRendererProps) {
  if (!data || !data.rows || !data.cols) {
    return (
      <div style={{ ...styles.empty, ...style }} className={className}>
        No data available
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
        // For now, render as scalar with a visual indicator
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
          <div style={styles.unsupported}>
            <div style={styles.unsupportedIcon}>📊</div>
            <div style={styles.unsupportedText}>
              {display} visualization is not yet supported
            </div>
            <DataTable cols={data.cols as any[]} rows={data.rows} />
          </div>
        );
    }
  })();

  return (
    <div style={{ ...styles.container, ...style }} className={className}>
      {content}
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    width: '100%',
    height: '100%',
    minHeight: 200
  },
  empty: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    height: 200,
    color: '#888',
    fontSize: 14
  },
  unsupported: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    padding: 16
  },
  unsupportedIcon: {
    fontSize: 32,
    marginBottom: 8
  },
  unsupportedText: {
    color: '#888',
    fontSize: 12,
    marginBottom: 16
  }
};

export default ChartRenderer;
