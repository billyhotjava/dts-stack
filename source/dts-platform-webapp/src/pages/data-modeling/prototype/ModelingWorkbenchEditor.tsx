import type {
	ModelAuthoringContext,
	ModelAuthoringProjectionNode,
	ModelAuthoringValidation,
} from "@/api/modelAuthoringApi";
import type { WarehousePlanSourceBindingView } from "@/api/warehousePlanApi";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelSpecField, ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { ConceptDimensionForm } from "./ConceptDimensionForm";
import { ModelAuthoringDiagnostics } from "./ModelAuthoringDiagnostics";
import { ModelDefinitionForm } from "./ModelDefinitionForm";
import { ModelImplementationForm } from "./ModelImplementationForm";
import { ModelWizardEditorActions } from "./ModelWizardEditorActions";
import type { WorkbenchDialog } from "./ModelWorkbenchDialog";
import { authoringOriginLabel, isConceptDimensionDraft } from "./modelWorkbenchPresentation";
import { Button } from "./PrototypePrimitives";
import type { ModelDraft, ModelDraftValidationErrors, ModelWorkbenchContext } from "./services/modelWorkbenchService";
import "./modeling-workbench.css";
import { type ModelingWorkbenchView, modelingCapabilityReasonsText } from "./modelingWorkbenchMode";

export type ModelingWorkbenchEditorProps = {
	definitionOnly?: boolean;
	draft: ModelDraft;
	context: ModelWorkbenchContext;
	dimensionDefinitions: DimensionDefinitionView[];
	dimensionDefinitionFailure: string;
	currentOwnerId: string;
	selectedModel: ModelSpecView | null;
	authoringContext: ModelAuthoringContext | null;
	authoringFailure: string;
	authoringValidation: ModelAuthoringValidation | null;
	authoringBusy: string;
	authoringConflict: boolean;
	canMaintain: boolean;
	readOnly: boolean;
	saving: boolean;
	dirty: boolean;
	validationErrors: ModelDraftValidationErrors;
	failureMessage: string;
	editorAccessMessage: string;
	fieldRowIds: string[];
	materializationRefreshKey?: number;
	view: ModelingWorkbenchView;
	onViewChange: (view: ModelingWorkbenchView) => void;
	onChange: (draft: ModelDraft) => void;
	onSave: () => void;
	onStash: () => void;
	onSubmitImplementation: () => void;
	onNext: () => void;
	onPrevious: () => void;
	onValidateAuthoring: () => void;
	onCommitAuthoring: () => void;
	onForkPublished: () => void;
	onOpenRawNode: (node: ModelAuthoringProjectionNode) => void;
	onConfirmDimension?: () => void;
	onRefresh: () => void;
	onDialog: (dialog: Exclude<WorkbenchDialog, null>) => void;
	onAddFields: (count: number) => void;
	onRemoveBlankFields: () => void;
	onUpdateField: (index: number, patch: Partial<ModelSpecField>) => void;
	onDeleteField: (index: number) => void;
	onStandardChange: (index: number, value: string) => void;
	onSourcesChanged: (sources: WarehousePlanSourceBindingView[], planId: string) => void;
};

export function ModelingWorkbenchEditor(props: ModelingWorkbenchEditorProps) {
	const {
		draft,
		selectedModel,
		authoringContext,
		authoringFailure,
		authoringValidation,
		authoringBusy,
		authoringConflict,
		canMaintain,
		readOnly,
		saving,
		dirty,
		failureMessage,
		editorAccessMessage,
		onSave,
		onValidateAuthoring,
		onCommitAuthoring,
		onForkPublished,
		onOpenRawNode,
		onConfirmDimension,
		onRefresh,
		onDialog,
		onViewChange,
		materializationRefreshKey,
		view,
	} = props;
	const conceptDimension = isConceptDimensionDraft(draft);
	const persisted = Boolean(selectedModel);
	const published = Boolean(
		selectedModel && (authoringContext?.publishedForkRequired || selectedModel.status === "PUBLISHED"),
	);
	const effectiveReadOnly = readOnly;
	const busy = saving || Boolean(authoringBusy);
	const projection = authoringContext?.projection;
	const currentAuthoringContext =
		authoringContext?.model.id === selectedModel?.id &&
		authoringContext?.model.revision === selectedModel?.revision &&
		authoringContext?.model.checksum === selectedModel?.checksum
			? authoringContext
			: null;
	const displayedFailure =
		authoringFailure ||
		(authoringConflict ? "草稿版本已变化，请刷新后继续，平台不会自动覆盖他人修改。" : failureMessage);

	return (
		<div className="dmx-workbench-editor">
			{editorAccessMessage ? <output className="dmx-editor-access-note">{editorAccessMessage}</output> : null}
			{selectedModel && !props.definitionOnly ? (
				// biome-ignore lint/a11y/useSemanticElements: this is a styled navigation switch rather than a form fieldset.
				<div aria-label="模型表现模式" className="dmx-workbench-mode-switch" role="group">
					<Button className={view === "visual" ? "active" : ""} onClick={() => onViewChange("visual")} type="text">
						可视化模式
					</Button>
					<Button className={view === "code" ? "active" : ""} onClick={() => onViewChange("code")} type="text">
						代码模式
					</Button>
				</div>
			) : null}
			{projection?.reasons.length ? (
				<div className="dmx-capability-note">{modelingCapabilityReasonsText(projection.reasons)}</div>
			) : null}

			{displayedFailure ? (
				<div className="dmx-inline-error" role="alert">
					{displayedFailure}
				</div>
			) : null}
			{authoringValidation?.modelIssues.length ? (
				<div className="dmx-dbt-diagnostics" role="alert">
					<h3>模型定义校验</h3>
					{authoringValidation.modelIssues.map((issue) => (
						<div key={`${issue.code}:${issue.field}`}>
							<b>{issue.field}</b>
							<p>{issue.message}</p>
						</div>
					))}
				</div>
			) : null}
			<ModelAuthoringDiagnostics validation={authoringValidation} />
			{projection?.rawNodes.length ? (
				<section className="dmx-dbt-diagnostics" aria-label="原始代码节点">
					<h3>原始代码节点</h3>
					{projection.rawNodes.map((node) => (
						<div key={node.nodeId}>
							<b>{node.sourcePath || node.nodeId}</b>
							<span>{node.kind}</span>

							<Button onClick={() => onOpenRawNode(node)} type="link">
								在代码视图定位
							</Button>
						</div>
					))}
				</section>
			) : null}

			<fieldset className="dmx-editor-fieldset dmx-editor-scroll" disabled={effectiveReadOnly || busy}>
				{conceptDimension ? (
					<ConceptDimensionForm {...props} draft={draft} />
				) : props.definitionOnly ? (
					<ModelDefinitionForm {...props} />
				) : (
					<ModelImplementationForm {...props} draft={draft} />
				)}
			</fieldset>
			{conceptDimension ? (
				<div className="dmx-editor-toolbar">
					<Button disabled={busy || effectiveReadOnly || !dirty} onClick={onSave} primary>
						保存草稿
					</Button>
					<Button disabled={busy || effectiveReadOnly || dirty} onClick={onConfirmDimension}>
						确认版本
					</Button>
				</div>
			) : (
				<ModelWizardEditorActions
					definition={Boolean(props.definitionOnly)}
					persisted={persisted}
					published={published}
					dirty={dirty}
					busy={busy}
					readOnly={effectiveReadOnly}
					canMaintain={canMaintain}
					context={currentAuthoringContext}
					onSave={onSave}
					onStash={props.onStash}
					onSubmit={props.onSubmitImplementation}
					onNext={props.onNext}
					onPrevious={props.onPrevious}
					onFork={onForkPublished}
				/>
			)}
			{persisted ? (
				<details>
					<summary>版本与记录</summary>
					<Button onClick={() => onDialog("versions")}>版本管理</Button>
					<Button onClick={() => onDialog("releases")}>发布记录</Button>
					<Button onClick={onRefresh} disabled={busy}>
						刷新模型
					</Button>
				</details>
			) : null}
		</div>
	);
}
