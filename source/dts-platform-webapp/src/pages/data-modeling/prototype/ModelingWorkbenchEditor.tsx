import {
	CheckCircle2,
	FileDown,
	Import,
	Link2,
	ListChecks,
	RefreshCw,
	Save,
	Settings2,
	ShieldCheck,
	Upload,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { listBusinessProcessesApi, type Sprint64BusinessProcess } from "@/api/sprint64GovernanceApi";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelRepresentationView } from "@/features/modeling/contracts/modelRepresentationContract";
import type { ModelSpecField, ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { ModelFieldEditorTable } from "./ModelFieldEditorTable";
import { ModelImplementationBindingFields } from "./ModelImplementationBindingFields";
import { ModelMaterializationStatusCard } from "./ModelMaterializationStatus";
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

export type ModelingWorkbenchEditorProps = {
	draft: ModelDraft;
	context: ModelWorkbenchContext;
	dimensionDefinitions: DimensionDefinitionView[];
	dimensionDefinitionFailure: string;
	currentOwnerId: string;
	selectedModel: ModelSpecView | null;
	representation: ModelRepresentationView | null;
	representationFailure: string;
	canMaintain: boolean;
	readOnly: boolean;
	saving: boolean;
	dirty: boolean;
	validationErrors: ModelDraftValidationErrors;
	failureMessage: string;
	editorAccessMessage: string;
	fieldRowIds: string[];
	materializationRefreshKey?: number;
	onChange: (draft: ModelDraft) => void;
	onSave: () => void;
	onConfirmDimension?: () => void;
	onRefresh: () => void;
	onDialog: (dialog: Exclude<WorkbenchDialog, null>) => void;
	onAddFields: (count: number) => void;
	onRemoveBlankFields: () => void;
	onUpdateField: (index: number, patch: Partial<ModelSpecField>) => void;
	onDeleteField: (index: number) => void;
	onStandardChange: (index: number, value: string) => void;
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
				canOpenCode={Boolean(selectedModel)}
				dimensionMode={dimensionMode}
				fieldRowIds={fieldRowIds}
				fields={draft.fields}
				onAddFields={onAddFields}
				onDelete={onDeleteField}
				onOpenAssociation={() => onDialog("association")}
				onOpenCode={() => onDialog("advanced")}
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
		<>
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
		</>
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
							<option value="table">table</option>
							<option value="incremental">incremental</option>
							<option value="view">view</option>
							<option value="ephemeral">ephemeral</option>
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
						<input onChange={(event) => patch({ partitionFields: event.target.value })} value={draft.partitionFields} />
					</label>
				</div>
			</section>
			<ModelImplementationBindingFields
				context={context}
				draft={draft}
				onChange={onChange}
				validationErrors={validationErrors}
			/>
			<FieldsPanel {...props} dimensionMode={false} />
		</>
	);
}

export function ModelingWorkbenchEditor(props: ModelingWorkbenchEditorProps) {
	const {
		draft,
		selectedModel,
		representation,
		representationFailure,
		canMaintain,
		readOnly,
		saving,
		dirty,
		failureMessage,
		editorAccessMessage,
		onSave,
		onConfirmDimension,
		onRefresh,
		onDialog,
		materializationRefreshKey,
	} = props;
	const conceptDimension = isConceptDimensionDraft(draft);
	const persisted = Boolean(selectedModel);
	const canWritePersisted = persisted && canMaintain && !readOnly;

	return (
		<div className="dmx-workbench-editor">
			{selectedModel ? (
				<div className="dmx-model-context">
					<span>模型 r{selectedModel.revision}</span>
					<span title={selectedModel.checksum}>模型校验和 {selectedModel.checksum?.slice(0, 12) || "—"}</span>
					<span>
						实现 {representation?.implementationRevision ? `r${representation.implementationRevision}` : "尚无"}
					</span>
					<span title={representation?.implementationChecksum || undefined}>
						实现校验和 {representation?.implementationChecksum?.slice(0, 12) || "—"}
					</span>
					<span>所有权 {representation?.ownershipMode || selectedModel.implementationMode}</span>
					<span>可视化 {representation?.visualizationCapability || "读取中"}</span>
					<span>漂移 {representation?.driftStatus || "—"}</span>
				</div>
			) : null}
			{representationFailure ? <div className="dmx-capability-note">{representationFailure}</div> : null}
			{selectedModel ? (
				<ModelMaterializationStatusCard
					canMaintain={canMaintain}
					currentImplementationRevision={representation?.implementationRevision}
					key={`${selectedModel.id}:${materializationRefreshKey || 0}`}
					model={selectedModel}
					onOpen={() => onDialog("publish")}
				/>
			) : null}
			{editorAccessMessage ? <output className="dmx-editor-access-note">{editorAccessMessage}</output> : null}
			<div className="dmx-editor-toolbar" role="toolbar">
				<Button
					disabled={
						!canMaintain ||
						readOnly ||
						saving ||
						!dirty ||
						(conceptDimension && draft.definitionBase?.status != null && draft.definitionBase.status !== "DRAFT")
					}
					onClick={onSave}
					primary
					title={
						!canMaintain
							? "当前账号无建模维护权限"
							: conceptDimension && draft.definitionBase && draft.definitionBase.status !== "DRAFT"
								? "已确认或已退役的维度不能修改，请新建维度"
								: !dirty
									? "当前没有待保存变更"
									: conceptDimension
										? "保存维度草稿"
										: "保存模型草稿"
					}
				>
					<Save size={15} />
					{saving ? "保存中…" : "保存"}
				</Button>
				{conceptDimension && draft.definitionBase?.status === "DRAFT" ? (
					<Button
						disabled={!canMaintain || readOnly || saving}
						onClick={onConfirmDimension}
						title="确认后，该定义将成为维度表可绑定的当前定义"
					>
						<CheckCircle2 size={15} />
						确认定义
					</Button>
				) : null}
				{conceptDimension ? null : (
					<>
						<Button disabled={saving || !persisted} onClick={() => onDialog("gates")}>
							<ListChecks size={15} />
							提交
						</Button>
						<Button disabled={saving} onClick={onRefresh}>
							<RefreshCw size={15} />
							刷新
						</Button>
						<Button disabled={saving || !persisted} onClick={() => onDialog("association")}>
							<Link2 size={15} />
							关联关系
						</Button>
						<Button disabled={saving || !canWritePersisted} onClick={() => onDialog("publish")}>
							<Upload size={15} />
							发布
						</Button>
						<Button disabled={saving || !persisted} onClick={() => onDialog("logs")}>
							<FileDown size={15} />
							日志
						</Button>
						<Button disabled={saving || !persisted} onClick={() => onDialog("quality")}>
							<ShieldCheck size={15} />
							质量规则
						</Button>
						<Button disabled={saving || !canWritePersisted} onClick={() => onDialog("advanced")}>
							<Settings2 size={15} />
							模型开发
						</Button>
						<Button disabled title="尚无模型导出服务端契约">
							<Import size={15} />
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
			<fieldset
				className="dmx-editor-fieldset dmx-editor-scroll"
				disabled={
					readOnly ||
					saving ||
					(conceptDimension && draft.definitionBase?.status != null && draft.definitionBase.status !== "DRAFT")
				}
			>
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
