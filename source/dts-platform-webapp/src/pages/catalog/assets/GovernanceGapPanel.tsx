import { CheckCircleOutlined } from "@ant-design/icons";

// 治理缺口面板：取代此前五张各说一事的 KPI 卡。
// 关键取舍：0 值原因一律中性灰，不用绿色/对勾——在「全量待处置」的语境下，
// 给「未定密 0」配成功色会被读成「已合规」，是误导性正反馈。

export type GovernanceGapReason = {
	key: string;
	label: string;
	count: number;
};

export interface GovernanceGapPanelProps {
	total: number;
	attention: number;
	reasons: GovernanceGapReason[];
	onReasonClick: (key: string) => void;
	loading?: boolean;
	/** 统计被扫描上限截断时，数字加 ≥ 前缀 */
	truncated?: boolean;
}

const REASON_SEGMENT_COLOR: Record<string, string> = {
	PENDING_DOMAIN: "bg-amber-400",
	PENDING_CLAIM: "bg-orange-400",
	PENDING_CLASSIFICATION: "bg-rose-400",
	STALE: "bg-red-400",
};

export function GovernanceGapPanel({
	total,
	attention,
	reasons,
	onReasonClick,
	loading,
	truncated = false,
}: GovernanceGapPanelProps) {
	const prefix = truncated ? "≥" : "";
	// total 为 0 时直接给 0，避免 0/0 产生 NaN
	const percent = total > 0 ? Math.round((attention / total) * 100) : 0;
	const healthy = attention === 0;

	if (loading) {
		return (
			<div className="rounded-xl border border-slate-200 bg-white p-4" aria-busy="true">
				<div className="h-4 w-40 animate-pulse rounded bg-slate-200" />
				<div className="mt-3 h-3 animate-pulse rounded bg-slate-100" />
				<div className="mt-3 h-3 w-2/3 animate-pulse rounded bg-slate-100" />
			</div>
		);
	}

	return (
		<div className="rounded-xl border border-slate-200 bg-white p-4">
			<div className="flex flex-wrap items-baseline justify-between gap-2">
				<div className="text-sm font-semibold text-slate-900">治理缺口</div>
				<div data-testid="gap-summary" className="text-xs text-slate-500">
					待处置 <span className="tabular-nums font-semibold text-slate-800">{`${prefix}${attention}`}</span>
					<span className="mx-1">/</span>
					总量 <span className="tabular-nums">{`${prefix}${total}`}</span>
					<span data-testid="gap-percent" className={`ml-2 tabular-nums ${healthy ? "text-green-600" : "text-amber-600"}`}>
						{percent}%
					</span>
				</div>
			</div>

			<div
				data-testid="gap-bar"
				data-state={healthy ? "healthy" : "attention"}
				className="mt-3 flex h-2 w-full overflow-hidden rounded-full bg-slate-100"
				role="img"
				aria-label={`待处置占比 ${percent}%`}
			>
				{healthy ? (
					<div className="h-full w-full bg-green-400" />
				) : (
					reasons
						.filter((reason) => reason.count > 0)
						.map((reason) => (
							<div
								key={reason.key}
								className={`h-full ${REASON_SEGMENT_COLOR[reason.key] ?? "bg-amber-300"}`}
								style={{ width: total > 0 ? `${(reason.count / total) * 100}%` : "0%" }}
							/>
						))
				)}
			</div>

			{healthy ? (
				<div className="mt-3 flex items-center gap-1 text-xs text-green-600">
					<CheckCircleOutlined />
					当前范围无待处置资产
				</div>
			) : (
				<div className="mt-3 flex flex-wrap items-center gap-2">
					{reasons.map((reason) => {
						const active = reason.count > 0;
						return (
							<button
								key={reason.key}
								type="button"
								data-testid={`gap-reason-${reason.key}`}
								data-tone={active ? "warning" : "neutral"}
								disabled={!active}
								onClick={active ? () => onReasonClick(reason.key) : undefined}
								className={[
									"rounded-md px-2 py-1 text-xs transition",
									active
										? "bg-amber-50 text-amber-700 hover:bg-amber-100"
										: "cursor-default bg-slate-50 text-slate-400",
								].join(" ")}
							>
								{reason.label} <span className="tabular-nums font-semibold">{`${active ? prefix : ""}${reason.count}`}</span>
							</button>
						);
					})}
				</div>
			)}
		</div>
	);
}
