import { Card } from "antd";

interface TrendPoint {
	date: string;
	value: number;
}

export interface IndicatorCardData {
	id: string;
	code: string;
	name: string;
	unit?: string;
	currentValue?: number | null;
	changeRate?: number | null;
	alertLevel?: string;
	thresholdMin?: number;
	thresholdMax?: number;
	trend?: TrendPoint[];
}

interface IndicatorCardProps {
	indicator: IndicatorCardData;
	onClick?: () => void;
}

const ALERT_COLORS: Record<string, string> = {
	RED: "#f5222d",
	YELLOW: "#faad14",
	GREEN: "#52c41a",
};

function MiniTrendBars({ trend }: { trend?: TrendPoint[] }) {
	if (!trend || trend.length === 0) {
		return <div style={{ color: "#bbb", fontSize: 12, marginTop: 8 }}>No trend data</div>;
	}

	const values = trend.map((t) => t.value ?? 0);
	const maxVal = Math.max(...values, 1);
	const barCount = Math.min(trend.length, 30);
	const displayTrend = trend.slice(-barCount);

	return (
		<div style={{ display: "flex", alignItems: "flex-end", gap: 1, height: 32, marginTop: 8 }}>
			{displayTrend.map((point, i) => {
				const h = maxVal > 0 ? Math.max(2, ((point.value ?? 0) / maxVal) * 30) : 2;
				return (
					<div
						key={i}
						style={{
							flex: 1,
							height: h,
							backgroundColor: "#1677ff",
							borderRadius: 1,
							minWidth: 2,
							maxWidth: 8,
							opacity: 0.7 + 0.3 * (i / barCount),
						}}
						title={`${point.date}: ${point.value}`}
					/>
				);
			})}
		</div>
	);
}

export default function IndicatorCard({ indicator, onClick }: IndicatorCardProps) {
	const alertColor = ALERT_COLORS[indicator.alertLevel ?? ""] ?? "#d9d9d9";
	const changeRate = indicator.changeRate;
	const hasChange = changeRate != null && !Number.isNaN(changeRate);

	let changeArrow = "";
	let changeColor = "#999";
	if (hasChange) {
		if (changeRate > 0) {
			changeArrow = "\u2191";
			changeColor = "#f5222d";
		} else if (changeRate < 0) {
			changeArrow = "\u2193";
			changeColor = "#52c41a";
		} else {
			changeArrow = "\u2192";
			changeColor = "#999";
		}
	}

	const thresholdText: string[] = [];
	if (indicator.thresholdMin != null) thresholdText.push(`Min: ${indicator.thresholdMin}`);
	if (indicator.thresholdMax != null) thresholdText.push(`Max: ${indicator.thresholdMax}`);

	return (
		<Card
			hoverable
			size="small"
			onClick={onClick}
			style={{ cursor: onClick ? "pointer" : "default", height: "100%" }}
			styles={{ body: { padding: "12px 16px" } }}
		>
			<div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8 }}>
				<div
					style={{
						width: 8,
						height: 8,
						borderRadius: "50%",
						backgroundColor: alertColor,
						flexShrink: 0,
					}}
				/>
				<div
					style={{
						flex: 1,
						fontWeight: 500,
						fontSize: 14,
						overflow: "hidden",
						textOverflow: "ellipsis",
						whiteSpace: "nowrap",
					}}
					title={indicator.name}
				>
					{indicator.name}
				</div>
			</div>

			<div style={{ display: "flex", alignItems: "baseline", gap: 6 }}>
				<span style={{ fontSize: 28, fontWeight: 700, lineHeight: 1.1 }}>
					{indicator.currentValue != null ? indicator.currentValue : "--"}
				</span>
				{indicator.unit && (
					<span style={{ fontSize: 12, color: "#888" }}>{indicator.unit}</span>
				)}
				{hasChange && (
					<span style={{ fontSize: 13, color: changeColor, marginLeft: 4 }}>
						{changeArrow} {Math.abs(changeRate * 100).toFixed(1)}%
					</span>
				)}
			</div>

			<MiniTrendBars trend={indicator.trend} />

			{thresholdText.length > 0 && (
				<div style={{ fontSize: 11, color: "#999", marginTop: 6 }}>
					Threshold: {thresholdText.join(" / ")}
				</div>
			)}
		</Card>
	);
}
