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
			className={`fixed z-[1070] px-3 py-2 bg-[var(--color-bg-dark)] text-[var(--color-text-inverse)] rounded-md shadow-lg text-sm pointer-events-none min-w-[120px] max-w-[280px] animate-[chart-tooltip-fade-in_0.1s_ease-out] dark:bg-surface-card dark:text-text-primary dark:border dark:border-border-default ${className}`}
			style={{
				left: adjustedPosition?.x ?? position.x,
				top: adjustedPosition?.y ?? position.y,
			}}
		>
			{data.title && (
				<div className="font-semibold mb-1 pb-1 border-b border-white/20 dark:border-border-default">
					{data.title}
				</div>
			)}
			<div className="flex flex-col gap-0.5">
				{data.items.map((item, index) => (
					<div key={index} className="flex items-center gap-1">
						{item.color && (
							<span
								className="w-2.5 h-2.5 rounded-xs shrink-0"
								style={{ backgroundColor: item.color }}
							/>
						)}
						<span className="flex-1 overflow-hidden text-ellipsis whitespace-nowrap opacity-80">{item.label}</span>
						<span className="font-semibold text-right">
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
			{data.footer && (
				<div className="mt-1 pt-1 border-t border-white/20 dark:border-border-default text-xs opacity-70">
					{data.footer}
				</div>
			)}
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
		<g>
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
