import React, { useMemo } from 'react';
import { formatChartValue } from './chartColors';

interface ScalarChartProps {
	data: {
		rows: any[][];
		cols: { name: string; display_name?: string; base_type?: string }[];
	};
	valueIndex?: number;
	compareIndex?: number;
	prefix?: string;
	suffix?: string;
	showTitle?: boolean;
	compact?: boolean;
}

const trendColorClass = {
	up: 'text-success',
	down: 'text-error',
	neutral: 'text-text-secondary',
} as const;

export function ScalarChart({
	data,
	valueIndex = 0,
	compareIndex,
	prefix = '',
	suffix = '',
	showTitle = true,
	compact = false
}: ScalarChartProps) {
	const { value, title, formattedValue, trend } = useMemo(() => {
		if (!data?.rows?.length || !data?.cols?.length) {
			return { value: null, title: '', formattedValue: '-', trend: null };
		}

		const rawValue = data.rows[0]?.[valueIndex];
		const col = data.cols[valueIndex];
		const title = col?.display_name || col?.name || '';

		if (rawValue === null || rawValue === undefined) {
			return { value: null, title, formattedValue: '-', trend: null };
		}

		const numValue = Number(rawValue);
		if (isNaN(numValue)) {
			return { value: rawValue, title, formattedValue: String(rawValue), trend: null };
		}

		// Calculate trend if we have multiple rows or a compare index
		let trend: { direction: 'up' | 'down' | 'neutral'; percentage: number; compareValue: number } | null = null;

		if (compareIndex !== undefined && data.rows[0]?.[compareIndex] !== undefined) {
			const compareValue = Number(data.rows[0][compareIndex]);
			if (!isNaN(compareValue) && compareValue !== 0) {
				const pct = ((numValue - compareValue) / Math.abs(compareValue)) * 100;
				trend = {
					direction: pct > 0.5 ? 'up' : pct < -0.5 ? 'down' : 'neutral',
					percentage: Math.abs(pct),
					compareValue,
				};
			}
		} else if (data.rows.length >= 2) {
			// Compare with the second row (previous period)
			const prevValue = Number(data.rows[1]?.[valueIndex]);
			if (!isNaN(prevValue) && prevValue !== 0) {
				const pct = ((numValue - prevValue) / Math.abs(prevValue)) * 100;
				trend = {
					direction: pct > 0.5 ? 'up' : pct < -0.5 ? 'down' : 'neutral',
					percentage: Math.abs(pct),
					compareValue: prevValue,
				};
			}
		}

		return {
			value: numValue,
			title,
			formattedValue: formatScalarNumber(numValue, compact),
			trend,
		};
	}, [data, valueIndex, compareIndex, compact]);

	return (
		<div className="flex flex-col items-center justify-center w-full h-full min-h-[140px] p-6">
			{showTitle && title && (
				<div className="text-sm text-text-secondary mb-2 text-center">
					{title}
				</div>
			)}

			<div className="flex items-baseline gap-1">
				{prefix && (
					<span className="text-xl text-text-muted font-normal">
						{prefix}
					</span>
				)}
				<span className="text-[48px] font-bold text-text-primary leading-none">
					{formattedValue}
				</span>
				{suffix && (
					<span className="text-xl text-text-muted font-normal">
						{suffix}
					</span>
				)}
			</div>

			{/* Trend Indicator */}
			{trend && (
				<div className={`inline-flex items-center gap-0.5 text-sm font-medium mt-2 ${trendColorClass[trend.direction]}`}>
					<span className="flex items-center justify-center">
						{trend.direction === 'up' && (
							<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
								<polyline points="23 6 13.5 15.5 8.5 10.5 1 18" />
								<polyline points="17 6 23 6 23 12" />
							</svg>
						)}
						{trend.direction === 'down' && (
							<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
								<polyline points="23 18 13.5 8.5 8.5 13.5 1 6" />
								<polyline points="17 18 23 18 23 12" />
							</svg>
						)}
						{trend.direction === 'neutral' && (
							<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
								<line x1="5" y1="12" x2="19" y2="12" />
								<polyline points="12 5 19 12 12 19" />
							</svg>
						)}
					</span>
					<span>{trend.percentage.toFixed(1)}%</span>
					<span className="text-xs text-text-muted ml-1">
						vs {formatChartValue(trend.compareValue, { compact: true })}
					</span>
				</div>
			)}
		</div>
	);
}

function formatScalarNumber(n: number, compact: boolean): string {
	if (compact) {
		if (Math.abs(n) >= 1000000000) return (n / 1000000000).toFixed(1) + 'B';
		if (Math.abs(n) >= 1000000) return (n / 1000000).toFixed(1) + 'M';
		if (Math.abs(n) >= 1000) return (n / 1000).toFixed(1) + 'K';
	}

	if (Number.isInteger(n)) {
		return n.toLocaleString();
	}

	if (Math.abs(n) >= 100) {
		return n.toLocaleString(undefined, { maximumFractionDigits: 0 });
	}
	if (Math.abs(n) >= 1) {
		return n.toLocaleString(undefined, { maximumFractionDigits: 2 });
	}
	return n.toLocaleString(undefined, { maximumFractionDigits: 4 });
}

export default ScalarChart;
