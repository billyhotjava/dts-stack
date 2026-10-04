import { useCallback, useEffect, useMemo, useState } from "react";
import { semanticApi } from "../../features/semantic/semanticApi";
import SemanticFieldExplorer from "../../features/semantic/SemanticFieldExplorer";
import SemanticModelCanvas from "../../features/semantic/SemanticModelCanvas";
import { buildSemanticJoinOptions } from "../../features/semantic/semanticCanvas.helpers";
import type {
	DerivedMetricDraft,
	DerivedMetricOperation,
	LoadState,
	QueryFilterDraft,
	SemanticModelMeta,
	SemanticQueryBody,
	SemanticQueryResponse,
	GraphDraftResponse,
	VisualAssetSummary,
	VisualAssetsResponse,
} from "../../features/semantic/semanticTypes";

const filterOps = ["=", "!=", ">", ">=", "<", "<=", "in", "like"];
const derivedMetricOps: Array<{ value: DerivedMetricOperation; label: string }> = [
	{ value: "sum", label: "SUM" },
	{ value: "count", label: "COUNT" },
	{ value: "count_distinct", label: "COUNT DISTINCT" },
	{ value: "avg", label: "AVG" },
	{ value: "count_if", label: "COUNT IF" },
	{ value: "sum_if", label: "SUM IF" },
	{ value: "ratio", label: "RATIO" },
	{ value: "case_when", label: "CASE WHEN" },
	{ value: "date_trunc", label: "DATE TRUNC" },
];
const timeGrains: NonNullable<DerivedMetricDraft["timeGrain"]>[] = ["day", "week", "month", "quarter", "year"];
const conditionOps: NonNullable<DerivedMetricDraft["conditionOp"]>[] = ["eq", "ne", "gt", "gte", "lt", "lte"];

function asArray<T = Record<string, unknown>>(value: unknown): T[] {
	return Array.isArray(value) ? (value as T[]) : [];
}

function readId(value: unknown): string {
	if (typeof value === "string") return value;
	if (typeof value === "number") return String(value);
	return "";
}

function readLabel(value: Record<string, unknown> | undefined, fallback: string): string {
	return String(value?.label ?? value?.display_name ?? value?.name ?? value?.id ?? fallback);
}

function toMessage(error: unknown): string {
	return error instanceof Error ? error.message : String(error);
}

function buildModelMap(models: SemanticModelMeta[]) {
	const entries: Array<[string, SemanticModelMeta]> = [];
	for (const model of models) {
		const id = readId(model.id);
		if (id) entries.push([id, model]);
	}
	return new Map<string, SemanticModelMeta>(entries);
}

function sameList(left: string[], right: string[]) {
	return left.length === right.length && left.every((value, index) => value === right[index]);
}

function fieldOptions(fields: string[] | undefined, kind: "metric" | "dimension") {
	return (fields ?? []).map((field) => ({
		id: field,
		label: field,
		type: kind,
	}));
}

function buildDerivedExpression(item: DerivedMetricDraft): string {
	const op = item.operation || "sum";
	const leftField = item.leftField?.trim();
	const conditionField = item.conditionField?.trim() || leftField;
	const conditionValue = item.conditionValue?.trim();
	const conditionOp = item.conditionOp || "eq";
	if (!leftField && op !== "case_when") return "";
	if (op === "ratio") {
		const rightField = item.rightField?.trim();
		return rightField ? `ratio(${leftField}, ${rightField})` : "";
	}
	if (op === "date_trunc") {
		return `date_trunc(${item.timeGrain || "day"}, ${leftField})`;
	}
	if (op === "count_if") {
		return conditionField && conditionValue ? `count_if(${conditionField}, ${conditionOp}, ${conditionValue})` : "";
	}
	if (op === "sum_if") {
		return leftField && conditionField && conditionValue ? `sum_if(${leftField}, ${conditionField}, ${conditionOp}, ${conditionValue})` : "";
	}
	if (op === "case_when") {
		const trueValue = item.trueValue?.trim() || "1";
		const falseValue = item.falseValue?.trim() || "0";
		return conditionField && conditionValue ? `case_when(${conditionField}, ${conditionOp}, ${conditionValue}, ${trueValue}, ${falseValue})` : "";
	}
	return `${op}(${leftField})`;
}

function assetToSemanticModel(asset: VisualAssetSummary): SemanticModelMeta {
	const tableName = asset.name || asset.assetKey;
	return {
		id: asset.assetId || asset.assetKey,
		label: tableName,
		subject_area: asset.domainCode || "未分域",
		security_level: asset.classification || "UNCLASSIFIED",
		grain: (asset.grain ?? []).join(" + ") || "未声明",
		warehouse_layer: asset.warehouseLayer,
		asset_key: asset.assetKey,
		governance_status: asset.governanceStatus,
		permission_decision: asset.permissionDecision,
		lineage_status: asset.lineageStatus,
		schema_name: asset.warehouseLayer?.toLowerCase(),
		table_name: tableName,
		description: asset.description || `${asset.warehouseLayer} 可视化资产，来源 ${asset.assetKey}`,
		metrics: fieldOptions(asset.metricColumns, "metric"),
		dimensions: fieldOptions([...(asset.dimensionColumns ?? []), ...(asset.timeColumns ?? [])], "dimension"),
		joins: [],
	};
}

function visualAssetsToSemanticMeta(value: VisualAssetsResponse) {
	return {
		spec_version: "sprint-35-visual-assets",
		generated_at: new Date().toISOString(),
		models: (value.data ?? []).map(assetToSemanticModel),
	};
}

function buildSemanticQuery({
	baseModelId,
	selectedJoinTargets,
	selectedMeasures,
	selectedDimensions,
	filters,
	derivedMetrics,
	limit,
	models,
}: {
	baseModelId: string;
	selectedJoinTargets: string[];
	selectedMeasures: string[];
	selectedDimensions: string[];
	filters: QueryFilterDraft[];
	derivedMetrics: DerivedMetricDraft[];
	limit: number;
	models: SemanticModelMeta[];
}): SemanticQueryBody {
	const optionMap = new Map(buildSemanticJoinOptions(models, baseModelId, selectedJoinTargets).map((item) => [item.targetId, item]));
	const modelMap = buildModelMap(models);
	const derivedMetricPayload = derivedMetrics
		.map((item) => ({ id: item.id, label: item.label.trim() || item.id, expression: buildDerivedExpression(item).trim() }))
		.filter((item) => item.expression);
	const graphNode = (modelId: string, role: "BASE" | "JOIN") => {
		const model = modelMap.get(modelId);
		return {
			id: modelId,
			role,
			warehouseLayer: model?.warehouse_layer,
			assetKey: model?.asset_key,
			grain: model?.grain ? [model.grain] : [],
			primaryKeys: [],
		};
	};
	return {
		base: baseModelId || undefined,
		nodes: [baseModelId ? graphNode(baseModelId, "BASE") : null, ...selectedJoinTargets.map((target) => graphNode(target, "JOIN"))].filter(
			Boolean,
		) as SemanticQueryBody["nodes"],
		joins: selectedJoinTargets.map((target) => ({
			to: target,
			via: optionMap.get(target)?.path,
			type: optionMap.get(target)?.joinType,
		})),
		measures: selectedMeasures,
		dimensions: selectedDimensions,
		filters: filters
			.filter((item) => item.field && item.op)
			.map((item) => ({
				field: item.field,
				op: item.op,
				value:
					item.op === "in"
						? item.value
								.split(",")
								.map((value) => value.trim())
								.filter(Boolean)
						: item.value,
			})),
		derived_metrics: derivedMetricPayload,
		limit,
		format: "json",
	};
}

export default function SemanticDesignerPage({ embedded }: { embedded: boolean }) {
	const [metaState, setMetaState] = useState<LoadState<ReturnType<typeof visualAssetsToSemanticMeta>>>({ state: "loading" });
	const [dwdState, setDwdState] = useState<LoadState<VisualAssetsResponse> | null>(null);
	const [previewState, setPreviewState] = useState<LoadState<SemanticQueryResponse> | null>(null);
	const [saveState, setSaveState] = useState<LoadState<GraphDraftResponse> | null>(null);
	const [dwdCandidateState, setDwdCandidateState] = useState<LoadState<GraphDraftResponse> | null>(null);
	const [baseModelId, setBaseModelId] = useState("");
	const [dwdAssetId, setDwdAssetId] = useState("");
	const [dwdGrain, setDwdGrain] = useState("");
	const [dwdStandardCode, setDwdStandardCode] = useState("");
	const [dwdMeasure, setDwdMeasure] = useState("");
	const [selectedJoinTargets, setSelectedJoinTargets] = useState<string[]>([]);
	const [selectedMeasures, setSelectedMeasures] = useState<string[]>([]);
	const [selectedDimensions, setSelectedDimensions] = useState<string[]>([]);
	const [filters, setFilters] = useState<QueryFilterDraft[]>([]);
	const [derivedMetrics, setDerivedMetrics] = useState<DerivedMetricDraft[]>([]);
	const [limit, setLimit] = useState(200);

	useEffect(() => {
		let cancelled = false;
		semanticApi
			.getVisualAssets({ layers: "DWS,ADS" })
			.then((value) => {
				if (cancelled) return;
				setMetaState({ state: "loaded", value: visualAssetsToSemanticMeta(value) });
			})
			.catch((error) => {
				if (cancelled) return;
				setMetaState({ state: "error", error });
			});
		return () => {
			cancelled = true;
		};
	}, []);

	useEffect(() => {
		let cancelled = false;
		setDwdState({ state: "loading" });
		semanticApi
			.getVisualAssets({ layers: "DWD", includeDrilldown: true })
			.then((value) => {
				if (cancelled) return;
				setDwdState({ state: "loaded", value });
			})
			.catch((error) => {
				if (cancelled) return;
				setDwdState({ state: "error", error });
			});
		return () => {
			cancelled = true;
		};
	}, []);

	const models = metaState.state === "loaded" ? (metaState.value.models ?? []) : [];
	const dwdAssets = dwdState?.state === "loaded" ? (dwdState.value.data ?? []) : [];
	const modelMap = useMemo(() => buildModelMap(models), [models]);
	const baseModel = modelMap.get(baseModelId);
	const selectedDwdAsset = useMemo(
		() => dwdAssets.find((asset) => (asset.assetId || asset.assetKey) === dwdAssetId),
		[dwdAssetId, dwdAssets],
	);
	const dwdGrainOptions = useMemo(
		() => Array.from(new Set([...(selectedDwdAsset?.grain ?? []), ...(selectedDwdAsset?.primaryKeys ?? [])])).filter(Boolean),
		[selectedDwdAsset],
	);
	const dwdMeasureOptions = selectedDwdAsset?.metricColumns ?? [];
	const selectedModelIds = useMemo(() => [baseModelId, ...selectedJoinTargets].filter(Boolean), [baseModelId, selectedJoinTargets]);
	const availableModels = useMemo(
		() => models.filter((model) => selectedModelIds.includes(readId(model.id))),
		[models, selectedModelIds],
	);
	const metricOptions = useMemo(
		() =>
			availableModels.flatMap((model) =>
				asArray<Record<string, unknown>>(model.metrics).map((metric) => ({
					value: readId(metric.id),
					label: `${readLabel(model as Record<string, unknown>, readId(model.id))} / ${readLabel(metric, readId(metric.id))}`,
				})),
			),
		[availableModels],
	);
	const dimensionOptions = useMemo(
		() =>
			availableModels.flatMap((model) =>
				asArray<Record<string, unknown>>(model.dimensions).map((dimension) => ({
					value: readId(dimension.id),
					label: `${readLabel(model as Record<string, unknown>, readId(model.id))} / ${readLabel(dimension, readId(dimension.id))}`,
				})),
			),
		[availableModels],
	);
	const derivedFieldOptions = useMemo(() => [...metricOptions, ...dimensionOptions], [dimensionOptions, metricOptions]);
	const currentQuery = buildSemanticQuery({
		baseModelId,
		selectedJoinTargets,
		selectedMeasures,
		selectedDimensions,
		filters,
		derivedMetrics,
		limit,
		models,
	});
	const previewText =
		previewState?.state === "loaded" ? String(previewState.value.meta?.sql_preview ?? "") : JSON.stringify(currentQuery, null, 2);

	useEffect(() => {
		if (baseModelId || !models[0]?.id) return;
		setBaseModelId(readId(models[0].id));
	}, [baseModelId, models]);

	useEffect(() => {
		if (dwdAssetId || dwdAssets.length === 0) return;
		setDwdAssetId(dwdAssets[0]?.assetId || dwdAssets[0]?.assetKey || "");
	}, [dwdAssetId, dwdAssets]);

	useEffect(() => {
		if (!selectedDwdAsset) return;
		setDwdGrain((current) => current || selectedDwdAsset.grain?.[0] || selectedDwdAsset.primaryKeys?.[0] || "");
		setDwdMeasure((current) => current || selectedDwdAsset.metricColumns?.[0] || "");
		setDwdStandardCode((current) => current || selectedDwdAsset.standardCodes?.[0] || "");
	}, [selectedDwdAsset]);

	useEffect(() => {
		const validJoins = new Set(buildSemanticJoinOptions(models, baseModelId, selectedJoinTargets).map((item) => item.targetId));
		setSelectedJoinTargets((current) => {
			const next = current.filter((target) => validJoins.has(target));
			return sameList(current, next) ? current : next;
		});
	}, [baseModelId, models, selectedJoinTargets]);

	useEffect(() => {
		const metricSet = new Set(metricOptions.map((item) => item.value));
		const dimensionSet = new Set(dimensionOptions.map((item) => item.value));
		setSelectedMeasures((current) => {
			const next = current.filter((item) => metricSet.has(item));
			return sameList(current, next) ? current : next;
		});
		setSelectedDimensions((current) => {
			const next = current.filter((item) => dimensionSet.has(item));
			return sameList(current, next) ? current : next;
		});
	}, [dimensionOptions, metricOptions]);

	const toggleJoinTarget = useCallback((targetId: string) => {
		setSelectedJoinTargets((current) => (current.includes(targetId) ? current.filter((item) => item !== targetId) : [...current, targetId]));
	}, []);

	const toggleMeasure = useCallback((measureId: string) => {
		setSelectedMeasures((current) => (current.includes(measureId) ? current.filter((item) => item !== measureId) : [...current, measureId]));
	}, []);

	const toggleDimension = useCallback((dimensionId: string) => {
		setSelectedDimensions((current) =>
			current.includes(dimensionId) ? current.filter((item) => item !== dimensionId) : [...current, dimensionId],
		);
	}, []);
	const updateDerivedMetric = useCallback((index: number, patch: Partial<DerivedMetricDraft>) => {
		setDerivedMetrics((current) => current.map((row, rowIndex) => (rowIndex === index ? { ...row, ...patch } : row)));
	}, []);
	const addDerivedMetric = useCallback(() => {
		const primaryField = metricOptions[0]?.value || dimensionOptions[0]?.value || "";
		const secondaryField = metricOptions[1]?.value || metricOptions[0]?.value || "";
		setDerivedMetrics((current) => [
			...current,
			{
				id: `derived_${current.length + 1}`,
				label: `派生指标 ${current.length + 1}`,
				operation: "sum",
				leftField: primaryField,
				rightField: secondaryField,
				conditionField: dimensionOptions[0]?.value || primaryField,
				conditionOp: "eq",
				conditionValue: "",
				trueValue: "1",
				falseValue: "0",
				timeGrain: "day",
				expression: "",
			},
		]);
	}, [dimensionOptions, metricOptions]);
	const handleDropField = useCallback((fieldId: string, fieldKind: "metric" | "dimension") => {
		if (fieldKind === "metric") {
			setSelectedMeasures((current) => (current.includes(fieldId) ? current : [...current, fieldId]));
			return;
		}
		setSelectedDimensions((current) => (current.includes(fieldId) ? current : [...current, fieldId]));
	}, []);

	const dwdCandidateGraph = useMemo<SemanticQueryBody | null>(() => {
		if (!selectedDwdAsset || !dwdGrain || !dwdStandardCode || !dwdMeasure) return null;
		const assetId = selectedDwdAsset.assetId || selectedDwdAsset.assetKey;
		return {
			base: assetId,
			nodes: [
				{
					id: assetId,
					role: "BASE",
					warehouseLayer: "DWD",
					assetKey: selectedDwdAsset.assetKey,
					grain: [dwdGrain],
					primaryKeys: selectedDwdAsset.primaryKeys?.length ? selectedDwdAsset.primaryKeys : [dwdGrain],
					standardCode: dwdStandardCode,
					standardCodes: [dwdStandardCode],
				},
			],
			measures: [dwdMeasure],
			dimensions: [dwdGrain],
			derived_metrics: [],
			limit,
			format: "json",
		};
	}, [dwdGrain, dwdMeasure, dwdStandardCode, limit, selectedDwdAsset]);

	const previewSql = async () => {
		if (!currentQuery.base) return;
		setPreviewState({ state: "loading" });
		try {
			const value = await semanticApi.previewSemanticSql(currentQuery);
			setPreviewState({ state: "loaded", value });
		} catch (error) {
			setPreviewState({ state: "error", error });
		}
	};

	const saveDwdCandidate = async () => {
		if (!dwdCandidateGraph) return;
		setDwdCandidateState({ state: "loading" });
		try {
			const value = await semanticApi.createGraphDraft(dwdCandidateGraph);
			setDwdCandidateState({ state: "loaded", value });
		} catch (error) {
			setDwdCandidateState({ state: "error", error });
		}
	};

	const saveMetricDraft = async () => {
		setSaveState({ state: "loading" });
		try {
			const value = await semanticApi.createGraphDraft(currentQuery);
			setSaveState({ state: "loaded", value });
		} catch (error) {
			setSaveState({ state: "error", error });
		}
	};

	return (
		<div className="semantic-designer">
			<div className="semantic-designer-header">
				<div>
					<h3>数据仓库可视化建模</h3>
					<p>默认从 platform 已治理 DWS/ADS 资产进入指标建模；DWD 仅在高级建模流程中受控使用。</p>
				</div>
				<div className="toolbar">
					<button className="button" type="button" onClick={previewSql} disabled={!currentQuery.base}>
						图预检
					</button>
					<button className="button primary" type="button" onClick={saveMetricDraft} disabled={!currentQuery.base}>
						保存图草稿
					</button>
				</div>
			</div>

			{metaState.state === "loading" ? <div className="semantic-empty">语义模型加载中...</div> : null}
			{metaState.state === "error" ? <div className="alert warn">DWS/ADS 可视化资产读取失败：{toMessage(metaState.error)}</div> : null}
			{metaState.state === "loaded" && models.length === 0 ? (
				<div className="semantic-empty">暂无可建模 DWS/ADS 资产，请确认 platform catalog 已发布治理后的 DWS/ADS。</div>
			) : null}
			{previewState?.state === "error" ? <div className="alert warn">图预检失败：{toMessage(previewState.error)}</div> : null}

			<div className="semantic-designer-grid">
				<aside className="semantic-side-panel">
					<label className="field-label" htmlFor="semantic-base-model">
						DWS/ADS 资产
					</label>
					<select
						className="semantic-input"
						id="semantic-base-model"
						value={baseModelId}
						onChange={(event) => {
							setBaseModelId(event.target.value);
							setSelectedJoinTargets([]);
						}}
					>
						{models.map((model) => (
							<option key={readId(model.id)} value={readId(model.id)}>
								{model.label || model.id} / {model.warehouse_layer || "未分层"}
							</option>
						))}
					</select>
					{baseModel ? (
						<div className="semantic-model-note">
							<strong>{baseModel.label || baseModel.id}</strong>
							<span>{baseModel.description || `粒度：${baseModel.grain || "未声明"}`}</span>
							<code>
								{baseModel.asset_key || `${baseModel.schema_name || "dws"}.${baseModel.table_name || baseModel.id}`}
							</code>
						</div>
					) : null}
					<div className="semantic-form-section">
						<div className="semantic-card-title compact">
							<h4>DWD 高级建模</h4>
							<span>{dwdState?.state === "loaded" ? `${dwdAssets.length} 个资产` : "WAITING"}</span>
						</div>
						{dwdState?.state === "error" ? <div className="alert warn">DWD 资产读取失败：{toMessage(dwdState.error)}</div> : null}
						<label className="field-label" htmlFor="dwd-asset">
							DWD 明细资产
						</label>
						<select
							className="semantic-input"
							id="dwd-asset"
							value={dwdAssetId}
							onChange={(event) => {
								setDwdAssetId(event.target.value);
								setDwdGrain("");
								setDwdStandardCode("");
								setDwdMeasure("");
							}}
						>
							<option value="">选择 DWD</option>
							{dwdAssets.map((asset) => (
								<option key={asset.assetId || asset.assetKey} value={asset.assetId || asset.assetKey}>
									{asset.name || asset.assetKey}
								</option>
							))}
						</select>
						<label className="field-label" htmlFor="dwd-grain">
							Grain / Primary key
						</label>
						<select className="semantic-input" id="dwd-grain" value={dwdGrain} onChange={(event) => setDwdGrain(event.target.value)}>
							<option value="">选择 grain</option>
							{dwdGrainOptions.map((grain) => (
								<option key={grain} value={grain}>
									{grain}
								</option>
							))}
						</select>
						<label className="field-label" htmlFor="dwd-standard-code">
							标准码
						</label>
						<input
							className="semantic-input"
							id="dwd-standard-code"
							value={dwdStandardCode}
							onChange={(event) => setDwdStandardCode(event.target.value)}
							placeholder="standard.order_id"
						/>
						<label className="field-label" htmlFor="dwd-measure">
							聚合指标
						</label>
						<select className="semantic-input" id="dwd-measure" value={dwdMeasure} onChange={(event) => setDwdMeasure(event.target.value)}>
							<option value="">选择指标</option>
							{dwdMeasureOptions.map((measure) => (
								<option key={measure} value={measure}>
									{measure}
								</option>
							))}
						</select>
						<button className="button" type="button" onClick={saveDwdCandidate} disabled={!dwdCandidateGraph || dwdCandidateState?.state === "loading"}>
							生成候选 DWS
						</button>
						{dwdCandidateState?.state === "loaded" ? <div className="alert ok">候选 DWS graph draft：{dwdCandidateState.value.id}</div> : null}
						{dwdCandidateState?.state === "error" ? <div className="alert warn">候选生成失败：{toMessage(dwdCandidateState.error)}</div> : null}
					</div>
					<SemanticFieldExplorer
						models={models}
						baseModelId={baseModelId}
						selectedModelIds={selectedModelIds}
						selectedMeasures={selectedMeasures}
						selectedDimensions={selectedDimensions}
						canEdit
						onToggleMeasure={toggleMeasure}
						onToggleDimension={toggleDimension}
					/>
				</aside>

				<section className="semantic-main-panel">
					<div className="semantic-card">
						<div className="semantic-card-title">
							<h4>模型关系画布</h4>
							<span>{selectedJoinTargets.length} 条 Join</span>
						</div>
						<SemanticModelCanvas
							models={models}
							baseModelId={baseModelId}
							selectedJoinTargets={selectedJoinTargets}
							selectedMeasures={selectedMeasures}
							selectedDimensions={selectedDimensions}
							canEdit
							onToggleJoin={toggleJoinTarget}
							onDropField={handleDropField}
						/>
					</div>

					<div className="semantic-card">
						<div className="semantic-card-title">
							<h4>Graph / DSL 预检</h4>
							<span>{previewState?.state === "loaded" ? previewState.value.status || "PREFLIGHT" : "WAITING"}</span>
						</div>
						<textarea className="code-editor tall" readOnly value={previewText} />
					</div>
				</section>

				<aside className="semantic-side-panel">
					<div className="semantic-card-title">
						<h4>属性面板</h4>
						<span>{embedded ? "embedded" : "standalone"}</span>
					</div>
					<SelectionBlock title="已选指标" values={selectedMeasures} options={metricOptions} onRemove={toggleMeasure} />
					<SelectionBlock title="已选维度" values={selectedDimensions} options={dimensionOptions} onRemove={toggleDimension} />

					<div className="semantic-form-section">
						<div className="semantic-card-title compact">
							<h4>筛选器</h4>
							<button className="button small" type="button" onClick={() => setFilters((current) => [...current, { field: "", op: "=", value: "" }])}>
								新增
							</button>
						</div>
						{filters.map((filter, index) => (
							<div className="filter-row" key={`${filter.field}-${index}`}>
								<select
									className="semantic-input"
									value={filter.field}
									onChange={(event) =>
										setFilters((current) =>
											current.map((item, rowIndex) => (rowIndex === index ? { ...item, field: event.target.value } : item)),
										)
									}
								>
									<option value="">字段</option>
									{[...dimensionOptions, ...metricOptions].map((item) => (
										<option key={item.value} value={item.value}>
											{item.label}
										</option>
									))}
								</select>
								<select
									className="semantic-input mini"
									value={filter.op}
									onChange={(event) =>
										setFilters((current) =>
											current.map((item, rowIndex) => (rowIndex === index ? { ...item, op: event.target.value } : item)),
										)
									}
								>
									{filterOps.map((op) => (
										<option key={op} value={op}>
											{op}
										</option>
									))}
								</select>
								<input
									className="semantic-input"
									value={filter.value}
									onChange={(event) =>
										setFilters((current) =>
											current.map((item, rowIndex) => (rowIndex === index ? { ...item, value: event.target.value } : item)),
										)
									}
									placeholder="值"
								/>
								<button className="icon-button" type="button" onClick={() => setFilters((current) => current.filter((_, rowIndex) => rowIndex !== index))}>
									x
								</button>
							</div>
						))}
					</div>

					<div className="semantic-form-section">
						<div className="semantic-card-title compact">
							<h4>派生指标</h4>
							<button
								className="button small"
								type="button"
								onClick={addDerivedMetric}
							>
								新增
							</button>
						</div>
						{derivedMetrics.map((item, index) => (
							<div className="derived-row" key={item.id}>
								<input
									className="semantic-input"
									value={item.label}
									onChange={(event) => updateDerivedMetric(index, { label: event.target.value })}
								/>
								<select
									aria-label="派生指标 DSL 操作"
									className="semantic-input"
									value={item.operation}
									onChange={(event) => updateDerivedMetric(index, { operation: event.target.value as DerivedMetricOperation })}
								>
									{derivedMetricOps.map((op) => (
										<option key={op.value} value={op.value}>
											{op.label}
										</option>
									))}
								</select>
								<select
									aria-label="派生指标主字段"
									className="semantic-input"
									value={item.leftField}
									onChange={(event) => updateDerivedMetric(index, { leftField: event.target.value })}
								>
									<option value="">字段</option>
									{derivedFieldOptions.map((field) => (
										<option key={field.value} value={field.value}>
											{field.label}
										</option>
										))}
								</select>
								{item.operation === "count_if" || item.operation === "sum_if" || item.operation === "case_when" ? (
									<>
										<select
											aria-label="派生指标条件字段"
											className="semantic-input"
											value={item.conditionField || item.leftField || ""}
											onChange={(event) => updateDerivedMetric(index, { conditionField: event.target.value })}
										>
											<option value="">条件字段</option>
											{derivedFieldOptions.map((field) => (
												<option key={field.value} value={field.value}>
													{field.label}
												</option>
											))}
										</select>
										<select
											aria-label="派生指标条件操作符"
											className="semantic-input"
											value={item.conditionOp || "eq"}
											onChange={(event) => updateDerivedMetric(index, { conditionOp: event.target.value as DerivedMetricDraft["conditionOp"] })}
										>
											{conditionOps.map((op) => (
												<option key={op} value={op}>
													{op}
												</option>
											))}
										</select>
										<input
											aria-label="派生指标条件值"
											className="semantic-input"
											value={item.conditionValue || ""}
											onChange={(event) => updateDerivedMetric(index, { conditionValue: event.target.value.replace(/[;'"]/g, "") })}
											placeholder="值"
										/>
									</>
								) : null}
								{item.operation === "ratio" ? (
									<select
										aria-label="派生指标分母字段"
										className="semantic-input"
										value={item.rightField || ""}
										onChange={(event) => updateDerivedMetric(index, { rightField: event.target.value })}
									>
										<option value="">分母字段</option>
										{derivedFieldOptions.map((field) => (
											<option key={field.value} value={field.value}>
												{field.label}
											</option>
										))}
									</select>
								) : null}
								{item.operation === "case_when" ? (
									<>
										<input
											aria-label="派生指标 THEN 值"
											className="semantic-input"
											value={item.trueValue || "1"}
											onChange={(event) => updateDerivedMetric(index, { trueValue: event.target.value.replace(/[;'"]/g, "") })}
										/>
										<input
											aria-label="派生指标 ELSE 值"
											className="semantic-input"
											value={item.falseValue || "0"}
											onChange={(event) => updateDerivedMetric(index, { falseValue: event.target.value.replace(/[;'"]/g, "") })}
										/>
									</>
								) : null}
								{item.operation === "date_trunc" ? (
									<select
										aria-label="派生指标时间粒度"
										className="semantic-input"
										value={item.timeGrain || "day"}
										onChange={(event) => updateDerivedMetric(index, { timeGrain: event.target.value as DerivedMetricDraft["timeGrain"] })}
									>
										{timeGrains.map((grain) => (
											<option key={grain} value={grain}>
												{grain}
											</option>
										))}
									</select>
								) : null}
								<textarea className="semantic-input expression" readOnly value={buildDerivedExpression(item)} />
								<button className="button small" type="button" onClick={() => setDerivedMetrics((current) => current.filter((_, rowIndex) => rowIndex !== index))}>
									删除
								</button>
							</div>
						))}
					</div>

					<label className="field-label" htmlFor="semantic-limit">
						结果限制
					</label>
					<input
						className="semantic-input"
						id="semantic-limit"
						type="number"
						min={1}
						max={5000}
						value={limit}
						onChange={(event) => setLimit(Number.parseInt(event.target.value || "200", 10) || 200)}
					/>
					{saveState?.state === "loaded" ? <div className="alert ok">graph draft 已保存到 dts-metrics：{saveState.value.id}</div> : null}
					{saveState?.state === "error" ? <div className="alert warn">保存失败：{toMessage(saveState.error)}</div> : null}
				</aside>
			</div>
		</div>
	);
}

function SelectionBlock({
	title,
	values,
	options,
	onRemove,
}: {
	title: string;
	values: string[];
	options: Array<{ value: string; label: string }>;
	onRemove: (value: string) => void;
}) {
	const optionMap = new Map(options.map((item) => [item.value, item.label]));
	return (
		<div className="semantic-form-section">
			<h4>{title}</h4>
			<div className="chip-list">
				{values.length === 0 ? <span className="muted">从左侧字段树选择</span> : null}
				{values.map((value) => (
					<button className="chip selected" key={value} type="button" onClick={() => onRemove(value)}>
						{optionMap.get(value) ?? value}
					</button>
				))}
			</div>
		</div>
	);
}
