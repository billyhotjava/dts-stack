import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router";
import { Alert, Breadcrumb, Button, Card, Empty, Input, Modal, Select, Space, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { PlusOutlined, SaveOutlined, DatabaseOutlined, DeploymentUnitOutlined, PlayCircleOutlined } from "@ant-design/icons";
import { toast } from "sonner";
import { PageHeader } from "@/components/page-header";
import { useUserRoles } from "@/store/userStore";
import {
	analyticsApi,
	type CardDetail,
	type CollectionListItem,
	type SemanticMetaResponse,
	type SemanticModelMeta,
	type SemanticPromoteResult,
	type SemanticQueryBody,
	type SemanticQueryResponse,
	type SemanticVirtualDataset,
} from "../../api/analyticsApi";
import { ErrorNotice } from "../../components/ErrorNotice";
import { ChartRenderer, type VisualizationType } from "../../components/charts";
import { getEffectiveLocale, type Locale } from "../../i18n";

type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

type QueryFilter = { field: string; op: string; value: string };
type DerivedMetricDraft = { id: string; label: string; expression: string };

const FILTER_OP_OPTIONS = [
	{ label: "=", value: "=" },
	{ label: "!=", value: "!=" },
	{ label: ">", value: ">" },
	{ label: ">=", value: ">=" },
	{ label: "<", value: "<" },
	{ label: "<=", value: "<=" },
	{ label: "IN", value: "in" },
	{ label: "LIKE", value: "like" },
];

const DISPLAY_OPTIONS: Array<{ label: string; value: VisualizationType }> = [
	{ label: "表格", value: "table" },
	{ label: "柱状", value: "bar" },
	{ label: "折线", value: "line" },
	{ label: "饼图", value: "pie" },
	{ label: "数字", value: "scalar" },
];

function toMessage(error: unknown, fallback: string): string {
	if (error instanceof Error && error.message) {
		return error.message;
	}
	return fallback;
}

function asRecord(value: unknown): Record<string, unknown> | null {
	return value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : null;
}

function asArray<T = Record<string, unknown>>(value: unknown): T[] {
	return Array.isArray(value) ? (value as T[]) : [];
}

function readId(value: unknown): string {
	if (typeof value === "string") return value;
	if (typeof value === "number") return String(value);
	return "";
}

function readLabel(value: Record<string, unknown>, fallback: string): string {
	return String(value.label ?? value.display_name ?? value.name ?? value.id ?? fallback);
}

function extractSemanticQuery(datasetQuery: unknown): SemanticQueryBody | null {
	const record = asRecord(datasetQuery);
	if (!record) return null;
	const semantic = asRecord(record.semantic_query);
	if (semantic) {
		return semantic as unknown as SemanticQueryBody;
	}
	return String(record.type ?? "").toLowerCase() === "semantic" ? (record as unknown as SemanticQueryBody) : null;
}

function toBaseType(type?: string): string {
	const normalized = String(type ?? "").toLowerCase();
	if (normalized === "number" || normalized === "integer" || normalized === "decimal" || normalized === "float") {
		return "type/Float";
	}
	if (normalized === "date") {
		return "type/Date";
	}
	if (normalized === "datetime" || normalized === "timestamp" || normalized === "time") {
		return "type/DateTime";
	}
	if (normalized === "boolean") {
		return "type/Boolean";
	}
	return "type/Text";
}

function applySemanticDraft(
	state: SemanticQueryBody | null | undefined,
	setBaseModelId: (value: string) => void,
	setSelectedJoinTargets: (value: string[]) => void,
	setSelectedMeasures: (value: string[]) => void,
	setSelectedDimensions: (value: string[]) => void,
	setFilters: (value: QueryFilter[]) => void,
	setDerivedMetrics: (value: DerivedMetricDraft[]) => void,
	setLimit: (value: number) => void,
) {
	if (!state) return;
	setBaseModelId(String(state.base ?? ""));
	setSelectedJoinTargets(asArray<Record<string, unknown>>(state.joins).map((item) => readId(item.to)).filter(Boolean));
	setSelectedMeasures(asArray<string>(state.measures).map((item) => String(item)));
	setSelectedDimensions(
		asArray<string | Record<string, unknown>>(state.dimensions).map((item) =>
			typeof item === "string" ? item : readId(item.id),
		).filter(Boolean),
	);
	setFilters(
		asArray<Record<string, unknown>>(state.filters).map((item) => ({
			field: readId(item.field),
			op: String(item.op ?? "="),
			value: Array.isArray(item.value) ? item.value.join(",") : String(item.value ?? ""),
		})),
	);
	setDerivedMetrics(
		asArray<Record<string, unknown>>(state.derived_metrics).map((item, index) => ({
			id: readId(item.id) || `derived_${index + 1}`,
			label: String(item.label ?? item.id ?? `派生指标 ${index + 1}`),
			expression: String(item.expression ?? ""),
		})),
	);
	setLimit(typeof state.limit === "number" ? state.limit : 200);
}

export default function SemanticCardEditorPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const navigate = useNavigate();
	const location = useLocation();
	const params = useParams();
	const roles = useUserRoles();
	const roleSet = new Set((roles || []).map((role) => String(role || "").trim().toUpperCase()));
	const canModel = roleSet.has("BI_ANALYST") || roleSet.has("BI_DATA_ENGINEER") || roleSet.has("OP_ADMIN");
	const canPromote = roleSet.has("BI_DATA_ENGINEER") || roleSet.has("OP_ADMIN");
	const isVirtualDatasetMode = location.pathname.includes("/virtual-datasets");
	const recordId = params.id ? String(params.id) : null;
	const vdsFromSearch = new URLSearchParams(location.search).get("vds");

	const [metaState, setMetaState] = useState<LoadState<SemanticMetaResponse>>({ state: "loading" });
	const [collectionsState, setCollectionsState] = useState<LoadState<CollectionListItem[]>>({ state: "loading" });
	const [recordState, setRecordState] = useState<LoadState<CardDetail | SemanticVirtualDataset> | null>(recordId || vdsFromSearch ? { state: "loading" } : null);
	const [queryState, setQueryState] = useState<LoadState<SemanticQueryResponse> | null>(null);
	const [previewState, setPreviewState] = useState<LoadState<SemanticQueryResponse> | null>(null);
	const [promoteState, setPromoteState] = useState<LoadState<SemanticPromoteResult> | null>(null);
	const [saving, setSaving] = useState(false);
	const [savingVds, setSavingVds] = useState(false);
	const [vdsModalOpen, setVdsModalOpen] = useState(false);

	const [name, setName] = useState("");
	const [description, setDescription] = useState("");
	const [collectionId, setCollectionId] = useState<number | null>(null);
	const [baseModelId, setBaseModelId] = useState("");
	const [selectedJoinTargets, setSelectedJoinTargets] = useState<string[]>([]);
	const [selectedMeasures, setSelectedMeasures] = useState<string[]>([]);
	const [selectedDimensions, setSelectedDimensions] = useState<string[]>([]);
	const [filters, setFilters] = useState<QueryFilter[]>([]);
	const [derivedMetrics, setDerivedMetrics] = useState<DerivedMetricDraft[]>([]);
	const [limit, setLimit] = useState(200);
	const [displayType, setDisplayType] = useState<VisualizationType>("table");

	useEffect(() => {
		let cancelled = false;
		analyticsApi
			.getSemanticMeta({ exposedToModeler: true })
			.then((value) => {
				if (cancelled) return;
				setMetaState({ state: "loaded", value });
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
		if (isVirtualDatasetMode) {
			setCollectionsState({ state: "loaded", value: [] });
			return () => {
				cancelled = true;
			};
		}
		analyticsApi
			.listCollections()
			.then((value) => {
				if (cancelled) return;
				setCollectionsState({ state: "loaded", value });
			})
			.catch((error) => {
				if (cancelled) return;
				setCollectionsState({ state: "error", error });
			});
		return () => {
			cancelled = true;
		};
	}, [isVirtualDatasetMode]);

	useEffect(() => {
		let cancelled = false;
		if (!recordId && !vdsFromSearch) {
			setRecordState(null);
			return () => {
				cancelled = true;
			};
		}

		const load = async () => {
			try {
				if (isVirtualDatasetMode && recordId) {
					const value = await analyticsApi.getSemanticVirtualDataset(recordId);
					if (cancelled) return;
					setRecordState({ state: "loaded", value });
					return;
				}
				if (!isVirtualDatasetMode && recordId) {
					const value = await analyticsApi.getCard(recordId);
					if (cancelled) return;
					setRecordState({ state: "loaded", value });
					return;
				}
				if (!isVirtualDatasetMode && vdsFromSearch) {
					const value = await analyticsApi.getSemanticVirtualDataset(vdsFromSearch);
					if (cancelled) return;
					setRecordState({ state: "loaded", value });
				}
			} catch (error) {
				if (cancelled) return;
				setRecordState({ state: "error", error });
			}
		};

		setRecordState({ state: "loading" });
		void load();
		return () => {
			cancelled = true;
		};
	}, [isVirtualDatasetMode, recordId, vdsFromSearch]);

	const models = metaState.state === "loaded" ? metaState.value.models ?? [] : [];
	const modelMap = useMemo(() => {
		const map = new Map<string, SemanticModelMeta>();
		for (const model of models) {
			const key = readId(model.id);
			if (key) {
				map.set(key, model);
			}
		}
		return map;
	}, [models]);

	const baseModel = modelMap.get(baseModelId);
	const joinOptions = asArray<Record<string, unknown>>(baseModel?.joins).map((join) => ({
		value: readId(join.to),
		label: `${readLabel(join, readId(join.to))} · ${String(join.type ?? "many_to_one")}`,
		join,
	}));

	const selectedModelIds = [baseModelId, ...selectedJoinTargets].filter(Boolean);
	const availableModels = models.filter((model) => selectedModelIds.includes(readId(model.id)));
	const metricOptions = availableModels.flatMap((model) =>
		asArray<Record<string, unknown>>(model.metrics).map((metric) => ({
			value: readId(metric.id),
			label: `${model.label || model.id} / ${readLabel(metric, readId(metric.id))}`,
			raw: metric,
		})),
	);
	const dimensionOptions = availableModels.flatMap((model) =>
		asArray<Record<string, unknown>>(model.dimensions).map((dimension) => ({
			value: readId(dimension.id),
			label: `${model.label || model.id} / ${readLabel(dimension, readId(dimension.id))}`,
			raw: dimension,
		})),
	);

	useEffect(() => {
		if (metaState.state !== "loaded") return;
		if (baseModelId) return;
		const searchBase = new URLSearchParams(location.search).get("base");
		if (searchBase && modelMap.has(searchBase)) {
			setBaseModelId(searchBase);
			return;
		}
		const first = models[0];
		if (first?.id) {
			setBaseModelId(readId(first.id));
		}
	}, [baseModelId, location.search, metaState.state, modelMap, models]);

	useEffect(() => {
		const joinSet = new Set(joinOptions.map((item) => item.value));
		setSelectedJoinTargets((current) => current.filter((item) => joinSet.has(item)));
	}, [joinOptions]);

	useEffect(() => {
		const metricSet = new Set(metricOptions.map((item) => item.value));
		setSelectedMeasures((current) => current.filter((item) => metricSet.has(item)));
	}, [metricOptions]);

	useEffect(() => {
		const dimensionSet = new Set(dimensionOptions.map((item) => item.value));
		setSelectedDimensions((current) => current.filter((item) => dimensionSet.has(item)));
		setFilters((current) => current.filter((item) => !item.field || dimensionSet.has(item.field) || metricOptions.some((metric) => metric.value === item.field)));
	}, [dimensionOptions, metricOptions]);

	useEffect(() => {
		if (recordState?.state !== "loaded") return;
		const loaded = recordState.value;
		if (isVirtualDatasetMode || "state" in loaded) {
			const vds = loaded as SemanticVirtualDataset;
			const state = asRecord(vds.state) as unknown as SemanticQueryBody | null;
			setName(String(vds.name ?? ""));
			setDescription(String(vds.description ?? ""));
			setCollectionId(null);
			setDisplayType("table");
			applySemanticDraft(
				state,
				setBaseModelId,
				setSelectedJoinTargets,
				setSelectedMeasures,
				setSelectedDimensions,
				setFilters,
				setDerivedMetrics,
				setLimit,
			);
			return;
		}

		const card = loaded as CardDetail;
		const semanticQuery = extractSemanticQuery(card.dataset_query);
		setName(String(card.name ?? ""));
		setDescription(String(card.description ?? ""));
		setCollectionId(typeof card.collection_id === "number" ? card.collection_id : null);
		setDisplayType((card.display as VisualizationType) || "table");
		if (semanticQuery) {
			applySemanticDraft(
				semanticQuery,
				setBaseModelId,
				setSelectedJoinTargets,
				setSelectedMeasures,
				setSelectedDimensions,
				setFilters,
				setDerivedMetrics,
				setLimit,
			);
		}
	}, [isVirtualDatasetMode, recordState]);

	const buildSemanticQuery = (): SemanticQueryBody => ({
		base: baseModelId || undefined,
		joins: selectedJoinTargets.map((target) => {
			const matched = joinOptions.find((item) => item.value === target)?.join;
			return {
				to: target,
				via: typeof matched?.path === "string" ? matched.path : undefined,
				type: typeof matched?.type === "string" ? String(matched.type) : undefined,
			};
		}),
		measures: selectedMeasures,
		dimensions: selectedDimensions,
		filters: filters
			.filter((item) => item.field && item.op)
			.map((item) => ({
				field: item.field,
				op: item.op,
				value: item.op === "in" ? item.value.split(",").map((value) => value.trim()).filter(Boolean) : item.value,
			})),
		derived_metrics: derivedMetrics
			.filter((item) => item.expression.trim())
			.map((item) => ({ id: item.id, label: item.label.trim() || item.id, expression: item.expression.trim() })),
		limit,
		format: "json",
	});

	const currentQuery = buildSemanticQuery();
	const currentDatabaseId = typeof baseModel?.database_id === "number" ? baseModel.database_id : null;
	const currentChartData =
		queryState?.state === "loaded"
			? {
				rows: queryState.value.rows ?? [],
				cols: (queryState.value.columns ?? []).map((column) => ({
					name: column.id ?? column.label ?? "col",
					display_name: column.label ?? column.id ?? "col",
					base_type: toBaseType(column.type),
				})),
			}
			: null;

	const fieldFilterOptions = [...metricOptions, ...dimensionOptions];
	const resultColumns: ColumnsType<Record<string, unknown>> = (queryState?.state === "loaded" ? (queryState.value.columns ?? []) : []).map((column, index) => ({
		title: column.label ?? column.id ?? `列${index + 1}`,
		dataIndex: String(index),
		key: String(column.id ?? index),
		render: (_: unknown, record) => record[String(index)] ?? "-",
	}));
	const resultRows =
		queryState?.state === "loaded"
			? (queryState.value.rows ?? []).map((row, rowIndex) => {
				const record: Record<string, unknown> = { key: rowIndex };
				(row ?? []).forEach((value, colIndex) => {
					record[String(colIndex)] = value;
				});
				return record;
			})
			: [];

	const runQuery = async () => {
		if (!currentQuery.base) {
			toast.error("请先选择基础模型");
			return;
		}
		setQueryState({ state: "loading" });
		try {
			const value = await analyticsApi.runSemanticQuery(currentQuery);
			setQueryState({ state: "loaded", value });
		} catch (error) {
			setQueryState({ state: "error", error });
		}
	};

	const previewSql = async () => {
		if (!currentQuery.base) {
			toast.error("请先选择基础模型");
			return;
		}
		setPreviewState({ state: "loading" });
		try {
			const value = await analyticsApi.previewSemanticSql(currentQuery);
			setPreviewState({ state: "loaded", value });
		} catch (error) {
			setPreviewState({ state: "error", error });
		}
	};

	const saveCard = async () => {
		if (!canModel) {
			toast.error("当前角色无权创建分析卡片");
			return;
		}
		if (!name.trim() || !currentDatabaseId || !currentQuery.base) {
			toast.error("请补全名称和基础模型");
			return;
		}
		setSaving(true);
		try {
			const body = {
				name: name.trim(),
				description: description.trim() || null,
				collection_id: collectionId,
				display: displayType,
				dataset_query: {
					database: currentDatabaseId,
					type: "semantic",
					semantic_query: currentQuery,
				},
				visualization_settings: {
					semantic: {
						measures: selectedMeasures,
						dimensions: selectedDimensions,
					},
				},
			};
			const saved = recordId && !isVirtualDatasetMode
				? await analyticsApi.updateCard(recordId, body)
				: await analyticsApi.createCard(body);
			toast.success("语义卡片已保存");
			navigate(`/bi/questions/${encodeURIComponent(String(saved.id))}`);
		} catch (error) {
			toast.error(toMessage(error, "保存卡片失败"));
		} finally {
			setSaving(false);
		}
	};

	const saveVirtualDataset = async () => {
		if (!currentQuery.base) {
			toast.error("请先选择基础模型");
			return;
		}
		if (!name.trim()) {
			toast.error("请先填写名称");
			return;
		}
		setSavingVds(true);
		try {
			const body = {
				name: name.trim(),
				description: description.trim() || null,
				state: currentQuery,
			};
			const saved = isVirtualDatasetMode && recordId
				? await analyticsApi.updateSemanticVirtualDataset(recordId, body)
				: await analyticsApi.createSemanticVirtualDataset(body);
			toast.success("虚拟数据集已保存");
			setVdsModalOpen(false);
			if (!isVirtualDatasetMode) {
				navigate(`/bi/virtual-datasets/${encodeURIComponent(String(saved.id))}`);
			}
		} catch (error) {
			toast.error(toMessage(error, "保存虚拟数据集失败"));
		} finally {
			setSavingVds(false);
		}
	};

	const promoteVirtualDataset = async () => {
		if (!recordId || !canPromote) {
			return;
		}
		setPromoteState({ state: "loading" });
		try {
			const value = await analyticsApi.promoteSemanticVirtualDataset(recordId);
			setPromoteState({ state: "loaded", value });
			toast.success("已生成提升到 dbt 的草案");
		} catch (error) {
			setPromoteState({ state: "error", error });
			toast.error(toMessage(error, "生成提升草案失败"));
		}
	};

	const semanticRecord = !isVirtualDatasetMode && recordState?.state === "loaded" && "dataset_query" in recordState.value
		? extractSemanticQuery(recordState.value.dataset_query)
		: null;
	const legacyCardDetected =
		!isVirtualDatasetMode
		&& recordId
		&& recordState?.state === "loaded"
		&& "dataset_query" in recordState.value
		&& !semanticRecord;

	return (
		<div className="space-y-4">
			<Breadcrumb
				items={[
					{ title: <Link to="/bi">BI</Link> },
					{ title: isVirtualDatasetMode ? <Link to="/bi/virtual-datasets">虚拟数据集</Link> : <Link to="/bi/explore">语义探索</Link> },
					{ title: isVirtualDatasetMode ? (recordId ? `VDS #${recordId}` : "新建虚拟数据集") : (recordId ? `Card #${recordId}` : "新建卡片") },
				]}
			/>

			<PageHeader
				title={isVirtualDatasetMode ? (recordId ? "编辑虚拟数据集" : "新建虚拟数据集") : (recordId ? "编辑语义卡片" : "新建语义卡片")}
				actions={
					<Space wrap>
						{!isVirtualDatasetMode && (
							<Button icon={<DatabaseOutlined />} onClick={() => setVdsModalOpen(true)}>
								保存为 VDS
							</Button>
						)}
						{isVirtualDatasetMode && (
							<Button icon={<SaveOutlined />} loading={savingVds} onClick={saveVirtualDataset}>
								保存 VDS
							</Button>
						)}
						{isVirtualDatasetMode && recordId && canPromote && (
							<Button icon={<DeploymentUnitOutlined />} onClick={promoteVirtualDataset}>
								提升到 dbt
							</Button>
						)}
						{!isVirtualDatasetMode && (
							<Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={saveCard}>
								保存卡片
							</Button>
						)}
					</Space>
				}
			/>

			{!canModel && (
				<Alert
					type="warning"
					showIcon
					message="当前账号没有建模权限"
					description="需要 BI_ANALYST、BI_DATA_ENGINEER 或 OP_ADMIN 角色才能创建和编辑语义卡片。"
				/>
			)}

			{metaState.state === "loading" && (
				<div className="loading-container" style={{ padding: 48 }}>
					<Spin size="large" />
				</div>
			)}
			{metaState.state === "error" && <ErrorNotice locale={locale} error={metaState.error} />}
			{recordState?.state === "error" && <ErrorNotice locale={locale} error={recordState.error} />}

			{legacyCardDetected && (
				<Alert
					type="info"
					showIcon
					message="这张卡片仍然使用旧 SQL / Notebook 编辑器"
					description={<Link to={`/bi/questions/${encodeURIComponent(String(recordId))}/edit`}>跳回旧版编辑器</Link>}
				/>
			)}

			{metaState.state === "loaded" && !legacyCardDetected && (
				<>
					<div style={{ display: "grid", gridTemplateColumns: "minmax(360px, 460px) minmax(0, 1fr)", gap: 16 }}>
						<Card title="建模画布" extra={baseModel ? <Tag color="blue">{baseModel.security_level || "INTERNAL"}</Tag> : null}>
							<Space direction="vertical" size={12} style={{ width: "100%" }}>
								<div>
									<div className="mb-1 text-xs text-secondary">名称</div>
									<Input value={name} onChange={(event) => setName(event.target.value)} placeholder={isVirtualDatasetMode ? "输入虚拟数据集名称" : "输入卡片名称"} />
								</div>
								<div>
									<div className="mb-1 text-xs text-secondary">描述</div>
									<Input.TextArea value={description} onChange={(event) => setDescription(event.target.value)} autoSize={{ minRows: 2, maxRows: 4 }} />
								</div>
								{!isVirtualDatasetMode && collectionsState.state === "loaded" && (
									<div>
										<div className="mb-1 text-xs text-secondary">集合</div>
										<Select
											allowClear
											style={{ width: "100%" }}
											value={collectionId ?? undefined}
											onChange={(value) => setCollectionId(typeof value === "number" ? value : null)}
											options={collectionsState.value.filter((item) => item.id !== "root").map((item) => ({
												value: item.id,
												label: item.name || `集合 ${item.id}`,
											}))}
										/>
									</div>
								)}
								<div>
									<div className="mb-1 text-xs text-secondary">基础模型</div>
									<Select
										showSearch
										style={{ width: "100%" }}
										value={baseModelId || undefined}
										onChange={(value) => {
											setBaseModelId(String(value));
											setSelectedJoinTargets([]);
										}}
										options={models.map((model) => ({
											value: readId(model.id),
											label: `${model.label || model.id} · ${model.subject_area || "未分域"}`,
										}))}
									/>
								</div>
								<div>
									<div className="mb-1 text-xs text-secondary">允许 Join 的模型</div>
									<Select
										mode="multiple"
										style={{ width: "100%" }}
										placeholder={joinOptions.length ? "选择 join 目标" : "当前模型未开放 join"}
										value={selectedJoinTargets}
										onChange={(value) => setSelectedJoinTargets((value ?? []).map((item) => String(item)))}
										options={joinOptions}
									/>
								</div>
								<div>
									<div className="mb-1 text-xs text-secondary">指标</div>
									<Select
										mode="multiple"
										style={{ width: "100%" }}
										value={selectedMeasures}
										onChange={(value) => setSelectedMeasures((value ?? []).map((item) => String(item)))}
										options={metricOptions}
									/>
								</div>
								<div>
									<div className="mb-1 text-xs text-secondary">维度</div>
									<Select
										mode="multiple"
										style={{ width: "100%" }}
										value={selectedDimensions}
										onChange={(value) => setSelectedDimensions((value ?? []).map((item) => String(item)))}
										options={dimensionOptions}
									/>
								</div>
								<div>
									<div className="mb-1 text-xs text-secondary">筛选器</div>
									<Space direction="vertical" style={{ width: "100%" }}>
										{filters.map((item, index) => (
											<Space key={`${item.field}-${index}`} style={{ width: "100%" }} align="start">
												<Select
													showSearch
													style={{ width: 180 }}
													value={item.field || undefined}
													onChange={(value) =>
														setFilters((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, field: String(value) } : row))
													}
													options={fieldFilterOptions}
												/>
												<Select
													style={{ width: 100 }}
													value={item.op}
													onChange={(value) =>
														setFilters((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, op: String(value) } : row))
													}
													options={FILTER_OP_OPTIONS}
												/>
												<Input
													style={{ flex: 1 }}
													value={item.value}
													onChange={(event) =>
														setFilters((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, value: event.target.value } : row))
													}
													placeholder={item.op === "in" ? "多个值用逗号分隔" : "输入筛选值"}
												/>
												<Button danger onClick={() => setFilters((current) => current.filter((_, rowIndex) => rowIndex !== index))}>
													删除
												</Button>
											</Space>
										))}
										<Button icon={<PlusOutlined />} onClick={() => setFilters((current) => [...current, { field: "", op: "=", value: "" }])}>
											新增筛选
										</Button>
									</Space>
								</div>
								<div>
									<div className="mb-1 text-xs text-secondary">派生指标</div>
									<Space direction="vertical" style={{ width: "100%" }}>
										{derivedMetrics.map((item, index) => (
											<Card key={item.id} size="small">
												<Space direction="vertical" style={{ width: "100%" }}>
													<Input
														value={item.label}
														onChange={(event) =>
															setDerivedMetrics((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, label: event.target.value } : row))
														}
														placeholder="指标名称"
													/>
													<Input.TextArea
														autoSize={{ minRows: 2, maxRows: 4 }}
														value={item.expression}
														onChange={(event) =>
															setDerivedMetrics((current) => current.map((row, rowIndex) => rowIndex === index ? { ...row, expression: event.target.value } : row))
														}
														placeholder="例如: [ads_sales_daily.revenue] / [ads_sales_daily.order_count]"
													/>
													<Button danger onClick={() => setDerivedMetrics((current) => current.filter((_, rowIndex) => rowIndex !== index))}>
														删除派生指标
													</Button>
												</Space>
											</Card>
										))}
										<Button
											icon={<PlusOutlined />}
											onClick={() => setDerivedMetrics((current) => [...current, {
												id: `derived_${current.length + 1}`,
												label: `派生指标 ${current.length + 1}`,
												expression: "",
											}])}
										>
											新增派生指标
										</Button>
									</Space>
								</div>
								<div>
									<div className="mb-1 text-xs text-secondary">结果限制</div>
									<Input
										type="number"
										min={1}
										max={5000}
										value={limit}
										onChange={(event) => setLimit(Number.parseInt(event.target.value || "200", 10) || 200)}
									/>
								</div>
								{baseModel && (
									<Alert
										type="info"
										showIcon
										message={`${baseModel.label || baseModel.id} · ${baseModel.subject_area || "未分域"}`}
										description={baseModel.description || `粒度：${baseModel.grain || "未声明"}，底表：${baseModel.schema_name || "public"}.${baseModel.table_name || baseModel.id}`}
									/>
								)}
							</Space>
						</Card>

						<Space direction="vertical" size={16} style={{ width: "100%" }}>
							<Card
								title="查询预览"
								extra={
									<Space>
										<Select
											style={{ width: 120 }}
											value={displayType}
											onChange={(value) => setDisplayType(value)}
											options={DISPLAY_OPTIONS}
										/>
										<Button icon={<DatabaseOutlined />} onClick={previewSql}>
											预览 SQL
										</Button>
										<Button type="primary" icon={<PlayCircleOutlined />} onClick={runQuery}>
											运行查询
										</Button>
									</Space>
								}
							>
								{previewState?.state === "error" && <ErrorNotice locale={locale} error={previewState.error} />}
								{previewState?.state === "loading" && <Spin />}
								<Input.TextArea
									readOnly
									autoSize={{ minRows: 10, maxRows: 18 }}
									value={previewState?.state === "loaded"
										? String(previewState.value.meta?.sql_preview ?? "")
										: queryState?.state === "loaded"
											? String(queryState.value.meta?.sql_preview ?? "")
											: ""}
									placeholder="点击“预览 SQL”后在这里查看编译结果"
								/>
								{(previewState?.state === "loaded" || queryState?.state === "loaded") && (
									<div className="mt-3 flex flex-wrap gap-2">
										{(previewState?.state === "loaded" ? previewState.value.meta?.security_applied : queryState?.state === "loaded" ? queryState.value.meta?.security_applied : [])?.map((item) => (
											<Tag key={item} color="processing">{item}</Tag>
										))}
										{(previewState?.state === "loaded" ? previewState.value.meta?.warnings : queryState?.state === "loaded" ? queryState.value.meta?.warnings : [])?.map((item) => (
											<Tag key={item} color="warning">{item}</Tag>
										))}
									</div>
								)}
							</Card>

							<Card title="结果渲染" extra={queryState?.state === "loaded" ? <Tag color="green">{queryState.value.meta?.row_count ?? 0} 行</Tag> : null}>
								{queryState?.state === "error" && <ErrorNotice locale={locale} error={queryState.error} />}
								{queryState?.state === "loading" && (
									<div className="loading-container" style={{ padding: 32 }}>
										<Spin size="large" />
									</div>
								)}
								{queryState == null && <Empty description="运行查询后显示结果" />}
								{queryState?.state === "loaded" && currentChartData && (
									<Space direction="vertical" size={16} style={{ width: "100%" }}>
											<ChartRenderer display={displayType} data={currentChartData} />
										<Table
											size="small"
											pagination={resultRows.length > 20 ? { pageSize: 20 } : false}
											columns={resultColumns}
											dataSource={resultRows}
											scroll={{ x: true }}
										/>
									</Space>
								)}
							</Card>

							{promoteState?.state === "loaded" && (
								<Card title="提升草案">
									<Typography.Paragraph>
										<Typography.Text strong>模型名：</Typography.Text>
										{promoteState.value.model_name || "-"}
									</Typography.Paragraph>
									<Typography.Paragraph>
										<Typography.Text strong>SQL：</Typography.Text>
									</Typography.Paragraph>
									<Input.TextArea readOnly autoSize={{ minRows: 8, maxRows: 16 }} value={String(promoteState.value.sql ?? "")} />
									<Typography.Paragraph style={{ marginTop: 12 }}>
										<Typography.Text strong>schema.yml：</Typography.Text>
									</Typography.Paragraph>
									<Input.TextArea readOnly autoSize={{ minRows: 8, maxRows: 16 }} value={String(promoteState.value.schema_yml ?? "")} />
								</Card>
							)}
							{promoteState?.state === "error" && <ErrorNotice locale={locale} error={promoteState.error} />}
						</Space>
					</div>

					<Modal
						open={vdsModalOpen}
						title="保存为虚拟数据集"
						onCancel={() => setVdsModalOpen(false)}
						onOk={saveVirtualDataset}
						confirmLoading={savingVds}
					>
						<Space direction="vertical" style={{ width: "100%" }}>
							<Input value={name} onChange={(event) => setName(event.target.value)} placeholder="虚拟数据集名称" />
							<Input.TextArea value={description} onChange={(event) => setDescription(event.target.value)} autoSize={{ minRows: 2, maxRows: 4 }} />
						</Space>
					</Modal>
				</>
			)}
		</div>
	);
}
