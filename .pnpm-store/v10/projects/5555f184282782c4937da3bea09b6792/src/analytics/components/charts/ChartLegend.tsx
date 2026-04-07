import { useState } from 'react';
import { getChartColor } from './chartColors';

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

const orientationClass = {
	horizontal: 'flex-row justify-center',
	vertical: 'flex-col items-start',
} as const;

const positionClass = {
	top: 'mb-4',
	bottom: 'mt-4',
	left: 'mr-4',
	right: 'ml-4',
} as const;

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
			className={`flex flex-wrap gap-2 ${orientationClass[orientation]} ${positionClass[position]} ${className}`}
		>
			{items.map((item, index) => {
				const color = item.color || getChartColor(index);
				const isHidden = hiddenItems.has(item.key);
				const isHovered = hoveredItem === item.key;

				return (
					<button
						key={item.key}
						type="button"
						className={`inline-flex items-center gap-1 px-2 py-1 border-none rounded-sm bg-transparent font-[inherit] text-sm text-text-primary cursor-default transition-[background-color,opacity] duration-150 ${isHidden ? 'opacity-50' : ''} ${isHovered ? 'bg-surface-muted' : ''} ${interactive ? 'cursor-pointer hover:bg-surface-muted' : ''}`}
						onClick={() => handleClick(item.key)}
						onMouseEnter={() => handleMouseEnter(item.key)}
						onMouseLeave={handleMouseLeave}
						disabled={!interactive}
					>
						<span
							className="w-3 h-3 rounded-xs shrink-0"
							style={{ backgroundColor: isHidden ? 'var(--color-text-tertiary)' : color }}
						/>
						<span className="overflow-hidden text-ellipsis whitespace-nowrap">{item.label}</span>
						{item.value !== undefined && (
							<span className="text-text-secondary font-medium ml-1">{item.value}</span>
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
		<div className={`flex flex-wrap gap-1 ${className}`}>
			{visibleItems.map((item, index) => {
				const color = item.color || getChartColor(index);
				return (
					<div key={item.key} className="inline-flex items-center gap-1 px-1 py-0.5">
						<span
							className="w-2 h-2 rounded-xs shrink-0"
							style={{ backgroundColor: color }}
						/>
						<span className="overflow-hidden text-ellipsis whitespace-nowrap">{item.label}</span>
					</div>
				);
			})}
			{remainingCount > 0 && (
				<span className="text-xs text-text-muted px-1 py-0.5">+{remainingCount} more</span>
			)}
		</div>
	);
}
