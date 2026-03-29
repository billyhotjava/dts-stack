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
		<div className="flex flex-col gap-2.5">
			{/* Header */}
			<div className="grid grid-cols-[120px_80px_110px_1fr] gap-3 items-center text-xs text-text-secondary uppercase tracking-wide max-[1200px]:grid-cols-1">
				<span>责任科室</span>
				<span>延期总数</span>
				<span>主因</span>
				<span>归因结构</span>
			</div>
			{/* Rows */}
			{summary.rows.map((row) => {
				const dept = String(row.dept ?? "-");
				const dominantKey = (row.dominantReason ?? "normal") as keyof typeof REASON_LABELS;
				return (
					<div key={dept} className="grid grid-cols-[120px_80px_110px_1fr] gap-3 items-center px-3.5 py-3 rounded-[14px] bg-surface-muted max-[1200px]:grid-cols-1">
						<strong
							className={onDrillDept ? "cursor-pointer text-brand hover:underline" : undefined}
							onClick={onDrillDept ? () => onDrillDept(dept) : undefined}
						>
							{dept}
						</strong>
						<span>{Number(row.total ?? 0)}</span>
						<span>{REASON_LABELS[dominantKey] ?? "-"}</span>
						<div className="flex gap-1.5 flex-wrap">
							{reasonKeys.map((key) => {
								const label = REASON_LABELS[key];
								const value = Number(row[key] ?? 0);
								return value > 0 ? (
									<span
										key={key}
										className={`inline-flex items-center px-2 py-1 rounded-full text-xs bg-blue-600/[0.08] text-blue-700 transition-colors duration-150${onDrillReason ? " cursor-pointer hover:bg-blue-600/[0.18]" : ""}`}
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
