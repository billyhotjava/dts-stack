import type { ModelGovernancePolicyImpact, ModelGovernanceQualityGate } from "@/types/infra";

export const REASON_MAX_LENGTH = 200;

export const QUALITY_GATE_LABELS: Record<ModelGovernanceQualityGate, string> = {
	ADVISORY: "提示",
	BLOCKING: "阻断",
};

export const QUALITY_GATE_DESCRIPTIONS: Record<ModelGovernanceQualityGate, string> = {
	ADVISORY: "缺少质量规则、质量不合格或结果过期时，仅在发布流程中提示，不阻止发布。",
	BLOCKING: "发布前业务质量检查必须通过；缺少规则、不合格或结果过期的模型不能发布。",
};

/** Returns the reason the form cannot be submitted, or null when it can. */
export function saveBlocker(
	current: ModelGovernanceQualityGate | undefined,
	selected: ModelGovernanceQualityGate,
	reason: string,
): string | null {
	if (!current) return "策略尚未读取";
	if (current === selected) return "策略未变化";
	const trimmed = reason.trim();
	if (!trimmed) return "请填写修改原因";
	if (trimmed.length > REASON_MAX_LENGTH) return `修改原因不能超过 ${REASON_MAX_LENGTH} 个字`;
	return null;
}

/** Only tightening to BLOCKING needs the impact preview and an explicit confirmation. */
export function requiresConfirmation(selected: ModelGovernanceQualityGate): boolean {
	return selected === "BLOCKING";
}

export function describeBlockingImpact(impact: ModelGovernancePolicyImpact): string {
	const recheck =
		impact.unfrozenCandidates > 0
			? `当前有 ${impact.unfrozenCandidates} 个尚未通过发布前检查的发布单，将按"阻断"重新判定，缺少、不合格或过期的质量结果会阻止发布。`
			: "当前没有尚未通过发布前检查的发布单。";
	const frozen =
		impact.frozenCandidates > 0 ? `已通过发布前检查的 ${impact.frozenCandidates} 个发布单按原策略继续，不受影响。` : "";
	return `切换后，新发起的发布前检查要求业务质量通过。${recheck}${frozen}`;
}
