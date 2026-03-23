import { Badge } from "../../../ui/Badge/Badge";
import { Card, CardBody } from "../../../ui/Card/Card";

type TrendInfo = {
	rate: number;
	direction: "up" | "down" | "flat";
};

type Props = {
	label: string;
	value: string;
	unit?: string;
	hint?: string;
	tone?: "default" | "success" | "warning" | "error" | "info";
	onDrill?: () => void;
	trend?: TrendInfo;
	/** For negative KPIs (e.g. risk count), "up" is bad (red) and "down" is good (green). Default: positive. */
	trendPolarity?: "positive" | "negative";
};

function TrendIndicator({ trend, polarity }: { trend: TrendInfo; polarity: "positive" | "negative" }) {
	if (trend.direction === "flat" || trend.rate === 0) return null;

	const isUp = trend.direction === "up";
	const isGood = polarity === "positive" ? isUp : !isUp;
	const color = isGood ? "#16a34a" : "#dc2626";
	const arrow = isUp ? "↑" : "↓";
	const pct = Math.abs(trend.rate * 100).toFixed(0);

	return (
		<span className="project-cockpit__metric-trend" style={{ color }}>
			{arrow}{pct}%
		</span>
	);
}

export function HealthScoreCard({
	label,
	value,
	unit,
	hint,
	tone = "default",
	onDrill,
	trend,
	trendPolarity = "positive",
}: Props) {
	return (
		<Card
			className={`project-cockpit__metric-card${onDrill ? " project-cockpit__metric-card--drillable" : ""}`}
			shadow="sm"
			onClick={onDrill}
			style={onDrill ? { cursor: "pointer" } : undefined}
			role={onDrill ? "button" : undefined}
			tabIndex={onDrill ? 0 : undefined}
		>
			<CardBody>
				<div className="project-cockpit__metric-label-row">
					<span className="project-cockpit__metric-label">{label}</span>
					<Badge size="sm" variant={tone}>
						{tone === "error" ? "重点" : tone === "warning" ? "跟踪" : "指标"}
					</Badge>
				</div>
				<div className="project-cockpit__metric-value">
					{value}
					{unit ? <span className="project-cockpit__metric-unit">{unit}</span> : null}
					{trend ? <TrendIndicator trend={trend} polarity={trendPolarity} /> : null}
				</div>
				{hint ? <div className="project-cockpit__metric-hint">{hint}</div> : null}
			</CardBody>
		</Card>
	);
}
