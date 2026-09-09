import { useState } from "react";
import type { WarehousePlanSourceBindingView } from "@/api/warehousePlanApi";
import type {
	ModelImplementationCapabilities,
	ModelImplementationInputMode,
} from "@/features/modeling/contracts/modelImplementationContract";
import {
	isModelSpecUpstreamAllowed,
	type ModelSpecFactShape,
	type ModelSpecSourceRef,
	type ModelSpecTimeSemanticsType,
} from "@/features/modeling/contracts/modelSpecV2Contract";
import { ModelSourceInventoryDialog } from "./ModelSourceInventoryDialog";
import { ModelUpstreamSelector } from "./ModelUpstreamSelector";
import { ModelVisualTransformationFields } from "./ModelVisualTransformationFields";
import { Button } from "./PrototypePrimitives";
import { reconcileModelInputIdentity } from "./services/modelInputIdentity";
import {
	applyModelDraftFieldPatch,
	MODEL_KIND_CONFIG,
	type ModelDraftValidationErrors,
	type ModelSpecDraft,
	type ModelWorkbenchContext,
	modelSourceRefFromBinding,
} from "./services/modelWorkbenchService";

type Props = {
	definitionOnly?: boolean;
	implementationOnly?: boolean;
	draft: ModelSpecDraft;
	context: ModelWorkbenchContext;
	validationErrors: ModelDraftValidationErrors;
	onChange: (draft: ModelSpecDraft) => void;
	onSourcesChanged: (sources: WarehousePlanSourceBindingView[], planId: string) => void;
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

const INPUT_MODE_LABELS: Record<ModelImplementationInputMode, string> = {
	PHYSICAL_ASSET: "直接选择输入源表",
	UPSTREAM_MODEL: "引用已有上游模型",
	GENERATED: "按模型生成",
};

const modeOptions = (
	draft: ModelSpecDraft,
	capabilities: ModelImplementationCapabilities,
): Array<{ value: ModelImplementationInputMode; label: string }> =>
	(capabilities.inputModesByModelType[MODEL_KIND_CONFIG[draft.createKind].modelType] || []).map((value) => ({
		value,
		label: INPUT_MODE_LABELS[value],
	}));

const normalizeSourceOrder = (sources: ModelSpecSourceRef[]): ModelSpecSourceRef[] =>
	sources.map((source, index) => ({
		...source,
		role: index === 0 ? "PRIMARY" : "JOINED",
		sortOrder: index,
	}));

export const applyFactTimeFieldSelection = (
	draft: ModelSpecDraft,
	fieldIndex: number,
	checked: boolean,
): ModelSpecDraft => {
	const field = draft.fields[fieldIndex];
	if (!field?.name.trim()) return draft;
	const nextDraft = checked ? applyModelDraftFieldPatch(draft, fieldIndex, { role: "TIME" }) : draft;
	return {
		...nextDraft,
		timeSemanticsFields: checked
			? Array.from(new Set([...nextDraft.timeSemanticsFields, field.name]))
			: nextDraft.timeSemanticsFields.filter((item) => item !== field.name),
	};
};

function ValidationMessage({ message }: { message?: string }) {
	return message ? (
		<small className="dmx-workbench-editor__validation" role="alert">
			{message}
		</small>
	) : null;
}

export function ModelImplementationBindingFields({
	definitionOnly = false,
	implementationOnly = false,
	draft,
	context,
	validationErrors,
	onChange,
	onSourcesChanged,
}: Props) {
	const [sourceDialogOpen, setSourceDialogOpen] = useState(false);
	const patch = (next: Partial<ModelSpecDraft>) => onChange(reconcileModelInputIdentity(draft, { ...draft, ...next }));
	const targetType = MODEL_KIND_CONFIG[draft.createKind].modelType;
	const modes = modeOptions(draft, context.implementationCapabilities);
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
	const staleDimensionRefs = draft.dimensionRefs.filter(
		(dimension) =>
			!dimensionCandidates.some(
				(candidate) => candidate.id === dimension.modelSpecId && candidate.revision === dimension.revision,
			),
	);
	const timeFieldCandidates = draft.fields
		.map((field, fieldIndex) => ({ field, fieldIndex }))
		.filter(({ field }) => Boolean(field.name.trim()));

	const changeMode = (mode: ModelImplementationInputMode | "") => {
		patch({
			implementationInputMode: mode,
			generationStrategyType:
				mode === "GENERATED" ? (draft.createKind === "dimension-table" ? "DATE_DIMENSION" : "SCHEMA_ONLY") : "",
			sourceRefs: mode === "PHYSICAL_ASSET" ? draft.sourceRefs : [],
			dependsOn: mode === "UPSTREAM_MODEL" || mode === "GENERATED" ? draft.dependsOn : [],
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

	const toggleTimeField = (fieldIndex: number, checked: boolean) =>
		onChange(applyFactTimeFieldSelection(draft, fieldIndex, checked));

	if (definitionOnly && draft.createKind !== "fact" && draft.createKind !== "application") return null;
	return (
		<section className="dmx-editor-panel">
			<h3>{definitionOnly ? "业务语义" : "数据来源与加工方式"}</h3>
			<div className="dmx-workbench-editor__basic-grid">
				{!definitionOnly ? (
					<>
						<label>
							<span className="required">数据来源方式</span>
							<select
								aria-label="数据来源方式"
								disabled={modes.length === 1}
								onChange={(event) => changeMode(event.target.value as ModelImplementationInputMode | "")}
								value={draft.implementationInputMode}
							>
								<option value="">请选择数据来源方式</option>
								{modes.map((mode) => (
									<option key={mode.value} value={mode.value}>
										{mode.label}
									</option>
								))}
							</select>
							<ValidationMessage message={validationErrors.implementationInputMode} />
						</label>

						{draft.implementationInputMode === "GENERATED" ? (
							<label>
								<span>生成策略</span>
								<select
									aria-label="生成策略"
									value={draft.generationStrategyType}
									onChange={(event) =>
										patch({ generationStrategyType: event.target.value as ModelSpecDraft["generationStrategyType"] })
									}
								>
									<option value="">请选择生成策略</option>
									<option value="SCHEMA_ONLY">仅创建表结构（空表）</option>
									{draft.createKind === "dimension-table" ? (
										<option value="DATE_DIMENSION">生成标准日期数据</option>
									) : null}
								</select>
								{draft.generationStrategyType === "SCHEMA_ONLY" ? (
									<small>按当前字段、类型和主键创建空表，不读取数据；目标表已存在时会停止。</small>
								) : null}
							</label>
						) : null}

						{draft.implementationInputMode === "PHYSICAL_ASSET" ? (
							<div className="dmx-workbench-editor__wide-field dmx-implementation-binding-list">
								<div className="dmx-source-inventory__heading">
									<strong>输入源表</strong>
									<Button onClick={() => setSourceDialogOpen(true)}>从资产目录登记源表</Button>
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
											<small>已确认输入源表 · 版本 {source.resolvedVersion || source.confirmedVersion}</small>
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
										<small>已绑定输入源表，当前规划中不可用；请取消后重新选择。</small>
									</label>
								))}
								{!context.sources.length && !staleSources.length ? (
									<small>
										{context.planId
											? "当前暂无已确认且有效的输入源表，请从资产目录登记。"
											: "当前尚未建立建模上下文，请从资产目录登记第一张输入源表。"}
									</small>
								) : null}
							</div>
						) : null}

						{draft.implementationInputMode === "UPSTREAM_MODEL" ? (
							<ModelUpstreamSelector draft={draft} candidates={upstreamCandidates} onChange={onChange} />
						) : null}
					</>
				) : null}

				{!implementationOnly && draft.createKind === "fact" ? (
					<div className="dmx-workbench-editor__wide-field dmx-implementation-binding-list">
						<strong>引用维度模型</strong>
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
								<small>DWD · 第 {model.revision} 版</small>
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
								<small>已绑定第 {dimension.revision} 版，当前不可用；请取消后重新选择。</small>
							</label>
						))}
						{!dimensionCandidates.length && !staleDimensionRefs.length ? (
							<small>当前数据域暂无可引用的维度模型。</small>
						) : null}
					</div>
				) : null}

				{!implementationOnly && draft.createKind === "fact" ? (
					<>
						<label>
							<span>事实类型（可选）</span>
							<select
								aria-label="事实类型"
								onChange={(event) => patch({ factShape: event.target.value as ModelSpecFactShape })}
								value={draft.factShape}
							>
								<option value="">普通明细（不限定事实类型）</option>
								{FACT_SHAPES.map((shape) => (
									<option key={shape.value} value={shape.value}>
										{shape.label}
									</option>
								))}
							</select>
							<ValidationMessage message={validationErrors.factShape} />
						</label>
						<label>
							<span>时间语义（普通明细可不配置）</span>
							<select
								aria-label="时间语义"
								onChange={(event) =>
									patch({
										timeSemanticsType: event.target.value as ModelSpecTimeSemanticsType | "",
										timeSemanticsFields: event.target.value ? draft.timeSemanticsFields : [],
									})
								}
								value={draft.timeSemanticsType}
							>
								<option value="">不配置时间语义</option>
								{TIME_SEMANTICS.map((item) => (
									<option key={item.value} value={item.value}>
										{item.label}
									</option>
								))}
							</select>
						</label>
						<div className="dmx-workbench-editor__wide-field dmx-implementation-binding-list">
							<strong>时间字段</strong>
							{timeFieldCandidates.map(({ field, fieldIndex }) => (
								<label key={`${field.name}:${fieldIndex}`}>
									<input
										aria-label={`选择时间字段 ${field.name}`}
										checked={draft.timeSemanticsFields.includes(field.name)}
										onChange={(event) => toggleTimeField(fieldIndex, event.target.checked)}
										type="checkbox"
									/>
									<span>
										{field.displayName?.trim()
											? `${field.displayName}（${field.name} · ${field.dataType}）`
											: `${field.name} · ${field.dataType}`}
									</span>
								</label>
							))}
							{!timeFieldCandidates.length ? <small>请先在字段管理中新增字段。</small> : null}
							<ValidationMessage message={validationErrors.timeSemantics} />
						</div>
					</>
				) : null}

				{!implementationOnly && draft.createKind === "application" ? (
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
				{!definitionOnly ? (
					<ModelVisualTransformationFields
						context={context}
						draft={draft}
						onChange={onChange}
						validationMessage={validationErrors.transformations}
					/>
				) : null}
			</div>
			{!definitionOnly && sourceDialogOpen ? (
				<ModelSourceInventoryDialog
					onClose={() => setSourceDialogOpen(false)}
					onSourcesChanged={onSourcesChanged}
					planId={context.planId}
				/>
			) : null}
		</section>
	);
}
