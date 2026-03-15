import { Badge } from "../../../ui/Badge/Badge";
import { Card, CardBody } from "../../../ui/Card/Card";

type Props = {
	label: string;
	value: string;
	unit?: string;
	hint?: string;
	tone?: "default" | "success" | "warning" | "error" | "info";
};

export function HealthScoreCard({ label, value, unit, hint, tone = "default" }: Props) {
	return (
		<Card className="project-cockpit__metric-card" shadow="sm">
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
				</div>
				{hint ? <div className="project-cockpit__metric-hint">{hint}</div> : null}
			</CardBody>
		</Card>
	);
}
