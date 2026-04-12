import React, { useMemo } from 'react';
import './BorderBox.css';

interface BorderBoxProps {
  boxType?: number;
  color?: string[];
  duration?: number;
  children?: React.ReactNode;
  width: number;
  height: number;
}

export const BorderBox: React.FC<BorderBoxProps> = ({
  boxType = 1,
  color,
  duration = 3,
  children,
  width,
  height,
}) => {
  const type = Math.max(1, Math.min(8, boxType));

  const primary = color?.[0] ?? 'var(--color-primary, #409eff)';
  const secondary = color?.[1] ?? (color?.[0] ? `${color[0]}80` : 'var(--color-border, rgba(255,255,255,0.1))');

  const cssVars = useMemo(() => {
    const vars: Record<string, string> = {
      '--bb-primary': primary,
      '--bb-secondary': secondary,
      '--bb-duration': `${duration}s`,
    };

    if (color?.[0]) {
      vars['--bb-glow-1'] = `${color[0]}40`;
      vars['--bb-glow-2'] = `${color[0]}20`;
      vars['--bb-glow-3'] = `${color[0]}10`;
      vars['--bb-glow-1-bright'] = `${color[0]}99`;
      vars['--bb-glow-2-bright'] = `${color[0]}59`;
      vars['--bb-glow-3-bright'] = `${color[0]}26`;
    }

    return vars as React.CSSProperties;
  }, [primary, secondary, duration, color]);

  const svgPolygonPoints = useMemo(() => {
    if (type !== 4) return '';
    const w = width;
    const h = height;
    const c = 10;
    return `${c},0 ${w - c},0 ${w},${c} ${w},${h - c} ${w - c},${h} ${c},${h} 0,${h - c} 0,${c}`;
  }, [type, width, height]);

  return (
    <div
      className={`datav-border-box datav-border-box--${type}`}
      style={{ width, height, ...cssVars }}
    >
      {type === 4 && (
        <svg
          className="datav-border-box__svg"
          viewBox={`0 0 ${width} ${height}`}
          preserveAspectRatio="none"
        >
          <polygon points={svgPolygonPoints} />
        </svg>
      )}
      <div className="datav-border-box__inner">
        {children}
      </div>
    </div>
  );
};
