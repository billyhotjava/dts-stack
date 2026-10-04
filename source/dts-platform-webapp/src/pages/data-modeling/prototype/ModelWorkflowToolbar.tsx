import { useReducer } from "react";
import type { ModelAuthoringContext, ModelAuthoringValidation } from "@/api/modelAuthoringApi";
import type { WorkbenchDialog } from "./ModelWorkbenchDialog";
import { resolveModelWorkflowAction, type ModelWorkflowAction } from "./modelWorkflowAction";
import { Button } from "./PrototypePrimitives";

type ModelWorkflowToolbarProps = {
	persisted: boolean;
	published: boolean;
	dirty: boolean;
	busy: boolean;
	saving: boolean;
	readOnly: boolean;
	canMaintain: boolean;
	authoringBusy: string;
	authoringContext: ModelAuthoringContext | null;
	authoringValidation: ModelAuthoringValidation | null;
	onSave: () => void;
	onValidate: () => void;
	onCommit: () => void;
	onForkPublished: () => void;
	onRefresh: () => void;
	onDialog: (dialog: Exclude<WorkbenchDialog, null>) => void;
};

const ACTION_LABELS: Record<ModelWorkflowAction, string> = {
	save: "保存草稿",
	validate: "校验",
	commit: "提交加工配置",
	deliver: "构建",
	fork: "创建新草稿版本",
};
const BUSY_LABELS: Record<string, string> = {
	save: "保存中…",
	validate: "校验中…",
	commit: "提交中…",
	create: "创建中…",
	load: "读取中…",
};
const INSPECTIONS = [
	["准入详情", "gates"],
	["关联关系", "association"],
	["质量检查", "quality"],
	["运行日志", "logs"],
] as const;

export function ModelWorkflowToolbar(props: ModelWorkflowToolbarProps) {
	const {
		persisted,
		published,
		dirty,
		busy,
		saving,
		readOnly,
		canMaintain,
		authoringBusy,
		authoringContext,
		authoringValidation,
		onSave,
		onValidate,
		onCommit,
		onForkPublished,
		onRefresh,
		onDialog,
	} = props;
	const input = {
		persisted,
		published,
		dirty,
		readOnly,
		canMaintain,
		context: authoringContext,
		validation: authoringValidation,
	};
	const [, refreshAction] = useReducer((value: number) => value + 1, 0);
	const deliverable = Boolean(
		persisted &&
			!dirty &&
			authoringContext?.implementation &&
			!(authoringContext.openDraft && authoringContext.openDraft.state !== "COMMITTED"),
	);
	const next = resolveModelWorkflowAction(input);
	const actions: Record<ModelWorkflowAction, () => void> = {
		save: onSave,
		validate: onValidate,
		commit: onCommit,
		fork: onForkPublished,
		deliver: () => onDialog("build"),
	};
	const execute = () => {
		const current = resolveModelWorkflowAction(input);
		if (busy || !current.enabled) return;
		if (current.action !== next.action) {
			refreshAction();
			return;
		}
		actions[current.action]();
	};
	const label = saving
		? "保存中…"
		: BUSY_LABELS[authoringBusy] || (next.action === "save" && !persisted ? "保存" : ACTION_LABELS[next.action]);
	return (
		<section aria-label="模型操作流程" className="dmx-model-workflow">
			<div aria-label="模型主流程操作" className="dmx-model-workflow__primary-actions" role="toolbar">
				<Button disabled={busy || !next.enabled} onClick={execute} primary>
					{label}
				</Button>
				{!next.enabled && next.reason && !busy ? <output>{next.reason}</output> : null}
			</div>
			<details className="dmx-model-workflow__secondary">
				<summary>检查与记录</summary>
				<div aria-label="模型检查与记录" role="toolbar">
					{INSPECTIONS.map(([title, dialog]) => (
						<Button key={dialog} disabled={busy || !persisted} onClick={() => onDialog(dialog)}>
							{title}
						</Button>
					))}
					{next.action !== "deliver" ? (
						<Button disabled={busy || !deliverable} onClick={() => onDialog("build")}>
							构建
						</Button>
					) : null}
					<Button disabled={busy || !deliverable} onClick={() => onDialog("release")}>
						版本发布
					</Button>
					<Button disabled={busy} onClick={onRefresh}>
						刷新状态
					</Button>
				</div>
			</details>
		</section>
	);
}
