import type { DbtImplementationDraft } from "@/api/dbtImplementationDraftApi";
import type { ModelAuthoringValidation } from "@/api/modelAuthoringApi";
import type { WorkbenchDialog } from "./ModelWorkbenchDialog";
import { Button } from "./PrototypePrimitives";

type WorkflowStep = "save" | "validate" | "commit" | "deliver";

type ModelWorkflowToolbarProps = {
	persisted: boolean;
	published: boolean;
	dirty: boolean;
	busy: boolean;
	saving: boolean;
	readOnly: boolean;
	canMaintain: boolean;
	authoringBusy: string;
	draftState?: DbtImplementationDraft["state"];
	hasImplementation: boolean;
	authoringValidation: ModelAuthoringValidation | null;
	onSave: () => void;
	onValidate: () => void;
	onCommit: () => void;
	onForkPublished: () => void;
	onRefresh: () => void;
	onDialog: (dialog: Exclude<WorkbenchDialog, null>) => void;
};

const WORKFLOW_STEPS: Array<{ key: WorkflowStep; label: string; description: string }> = [
	{ key: "save", label: "保存草稿", description: "保存当前修改" },
	{ key: "validate", label: "校验", description: "检查定义和依赖" },
	{ key: "commit", label: "提交实现", description: "生成可执行版本" },
	{ key: "deliver", label: "发布与物化", description: "构建目标表并交付" },
];

export function ModelWorkflowToolbar({
	persisted,
	published,
	dirty,
	busy,
	saving,
	readOnly,
	canMaintain,
	authoringBusy,
	draftState,
	hasImplementation,
	authoringValidation,
	onSave,
	onValidate,
	onCommit,
	onForkPublished,
	onRefresh,
	onDialog,
}: ModelWorkflowToolbarProps) {
	const validationReady = Boolean(
		draftState === "VALIDATED" &&
			authoringValidation?.implementationValidation &&
			!authoringValidation.modelIssues.length,
	);
	const pendingDraft = draftState === "DRAFT" || draftState === "VALIDATED" || draftState === "COMMITTING";
	const committedCurrent = hasImplementation && !dirty && !pendingDraft;
	const activeStep: WorkflowStep | null = published
		? null
		: !persisted || dirty
			? "save"
			: validationReady
				? "commit"
				: committedCurrent
					? "deliver"
					: "validate";
	const completed = (step: WorkflowStep) => {
		if (published) return true;
		if (step === "save") return persisted && !dirty;
		if (step === "validate") return validationReady || committedCurrent;
		if (step === "commit") return committedCurrent;
		return false;
	};
	const statusText = (() => {
		if (!canMaintain || readOnly) return "当前仅可查看；需要维护权限后才能继续操作。";
		if (authoringBusy === "create") return "正在创建新的模型草稿版本…";
		if (saving || authoringBusy === "save") return "正在保存模型草稿…";
		if (authoringBusy === "validate") return "正在校验模型定义、实现和依赖…";
		if (authoringBusy === "commit") return "正在提交模型实现…";
		if (published) return "当前版本已发布；如需修改，请先创建新的草稿版本。";
		if (!persisted || dirty) return "下一步：保存草稿，保留当前模型修改。";
		if (validationReady) return "下一步：提交实现，生成可用于构建的版本。";
		if (committedCurrent) return "下一步：发布与物化，构建目标表并进入交付流程。";
		return draftState === "VALIDATED"
			? "下一步：重新校验以恢复本次提交凭据，然后提交实现。"
			: "下一步：校验模型定义、实现配置和依赖关系。";
	})();

	return (
		<section aria-label="模型操作流程" className="dmx-model-workflow">
			<header className="dmx-model-workflow__header">
				<div>
					<strong>操作流程</strong>
					<span>按顺序完成模型设计、实现和交付</span>
				</div>
				<p aria-live="polite">{statusText}</p>
			</header>
			<ol className="dmx-model-workflow__steps">
				{WORKFLOW_STEPS.map((step, index) => {
					const state = completed(step.key) ? "complete" : activeStep === step.key ? "active" : "pending";
					return (
						<li aria-current={state === "active" ? "step" : undefined} data-state={state} key={step.key}>
							<span aria-hidden="true" className="dmx-model-workflow__index">
								{state === "complete" ? "✓" : index + 1}
							</span>
							<span>
								<b>{step.label}</b>
								<small>{step.description}</small>
							</span>
						</li>
					);
				})}
			</ol>
			<div aria-label="模型主流程操作" className="dmx-model-workflow__primary-actions" role="toolbar">
				{published ? (
					<Button disabled={!canMaintain || busy} onClick={onForkPublished} primary>
						{authoringBusy === "create" ? "创建中…" : "创建新草稿版本"}
					</Button>
				) : (
					<Button
						disabled={!canMaintain || readOnly || busy || !dirty}
						onClick={onSave}
						primary={activeStep === "save"}
						title={!dirty ? "当前没有待保存变更" : "保存当前模型修改"}
					>
						{saving || authoringBusy === "save" ? "保存中…" : persisted ? "保存草稿" : "保存"}
					</Button>
				)}
				{!published ? (
					<>
						<Button
							disabled={!canMaintain || busy || !persisted}
							onClick={onValidate}
							primary={activeStep === "validate"}
							title={dirty ? "校验前会先保存当前修改" : "检查模型定义、实现配置和依赖关系"}
						>
							{authoringBusy === "validate" ? "校验中…" : "校验"}
						</Button>
						<Button
							disabled={!canMaintain || busy || dirty || !validationReady}
							onClick={onCommit}
							primary={activeStep === "commit"}
							title={!validationReady ? "请先完成校验，再提交实现" : "生成新的可执行实现版本"}
						>
							{authoringBusy === "commit" ? "提交中…" : "提交实现"}
						</Button>
					</>
				) : null}
				<Button
					disabled={busy || !persisted || !canMaintain}
					onClick={() => onDialog("publish")}
					primary={activeStep === "deliver"}
					title="创建或查看交付候选，执行目标表构建、质量检查和发布"
				>
					发布与物化
				</Button>
			</div>
			<details className="dmx-model-workflow__secondary">
				<summary>检查与记录</summary>
				<div aria-label="模型检查与记录" role="toolbar">
					<Button disabled={busy || !persisted} onClick={() => onDialog("gates")}>
						准入详情
					</Button>
					<Button disabled={busy || !persisted} onClick={() => onDialog("association")}>
						关联关系
					</Button>
					<Button disabled={busy || !persisted} onClick={() => onDialog("quality")}>
						质量门禁
					</Button>
					<Button disabled={busy || !persisted} onClick={() => onDialog("logs")}>
						运行日志
					</Button>
					<Button disabled={busy} onClick={onRefresh}>
						刷新状态
					</Button>
				</div>
			</details>
		</section>
	);
}
