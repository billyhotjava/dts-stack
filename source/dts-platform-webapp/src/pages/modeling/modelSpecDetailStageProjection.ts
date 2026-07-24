import type { ModelSpecDetailStage } from "./modelSpecDetailNavigation";

export const MODEL_SPEC_DETAIL_PRIMARY_ACTIONS = [
	"保存逻辑设计",
	"配置数据实现",
	"验证实现",
	"生成并发布",
] as const;

export type ModelSpecDetailPrimaryActionLabel = (typeof MODEL_SPEC_DETAIL_PRIMARY_ACTIONS)[number];
export type ModelSpecDetailLifecycleStatus =
	| "DRAFT"
	| "DESIGNING"
	| "VALIDATING"
	| "READY_TO_PUBLISH"
	| "PUBLISHED"
	| "ARCHIVED";

export type ModelSpecDetailBlocker = {
	message: string;
	recoveryStage?: ModelSpecDetailStage;
};

export type ModelSpecDetailStageProjectionInput = {
	stage: ModelSpecDetailStage;
	logicalDirty?: boolean;
	implementationConfigured?: boolean;
	implementationDirty?: boolean;
	implementationValidated?: boolean;
	canEdit: boolean;
	blocker?: ModelSpecDetailBlocker | null;
	lifecycleStatus?: ModelSpecDetailLifecycleStatus;
};

export type ModelSpecDetailPrimaryAction = {
	label: ModelSpecDetailPrimaryActionLabel;
	disabled: boolean;
	recoveryStage: ModelSpecDetailStage;
	recoveryMessage?: string;
};

export type ModelSpecDetailStageProjection = {
	stage: ModelSpecDetailStage;
	primaryAction: ModelSpecDetailPrimaryAction;
};

const primaryActionForStage = (stage: ModelSpecDetailStage): ModelSpecDetailPrimaryActionLabel => {
	if (stage === "logical") return "保存逻辑设计";
	if (stage === "implementation") return "配置数据实现";
	return "生成并发布";
};

const nextAction = (input: ModelSpecDetailStageProjectionInput): Pick<ModelSpecDetailPrimaryAction, "label" | "recoveryStage"> => {
	if (input.logicalDirty) return { label: "保存逻辑设计", recoveryStage: "logical" };
	if (input.stage === "logical") return { label: "配置数据实现", recoveryStage: "implementation" };
	if (!input.implementationConfigured || input.implementationDirty) {
		return { label: "配置数据实现", recoveryStage: "implementation" };
	}
	if (!input.implementationValidated) return { label: "验证实现", recoveryStage: "implementation" };
	return { label: "生成并发布", recoveryStage: "physical" };
};

const lifecycleRecoveryMessage = (status: ModelSpecDetailLifecycleStatus): string =>
	status === "PUBLISHED" ? "模型已发布，当前版本不能再修改或重复发布" : "模型已归档，请恢复为可维护版本后再操作";

export const getModelSpecDetailStageProjection = (
	input: ModelSpecDetailStageProjectionInput,
): ModelSpecDetailStageProjection => {
	const blockedAction = input.blocker
		? {
				label: primaryActionForStage(input.blocker.recoveryStage || input.stage),
				recoveryStage: input.blocker.recoveryStage || input.stage,
			}
		: nextAction(input);
	const readonlyLifecycle = input.lifecycleStatus === "PUBLISHED" || input.lifecycleStatus === "ARCHIVED";
	const recoveryMessage = input.blocker?.message
		? input.blocker.message
		: !input.canEdit
			? "当前账号没有维护该模型的权限"
			: readonlyLifecycle
				? lifecycleRecoveryMessage(input.lifecycleStatus as "PUBLISHED" | "ARCHIVED")
				: undefined;

	return {
		stage: input.stage,
		primaryAction: {
			...blockedAction,
			disabled: Boolean(recoveryMessage),
			...(recoveryMessage ? { recoveryMessage } : {}),
		},
	};
};
