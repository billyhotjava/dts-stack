interface SparklineProps {
	data: number[];
	width?: number;
	height?: number;
	color?: string;
}

/** 迷你走势线（纯 SVG，无图表库依赖，Chrome 95 安全）。 */
export function Sparkline({ data, width = 120, height = 32, color = "var(--accent)" }: SparklineProps) {
	if (data.length < 2) return null;
	const min = Math.min(...data);
	const max = Math.max(...data);
	const span = max - min || 1;
	const stepX = width / (data.length - 1);
	const points = data.map((v, i) => `${(i * stepX).toFixed(1)},${(height - ((v - min) / span) * (height - 4) - 2).toFixed(1)}`);

	return (
		<svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" aria-hidden>
			<polyline points={points.join(" ")} fill="none" stroke={color} strokeWidth={1.5} strokeLinejoin="round" strokeLinecap="round" />
		</svg>
	);
}
