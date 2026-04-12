import React, { useMemo } from 'react';
import './Decoration.css';

interface DecorationProps {
  decorationType?: number;
  color?: string[];
  backgroundColor?: string;
  duration?: number;
  style?: React.CSSProperties;
}

const VALID_TYPES = [1, 2, 3, 4, 5, 6, 8, 10] as const;

export const Decoration: React.FC<DecorationProps> = ({
  decorationType = 1,
  color,
  backgroundColor,
  duration = 3,
  style,
}) => {
  const type = VALID_TYPES.includes(decorationType as (typeof VALID_TYPES)[number])
    ? decorationType
    : 1;

  const primary = color?.[0] ?? 'var(--color-primary, #409eff)';
  const secondary = color?.[1] ?? primary;

  const cssVars = useMemo(
    () =>
      ({
        '--dec-primary': primary,
        '--dec-secondary': secondary,
        '--dec-duration': `${duration}s`,
      }) as React.CSSProperties,
    [primary, secondary, duration],
  );

  const wavePath = useMemo(() => {
    if (type !== 6) return '';
    const w = 200;
    const h = 20;
    const mid = h / 2;
    return `M0,${mid} Q${w / 4},0 ${w / 2},${mid} Q${w * 3 / 4},${h} ${w},${mid} Q${w * 5 / 4},0 ${w * 3 / 2},${mid} Q${w * 7 / 4},${h} ${w * 2},${mid}`;
  }, [type]);

  const particles = useMemo(() => {
    if (type !== 10) return [];
    const seed = 42;
    const count = 10;
    return Array.from({ length: count }, (_, i) => ({
      cx: ((seed * (i + 1) * 37) % 90) + 5,
      cy: ((seed * (i + 1) * 53) % 80) + 10,
      r: ((i % 3) + 1) * 1.5,
      delay: (i * duration) / count,
    }));
  }, [type, duration]);

  const renderContent = () => {
    switch (type) {
      case 1:
        return <div className="datav-decoration__line" />;

      case 2:
        return <div className="datav-decoration__dot" />;

      case 3: {
        const size = 30;
        const r = size / 2 - 2;
        return (
          <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
            <circle cx={size / 2} cy={size / 2} r={r} />
          </svg>
        );
      }

      case 4:
        return (
          <>
            <div className="datav-decoration__corner datav-decoration__corner--tl" />
            <div className="datav-decoration__corner datav-decoration__corner--tr" />
            <div className="datav-decoration__corner datav-decoration__corner--bl" />
            <div className="datav-decoration__corner datav-decoration__corner--br" />
          </>
        );

      case 5:
        return <div className="datav-decoration__stripes" />;

      case 6:
        return (
          <svg viewBox="0 0 400 20" preserveAspectRatio="none">
            <path d={wavePath} />
          </svg>
        );

      case 8: {
        const size = 40;
        const cx = size / 2;
        const cy = size / 2;
        const r1 = size / 2 - 2;
        const r2 = size / 2 - 7;
        return (
          <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
            <g className="datav-decoration__arc-cw">
              <circle
                cx={cx} cy={cy} r={r1}
                stroke={primary}
                strokeDasharray="90 270"
              />
            </g>
            <g className="datav-decoration__arc-ccw">
              <circle
                cx={cx} cy={cy} r={r2}
                stroke={secondary}
                strokeDasharray="90 270"
              />
            </g>
          </svg>
        );
      }

      case 10:
        return (
          <svg width="100%" height="100%" viewBox="0 0 100 100" preserveAspectRatio="none">
            {particles.map((p, i) => (
              <circle
                key={i}
                cx={p.cx}
                cy={p.cy}
                r={p.r}
                fill={primary}
                style={{ animationDelay: `${p.delay}s` }}
              />
            ))}
          </svg>
        );

      default:
        return null;
    }
  };

  return (
    <div
      className={`datav-decoration datav-decoration--${type}`}
      style={{ ...cssVars, ...style, ...(backgroundColor ? { background: backgroundColor } : {}) }}
    >
      {renderContent()}
    </div>
  );
};
