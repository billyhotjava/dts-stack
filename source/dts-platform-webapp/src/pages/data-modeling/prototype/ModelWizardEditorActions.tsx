import type { ModelAuthoringContext } from "@/api/modelAuthoringApi";
import { Button } from "./PrototypePrimitives";

export function ModelWizardEditorActions({
	definition,
	persisted,
	published,
	dirty,
	busy,
	readOnly,
	canMaintain,
	context,
	onSave,
	onSubmit,
	onNext,
	onPrevious,
	onFork,
	onStash,
}: {
	definition: boolean;
	persisted: boolean;
	published: boolean;
	dirty: boolean;
	busy: boolean;
	readOnly: boolean;
	canMaintain: boolean;
	context: ModelAuthoringContext | null;
	onSave: () => void;
	onSubmit: () => void;
	onNext: () => void;
	onPrevious: () => void;
	onFork: () => void;
	onStash: () => void;
}) {
	const pending = context?.openDraft && context.openDraft.state !== "COMMITTED";
	const canProceed = !definition && !dirty && context?.implementation && !pending;
	const allowed = context?.allowedActions || [];
	const enabled = published
		? allowed.includes("FORK_DRAFT")
		: canProceed ||
			(!readOnly &&
				(!persisted ||
					allowed.some((action) =>
						["SAVE", "EDIT_MODEL", "EDIT_IMPLEMENTATION", "VALIDATE", "COMMIT"].includes(action),
					)));
	const execute = published ? onFork : definition ? onSave : canProceed ? onNext : onSubmit;
	return (
		<div className="dmx-editor-toolbar" aria-label="当前步骤操作" role="toolbar">
			{!definition ? (
				<Button disabled={busy} onClick={onPrevious}>
					上一步
				</Button>
			) : null}
			{!published && dirty ? (
				<Button disabled={busy || readOnly || !canMaintain} onClick={onStash}>
					暂存草稿
				</Button>
			) : null}
			<Button primary disabled={busy || !canMaintain || !enabled} onClick={execute}>
				{busy
					? "处理中…"
					: published
						? "创建新草稿版本"
						: definition
							? "保存并继续"
							: canProceed
								? "下一步"
								: "提交实现并继续"}
			</Button>
		</div>
	);
}
