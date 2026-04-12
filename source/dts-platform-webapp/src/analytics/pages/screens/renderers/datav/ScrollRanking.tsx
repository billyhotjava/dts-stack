import React, { useMemo } from 'react';

interface ScrollRankingProps {
  data?: Array<{ name: string; value: number }>;
  color?: string[];
  duration?: number;
  rowCount?: number;
  style?: React.CSSProperties;
}

export const ScrollRanking: React.FC<ScrollRankingProps> = ({
  data,
  color,
  duration = 10,
  rowCount = 5,
  style,
}) => {
  const primary = color?.[0] ?? 'var(--color-primary, #409eff)';
  const items = data ?? [];
  const maxVal = useMemo(
    () => Math.max(...items.map((d) => d.value), 1),
    [items],
  );

  const needsScroll = items.length > rowCount;
  const rowHeight = 32;
  const visibleHeight = rowCount * rowHeight;

  const renderRow = (item: { name: string; value: number }, index: number) => {
    const rank = index + 1;
    const highlighted = rank <= 3;
    const barWidth = (item.value / maxVal) * 100;

    return (
      <div
        key={`${index}-${item.name}`}
        style={{
          display: 'flex',
          alignItems: 'center',
          height: rowHeight,
          gap: 8,
          fontSize: 13,
          color: 'var(--color-text-primary, #e0e0e0)',
        }}
      >
        <span
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 20,
            height: 20,
            borderRadius: 4,
            fontSize: 12,
            fontWeight: 700,
            background: highlighted ? primary : 'rgba(255,255,255,0.08)',
            color: highlighted ? '#fff' : 'rgba(255,255,255,0.5)',
            flexShrink: 0,
          }}
        >
          {rank}
        </span>
        <span
          style={{
            flex: '0 0 80px',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {item.name}
        </span>
        <div
          style={{
            flex: 1,
            height: 6,
            borderRadius: 3,
            background: 'rgba(255,255,255,0.06)',
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              width: `${barWidth}%`,
              height: '100%',
              borderRadius: 3,
              background: primary,
              transition: 'width 0.6s ease',
            }}
          />
        </div>
        <span style={{ flex: '0 0 50px', textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
          {item.value}
        </span>
      </div>
    );
  };

  if (items.length === 0) {
    return (
      <div style={{ ...style, color: 'rgba(255,255,255,0.3)', textAlign: 'center', padding: 16 }}>
        No data
      </div>
    );
  }

  if (!needsScroll) {
    return (
      <div style={{ ...style, padding: '4px 0' }}>
        {items.map((item, i) => renderRow(item, i))}
      </div>
    );
  }

  const duplicated = [...items, ...items];
  const totalHeight = items.length * rowHeight;

  return (
    <div
      style={{
        ...style,
        height: visibleHeight,
        overflow: 'hidden',
        position: 'relative',
      }}
    >
      <style>{`
        @keyframes scroll-ranking {
          0% { transform: translateY(0); }
          100% { transform: translateY(-${totalHeight}px); }
        }
      `}</style>
      <div
        style={{
          animation: `scroll-ranking ${duration}s linear infinite`,
        }}
      >
        {duplicated.map((item, i) => renderRow(item, i % items.length))}
      </div>
    </div>
  );
};
