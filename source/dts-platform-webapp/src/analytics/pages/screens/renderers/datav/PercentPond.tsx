import React from 'react';

interface PercentPondProps {
  value?: number;
  colors?: string[];
  textColor?: string;
  backgroundColor?: string;
  borderRadius?: number;
  borderWidth?: number;
  width: number;
  height: number;
}

export const PercentPond: React.FC<PercentPondProps> = ({
  value = 0,
  colors,
  textColor,
  backgroundColor,
  borderRadius = 8,
  borderWidth = 2,
  width,
  height,
}) => {
  const clamped = Math.max(0, Math.min(100, value));
  const c0 = colors?.[0] ?? 'var(--color-primary, #409eff)';
  const c1 = colors?.[1] ?? c0;

  return (
    <div
      style={{
        position: 'relative',
        width,
        height,
        border: `${borderWidth}px solid ${c1}`,
        borderRadius,
        overflow: 'hidden',
        boxSizing: 'border-box',
        ...(backgroundColor ? { background: backgroundColor } : {}),
      }}
    >
      <style>{`
        @keyframes pp-shimmer {
          from { background-position: -200% 0; }
          to { background-position: 200% 0; }
        }
      `}</style>
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          width: `${clamped}%`,
          height: '100%',
          background: `linear-gradient(90deg, ${c0}, ${c1})`,
          borderRadius: Math.max(0, borderRadius - borderWidth),
          transition: 'width 0.6s ease',
        }}
      >
        <div
          style={{
            position: 'absolute',
            inset: 0,
            background: `linear-gradient(90deg, transparent, rgba(255,255,255,0.15), transparent)`,
            backgroundSize: '200% 100%',
            animation: 'pp-shimmer 2s linear infinite',
          }}
        />
      </div>
      <div
        style={{
          position: 'absolute',
          inset: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: textColor || '#fff',
          fontSize: Math.max(12, height * 0.45),
          fontWeight: 600,
          textShadow: '0 1px 2px rgba(0,0,0,0.3)',
          pointerEvents: 'none',
          zIndex: 1,
        }}
      >
        {clamped}%
      </div>
    </div>
  );
};
