import { useCallback, useEffect, useMemo, useState } from "react";
import { semanticApi } from "../../features/semantic/semanticApi";
import SemanticFieldExplorer from "../../features/semantic/SemanticFieldExplorer";
import SemanticModelCanvas from "../../features/semantic/SemanticModelCanvas";
import { buildSemanticJoinOptions } from "../../features/semantic/semanticCanvas.helpers";
import type {
	DerivedMetricDraft,
	LoadState,
	QueryFilterDraft,
	SemanticMetaResponse,
	SemanticModelMeta,
	SemanticQueryBody,
	SemanticQueryResponse,
} from "../../features/semantic/semanticTypes";

const fallbackMeta: SemanticMetaResponse = {
	spec_version: "local-fallback",
	models: [
		{
			id: "dwd_project_detail",
			label: "项目明细主体",
			subject_area: "项目管理",
			security_level: "CONFIDENTIAL",
			grain: "project_id + stat_month",
			database_id: 1,
			schema_name: "dwd",
			table_name: "dwd_project_detail",
			description: "platform 已开放给指标建模的项目明细模型",
			metrics: [
				{ id: "project_cnt", label: "项目数", aggregation: "count_distinct", field: "project_id" },
				{ id: "direct_cost_amount", label: "直接成本", aggregation: "sum", field: "direct_cost_amount" },
				{ id: "overdue_project_cnt", label: "延期项目数", aggregation: "count_if", field: "overdue_flag" },
			],
			dimensions: [
				{ id: "stat_month", label: "统计月份", type: "date" },
				{ id: "dept_name", label: "责任科室", type: "varchar" },
				{ id: "project_type", label: "项目类型", type: "varchar" },
			],
			joins: [
				{
					to: "dwd_contract_detail",
					type: "many_to_one",
					relationship: "left",
					path: "project_id",
					fanout_warning: true,
				},
			],
		},
		{
			id: "dwd_contract_detail",
			label: "合同明细主体",
			subject_area: "合同管理",
			security_level: "INTERNAL",
			grain: "contract_id",
			database_id: 1,
			schema_name: "dwd",
			table_name: "dwd_contract_detail",
			metrics: [
				{ id: "contract_cnt", label: "合同数", aggregation: "count_distinct", field: "contract_id" },
				{ id: "contract_amount", label: "合同金额", aggregation: "sum", field: "contract_amount" },
			],
			dimensions: [
				{ id: "supplier_name", label: "供应商", type: "varchar" },
				{ id: "contract_status", label: "合同状态", type: "varchar" },
			],
			joins: [],
		},
	],
};

const filterOps = ["=", "!=", ">", ">=", "<", "<=", "in", "like"];

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
	return new Map(models.map((model) => [readId(model.id), model]).filter(([id]) => id));
}

function sameList(left: string[], right: string[]) {
	return left.length === right.length && left.every((value, index) => value === right[index]);
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
	return {
		base: baseModelId || undefined,
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
		derived_metrics: derivedMetrics
			.filter((item) => item.expression.trim())
			.map((item) => ({ id: item.id, label: item.label.trim() || item.id, expression: item.expression.trim() })),
		limit,
		format: "json",
	};
}

function localSqlPreview(query: SemanticQueryBody, models: SemanticModelMeta[]): SemanticQueryResponse {
	const model = models.find((item) => readId(item.id) === query.base) ?? models[0];
	const table = `${model?.schema_name ?? "semantic"}.${model?.table_name ?? query.base ?? "model"}`;
	const selected = [
		...(query.dimensions ?? []).map((item) => (typeof item === "string" ? item : item.id)),
		...(query.measures ?? []),
		...(query.derived_metrics ?? []).map((item) => `${item.expression} as ${item.id}`),
	];
	const where = (query.filters ?? []).map((item) => `${item.field} ${item.op} ${JSON.stringify(item.value ?? "")}`);
	const sql = [
		`select ${selected.length ? selected.join(", ") : "*"}`,
		`from ${table}`,
		...(query.joins ?? []).map((join) => `left join ${join.to} on ${table}.${join.via ?? "id"} = ${join.to}.${join.via ?? "id"}`),
		where.length ? `where ${where.join(" and ")}` : "",
		`limit ${query.limit ?? 200}`,
	]
		.filter(Boolean)
		.join("\n");
	return {
		status: "LOCAL_PREVIEW",
		meta: {
			sql_preview: sql,
			row_count: 0,
			warnings: ["platform semantic preview 不可用时使用本地 DSL 预览"],
			security_applied: ["RLS placeholder"],
		},
		columns: [],
		rows: [],
	};
}

export default function SemanticDesignerPage({ embedded }: { embedded: boolean }) {
	const [metaState, setMetaState] = useState<LoadState<SemanticMetaResponse>>({ state: "loading" });
	const [previewState, setPreviewState] = useState<LoadState<SemanticQueryResponse> | null>(null);
	const [saveState, setSaveState] = useState<LoadState<Record<string, unknown>> | null>(null);
	const [baseModelId, setBaseModelId] = useState("");
	const [selectedJoinTargets, setSelectedJoinTargets] = useState<string[]>([]);
	const [selectedMeasures, setSelectedMeasures] = useState<string[]>([]);
	const [selectedDimensions, setSelectedDimensions] = useState<string[]>([]);
	const [filters, setFilters] = useState<QueryFilterDraft[]>([]);
	const [derivedMetrics, setDerivedMetrics] = useState<DerivedMetricDraft[]>([
		{ id: "direct_cost_execution_rate", label: "直接成本执行率", expression: "[direct_cost_amount] / [contract_amount]" },
	]);
	const [limit, setLimit] = useState(200);

	useEffect(() => {
		let cancelled = false;
		semanticApi
			.getSemanticMeta({ exposedToModeler: true })
			.then((value) => {
				if (cancelled) return;
				const safeValue = value?.models?.length ? value : fallbackMeta;
				setMetaState({ state: "loaded", value: safeValue });
			})
			.catch((error) => {
				if (cancelled) return;
				setMetaState({ state: "loaded", value: fallbackMeta });
				setPreviewState({ state: "error", error });
			});
		return () => {
			cancelled = true;
		};
	}, []);

	const models = metaState.state === "loaded" ? (metaState.value.models ?? []) : [];
	const modelMap = useMemo(() => buildModelMap(models), [models]);
	const baseModel = modelMap.get(baseModelId);
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

	const previewSql = async () => {
		if (!currentQuery.base) return;
		setPreviewState({ state: "loading" });
		try {
			const value = await semanticApi.previewSemanticSql(currentQuery);
			setPreviewState({ state: "loaded", value });
		} catch (error) {
			setPreviewState({ state: "loaded", value: localSqlPreview(currentQuery, models) });
		}
	};

	const saveMetricDraft = async () => {
		const firstMeasure = selectedMeasures[0] ?? derivedMetrics[0]?.id ?? "semantic_metric";
		setSaveState({ state: "loading" });
		try {
			const value = await semanticApi.createSemanticMetric({
				code: firstMeasure,
				name: metricOptions.find((item) => item.value === firstMeasure)?.label ?? firstMeasure,
				formulaType: derivedMetrics.length ? "composite" : "aggregation",
				formulaJson: JSON.stringify(currentQuery),
				status: "DRAFT",
			});
			setSaveState({ state: "loaded", value: value as Record<string, unknown> });
		} catch (error) {
			setSaveState({ state: "error", error });
		}
	};

	return (
		<div className="semantic-designer">
			<div className="semantic-designer-header">
				<div>
					<h3>语义指标设计器</h3>
					<p>迁入 platform 原语义建模链路：基础模型、Join 画布、指标/维度树、筛选、派生指标和 SQL 预览。</p>
				</div>
				<div className="toolbar">
					<button className="button" type="button" onClick={previewSql} disabled={!currentQuery.base}>
						预览 SQL
					</button>
					<button className="button primary" type="button" onClick={saveMetricDraft} disabled={!currentQuery.base}>
						保存指标草稿
					</button>
				</div>
			</div>

			{metaState.state === "loading" ? <div className="semantic-empty">语义模型加载中...</div> : null}
			{previewState?.state === "error" ? <div className="alert warn">语义元数据读取失败：{toMessage(previewState.error)}</div> : null}

			<div className="semantic-designer-grid">
				<aside className="semantic-side-panel">
					<label className="field-label" htmlFor="semantic-base-model">
						基础模型
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
								{model.label || model.id} / {model.subject_area || "未分域"}
							</option>
						))}
					</select>
					{baseModel ? (
						<div className="semantic-model-note">
							<strong>{baseModel.label || baseModel.id}</strong>
							<span>{baseModel.description || `粒度：${baseModel.grain || "未声明"}`}</span>
							<code>
								{baseModel.schema_name || "public"}.{baseModel.table_name || baseModel.id}
							</code>
						</div>
					) : null}
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
							canEdit
							onToggleJoin={toggleJoinTarget}
						/>
					</div>

					<div className="semantic-card">
						<div className="semantic-card-title">
							<h4>SQL / DSL 预览</h4>
							<span>{previewState?.state === "loaded" ? previewState.value.status || "PREVIEW" : "LOCAL_DSL"}</span>
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
								onClick={() =>
									setDerivedMetrics((current) => [
										...current,
										{ id: `derived_${current.length + 1}`, label: `派生指标 ${current.length + 1}`, expression: "" },
									])
								}
							>
								新增
							</button>
						</div>
						{derivedMetrics.map((item, index) => (
							<div className="derived-row" key={item.id}>
								<input
									className="semantic-input"
									value={item.label}
									onChange={(event) =>
										setDerivedMetrics((current) =>
											current.map((row, rowIndex) => (rowIndex === index ? { ...row, label: event.target.value } : row)),
										)
									}
								/>
								<textarea
									className="semantic-input expression"
									value={item.expression}
									onChange={(event) =>
										setDerivedMetrics((current) =>
											current.map((row, rowIndex) => (rowIndex === index ? { ...row, expression: event.target.value } : row)),
										)
									}
								/>
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
					{saveState?.state === "loaded" ? <div className="alert ok">指标草稿已写入 platform semantic metrics。</div> : null}
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
