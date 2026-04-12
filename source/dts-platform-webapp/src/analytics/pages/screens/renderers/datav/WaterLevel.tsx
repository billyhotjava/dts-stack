import React, { useMemo } from 'react';

interface WaterLevelProps {
  value?: number;
  shape?: string;
  color?: string[];
  width: number;
  height: number;
}

const WAVE_STYLE: React.CSSProperties = {
  animation: 'waterlevel-wave var(--wl-duration, 3s) linear infinite',
};

export const WaterLevel: React.FC<WaterLevelProps> = ({
  value = 0,
  shape = 'round',
  color,
  width,
  height,
}) => {
  const clamped = Math.max(0, Math.min(100, value));
  const primary = color?.[0] ?? '#409eff';
  const secondary = color?.[1] ?? primary;

  const borderRadius = shape === 'round' ? '50%' : '8px';

  const wavePath = useMemo(() => {
    const w = width * 2;
    const amplitude = 6;
    const waterY = height * (1 - clamped / 100);
    return [
      `M0,${waterY}`,
      `Q${w / 8},${waterY - amplitude} ${w / 4},${waterY}`,
      `Q${w * 3 / 8},${waterY + amplitude} ${w / 2},${waterY}`,
      `Q${w * 5 / 8},${waterY - amplitude} ${w * 3 / 4},${waterY}`,
      `Q${w * 7 / 8},${waterY + amplitude} ${w},${waterY}`,
      `L${w},${height}`,
      `L0,${height}`,
      'Z',
    ].join(' ');
  }, [width, height, clamped]);

  const keyframesId = 'waterlevel-wave';

  return (
    <div
      style={{
        position: 'relative',
        width,
        height,
        borderRadius,
        overflow: 'hidden',
        border: `1px solid ${primary}40`,
        background: 'rgba(0,0,0,0.1)',
      }}
    >
      <style>{`
        @keyframes ${keyframesId} {
          from { transform: translateX(0); }
          to { transform: translateX(-50%); }
        }
      `}</style>
      <svg
        width={width * 2}
        height={height}
        viewBox={`0 0 ${width * 2} ${height}`}
        style={WAVE_STYLE}
      >
        <defs>
          <linearGradient id="wl-grad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={primary} stopOpacity={0.8} />
            <stop offset="100%" stopColor={secondary} stopOpacity={0.5} />
          </linearGradient>
        </defs>
        <path d={wavePath} fill="url(#wl-grad)" />
      </svg>
      <div
        style={{
          position: 'absolute',
          inset: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#fff',
          fontSize: Math.min(width, height) * 0.2,
          fontWeight: 700,
          textShadow: '0 1px 4px rgba(0,0,0,0.4)',
          zIndex: 1,
          pointerEvents: 'none',
        }}
      >
        {clamped}%
      </div>
    </div>
  );
};
