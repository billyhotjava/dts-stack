import type { ModelAuthoringContext, ModelAuthoringValidation } from "@/api/modelAuthoringApi";

export type ModelWorkflowAction = "save" | "validate" | "commit" | "deliver" | "fork";
export type ModelWorkflowInput = {
	persisted: boolean;
	published: boolean;
	dirty: boolean;
	readOnly: boolean;
	canMaintain: boolean;
	context: ModelAuthoringContext | null;
	validation: ModelAuthoringValidation | null;
};

/** Capabilities come from authoring-context; a validation receipt must match the open draft. */
export function hasCurrentAuthoringValidation(
	context: ModelAuthoringContext | null,
	validation: ModelAuthoringValidation | null,
	now = Date.now(),
): boolean {
	const open = context?.openDraft;
	const checked = validation?.implementationValidation;
	return Boolean(
		open &&
			checked &&
			open.state === "VALIDATED" &&
			checked.draftId === open.draftId &&
			checked.etag === open.etag &&
			checked.validatedChecksum &&
			Date.parse(checked.expiresAt) > now &&
			!validation?.modelIssues.length &&
			![...(validation?.projectionIssues || []), ...checked.diagnostics].some((issue) =>
				["ERROR", "FATAL"].includes(issue.severity.toUpperCase()),
			),
	);
}

export function resolveModelWorkflowAction(
	input: ModelWorkflowInput,
	now = Date.now(),
): {
	action: ModelWorkflowAction;
	enabled: boolean;
	reason: string;
} {
	const { persisted, published, dirty, readOnly, canMaintain, context, validation } = input;
	const allowed = context?.allowedActions || [];
	const pending = context?.openDraft && context.openDraft.state !== "COMMITTED";
	const action: ModelWorkflowAction = published
		? "fork"
		: !persisted || dirty
			? "save"
			: allowed.includes("COMMIT") && hasCurrentAuthoringValidation(context, validation, now)
				? "commit"
				: context?.implementation && !pending
					? "deliver"
					: "validate";
	if (!canMaintain) return { action, enabled: false, reason: "当前无模型维护权限" };
	if (readOnly && action !== "fork") return { action, enabled: false, reason: "当前版本只读" };
	if (!persisted) return { action, enabled: action === "save", reason: "" };
	if (!context) return { action, enabled: false, reason: "操作权限尚未读取，请刷新状态" };
	const permission = { save: "SAVE", validate: "VALIDATE", commit: "COMMIT", fork: "FORK_DRAFT" } as const;
	// Delivery is navigation only. Build/quality/publication retain their existing server-side gates.
	const enabled = action === "deliver" || allowed.includes(permission[action]);
	return { action, enabled, reason: enabled ? "" : "当前状态不允许此操作，请刷新状态" };
}
