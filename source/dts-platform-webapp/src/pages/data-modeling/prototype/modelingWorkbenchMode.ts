export type ModelingWorkbenchView = "visual" | "code";
export type ModelingModeAccess = "EDIT" | "PREVIEW" | "BLOCKED";

const CAPABILITY_REASON_TEXT: Record<string, string> = {
	MODEL_AUTHORING_PROJECTION_NOT_PREPARED: "实现投影尚未生成，可继续编辑业务定义或进入代码视图。",
	MODEL_AUTHORING_PROJECTION_PENDING: "实现投影正在等待首次保存。",
	MODEL_AUTHORING_SOURCE_BUNDLE_UNAVAILABLE: "当前尚无可读取的实现文件。",
	MODEL_REPRESENTATION_NO_IMPLEMENTATION: "尚未生成可读取的模型实现。",
	MODEL_REPRESENTATION_ARTIFACT_PIN_MISMATCH: "模型与实现版本不一致，请刷新后重试。",
	MODEL_REPRESENTATION_FIELDS_UNTRUSTED: "字段结构缺少可信证据，暂不能生成可视化投影。",
	MODEL_REPRESENTATION_DYNAMIC_DEPENDENCY: "代码包含动态依赖，暂不能安全生成可视化投影。",
	MODEL_REPRESENTATION_DEPENDENCIES_UNTRUSTED: "上游依赖缺少可信证据，暂不能生成可视化投影。",
	MODEL_REPRESENTATION_RUNTIME_EVIDENCE_STALE: "运行证据已过期，请重新编译后重试。",
	MODEL_REPRESENTATION_ADVANCED_REQUIRES_DBT_MANAGED: "当前实现尚未具备可读取的代码投影。",
	MODEL_REPRESENTATION_SCHEMA_INVALID: "模型结构证据无效，暂不能生成可视化投影。",
};

export const modelingCapabilityReasonText = (reason: string) =>
	CAPABILITY_REASON_TEXT[reason] || (reason && !reason.startsWith("MODEL_REPRESENTATION_") ? reason : "当前模型表示暂不可用。");

export const modelingCapabilityReasonsText = (reasons: readonly string[]) => reasons.map(modelingCapabilityReasonText).join("；");

export function resolveModelingModeAccess({
	view,
	canMaintain,
	allowedActions,
	capabilityReasons,
}: {
	view: ModelingWorkbenchView;
	canMaintain: boolean;
	allowedActions: readonly string[];
	capabilityReasons: readonly string[];
}): { access: ModelingModeAccess; reason: string } {
	const canEdit = view === "visual" ? allowedActions.includes("EDIT_MODEL") : allowedActions.includes("EDIT_IMPLEMENTATION");
	const canPreview = view === "visual" ? allowedActions.includes("OPEN_VISUAL") : allowedActions.includes("OPEN_CODE");
	if (canEdit && canMaintain) return { access: "EDIT", reason: "" };
	if (canPreview || canEdit) return { access: "PREVIEW", reason: canMaintain ? "当前模式仅可查看。" : "当前账号仅可查看。" };
	return { access: "BLOCKED", reason: modelingCapabilityReasonsText(capabilityReasons) || "当前模型不支持该表现模式。" };
}

/** New drafts have no server representation yet, so capability must not lock their visual form. */
export const isPersistedVisualReadOnly = (persisted: boolean, access: ModelingModeAccess) => persisted && access !== "EDIT";

export function normalizeWorkbenchView(params: URLSearchParams): {
	view: ModelingWorkbenchView;
	legacyAdvanced: boolean;
} {
	const legacyAdvanced = params.get("open") === "advanced";
	return { view: params.get("view") === "code" || legacyAdvanced ? "code" : "visual", legacyAdvanced };
}

export function updateWorkbenchView(params: URLSearchParams, view: ModelingWorkbenchView): URLSearchParams {
	const next = new URLSearchParams(params);
	next.set("view", view);
	next.delete("open");
	return next;
}
