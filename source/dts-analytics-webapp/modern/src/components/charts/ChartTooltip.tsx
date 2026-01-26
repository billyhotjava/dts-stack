import { ReactNode, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { formatChartValue } from './chartColors';
import './ChartComponents.css';

export interface TooltipData {
  title?: string;
  items: {
    label: string;
    value: number | string;
    color?: string;
    prefix?: string;
    suffix?: string;
  }[];
  footer?: string;
}

export interface ChartTooltipProps {
  data: TooltipData | null;
  position: { x: number; y: number } | null;
  visible?: boolean;
  className?: string;
}

export function ChartTooltip({
  data,
  position,
  visible = true,
  className = '',
}: ChartTooltipProps) {
  const tooltipRef = useRef<HTMLDivElement>(null);
  const [adjustedPosition, setAdjustedPosition] = useState<{ x: number; y: number } | null>(null);

  useEffect(() => {
    if (position && tooltipRef.current) {
      const rect = tooltipRef.current.getBoundingClientRect();
      const padding = 12;

      let x = position.x;
      let y = position.y;

      // Adjust horizontal position
      if (x + rect.width + padding > window.innerWidth) {
        x = position.x - rect.width - padding;
      } else {
        x = position.x + padding;
      }

      // Adjust vertical position
      if (y + rect.height + padding > window.innerHeight) {
        y = position.y - rect.height - padding;
      } else {
        y = position.y + padding;
      }

      setAdjustedPosition({ x, y });
    }
  }, [position, data]);

  if (!visible || !data || !position) {
    return null;
  }

  const tooltipContent = (
    <div
      ref={tooltipRef}
      className={`chart-tooltip ${className}`}
      style={{
        left: adjustedPosition?.x ?? position.x,
        top: adjustedPosition?.y ?? position.y,
      }}
    >
      {data.title && <div className="chart-tooltip__title">{data.title}</div>}
      <div className="chart-tooltip__items">
        {data.items.map((item, index) => (
          <div key={index} className="chart-tooltip__item">
            {item.color && (
              <span
                className="chart-tooltip__color"
                style={{ backgroundColor: item.color }}
              />
            )}
            <span className="chart-tooltip__label">{item.label}</span>
            <span className="chart-tooltip__value">
              {typeof item.value === 'number'
                ? formatChartValue(item.value, {
                    prefix: item.prefix,
                    suffix: item.suffix,
                  })
                : item.value}
            </span>
          </div>
        ))}
      </div>
      {data.footer && <div className="chart-tooltip__footer">{data.footer}</div>}
    </div>
  );

  return createPortal(tooltipContent, document.body);
}

// Hook for managing tooltip state
export function useChartTooltip() {
  const [tooltipData, setTooltipData] = useState<TooltipData | null>(null);
  const [tooltipPosition, setTooltipPosition] = useState<{ x: number; y: number } | null>(null);
  const [isVisible, setIsVisible] = useState(false);

  const showTooltip = (data: TooltipData, position: { x: number; y: number }) => {
    setTooltipData(data);
    setTooltipPosition(position);
    setIsVisible(true);
  };

  const hideTooltip = () => {
    setIsVisible(false);
  };

  const updatePosition = (position: { x: number; y: number }) => {
    setTooltipPosition(position);
  };

  return {
    tooltipData,
    tooltipPosition,
    isVisible,
    showTooltip,
    hideTooltip,
    updatePosition,
  };
}

// Crosshair component for line/area charts
export interface ChartCrosshairProps {
  x: number;
  y: number;
  chartWidth: number;
  chartHeight: number;
  showVertical?: boolean;
  showHorizontal?: boolean;
  color?: string;
}

export function ChartCrosshair({
  x,
  y,
  chartWidth,
  chartHeight,
  showVertical = true,
  showHorizontal = false,
  color = 'var(--color-border-strong)',
}: ChartCrosshairProps) {
  return (
    <g className="chart-crosshair">
      {showVertical && (
        <line
          x1={x}
          y1={0}
          x2={x}
          y2={chartHeight}
          stroke={color}
          strokeWidth={1}
          strokeDasharray="4 4"
        />
      )}
      {showHorizontal && (
        <line
          x1={0}
          y1={y}
          x2={chartWidth}
          y2={y}
          stroke={color}
          strokeWidth={1}
          strokeDasharray="4 4"
        />
      )}
    </g>
  );
}
