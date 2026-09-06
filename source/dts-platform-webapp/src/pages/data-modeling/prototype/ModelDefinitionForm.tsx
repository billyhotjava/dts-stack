import { useEffect, useMemo, useState } from "react";
import { listBusinessProcessesApi, type Sprint64BusinessProcess } from "@/api/sprint64GovernanceApi";
import { ModelDimensionHistoryFields } from "./ModelDimensionHistoryFields";
import { ModelFieldEditorTable } from "./ModelFieldEditorTable";
import { ModelImplementationBindingFields } from "./ModelImplementationBindingFields";
import { ModelImplementationExecutionFields } from "./ModelImplementationExecutionFields";
import type { ModelingWorkbenchEditorProps } from "./ModelingWorkbenchEditor";
import { isDimensionTableDraft, resolveDimensionFormPresentation } from "./modelWorkbenchPresentation";
import { MODEL_KIND_CONFIG, type ModelSpecDraft } from "./services/modelWorkbenchService";
import { resolveBusinessProcessBinding } from "./services/planningContextPolicyService";

type DraftFormProps = Pick<
	ModelingWorkbenchEditorProps,
	| "definitionOnly"
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
					{!props.definitionOnly ? (
						<ModelImplementationExecutionFields
							capabilities={context.implementationCapabilities}
							dimensionMode
							draft={draft}
							onChange={patch}
						/>
					) : null}
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
					{!props.definitionOnly ? (
						<label>
							<span>表名规则</span>
							<input aria-label="表名规则" disabled value={presentation.tableNamingRule} />
						</label>
					) : null}
					{!props.definitionOnly ? (
						<label>
							<span className="required">产出表英文名</span>
							<input
								aria-label="产出表英文名"
								onChange={(event) => patch({ physicalName: event.target.value })}
								value={draft.physicalName}
							/>
							<ValidationMessage message={validationErrors.physicalName} />
							{draft.base && !draft.physicalName.trim() ? (
								<small>历史草稿尚未保存产出表英文名，请补录后保存。</small>
							) : null}
						</label>
					) : null}
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
			<FieldsPanel {...props} dimensionMode />
			{!props.definitionOnly ? <ModelDimensionHistoryFields draft={draft} onChange={patch} /> : null}
			<ModelImplementationBindingFields
				definitionOnly={props.definitionOnly}
				context={context}
				draft={draft}
				onChange={onChange}
				onSourcesChanged={props.onSourcesChanged}
				validationErrors={validationErrors}
			/>
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
						<input aria-label="模型名称" onChange={(event) => patch({ name: event.target.value })} value={draft.name} />
						<ValidationMessage message={validationErrors.name} />
					</label>
					{!props.definitionOnly ? (
						<label>
							<span className="required">产出表英文名</span>
							<input
								aria-label="产出表英文名"
								onChange={(event) => patch({ physicalName: event.target.value })}
								value={draft.physicalName}
							/>
							<ValidationMessage message={validationErrors.physicalName} />
						</label>
					) : null}
					<label className="dmx-workbench-editor__wide-field">
						<span>业务定义</span>
						<textarea onChange={(event) => patch({ description: event.target.value })} value={draft.description} />
					</label>
					<label className="dmx-workbench-editor__wide-field">
						<span className="required">模型粒度</span>
						<input onChange={(event) => patch({ grainStatement: event.target.value })} value={draft.grainStatement} />
						<ValidationMessage message={validationErrors.grainStatement} />
					</label>
					{!props.definitionOnly ? (
						<ModelImplementationExecutionFields
							capabilities={context.implementationCapabilities}
							draft={draft}
							onChange={patch}
							partitionError={validationErrors.partitionFields}
						/>
					) : null}
				</div>
			</section>
			<FieldsPanel {...props} dimensionMode={false} />
			<ModelImplementationBindingFields
				definitionOnly={props.definitionOnly}
				context={context}
				draft={draft}
				onChange={onChange}
				onSourcesChanged={props.onSourcesChanged}
				validationErrors={validationErrors}
			/>
		</>
	);
}

export function ModelDefinitionForm(props: DraftFormProps & { draft: ModelSpecDraft }) {
	return isDimensionTableDraft(props.draft) ? <DimensionDraftForm {...props} /> : <CompatibilityDraftForm {...props} />;
}
