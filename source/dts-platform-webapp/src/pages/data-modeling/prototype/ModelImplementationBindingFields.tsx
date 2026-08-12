import { useState } from "react";
import type { WarehousePlanSourceBindingView } from "@/api/warehousePlanApi";
import type { ModelImplementationInputMode } from "@/features/modeling/contracts/modelImplementationContract";
import {
	isModelSpecUpstreamAllowed,
	type ModelSpecFactShape,
	type ModelSpecSourceRef,
	type ModelSpecTimeSemanticsType,
} from "@/features/modeling/contracts/modelSpecV2Contract";
import { ModelSourceInventoryDialog } from "./ModelSourceInventoryDialog";
import { Button } from "./PrototypePrimitives";
import {
	MODEL_KIND_CONFIG,
	type ModelDraftValidationErrors,
	type ModelSpecDraft,
	type ModelWorkbenchContext,
	modelSourceRefFromBinding,
} from "./services/modelWorkbenchService";

type Props = {
	draft: ModelSpecDraft;
	context: ModelWorkbenchContext;
	validationErrors: ModelDraftValidationErrors;
	onChange: (draft: ModelSpecDraft) => void;
	onSourcesChanged: (sources: WarehousePlanSourceBindingView[]) => void;
};

const FACT_SHAPES: Array<{ value: ModelSpecFactShape; label: string }> = [
	{ value: "TRANSACTION", label: "事务事实" },
	{ value: "PERIODIC_SNAPSHOT", label: "周期快照" },
	{ value: "ACCUMULATING_SNAPSHOT", label: "累积快照" },
];

const TIME_SEMANTICS: Array<{ value: ModelSpecTimeSemanticsType; label: string }> = [
	{ value: "EVENT_TIME", label: "事件时间" },
	{ value: "SNAPSHOT_DATE", label: "快照日期" },
	{ value: "PERIOD", label: "统计周期" },
	{ value: "MILESTONE_DATES", label: "里程碑日期" },
];

const modeOptions = (draft: ModelSpecDraft): Array<{ value: ModelImplementationInputMode; label: string }> => {
	if (draft.createKind === "dimension-table") {
		return [
			{ value: "PHYSICAL_ASSET", label: "关联数仓来源" },
			{
				value: "GENERATED",
				label: draft.implementationMode === "DBT_MANAGED" ? "无上游（手工 SQL 生成）" : "受控日期维度生成器",
			},
		];
	}
	if (draft.createKind === "fact") {
		return [
			{ value: "PHYSICAL_ASSET", label: "关联数仓来源" },
			{ value: "UPSTREAM_MODEL", label: "关联上游模型" },
		];
	}
	return [{ value: "UPSTREAM_MODEL", label: "关联上游模型" }];
};

const normalizeSourceOrder = (sources: ModelSpecSourceRef[]): ModelSpecSourceRef[] =>
	sources.map((source, index) => ({
		...source,
		role: index === 0 ? "PRIMARY" : "JOINED",
		sortOrder: index,
	}));

function ValidationMessage({ message }: { message?: string }) {
	return message ? (
		<small className="dmx-workbench-editor__validation" role="alert">
			{message}
		</small>
	) : null;
}

export function ModelImplementationBindingFields({
	draft,
	context,
	validationErrors,
	onChange,
	onSourcesChanged,
}: Props) {
	const [sourceDialogOpen, setSourceDialogOpen] = useState(false);
	const patch = (next: Partial<ModelSpecDraft>) => onChange({ ...draft, ...next });
	const dbtManaged = draft.implementationMode === "DBT_MANAGED";
	const modes = modeOptions(draft);
	const targetType = MODEL_KIND_CONFIG[draft.createKind].modelType;
	const upstreamCandidates = context.models.filter(
		(model) =>
			model.id !== draft.base?.id &&
			model.compatibilityMode === "CANONICAL" &&
			isModelSpecUpstreamAllowed(targetType, model),
	);
	const dimensionCandidates = context.models.filter(
		(model) =>
			model.id !== draft.base?.id &&
			model.compatibilityMode === "CANONICAL" &&
			model.modelType === "DIMENSION" &&
			model.layer === "DWD" &&
			model.domainId === draft.domainId,
	);
	const staleSources = draft.sourceRefs.filter(
		(source) =>
			!context.sources.some(
				(candidate) =>
					candidate.bindingId === source.sourceBindingId &&
					(candidate.resolvedVersion || candidate.confirmedVersion) === source.resolvedVersion,
			),
	);
	const staleDependencies = draft.dependsOn.filter(
		(dependency) =>
			!upstreamCandidates.some(
				(candidate) => candidate.id === dependency.modelSpecId && candidate.revision === dependency.revision,
			),
	);
	const staleDimensionRefs = draft.dimensionRefs.filter(
		(dimension) =>
			!dimensionCandidates.some(
				(candidate) => candidate.id === dimension.modelSpecId && candidate.revision === dimension.revision,
			),
	);
	const timeFieldNames = Array.from(
		new Set([
			...draft.fields.filter((field) => field.role === "TIME").map((field) => field.name),
			...draft.timeSemanticsFields,
		]),
	).filter(Boolean);

	const changeMode = (mode: ModelImplementationInputMode | "") => {
		patch({
			implementationInputMode: mode,
			generationStrategyType: mode === "GENERATED" && !dbtManaged ? "DATE_DIMENSION" : "",
			sourceRefs: mode === "PHYSICAL_ASSET" ? draft.sourceRefs : [],
			dependsOn: mode === "UPSTREAM_MODEL" ? draft.dependsOn : [],
		});
	};

	const toggleSource = (sourceBindingId: string, checked: boolean) => {
		if (!checked) {
			patch({
				sourceRefs: normalizeSourceOrder(draft.sourceRefs.filter((item) => item.sourceBindingId !== sourceBindingId)),
			});
			return;
		}
		const binding = context.sources.find((item) => item.bindingId === sourceBindingId);
		const source = binding ? modelSourceRefFromBinding(binding, draft.sourceRefs.length) : null;
		if (!source) return;
		patch({
			sourceRefs: normalizeSourceOrder([
				...draft.sourceRefs.filter((item) => item.sourceBindingId !== sourceBindingId),
				source,
			]),
		});
	};

	const toggleUpstream = (modelSpecId: string, checked: boolean) => {
		if (!checked) {
			patch({ dependsOn: draft.dependsOn.filter((item) => item.modelSpecId !== modelSpecId) });
			return;
		}
		const model = upstreamCandidates.find((item) => item.id === modelSpecId);
		if (!model) return;
		patch({
			dependsOn: [
				...draft.dependsOn.filter((item) => item.modelSpecId !== modelSpecId),
				{ modelSpecId: model.id, revision: model.revision },
			],
		});
	};

	const toggleDimension = (modelSpecId: string, checked: boolean) => {
		if (!checked) {
			patch({ dimensionRefs: draft.dimensionRefs.filter((item) => item.modelSpecId !== modelSpecId) });
			return;
		}
		const model = dimensionCandidates.find((item) => item.id === modelSpecId);
		if (!model) return;
		patch({
			dimensionRefs: [
				...draft.dimensionRefs.filter((item) => item.modelSpecId !== modelSpecId),
				{ modelSpecId: model.id, revision: model.revision },
			],
		});
	};

	const toggleTimeField = (fieldName: string, checked: boolean) =>
		patch({
			timeSemanticsFields: checked
				? [...draft.timeSemanticsFields, fieldName]
				: draft.timeSemanticsFields.filter((item) => item !== fieldName),
		});

	return (
		<section className="dmx-editor-panel">
			<h3>实现绑定</h3>
			<div className="dmx-workbench-editor__basic-grid">
				<label>
					<span className="required">实现维护方式</span>
					<select
						aria-label="实现维护方式"
						disabled={Boolean(draft.base)}
						onChange={(event) => {
							const implementationMode = event.target.value as ModelSpecDraft["implementationMode"];
							patch({
								implementationMode,
								...(implementationMode === "DBT_MANAGED" && draft.implementationInputMode === "GENERATED"
									? { implementationInputMode: "PHYSICAL_ASSET", generationStrategyType: "" as const }
									: {}),
							});
						}}
						value={draft.implementationMode}
					>
						<option value="DESIGNER_GENERATED">可视化配置</option>
						<option value="DBT_MANAGED">手工 dbt SQL</option>
					</select>
					<small>
						{dbtManaged
							? "保存逻辑模型后，从“高级 dbt 工作区”创建并维护 dbt SQL；来源与上游关系仍在此处登记。"
							: "由平台根据来源、字段映射和目标配置生成实现。"}
					</small>
				</label>
				<label>
					<span className="required">{dbtManaged ? "模型来源关系" : "实现输入方式"}</span>
					<select
						aria-label="实现输入方式"
						disabled={modes.length === 1}
						onChange={(event) => changeMode(event.target.value as ModelImplementationInputMode | "")}
						value={draft.implementationInputMode}
					>
						<option value="">请选择实现输入方式</option>
						{modes.map((mode) => (
							<option key={mode.value} value={mode.value}>
								{mode.label}
							</option>
						))}
					</select>
					<ValidationMessage message={validationErrors.implementationInputMode} />
					{dbtManaged ? <small>这里登记可追溯的来源关系；SQL/Jinja 在高级 dbt 工作区维护。</small> : null}
				</label>

				{draft.implementationInputMode === "GENERATED" ? (
					<label>
						<span>生成策略</span>
						<input disabled value={dbtManaged ? "手工 dbt SQL" : "受控日期维度生成器"} />
						<small>
							{dbtManaged
								? "当前 SQL 不读取物理来源或上游模型；实现提交后仍由候选版本固定并审计。"
								: "保存模型时创建统一日期维度实现，可在后续版本继续物化。"}
						</small>
					</label>
				) : null}

				{draft.implementationInputMode === "PHYSICAL_ASSET" ? (
					<div className="dmx-workbench-editor__wide-field dmx-implementation-binding-list">
						<div className="dmx-source-inventory__heading">
							<strong>物理来源</strong>
							<Button disabled={!context.planId} onClick={() => setSourceDialogOpen(true)}>
								维护来源
							</Button>
						</div>
						{context.sources.map((source) => {
							const label =
								source.displayName?.trim() || source.locator?.objectName || source.sourceId || source.bindingId;
							return (
								<label key={source.bindingId}>
									<input
										aria-label={`选择来源 ${label}`}
										checked={draft.sourceRefs.some(
											(item) =>
												item.sourceBindingId === source.bindingId &&
												item.resolvedVersion === (source.resolvedVersion || source.confirmedVersion),
										)}
										onChange={(event) => toggleSource(source.bindingId, event.target.checked)}
										type="checkbox"
									/>
									<span>{label}</span>
									<small>
										{source.sourceType} · {source.resolvedVersion || source.confirmedVersion}
									</small>
								</label>
							);
						})}
						{staleSources.map((source) => (
							<label key={`stale-source:${source.sourceBindingId}:${source.resolvedVersion}`}>
								<input
									aria-label={`取消不可用来源 ${source.ref}`}
									checked
									onChange={(event) => toggleSource(source.sourceBindingId, event.target.checked)}
									type="checkbox"
								/>
								<span>{source.ref}</span>
								<small>已保存来源，当前规划中不可用；请取消后重新选择。</small>
							</label>
						))}
						{!context.sources.length && !staleSources.length ? (
							<small>当前规划暂无已确认且当前有效的来源，请先在数仓规划中确认来源。</small>
						) : null}
					</div>
				) : null}

				{draft.implementationInputMode === "UPSTREAM_MODEL" ? (
					<div className="dmx-workbench-editor__wide-field dmx-implementation-binding-list">
						<strong>上游模型</strong>
						{upstreamCandidates.map((model) => (
							<label key={model.id}>
								<input
									aria-label={`选择上游 ${model.name}`}
									checked={draft.dependsOn.some(
										(item) => item.modelSpecId === model.id && item.revision === model.revision,
									)}
									onChange={(event) => toggleUpstream(model.id, event.target.checked)}
									type="checkbox"
								/>
								<span>{model.name}</span>
								<small>
									{model.modelType} · {model.layer} · r{model.revision}
								</small>
							</label>
						))}
						{staleDependencies.map((dependency) => (
							<label key={`stale-upstream:${dependency.modelSpecId}:${dependency.revision}`}>
								<input
									aria-label={`取消不可用上游 ${dependency.modelSpecId}`}
									checked
									onChange={(event) => toggleUpstream(dependency.modelSpecId, event.target.checked)}
									type="checkbox"
								/>
								<span>{dependency.modelSpecId}</span>
								<small>已绑定 r{dependency.revision}，当前不可作为上游；请重新选择。</small>
							</label>
						))}
						{!upstreamCandidates.length && !staleDependencies.length ? (
							<small>当前暂无满足分层规则且已有当前修订的上游模型。</small>
						) : null}
					</div>
				) : null}

				{draft.createKind === "fact" ? (
					<div className="dmx-workbench-editor__wide-field dmx-implementation-binding-list">
						<strong>引用维度模型</strong>
						<small>固定当前维度模型修订，用于关系图、发布门禁和候选版本审计。</small>
						{dimensionCandidates.map((model) => (
							<label key={model.id}>
								<input
									aria-label={`引用维度模型 ${model.name}`}
									checked={draft.dimensionRefs.some(
										(item) => item.modelSpecId === model.id && item.revision === model.revision,
									)}
									onChange={(event) => toggleDimension(model.id, event.target.checked)}
									type="checkbox"
								/>
								<span>{model.name}</span>
								<small>DWD · r{model.revision}</small>
							</label>
						))}
						{staleDimensionRefs.map((dimension) => (
							<label key={`stale-dimension:${dimension.modelSpecId}:${dimension.revision}`}>
								<input
									aria-label={`取消不可用维度 ${dimension.modelSpecId}`}
									checked
									onChange={(event) => toggleDimension(dimension.modelSpecId, event.target.checked)}
									type="checkbox"
								/>
								<span>{dimension.modelSpecId}</span>
								<small>已绑定 r{dimension.revision}，当前不可用；请取消后重新选择。</small>
							</label>
						))}
						{!dimensionCandidates.length && !staleDimensionRefs.length ? (
							<small>当前数据域暂无可引用的维度模型。</small>
						) : null}
					</div>
				) : null}

				{draft.createKind === "fact" ? (
					<>
						<label>
							<span className="required">事实类型</span>
							<select
								aria-label="事实类型"
								onChange={(event) => patch({ factShape: event.target.value as ModelSpecFactShape })}
								value={draft.factShape}
							>
								<option value="">请选择事实类型</option>
								{FACT_SHAPES.map((shape) => (
									<option key={shape.value} value={shape.value}>
										{shape.label}
									</option>
								))}
							</select>
							<ValidationMessage message={validationErrors.factShape} />
						</label>
						<label>
							<span className="required">时间语义</span>
							<select
								aria-label="时间语义"
								onChange={(event) => patch({ timeSemanticsType: event.target.value as ModelSpecTimeSemanticsType })}
								value={draft.timeSemanticsType}
							>
								<option value="">请选择时间语义</option>
								{TIME_SEMANTICS.map((item) => (
									<option key={item.value} value={item.value}>
										{item.label}
									</option>
								))}
							</select>
						</label>
						<div className="dmx-workbench-editor__wide-field dmx-implementation-binding-list">
							<strong>时间字段</strong>
							{timeFieldNames.map((fieldName) => (
								<label key={fieldName}>
									<input
										aria-label={`选择时间字段 ${fieldName}`}
										checked={draft.timeSemanticsFields.includes(fieldName)}
										onChange={(event) => toggleTimeField(fieldName, event.target.checked)}
										type="checkbox"
									/>
									<span>{fieldName}</span>
								</label>
							))}
							{!timeFieldNames.length ? <small>请先在字段管理中新增字段，并将字段作用设置为“时间”。</small> : null}
							<ValidationMessage message={validationErrors.timeSemantics} />
						</div>
					</>
				) : null}

				{draft.createKind === "application" ? (
					<label className="dmx-workbench-editor__wide-field">
						<span className="required">应用场景</span>
						<textarea
							aria-label="应用场景"
							onChange={(event) => patch({ consumptionScenario: event.target.value })}
							value={draft.consumptionScenario}
						/>
						<ValidationMessage message={validationErrors.consumptionScenario} />
					</label>
				) : null}
			</div>
			{sourceDialogOpen ? (
				<ModelSourceInventoryDialog
					onClose={() => setSourceDialogOpen(false)}
					onSourcesChanged={onSourcesChanged}
					planId={context.planId}
				/>
			) : null}
		</section>
	);
}
