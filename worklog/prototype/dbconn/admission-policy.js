/**
 * Revision 级准入决定与风险确认绑定。
 * AdmissionDecision 是规则证据；组织审批仅作为可选外部决定引用。
 */

(function () {

const META = Object.freeze({
	READY: ["规则通过", "ok"],
	CONFIRMATION_REQUIRED: ["需人工确认", "warn"],
	BLOCKED: ["准入阻断", "bad"],
	PENDING_EXTERNAL_APPROVAL: ["等待外部流程", "warn"],
	ADMITTED: ["已准入", "ok"],
});
const CLASSIFICATIONS = new Set(["公开", "内部", "秘密", "机密"]);

function resolve(summary) {
	if (!summary || !Array.isArray(summary.resourceKeys) || !summary.resourceKeys.length
		|| !CLASSIFICATIONS.has(summary.effectiveClassification)
		|| !Array.isArray(summary.classificationEvidence) || !summary.classificationEvidence.length) {
		return { outcome: "BLOCKED", reasons: ["资源、密级或准入证据不完整"] };
	}
	const raw = summary && summary.admissionDecision;
	if (!raw || !Object.prototype.hasOwnProperty.call(META, raw.outcome)) {
		return { outcome: "BLOCKED", reasons: ["缺少或无法识别准入判定结果"] };
	}
	const reasons = Array.isArray(raw.reasons) ? [...raw.reasons] : [];
	if (raw.outcome === "CONFIRMATION_REQUIRED" && !reasons.length) {
		return { outcome: "BLOCKED", reasons: ["需要人工确认但缺少具体风险原因"] };
	}
	return { ...raw, reasons };
}

function presentation(summary) {
	const decision = resolve(summary);
	const [label, tone] = META[decision.outcome];
	return { decision, label, tone };
}

function fingerprint(revisionId, summary) {
	const decision = resolve(summary);
	return JSON.stringify({
		revisionId,
		resourceKeys: [...(summary.resourceKeys || [])],
		outcome: decision.outcome,
		reasons: decision.reasons,
	});
}

function recordConfirmation(revisionId, summary, reason) {
	const decision = resolve(summary);
	return {
		revisionId,
		resourceKeys: [...(summary.resourceKeys || [])],
		outcome: decision.outcome,
		reasons: [...decision.reasons],
		decisionFingerprint: fingerprint(revisionId, summary),
		reason: String(reason || ""),
	};
}

function confirmationFor(revisionId, summary, record) {
	if (resolve(summary).outcome !== "CONFIRMATION_REQUIRED") return null;
	if (!record || record.decisionFingerprint !== fingerprint(revisionId, summary)) return null;
	return record;
}

function matchingConfirmation(revisionId, summary, record) {
	const current = confirmationFor(revisionId, summary, record);
	return current && current.reason.trim().length >= 8 ? current : null;
}

function publishGate(revisionId, summary, record) {
	const state = presentation(summary);
	if (state.decision.outcome === "READY") {
		return { ok: true, detail: `${summary.effectiveClassification} · ${state.label}`, reason: "" };
	}
	if (state.decision.outcome === "CONFIRMATION_REQUIRED") {
		const confirmed = matchingConfirmation(revisionId, summary, record);
		return confirmed
			? { ok: true, detail: `${summary.effectiveClassification} · 风险确认已绑定当前决定`, reason: "" }
			: { ok: false, detail: `${state.label} · 尚不可发布`, reason: "请填写至少 8 个字符的风险确认理由" };
	}
	const reasons = state.decision.reasons.join("；");
	if (state.decision.outcome === "PENDING_EXTERNAL_APPROVAL") {
		return { ok: false, detail: `${state.label} · 尚不可发布`, reason: "等待已绑定的外部审批流程返回决定" };
	}
	if (state.decision.outcome === "ADMITTED") {
		return { ok: false, detail: "该 Revision 已准入并生效", reason: "已生效 Revision 不可重复发布" };
	}
	return { ok: false, detail: `${state.label} · 尚不可发布`, reason: reasons || "准入规则阻断" };
}

window.AdmissionPolicy = Object.freeze({
	resolve,
	presentation,
	fingerprint,
	recordConfirmation,
	confirmationFor,
	matchingConfirmation,
	publishGate,
});

})();
