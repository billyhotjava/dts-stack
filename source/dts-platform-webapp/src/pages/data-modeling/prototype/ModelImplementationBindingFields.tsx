import { useState } from "react";
import type { WarehousePlanSourceBindingView } from "@/api/warehousePlanApi";
import type { ModelImplementationInputMode } from "@/features/modeling/contracts/modelImplementationContract";
import {
	isModelSpecUpstreamAllowed,
	type ModelSpecFactShape,
	type ModelSpecSourceRef,
	type ModelSpecTimeSemanticsType,
	type ModelSpecType,
} from "@/features/modeling/contracts/modelSpecV2Contract";
import { ModelSourceInventoryDialog } from "./ModelSourceInventoryDialog";
import { ModelVisualTransformationFields } from "./ModelVisualTransformationFields";
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

const MODEL_TYPE_LABELS: Record<ModelSpecType, string> = {
	DIMENSION: "维度表",
	FACT: "明细表",
	SUMMARY: "汇总表",
	APPLICATION: "应用表",
};

const modeOptions = (draft: ModelSpecDraft): Array<{ value: ModelImplementationInputMode; label: string }> => {
	if (draft.createKind === "dimension-table") {
		return [
			{ value: "PHYSICAL_ASSET", label: "直接选择输入源表" },
			{ value: "GENERATED", label: "系统生成标准日期维度" },
		];
	}
	if (draft.createKind === "fact") {
		return [
			{ value: "PHYSICAL_ASSET", label: "直接选择输入源表" },
			{ value: "UPSTREAM_MODEL", label: "引用已有上游模型" },
		];
	}
	return [{ value: "UPSTREAM_MODEL", label: "引用已有上游模型" }];
};

const sourceModeDescription = (draft: ModelSpecDraft): string => {
	if (draft.createKind === "dimension-table") {
		return "维度表可以从输入源表加工，日期维度也可以由系统生成。";
	}
	if (draft.createKind === "fact") {
		return "明细表可以直接读取 ODS 或源系统表，也可以基于已有明细模型继续加工。";
	}
	if (draft.createKind === "summary") {
		return "汇总表基于已治理的上游模型进行汇总，无需登记物理源表。";
	}
	return "应用表基于已治理的上游模型加工，无需登记物理源表。";
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
			generationStrategyType: mode === "GENERATED" ? "DATE_DIMENSION" : "",
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
			<h3>数据来源与加工方式</h3>
			<div className="dmx-workbench-editor__basic-grid">
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
					<small>{sourceModeDescription(draft)}</small>
				</label>

				{draft.implementationInputMode === "GENERATED" ? (
					<label>
						<span>生成策略</span>
						<input
							disabled
							value={draft.generationStrategyType === "DATE_DIMENSION" ? "系统生成标准日期维度" : "生成策略未配置"}
						/>
						<small>
							{draft.generationStrategyType === "DATE_DIMENSION"
								? "保存模型时由系统创建标准日期维度，后续可以继续调整并生成目标表。"
								: "请选择系统生成标准日期维度，或切换到代码模式维护原始实现。"}
						</small>
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
									{MODEL_TYPE_LABELS[model.modelType]} · {model.layer} · 第 {model.revision} 版
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
								<small>已绑定第 {dependency.revision} 版，当前不可作为上游；请重新选择。</small>
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
				<ModelVisualTransformationFields
					context={context}
					draft={draft}
					onChange={onChange}
					validationMessage={validationErrors.transformations}
				/>
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
