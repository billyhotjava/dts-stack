import "./DelayReasonMatrix.css";
import { buildDelayReasonMatrixSummary } from "../views/riskAttributionView.helpers";

type Props = {
	rows: Array<Record<string, unknown>>;
	onDrillReason?: (dept: string, reason: string) => void;
	onDrillDept?: (dept: string) => void;
};

const REASON_LABELS = {
	technical: "技术攻关",
	quality: "质量整改",
	change: "计划变更",
	coordination: "接口协同",
	supplier: "外协外购",
	test: "试验排期",
	archive: "资料归档",
	normal: "正常推进",
} as const;

export function DelayReasonMatrix({ rows, onDrillReason, onDrillDept }: Props) {
	const summary = buildDelayReasonMatrixSummary(rows);
	const reasonKeys = Object.keys(REASON_LABELS) as Array<keyof typeof REASON_LABELS>;

	return (
		<div className="project-cockpit__matrix">
			<div className="project-cockpit__matrix-header">
				<span>责任科室</span>
				<span>延期总数</span>
				<span>主因</span>
				<span>归因结构</span>
			</div>
			{summary.rows.map((row) => {
				const dept = String(row.dept ?? "-");
				const dominantKey = (row.dominantReason ?? "normal") as keyof typeof REASON_LABELS;
				return (
					<div key={dept} className="project-cockpit__matrix-row">
						<strong
							className={onDrillDept ? "project-cockpit__matrix-link" : undefined}
							onClick={onDrillDept ? () => onDrillDept(dept) : undefined}
						>
							{dept}
						</strong>
						<span>{Number(row.total ?? 0)}</span>
						<span>{REASON_LABELS[dominantKey] ?? "-"}</span>
						<div className="project-cockpit__matrix-bars">
							{reasonKeys.map((key) => {
								const label = REASON_LABELS[key];
								const value = Number(row[key] ?? 0);
								return value > 0 ? (
									<span
										key={key}
										className={`project-cockpit__matrix-pill${onDrillReason ? " project-cockpit__matrix-pill--clickable" : ""}`}
										title={`${label}: ${value}`}
										onClick={onDrillReason ? () => onDrillReason(dept, key) : undefined}
									>
										{label} {value}
									</span>
								) : null;
							})}
						</div>
					</div>
				);
			})}
		</div>
	);
}
