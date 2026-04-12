import React, { useMemo } from 'react';

interface FlylineChartProps {
  points?: Array<{ from: [number, number]; to: [number, number] }>;
  color?: string[];
  backgroundColor?: string;
  duration?: number;
  width: number;
  height: number;
}

function computeControlPoint(
  from: [number, number],
  to: [number, number],
): [number, number] {
  const midX = (from[0] + to[0]) / 2;
  const midY = (from[1] + to[1]) / 2;
  const dx = to[0] - from[0];
  const dy = to[1] - from[1];
  const dist = Math.sqrt(dx * dx + dy * dy);
  const offset = dist * 0.25;
  return [midX - (dy / dist) * offset, midY + (dx / dist) * offset];
}

export const FlylineChart: React.FC<FlylineChartProps> = ({
  points,
  color,
  backgroundColor,
  duration = 2,
  width,
  height,
}) => {
  const primary = color?.[0] ?? '#409eff';
  const lines = points ?? [];

  const paths = useMemo(
    () =>
      lines.map((line) => {
        const cp = computeControlPoint(line.from, line.to);
        const d = `M${line.from[0]},${line.from[1]} Q${cp[0]},${cp[1]} ${line.to[0]},${line.to[1]}`;
        // Approximate arc length for dasharray
        const dx = line.to[0] - line.from[0];
        const dy = line.to[1] - line.from[1];
        const approxLen = Math.sqrt(dx * dx + dy * dy) * 1.2;
        return { d, approxLen, from: line.from, to: line.to };
      }),
    [lines],
  );

  const allPoints = useMemo(() => {
    const set = new Map<string, [number, number]>();
    for (const line of lines) {
      const fk = `${line.from[0]},${line.from[1]}`;
      const tk = `${line.to[0]},${line.to[1]}`;
      set.set(fk, line.from);
      set.set(tk, line.to);
    }
    return Array.from(set.values());
  }, [lines]);

  if (lines.length === 0) {
    return (
      <svg width={width} height={height} style={backgroundColor ? { background: backgroundColor } : undefined}>
        <text
          x={width / 2}
          y={height / 2}
          textAnchor="middle"
          fill="rgba(255,255,255,0.3)"
          fontSize={12}
        >
          No data
        </text>
      </svg>
    );
  }

  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} style={backgroundColor ? { background: backgroundColor } : undefined}>
      <style>{`
        @keyframes flyline-dash {
          from { stroke-dashoffset: var(--fl-len); }
          to { stroke-dashoffset: 0; }
        }
      `}</style>
      {paths.map((p, i) => {
        const dotLen = Math.min(20, p.approxLen * 0.15);
        const gapLen = p.approxLen - dotLen;
        return (
          <g key={i}>
            <path
              d={p.d}
              fill="none"
              stroke={primary}
              strokeWidth={1}
              strokeOpacity={0.15}
            />
            <path
              d={p.d}
              fill="none"
              stroke={primary}
              strokeWidth={2}
              strokeDasharray={`${dotLen} ${gapLen}`}
              strokeLinecap="round"
              style={{
                '--fl-len': `${dotLen + gapLen}`,
                animation: `flyline-dash ${duration}s linear infinite`,
                animationDelay: `${i * 0.3}s`,
              } as React.CSSProperties}
            />
          </g>
        );
      })}
      {allPoints.map((pt, i) => (
        <circle
          key={i}
          cx={pt[0]}
          cy={pt[1]}
          r={3}
          fill={primary}
          stroke="#fff"
          strokeWidth={1}
        />
      ))}
    </svg>
  );
};
