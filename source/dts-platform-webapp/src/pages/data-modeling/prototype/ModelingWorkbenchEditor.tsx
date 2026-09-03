import { useEffect, useMemo, useState } from "react";
import type {
	ModelAuthoringContext,
	ModelAuthoringProjectionNode,
	ModelAuthoringValidation,
} from "@/api/modelAuthoringApi";
import { listBusinessProcessesApi, type Sprint64BusinessProcess } from "@/api/sprint64GovernanceApi";
import type { WarehousePlanSourceBindingView } from "@/api/warehousePlanApi";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelSpecField, ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { materializationLabel } from "@/utils/customerDisplayLabels";
import { ModelFieldEditorTable } from "./ModelFieldEditorTable";
import { ModelImplementationBindingFields } from "./ModelImplementationBindingFields";
import { ModelMaterializationStatusCard } from "./ModelMaterializationStatus";
import { ModelServingSyncStatus } from "./ModelServingSyncStatus";
import type { WorkbenchDialog } from "./ModelWorkbenchDialog";
import {
	DIMENSION_STORAGE_OPTIONS,
	isConceptDimensionDraft,
	isDimensionTableDraft,
	resolveConceptDimensionPresentation,
	resolveDimensionFormPresentation,
} from "./modelWorkbenchPresentation";
import { Button } from "./PrototypePrimitives";
import {
	type ConceptDimensionDraft,
	MODEL_KIND_CONFIG,
	type ModelDraft,
	type ModelDraftValidationErrors,
	type ModelSpecDraft,
	type ModelWorkbenchContext,
} from "./services/modelWorkbenchService";
import { resolveBusinessProcessBinding } from "./services/planningContextPolicyService";
import "./modeling-workbench.css";
import { type ModelingWorkbenchView, modelingCapabilityReasonsText } from "./modelingWorkbenchMode";

export type ModelingWorkbenchEditorProps = {
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

type DraftFormProps = Pick<
	ModelingWorkbenchEditorProps,
	| "draft"
	| "context"
	| "dimensionDefinitions"
	| "dimensionDefinitionFailure"
	| "currentOwnerId"
	| "selectedModel"
	| "readOnly"
	| "validationErrors"
	| "fieldRowIds"
	| "onChange"
	| "onDialog"
	| "onAddFields"
	| "onRemoveBlankFields"
	| "onUpdateField"
	| "onDeleteField"
	| "onStandardChange"
	| "onSourcesChanged"
	| "onViewChange"
>;

type ConceptDimensionFormProps = Omit<DraftFormProps, "draft"> & { draft: ConceptDimensionDraft };
type ModelSpecFormProps = Omit<DraftFormProps, "draft"> & { draft: ModelSpecDraft };

function ValidationMessage({ message }: { message?: string }) {
	return message ? (
		<small className="dmx-workbench-editor__validation" role="alert">
			{message}
		</small>
	) : null;
}

function FieldsPanel(props: ModelSpecFormProps & { dimensionMode: boolean }) {
	const {
		draft,
		context,
		fieldRowIds,
		dimensionMode,
		readOnly,
		selectedModel,
		validationErrors,
		onAddFields,
		onRemoveBlankFields,
		onUpdateField,
		onDeleteField,
		onStandardChange,
		onDialog,
	} = props;
	return (
		<section className="dmx-editor-panel">
			<h3>字段管理</h3>
			<ValidationMessage message={validationErrors.fields} />
			<ModelFieldEditorTable
				bindings={draft.standardBindings}
				canAssociate={Boolean(selectedModel)}
				canOpenCode={false}
				showCodeAction={false}
				dimensionMode={dimensionMode}
				fieldRowIds={fieldRowIds}
				fields={draft.fields}
				onAddFields={onAddFields}
				onDelete={onDeleteField}
				onOpenAssociation={() => onDialog("association")}
				onOpenCode={() => undefined}
				onRemoveBlankFields={onRemoveBlankFields}
				onStandardChange={onStandardChange}
				onUpdate={onUpdateField}
				readOnly={readOnly}
				standards={context.standards}
			/>
		</section>
	);
}

function ConceptDimensionForm(props: ConceptDimensionFormProps) {
	const { draft, context, validationErrors, onChange } = props;
	const patch = (next: Partial<ConceptDimensionDraft>) => onChange({ ...draft, ...next });
	const presentation = resolveConceptDimensionPresentation({ draft, domains: context.domains });

	return (
		<section className="dmx-editor-panel">
			<h3>基本信息</h3>
			<div className="dmx-workbench-editor__basic-grid">
				<label>
					<span>数仓分层</span>
					<input aria-label="数仓分层" disabled value={presentation.warehouseLayer} />
				</label>
				<label>
					<span className="required">数据域</span>
					<select
						aria-label="数据域"
						disabled={Boolean(draft.definitionBase)}
						onChange={(event) => patch({ domainId: event.target.value })}
						value={draft.domainId}
					>
						<option value="">请选择数据域</option>
						{context.domains
							.filter((item) => Boolean(item.parentCode))
							.map((item) => (
								<option key={item.id} value={item.id}>
									{item.name} · {item.code}
								</option>
							))}
					</select>
					<ValidationMessage message={validationErrors.domainId} />
				</label>
				<label>
					<span>系统编码</span>
					<input aria-label="系统编码" disabled value={presentation.systemCode} />
				</label>
				<label>
					<span className="required">中文名称</span>
					<input aria-label="中文名称" onChange={(event) => patch({ name: event.target.value })} value={draft.name} />
					<ValidationMessage message={validationErrors.name} />
				</label>
				<label className="dmx-workbench-editor__wide-field">
					<span>描述</span>
					<textarea
						aria-label="描述"
						onChange={(event) => patch({ description: event.target.value })}
						value={draft.description}
					/>
				</label>
			</div>
		</section>
	);
}

function DimensionDraftForm(props: ModelSpecFormProps) {
	const {
		draft,
		context,
		dimensionDefinitions,
		dimensionDefinitionFailure,
		currentOwnerId,
		validationErrors,
		onChange,
	} = props;
	const patch = (next: Partial<ModelSpecDraft>) => onChange({ ...draft, ...next });
	const definition = dimensionDefinitions.find((item) => item.id === draft.dimensionDefinitionId) || null;
	const presentation = resolveDimensionFormPresentation({
		draft,
		domains: context.domains,
		definition,
		currentOwnerId,
	});
	const domainOptions = context.domains.filter((item) => Boolean(item.parentCode));
	const missingPersistedDomain = Boolean(draft.domainId && !domainOptions.some((item) => item.id === draft.domainId));
	const missingPersistedDefinition = Boolean(
		draft.base &&
			draft.dimensionDefinitionId &&
			!dimensionDefinitions.some((item) => item.id === draft.dimensionDefinitionId),
	);

	return (
		<>
			<section className="dmx-editor-panel">
				<h3>基本信息</h3>
				<div className="dmx-workbench-editor__basic-grid">
					<label>
						<span>数仓分层</span>
						<select
							aria-label="数仓分层"
							disabled={Boolean(draft.base)}
							onChange={() => undefined}
							value="公共层 / 维度层"
						>
							<option value="公共层 / 维度层">公共层 / 维度层</option>
						</select>
					</label>
					<label>
						<span className="required">数据域</span>
						<select
							aria-label="数据域"
							disabled={Boolean(draft.base)}
							onChange={(event) => patch({ domainId: event.target.value })}
							value={draft.domainId}
						>
							<option value="">请选择数据域</option>
							{domainOptions.map((item) => (
								<option key={item.id} value={item.id}>
									{item.name} · {item.code}
								</option>
							))}
							{missingPersistedDomain ? (
								<option disabled value={draft.domainId}>
									已保存数据域 · {draft.domainId}
								</option>
							) : null}
						</select>
						<ValidationMessage message={validationErrors.domainId} />
						{!missingPersistedDomain && !domainOptions.length ? (
							<small>当前规划暂无数据域，请先在数仓规划中创建数据域。</small>
						) : null}
					</label>
					<label>
						<span>存储策略</span>
						<select
							aria-label="存储策略"
							onChange={(event) =>
								patch({
									materialization: event.target.value,
									loadStrategy: event.target.value === "incremental" ? "INCREMENTAL" : "FULL",
								})
							}
							value={draft.materialization}
						>
							{DIMENSION_STORAGE_OPTIONS.map((item) => (
								<option key={item.value} value={item.value}>
									{item.label}
								</option>
							))}
						</select>
					</label>
					<label>
						<span className="required">维度</span>
						<select
							aria-label="维度"
							disabled={Boolean(draft.base)}
							onChange={(event) => patch({ dimensionDefinitionId: event.target.value })}
							value={draft.dimensionDefinitionId}
						>
							<option value="">请选择维度</option>
							{dimensionDefinitions.map((item) => (
								<option disabled={item.status !== "CURRENT"} key={item.id} value={item.id}>
									{item.name} · {item.systemCode}
									{item.status === "DRAFT" ? "（草稿，需先确认定义）" : ""}
								</option>
							))}
							{missingPersistedDefinition ? (
								<option disabled value={draft.dimensionDefinitionId}>
									已保存维度 · {draft.dimensionDefinitionId}
								</option>
							) : null}
						</select>
						<ValidationMessage message={validationErrors.dimensionDefinitionId} />
						{dimensionDefinitionFailure ? (
							<small className="dmx-workbench-editor__validation" role="alert">
								{dimensionDefinitionFailure}
							</small>
						) : !dimensionDefinitions.length && !missingPersistedDefinition ? (
							<small>当前数据域暂无维度，请先创建维度。</small>
						) : dimensionDefinitions.every((item) => item.status !== "CURRENT") ? (
							<small>当前数据域有 {dimensionDefinitions.length} 个未确认维度，请先“确认定义”后再绑定。</small>
						) : null}
					</label>
					<label>
						<span>表名规则</span>
						<input aria-label="表名规则" disabled value={presentation.tableNamingRule} />
					</label>
					<label>
						<span className="required">表名</span>
						<input
							aria-label="表名"
							onChange={(event) => patch({ physicalName: event.target.value })}
							value={draft.physicalName}
						/>
						<ValidationMessage message={validationErrors.physicalName} />
						{draft.base && !draft.physicalName.trim() ? <small>历史草稿尚未保存物理表名，请补录后保存。</small> : null}
					</label>
					<label>
						<span className="required">表中文名</span>
						<input aria-label="表中文名" onChange={(event) => patch({ name: event.target.value })} value={draft.name} />
						<ValidationMessage message={validationErrors.name} />
					</label>
					<label>
						<span>生命周期</span>
						<input aria-label="生命周期" disabled value={presentation.lifecycle} />
					</label>
					<label>
						<span>负责人</span>
						<input aria-label="负责人" disabled value={presentation.owner} />
					</label>
					<label className="dmx-workbench-editor__wide-field">
						<span className="required">描述（维度定义）</span>
						<textarea
							aria-label="描述"
							onChange={(event) => patch({ description: event.target.value })}
							value={draft.description}
						/>
						<ValidationMessage message={validationErrors.description} />
					</label>
				</div>
			</section>
			<ModelImplementationBindingFields
				context={context}
				draft={draft}
				onChange={onChange}
				onSourcesChanged={props.onSourcesChanged}
				validationErrors={validationErrors}
			/>
			<FieldsPanel {...props} dimensionMode />
		</>
	);
}

function CompatibilityDraftForm(props: ModelSpecFormProps) {
	const { draft, context, validationErrors, onChange } = props;
	const config = MODEL_KIND_CONFIG[draft.createKind];
	const patch = (next: Partial<ModelSpecDraft>) => onChange({ ...draft, ...next });
	const factMode = draft.createKind === "fact";
	const applicationMode = draft.createKind === "application";
	const [processes, setProcesses] = useState<Sprint64BusinessProcess[]>([]);
	const [processLoaded, setProcessLoaded] = useState(false);
	const [processFailure, setProcessFailure] = useState("");
	useEffect(() => {
		if (!factMode || !draft.domainId) {
			setProcesses([]);
			setProcessLoaded(!factMode);
			return;
		}
		let active = true;
		setProcessLoaded(false);
		setProcessFailure("");
		void listBusinessProcessesApi(draft.domainId)
			.then((items) => {
				if (active) setProcesses(items);
			})
			.catch(() => {
				if (active) {
					setProcesses([]);
					setProcessFailure("业务过程读取失败，请刷新后重试。");
				}
			})
			.finally(() => {
				if (active) setProcessLoaded(true);
			});
		return () => {
			active = false;
		};
	}, [draft.domainId, factMode]);
	const processBinding = useMemo(
		() => resolveBusinessProcessBinding(draft.businessProcessId, processes),
		[draft.businessProcessId, processes],
	);
	useEffect(() => {
		if (!factMode || !processLoaded) return;
		const currentIsValid = processBinding.processes.some((item) => item.id === draft.businessProcessId);
		if (draft.businessProcessId && !currentIsValid) {
			onChange({ ...draft, businessProcessId: "" });
			return;
		}
		if (
			!processBinding.showSelector &&
			processBinding.selectedId &&
			draft.businessProcessId !== processBinding.selectedId
		) {
			onChange({ ...draft, businessProcessId: processBinding.selectedId });
		}
	}, [
		draft,
		factMode,
		onChange,
		processBinding.processes,
		processBinding.selectedId,
		processBinding.showSelector,
		processLoaded,
	]);
	const missingPersistedDomain = Boolean(
		draft.base && draft.domainId && !context.domains.some((item) => item.id === draft.domainId),
	);
	const dataMartOptions = context.dataMarts || [];
	const subjectDomainOptions = (context.subjectDomains || []).filter((item) => item.martId === draft.dataMartId);
	const missingPersistedDataMart = Boolean(
		draft.dataMartId && !dataMartOptions.some((item) => item.id === draft.dataMartId),
	);
	const missingPersistedSubjectDomain = Boolean(
		draft.subjectDomainId && !subjectDomainOptions.some((item) => item.id === draft.subjectDomainId),
	);
	return (
		<>
			<section className="dmx-editor-panel">
				<h3>基本信息</h3>
				<div className="dmx-workbench-editor__basic-grid">
					<label>
						<span className="required">数据域</span>
						<select
							aria-label="数据域"
							disabled={Boolean(draft.base)}
							onChange={(event) => patch({ domainId: event.target.value, businessProcessId: "" })}
							value={draft.domainId}
						>
							<option value="">请选择数据域</option>
							{context.domains
								.filter((item) => Boolean(item.parentCode))
								.map((item) => (
									<option key={item.id} value={item.id}>
										{item.name} · {item.code}
									</option>
								))}
							{missingPersistedDomain ? (
								<option disabled value={draft.domainId}>
									已保存数据域 · {draft.domainId}
								</option>
							) : null}
						</select>
						<ValidationMessage message={validationErrors.domainId} />
					</label>
					{factMode ? (
						// biome-ignore lint/a11y/noLabelWithoutControl: the select is conditional while its status text stays in the same labeled field.
						<label>
							<span className="required">业务过程</span>
							{processBinding.showSelector ? (
								<select
									aria-label="业务过程"
									onChange={(event) => patch({ businessProcessId: event.target.value })}
									value={draft.businessProcessId || ""}
								>
									<option value="">请选择业务过程</option>
									{processBinding.processes.map((item) => (
										<option key={item.id} value={item.id}>
											{item.name} · {item.processId}
										</option>
									))}
								</select>
							) : (
								<small>{processLoaded ? processBinding.message : "正在读取业务过程…"}</small>
							)}
							<ValidationMessage message={validationErrors.businessProcessId} />
							{processFailure ? <ValidationMessage message={processFailure} /> : null}
						</label>
					) : null}
					{applicationMode ? (
						<>
							<label>
								<span className="required">数据集市</span>
								<select
									aria-label="数据集市"
									onChange={(event) => patch({ dataMartId: event.target.value, subjectDomainId: "" })}
									value={draft.dataMartId || ""}
								>
									<option value="">请选择数据集市</option>
									{dataMartOptions.map((item) => (
										<option key={item.id} value={item.id}>
											{item.name} · {item.code}
										</option>
									))}
									{missingPersistedDataMart ? (
										<option disabled value={draft.dataMartId}>
											已失效数据集市 · {draft.dataMartId}
										</option>
									) : null}
								</select>
								<ValidationMessage message={validationErrors.dataMartId} />
							</label>
							<label>
								<span className="required">主题域</span>
								<select
									aria-label="主题域"
									disabled={!draft.dataMartId}
									onChange={(event) => patch({ subjectDomainId: event.target.value })}
									value={draft.subjectDomainId || ""}
								>
									<option value="">{draft.dataMartId ? "请选择主题域" : "请先选择数据集市"}</option>
									{subjectDomainOptions.map((item) => (
										<option key={item.id} value={item.id}>
											{item.name} · {item.code}
										</option>
									))}
									{missingPersistedSubjectDomain ? (
										<option disabled value={draft.subjectDomainId}>
											已失效主题域 · {draft.subjectDomainId}
										</option>
									) : null}
								</select>
								<ValidationMessage message={validationErrors.subjectDomainId} />
							</label>
						</>
					) : null}
					<label>
						<span>模型类型</span>
						<input disabled value={config.label} />
					</label>
					<label>
						<span className="required">数仓分层</span>
						<select
							onChange={(event) => patch({ warehouseLayerCode: event.target.value })}
							value={draft.warehouseLayerCode}
						>
							<option value="">请选择数仓分层</option>
							{context.warehouseLayers
								.filter((layer) => layer.systemLayerCode === config.layer)
								.map((layer) => (
									<option key={layer.code} value={layer.code}>
										{layer.name}（{layer.code}）{layer.builtin ? " · 系统" : " · 自定义"}
									</option>
								))}
							{draft.base && !context.warehouseLayers.some((layer) => layer.code === draft.warehouseLayerCode) ? (
								<option disabled value={draft.warehouseLayerCode}>
									已删除分层 · {draft.warehouseLayerCode}
								</option>
							) : null}
						</select>
					</label>
					<label>
						<span className="required">模型名称</span>
						<input onChange={(event) => patch({ name: event.target.value })} value={draft.name} />
					</label>
					<label>
						<span>物理表名</span>
						<input
							aria-label="物理表名"
							onChange={(event) => patch({ physicalName: event.target.value })}
							value={draft.physicalName}
						/>
						<ValidationMessage message={validationErrors.physicalName} />
					</label>
					<label className="dmx-workbench-editor__wide-field">
						<span>业务定义</span>
						<textarea onChange={(event) => patch({ description: event.target.value })} value={draft.description} />
					</label>
					<label className="dmx-workbench-editor__wide-field">
						<span className="required">模型粒度</span>
						<input onChange={(event) => patch({ grainStatement: event.target.value })} value={draft.grainStatement} />
						<ValidationMessage message={validationErrors.grainStatement} />
					</label>
					<label>
						<span>物化方式</span>
						<select onChange={(event) => patch({ materialization: event.target.value })} value={draft.materialization}>
							<option value="table">{materializationLabel("table")}</option>
							<option value="incremental">{materializationLabel("incremental")}</option>
							<option value="view">{materializationLabel("view")}</option>
							<option value="ephemeral">{materializationLabel("ephemeral")}</option>
						</select>
					</label>
					<label>
						<span>加载策略</span>
						<select
							onChange={(event) => patch({ loadStrategy: event.target.value as ModelSpecDraft["loadStrategy"] })}
							value={draft.loadStrategy}
						>
							<option value="FULL">全量</option>
							<option value="INCREMENTAL">增量</option>
							<option value="SNAPSHOT">快照</option>
						</select>
					</label>
					<label className="dmx-workbench-editor__wide-field">
						<span>分区字段</span>
						<input
							aria-label="分区字段"
							onChange={(event) => patch({ partitionFields: event.target.value })}
							placeholder="可留空；多个字段用逗号分隔"
							value={draft.partitionFields}
						/>
						<ValidationMessage message={validationErrors.partitionFields} />
					</label>
				</div>
			</section>
			<ModelImplementationBindingFields
				context={context}
				draft={draft}
				onChange={onChange}
				onSourcesChanged={props.onSourcesChanged}
				validationErrors={validationErrors}
			/>
			<FieldsPanel {...props} dimensionMode={false} />
		</>
	);
}

const authoringOriginLabel = (context: ModelAuthoringContext | null) => {
	switch (context?.provenance.origin) {
		case "SYSTEM_GENERATED":
			return "平台生成";
		case "MANUAL_CODE":
			return "手工代码";
		case "DBT_ZIP_IMPORT":
			return "dbt ZIP 导入";
		default:
			return "历史模型";
	}
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
	const validationBlocked = Boolean(authoringValidation?.modelIssues.length);

	return (
		<div className="dmx-workbench-editor">
			{selectedModel ? (
				<div className="dmx-model-context">
					<span>模型 r{authoringContext?.model.revision || selectedModel.revision}</span>
					<span title={authoringContext?.model.checksum || selectedModel.checksum}>
						模型校验和 {(authoringContext?.model.checksum || selectedModel.checksum)?.slice(0, 12) || "—"}
					</span>
					<span>
						实现{" "}
						{authoringContext?.implementation?.implementationRevision
							? `r${authoringContext.implementation.implementationRevision}`
							: "尚无"}
					</span>
					<span title={authoringContext?.implementation?.implementationChecksum || undefined}>
						实现校验和 {authoringContext?.implementation?.implementationChecksum?.slice(0, 12) || "—"}
					</span>
					<span>来源 {authoringOriginLabel(authoringContext)}</span>
					<span>投影 {projection?.coverage || (authoringBusy === "load" ? "读取中" : "UNKNOWN")}</span>
					<span>原始代码节点 {projection?.rawNodes.length || 0}</span>
					<ModelServingSyncStatus canMaintain={canMaintain} modelSpecId={selectedModel.id} />
				</div>
			) : null}
			{authoringFailure ? <div className="dmx-capability-note">{authoringFailure}</div> : null}
			{authoringConflict ? (
				<div className="dmx-inline-error" role="alert">
					草稿版本已变化，请刷新后继续，平台不会自动覆盖他人修改。
				</div>
			) : null}
			{selectedModel ? (
				<ModelMaterializationStatusCard
					canMaintain={canMaintain}
					currentImplementationRevision={authoringContext?.implementation?.implementationRevision}
					key={`${selectedModel.id}:${materializationRefreshKey || 0}`}
					model={selectedModel}
					onOpen={() => onDialog("publish")}
				/>
			) : null}
			{editorAccessMessage ? <output className="dmx-editor-access-note">{editorAccessMessage}</output> : null}
			{selectedModel ? (
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
			<div className="dmx-editor-toolbar" role="toolbar">
				{published ? (
					<Button disabled={!canMaintain || busy} onClick={onForkPublished} primary>
						{authoringBusy === "create" ? "创建中…" : "创建新草稿版本"}
					</Button>
				) : (
					<Button
						disabled={!canMaintain || effectiveReadOnly || busy || !dirty}
						onClick={onSave}
						primary
						title={!dirty ? "当前没有待保存变更" : conceptDimension ? "保存维度草稿" : "保存模型创作草稿"}
					>
						{saving || authoringBusy === "save" ? "保存中…" : conceptDimension || !persisted ? "保存" : "保存草稿"}
					</Button>
				)}
				{conceptDimension && draft.definitionBase?.status === "DRAFT" ? (
					<Button
						disabled={!canMaintain || effectiveReadOnly || busy}
						onClick={onConfirmDimension}
						title="确认后，该定义将成为维度表可绑定的当前定义"
					>
						确认定义
					</Button>
				) : null}
				{!conceptDimension && persisted && !published ? (
					<>
						<Button disabled={!canMaintain || busy} onClick={onValidateAuthoring}>
							{authoringBusy === "validate" ? "校验中…" : "校验"}
						</Button>
						<Button
							disabled={
								!canMaintain || busy || dirty || validationBlocked || !authoringValidation?.implementationValidation
							}
							onClick={onCommitAuthoring}
						>
							{authoringBusy === "commit" ? "提交中…" : "提交实现"}
						</Button>
					</>
				) : null}
				{conceptDimension ? null : (
					<>
						<Button disabled={busy || !persisted} onClick={() => onDialog("gates")}>
							交付检查
						</Button>
						<Button disabled={busy} onClick={onRefresh}>
							刷新
						</Button>
						<Button disabled={busy || !persisted} onClick={() => onDialog("association")}>
							关联关系
						</Button>
						<Button disabled={busy || !persisted || !canMaintain} onClick={() => onDialog("publish")}>
							发布
						</Button>
						<Button disabled={busy || !persisted} onClick={() => onDialog("logs")}>
							日志
						</Button>
						<Button disabled={busy || !persisted} onClick={() => onDialog("quality")}>
							质量约束
						</Button>
						<Button disabled title="尚无模型导出服务端契约">
							导出
						</Button>
					</>
				)}
			</div>
			{failureMessage ? (
				<div className="dmx-inline-error" role="alert">
					{failureMessage}
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
			{projection?.rawNodes.length ? (
				<section className="dmx-dbt-diagnostics" aria-label="原始代码节点">
					<h3>原始代码节点</h3>
					{projection.rawNodes.map((node) => (
						<div key={node.nodeId}>
							<b>{node.sourcePath || node.nodeId}</b>
							<span>{node.kind}</span>
							<p>该实现片段无法安全转换成结构化表单，原始代码保持不变。</p>
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
				) : isDimensionTableDraft(draft) ? (
					<DimensionDraftForm {...props} draft={draft} />
				) : (
					<CompatibilityDraftForm {...props} draft={draft} />
				)}
			</fieldset>
		</div>
	);
}
