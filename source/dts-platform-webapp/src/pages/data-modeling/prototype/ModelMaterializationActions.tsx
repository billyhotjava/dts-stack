import type { DeliveryAction } from "@/api/modelDeliveryStatusApi";
import { Button } from "./PrototypePrimitives";

export type MaterializationBuildAction =
	| "CREATE_CANDIDATE"
	| "REFRESH_AND_CREATE"
	| "CREATE_AFTER_TERMINAL"
	| "CANCEL_AND_CREATE"
	| "REFRESH_AND_REPLACE"
	| "CREATE_REPLACEMENT"
	| "REMATERIALIZE"
	| "RETRY_BUILD"
	| "START_BUILD";

const WIZARD_LABELS: Record<string, string> = {
	CONFIGURE_QUALITY_RULES: "配置质量规则",
	RERUN_GOVERNANCE_QUALITY: "执行质量检查",
	RUN_QUALITY: "执行检查",
	NEXT: "下一步",
	RETRY_BUILD: "重试构建",
};
const BUILD_LABELS: Partial<Record<MaterializationBuildAction, string>> = {
	REFRESH_AND_REPLACE: "按新修订重新构建",
	CREATE_REPLACEMENT: "按新修订重新构建",
	REFRESH_AND_CREATE: "按新范围重新构建",
	CREATE_AFTER_TERMINAL: "按新范围重新构建",
	CANCEL_AND_CREATE: "替换发布单并构建",
	REMATERIALIZE: "重新构建",
	RETRY_BUILD: "重试构建",
	START_BUILD: "开始构建",
};

/** The standalone build button label; shared with the release journey status bar (F15-T02). */
export function materializationActionLabel({
	busy,
	buildAction,
	modelCount,
}: {
	busy: string;
	buildAction: MaterializationBuildAction | null;
	modelCount: number;
}): string {
	if (busy === "build") return "处理中…";
	return (
		(buildAction && BUILD_LABELS[buildAction]) || (modelCount > 1 ? `创建并运行 ${modelCount} 个模型` : "创建并运行")
	);
}

export function ModelMaterializationActions({
	embedded,
	showBack = true,
	pageAction,
	canMaintain,
	canConfigureQuality,
	busy,
	canBuild,
	buildAction,
	modelCount,
	buildBlocker,
	onBack,
	onPrimaryAction,
	onConfigureQuality,
}: {
	embedded: boolean;
	showBack?: boolean;
	pageAction?: DeliveryAction | null;
	canMaintain: boolean;
	canConfigureQuality: boolean;
	busy: string;
	canBuild: boolean;
	buildAction: MaterializationBuildAction | null;
	modelCount: number;
	buildBlocker?: string;
	onBack: () => void;
	onPrimaryAction: () => void;
	onConfigureQuality?: () => void;
}) {
	const configureQuality = embedded && pageAction?.code === "CONFIGURE_QUALITY_RULES";
	// Opening the editor preserves its draft and does not submit a lifecycle command.
	const blockedByBusy = Boolean(busy) && !(configureQuality && busy === "load");
	const invokesBuild = !embedded || !["CONFIGURE_QUALITY_RULES", "RUN_QUALITY", "NEXT", "RERUN_GOVERNANCE_QUALITY"].includes(pageAction?.code || "");
	const disabled =
		(configureQuality ? !canConfigureQuality || !onConfigureQuality : !canMaintain) ||
		blockedByBusy ||
		(embedded && !pageAction?.enabled) ||
		(invokesBuild && !canBuild);
	const label = embedded
		? blockedByBusy
			? "处理中…"
			: WIZARD_LABELS[pageAction?.code || ""] || "开始构建"
		: materializationActionLabel({ busy, buildAction, modelCount });
	return (
		<div className="dmx-dialog-actions">
			{showBack ? <Button onClick={onBack}>{embedded ? "上一步" : "取消"}</Button> : null}
			<Button
				primary
				disabled={disabled}
				onClick={configureQuality ? onConfigureQuality : onPrimaryAction}
				aria-controls={configureQuality ? "model-target-quality" : undefined}
				title={
					!disabled
						? undefined
						: configureQuality
							? "请确认维护权限、保存模型和加工配置修改，并等待当前操作完成"
							: buildBlocker || "当前发布单不允许执行此操作"
				}
			>
				{label}
			</Button>
		</div>
	);
}
