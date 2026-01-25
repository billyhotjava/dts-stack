import React, { useMemo } from 'react';

interface BarChartProps {
  data: {
    rows: any[][];
    cols: { name: string; display_name?: string; base_type?: string }[];
  };
  xAxisIndex?: number;
  yAxisIndex?: number;
  orientation?: 'vertical' | 'horizontal';
  showValues?: boolean;
  colors?: string[];
}

const DEFAULT_COLORS = [
  '#509EE3', '#88BF4D', '#F9D45C', '#F2A86F', '#EF8C8C',
  '#A989C5', '#98D9D9', '#7172AD', '#6450a2', '#4C5773'
];

export function BarChart({
  data,
  xAxisIndex = 0,
  yAxisIndex = 1,
  orientation = 'vertical',
  showValues = false,
  colors = DEFAULT_COLORS
}: BarChartProps) {
  const { bars, maxValue, labels } = useMemo(() => {
    if (!data?.rows?.length || !data?.cols?.length) {
      return { bars: [], maxValue: 0, labels: [] };
    }

    const values = data.rows.map(row => {
      const value = Number(row[yAxisIndex]) || 0;
      const label = String(row[xAxisIndex] ?? '');
      return { value, label };
    });

    const max = Math.max(...values.map(v => v.value), 1);

    return {
      bars: values,
      maxValue: max,
      labels: values.map(v => v.label)
    };
  }, [data, xAxisIndex, yAxisIndex]);

  if (bars.length === 0) {
    return <div style={styles.empty}>No data to display</div>;
  }

  const xLabel = data.cols[xAxisIndex]?.display_name || data.cols[xAxisIndex]?.name || 'X';
  const yLabel = data.cols[yAxisIndex]?.display_name || data.cols[yAxisIndex]?.name || 'Y';

  if (orientation === 'horizontal') {
    return (
      <div style={styles.container}>
        <div style={styles.chartArea}>
          <div style={styles.yAxisLabel}>{yLabel}</div>
          <div style={styles.horizontalBars}>
            {bars.map((bar, i) => (
              <div key={i} style={styles.horizontalBarRow}>
                <div style={styles.horizontalBarLabel} title={bar.label}>
                  {bar.label.length > 15 ? bar.label.slice(0, 15) + '...' : bar.label}
                </div>
                <div style={styles.horizontalBarContainer}>
                  <div
                    style={{
                      ...styles.horizontalBar,
                      width: `${(bar.value / maxValue) * 100}%`,
                      backgroundColor: colors[i % colors.length]
                    }}
                  >
                    {showValues && (
                      <span style={styles.barValue}>{formatNumber(bar.value)}</span>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
        <div style={styles.xAxisLabel}>{xLabel}</div>
      </div>
    );
  }

  // Vertical bars
  const barWidth = Math.max(20, Math.min(60, 400 / bars.length));

  return (
    <div style={styles.container}>
      <div style={styles.yAxisLabel}>{yLabel}</div>
      <div style={styles.chartArea}>
        <div style={styles.yAxis}>
          {[1, 0.75, 0.5, 0.25, 0].map(ratio => (
            <div key={ratio} style={styles.yAxisTick}>
              {formatNumber(maxValue * ratio)}
            </div>
          ))}
        </div>
        <div style={styles.barsContainer}>
          <div style={styles.gridLines}>
            {[0, 1, 2, 3, 4].map(i => (
              <div key={i} style={styles.gridLine} />
            ))}
          </div>
          <div style={styles.bars}>
            {bars.map((bar, i) => (
              <div key={i} style={{ ...styles.barColumn, width: barWidth }}>
                <div style={styles.barWrapper}>
                  <div
                    style={{
                      ...styles.bar,
                      height: `${(bar.value / maxValue) * 100}%`,
                      backgroundColor: colors[i % colors.length]
                    }}
                  >
                    {showValues && (
                      <span style={styles.verticalBarValue}>{formatNumber(bar.value)}</span>
                    )}
                  </div>
                </div>
                <div style={styles.barLabel} title={bar.label}>
                  {bar.label.length > 8 ? bar.label.slice(0, 8) + '...' : bar.label}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
      <div style={styles.xAxisLabel}>{xLabel}</div>
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
    display: 'flex',
    flexDirection: 'column',
    width: '100%',
    height: '100%',
    minHeight: 300,
    padding: 16,
    boxSizing: 'border-box'
  },
  chartArea: {
    display: 'flex',
    flex: 1,
    position: 'relative'
  },
  yAxisLabel: {
    writingMode: 'vertical-rl',
    textOrientation: 'mixed',
    transform: 'rotate(180deg)',
    padding: '8px 4px',
    fontSize: 12,
    color: '#666',
    fontWeight: 500
  },
  xAxisLabel: {
    textAlign: 'center',
    padding: '8px 0',
    fontSize: 12,
    color: '#666',
    fontWeight: 500
  },
  yAxis: {
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'space-between',
    paddingRight: 8,
    fontSize: 11,
    color: '#888',
    width: 50,
    textAlign: 'right'
  },
  yAxisTick: {
    height: 20
  },
  barsContainer: {
    flex: 1,
    position: 'relative',
    display: 'flex',
    flexDirection: 'column'
  },
  gridLines: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 24,
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'space-between'
  },
  gridLine: {
    height: 1,
    backgroundColor: '#eee'
  },
  bars: {
    flex: 1,
    display: 'flex',
    alignItems: 'flex-end',
    justifyContent: 'space-around',
    paddingBottom: 24,
    gap: 4
  },
  barColumn: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    height: '100%'
  },
  barWrapper: {
    flex: 1,
    display: 'flex',
    alignItems: 'flex-end',
    width: '100%'
  },
  bar: {
    width: '100%',
    borderRadius: '4px 4px 0 0',
    transition: 'height 0.3s ease',
    position: 'relative',
    minHeight: 4
  },
  barLabel: {
    fontSize: 10,
    color: '#666',
    textAlign: 'center',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    width: '100%',
    paddingTop: 4
  },
  verticalBarValue: {
    position: 'absolute',
    top: -20,
    left: '50%',
    transform: 'translateX(-50%)',
    fontSize: 10,
    color: '#333',
    fontWeight: 500
  },
  horizontalBars: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    gap: 8,
    paddingLeft: 8
  },
  horizontalBarRow: {
    display: 'flex',
    alignItems: 'center',
    gap: 8
  },
  horizontalBarLabel: {
    width: 100,
    fontSize: 11,
    color: '#666',
    textAlign: 'right',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap'
  },
  horizontalBarContainer: {
    flex: 1,
    height: 24,
    backgroundColor: '#f5f5f5',
    borderRadius: 4
  },
  horizontalBar: {
    height: '100%',
    borderRadius: 4,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'flex-end',
    paddingRight: 8,
    transition: 'width 0.3s ease',
    minWidth: 4
  },
  barValue: {
    fontSize: 10,
    color: '#fff',
    fontWeight: 500
  },
  empty: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    height: 200,
    color: '#888'
  }
};

export default BarChart;
