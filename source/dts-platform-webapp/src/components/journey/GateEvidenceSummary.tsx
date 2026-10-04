import { Card, Space, Tag } from "antd";
import { useRouter } from "@/routes/hooks";
import { cn } from "@/utils";
import type { DataProductJourneyStageKey } from "./journeyContext";
import { buildGateEvidence, type GateCheckStatus, type GateEvidence, type GateVerdict } from "./gateEvidence";
import { useDataProductJourneyContext } from "./useDataProductJourneyContext";

const CHECK_STATUS_COLOR: Record<GateCheckStatus, string> = {
	ready: "green",
	missing: "orange",
	blocked: "red",
};

const CHECK_STATUS_LABEL: Record<GateCheckStatus, string> = {
	ready: "可查",
	missing: "缺失",
	blocked: "阻断",
};

const VERDICT_COLOR: Record<GateVerdict, string> = {
	pass: "green",
	warn: "orange",
	fail: "red",
};

const VERDICT_LABEL: Record<GateVerdict, string> = {
	pass: "门禁通过",
	warn: "存在缺失",
	fail: "门禁阻断",
};

type GateEvidenceSummaryProps = {
	evidence: GateEvidence;
	className?: string;
	compact?: boolean;
};

export function GateEvidenceSummary({ evidence, className, compact = false }: GateEvidenceSummaryProps) {
	const router = useRouter();
	const body = (
		<Space wrap size={[8, 8]} data-testid="gate-evidence-summary">
			<Tag color={VERDICT_COLOR[evidence.verdict]} data-testid="gate-evidence-verdict">
				{VERDICT_LABEL[evidence.verdict]}
			</Tag>
			{evidence.checks.map((check) => (
				<Tag
					key={check.key}
					color={CHECK_STATUS_COLOR[check.status]}
					data-testid={`gate-check-${check.key}`}
					style={{ cursor: check.evidenceUrl ? "pointer" : undefined }}
					title={check.apiName ? `${check.detail}（待补 ${check.apiName}）` : check.detail}
					onClick={() => {
						if (check.evidenceUrl) router.push(check.evidenceUrl);
					}}
				>
					{check.label}·{CHECK_STATUS_LABEL[check.status]}
				</Tag>
			))}
		</Space>
	);
	if (compact) return <div className={className}>{body}</div>;
	return (
		<Card size="small" className={cn("border-dashed", className)} title="发布门禁">
			{body}
		</Card>
	);
}

type JourneyGateEvidenceSummaryProps = {
	stage: DataProductJourneyStageKey;
	className?: string;
};

// 页面级便捷封装：仅在旅程模式下渲染，基于当前旅程上下文推导门禁证据。
export function JourneyGateEvidenceSummary({ stage, className }: JourneyGateEvidenceSummaryProps) {
	const context = useDataProductJourneyContext(stage);
	if (!context.enabled) return null;
	return <GateEvidenceSummary evidence={buildGateEvidence(context.params)} className={className} />;
}
