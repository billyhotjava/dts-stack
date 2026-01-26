import React, { useMemo } from 'react';

interface ScalarChartProps {
  data: {
    rows: any[][];
    cols: { name: string; display_name?: string; base_type?: string }[];
  };
  valueIndex?: number;
  prefix?: string;
  suffix?: string;
  showTitle?: boolean;
  compact?: boolean;
}

export function ScalarChart({
  data,
  valueIndex = 0,
  prefix = '',
  suffix = '',
  showTitle = true,
  compact = false
}: ScalarChartProps) {
  const { value, title, formattedValue } = useMemo(() => {
    if (!data?.rows?.length || !data?.cols?.length) {
      return { value: null, title: '', formattedValue: '-' };
    }

    const rawValue = data.rows[0]?.[valueIndex];
    const col = data.cols[valueIndex];
    const title = col?.display_name || col?.name || '';

    if (rawValue === null || rawValue === undefined) {
      return { value: null, title, formattedValue: '-' };
    }

    const numValue = Number(rawValue);
    if (isNaN(numValue)) {
      return { value: rawValue, title, formattedValue: String(rawValue) };
    }

    return {
      value: numValue,
      title,
      formattedValue: formatNumber(numValue, compact)
    };
  }, [data, valueIndex, compact]);

  return (
    <div style={styles.container}>
      {showTitle && title && (
        <div style={styles.title}>{title}</div>
      )}
      <div style={styles.value}>
        {prefix && <span style={styles.prefix}>{prefix}</span>}
        <span style={styles.number}>{formattedValue}</span>
        {suffix && <span style={styles.suffix}>{suffix}</span>}
      </div>
    </div>
  );
}

function formatNumber(n: number, compact: boolean): string {
  if (compact) {
    if (Math.abs(n) >= 1000000000) return (n / 1000000000).toFixed(1) + 'B';
    if (Math.abs(n) >= 1000000) return (n / 1000000).toFixed(1) + 'M';
    if (Math.abs(n) >= 1000) return (n / 1000).toFixed(1) + 'K';
  }

  if (Number.isInteger(n)) {
    return n.toLocaleString();
  }

  // Format with appropriate decimal places
  if (Math.abs(n) >= 100) {
    return n.toLocaleString(undefined, { maximumFractionDigits: 0 });
  }
  if (Math.abs(n) >= 1) {
    return n.toLocaleString(undefined, { maximumFractionDigits: 2 });
  }
  return n.toLocaleString(undefined, { maximumFractionDigits: 4 });
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    width: '100%',
    height: '100%',
    minHeight: 120,
    padding: 24
  },
  title: {
    fontSize: 14,
    color: '#666',
    marginBottom: 8,
    textAlign: 'center'
  },
  value: {
    display: 'flex',
    alignItems: 'baseline',
    gap: 4
  },
  prefix: {
    fontSize: 24,
    color: '#888',
    fontWeight: 400
  },
  number: {
    fontSize: 48,
    fontWeight: 700,
    color: '#333',
    lineHeight: 1
  },
  suffix: {
    fontSize: 24,
    color: '#888',
    fontWeight: 400
  }
};

export default ScalarChart;
