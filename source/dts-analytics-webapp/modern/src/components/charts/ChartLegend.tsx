import { useState } from 'react';
import { getChartColor } from './chartColors';
import './ChartComponents.css';

export interface LegendItem {
  key: string;
  label: string;
  color?: string;
  value?: number | string;
}

export interface ChartLegendProps {
  items: LegendItem[];
  orientation?: 'horizontal' | 'vertical';
  position?: 'top' | 'bottom' | 'left' | 'right';
  interactive?: boolean;
  onItemClick?: (key: string) => void;
  onItemHover?: (key: string | null) => void;
  hiddenItems?: Set<string>;
  className?: string;
}

export function ChartLegend({
  items,
  orientation = 'horizontal',
  position = 'bottom',
  interactive = true,
  onItemClick,
  onItemHover,
  hiddenItems = new Set(),
  className = '',
}: ChartLegendProps) {
  const [hoveredItem, setHoveredItem] = useState<string | null>(null);

  const handleClick = (key: string) => {
    if (interactive && onItemClick) {
      onItemClick(key);
    }
  };

  const handleMouseEnter = (key: string) => {
    setHoveredItem(key);
    if (interactive && onItemHover) {
      onItemHover(key);
    }
  };

  const handleMouseLeave = () => {
    setHoveredItem(null);
    if (interactive && onItemHover) {
      onItemHover(null);
    }
  };

  return (
    <div
      className={`chart-legend chart-legend--${orientation} chart-legend--${position} ${className}`}
    >
      {items.map((item, index) => {
        const color = item.color || getChartColor(index);
        const isHidden = hiddenItems.has(item.key);
        const isHovered = hoveredItem === item.key;

        return (
          <button
            key={item.key}
            type="button"
            className={`chart-legend__item ${isHidden ? 'chart-legend__item--hidden' : ''} ${isHovered ? 'chart-legend__item--hovered' : ''} ${interactive ? 'chart-legend__item--interactive' : ''}`}
            onClick={() => handleClick(item.key)}
            onMouseEnter={() => handleMouseEnter(item.key)}
            onMouseLeave={handleMouseLeave}
            disabled={!interactive}
          >
            <span
              className="chart-legend__color"
              style={{ backgroundColor: isHidden ? 'var(--color-text-tertiary)' : color }}
            />
            <span className="chart-legend__label">{item.label}</span>
            {item.value !== undefined && (
              <span className="chart-legend__value">{item.value}</span>
            )}
          </button>
        );
      })}
    </div>
  );
}

// Compact Legend (for small spaces)
export interface CompactLegendProps {
  items: LegendItem[];
  maxVisible?: number;
  className?: string;
}

export function CompactLegend({
  items,
  maxVisible = 4,
  className = '',
}: CompactLegendProps) {
  const visibleItems = items.slice(0, maxVisible);
  const remainingCount = items.length - maxVisible;

  return (
    <div className={`chart-legend chart-legend--compact ${className}`}>
      {visibleItems.map((item, index) => {
        const color = item.color || getChartColor(index);
        return (
          <div key={item.key} className="chart-legend__item chart-legend__item--compact">
            <span
              className="chart-legend__color chart-legend__color--small"
              style={{ backgroundColor: color }}
            />
            <span className="chart-legend__label">{item.label}</span>
          </div>
        );
      })}
      {remainingCount > 0 && (
        <span className="chart-legend__more">+{remainingCount} more</span>
      )}
    </div>
  );
}
