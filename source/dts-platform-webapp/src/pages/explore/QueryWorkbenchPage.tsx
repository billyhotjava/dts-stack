import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { useLocation, useNavigate } from "react-router";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Checkbox } from "@/ui/checkbox";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Textarea } from "@/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/ui/tabs";
import {
	executeExplore,
	saveExploreResult,
	listQueryExecutions,
	listSavedQueries,
	getSavedQuery,
	listDatasets,
	getDataset,
} from "@/api/platformApi";
import { CLASSIFICATION_LABELS_ZH, normalizeClassification, type ClassificationLevel } from "@/utils/classification";
import { GLOBAL_CONFIG } from "@/global-config";
import { useActiveDept } from "@/store/contextStore";
import { SqlWorkbenchExperimental } from "@/components/sql/SqlWorkbenchExperimental";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Drawer, DrawerContent, DrawerDescription, DrawerHeader, DrawerTitle } from "@/ui/drawer";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/ui/tooltip";

const CLASSIFICATION_META: Record<ClassificationLevel, { label: string; tone: string }> = {
	TOP_SECRET: { label: CLASSIFICATION_LABELS_ZH.TOP_SECRET, tone: "bg-rose-500/10 text-rose-500" },
	SECRET: { label: CLASSIFICATION_LABELS_ZH.SECRET, tone: "bg-amber-500/10 text-amber-500" },
	INTERNAL: { label: CLASSIFICATION_LABELS_ZH.INTERNAL, tone: "bg-sky-500/10 text-sky-500" },
	PUBLIC: { label: CLASSIFICATION_LABELS_ZH.PUBLIC, tone: "bg-emerald-500/10 text-emerald-600" },
};

type Classification = ClassificationLevel;

type DatasetField = {
	name: string;
	type: string;
	description?: string;
	lineage?: string;
	term?: string;
};

type Dataset = {
    id: string;
    name: string;
    source: string;
    database: string;
    schema: string;
    classification: Classification;
    rowCount: number;
    description?: string;
    fields: DatasetField[];
};

function toUiDataset(apiItem: any): Dataset {
	const dataLevel: string = String(apiItem.dataLevel || "").toUpperCase();
	const fallback = normalizeClassification(apiItem.classification);
	const derived: Classification =
		dataLevel === "DATA_TOP_SECRET"
			? "TOP_SECRET"
			: dataLevel === "DATA_SECRET"
			? "SECRET"
			: dataLevel === "DATA_INTERNAL"
			? "INTERNAL"
			: dataLevel === "DATA_PUBLIC"
			? "PUBLIC"
			: fallback;
	return {
		id: String(apiItem.id),
		name: String(apiItem.hiveTable || apiItem.name || apiItem.id),
		source: String(apiItem.trinoCatalog || "default"),
		database: String(apiItem.trinoCatalog || ""),
		schema: String(apiItem.hiveDatabase || ""),
		classification: derived,
		rowCount: 0,
		description: undefined,
		fields: [],
	};
}

type AggregationFunction =
	| "SUM"
	| "AVG"
	| "COUNT"
	| "COUNT_DISTINCT"
	| "MIN"
	| "MAX"
	| "STDDEV_SAMP"
	| "STDDEV_POP"
	| "VARIANCE_SAMP"
	| "VARIANCE_POP"
	| "COLLECT_SET"
	| "COLLECT_LIST";

type Aggregation = {
	id: string;
	field: string;
	fn: AggregationFunction;
};

type FilterOperator = "=" | ">" | ">=" | "<" | "<=" | "<>" | "LIKE";

type VisualFilter = {
	id: string;
	field: string;
	operator: FilterOperator;
	value: string;
};

type VisualSort = {
	id: string;
	field: string;
	direction: "ASC" | "DESC";
};

type VisualQueryState = {
	fields: string[];
	groupings: string[];
	aggregations: Aggregation[];
	filters: VisualFilter[];
	sorters: VisualSort[];
	limit: number;
};

type ResultRow = Record<string, string | number | boolean | null>;

type RunResult = {
	timestamp: Date;
	durationMs: number;
	connectMillis?: number;
	queryMillis?: number;
	rowCount: number;
	headers: string[];
	rows: ResultRow[];
	effectiveSql?: string;
	executionId?: string;
};

type ExecRecord = {
	id: string;
	status: string;
	startedAt?: string;
	finishedAt?: string;
	rowCount?: number;
	datasetName?: string;
	classification?: string;
	durationMs?: number;
	sqlText?: string;
};
type SavedQueryItem = { id: string; name?: string; title?: string; updatedAt?: string };
type TableItem = { id: string; name: string };

const QUOTA_USAGE = [
	{ name: "本日剩余扫描量", value: "1.2 TB / 5 TB" },
	{ name: "本月 API 并发", value: "3 / 15" },
	{ name: "当前会话资源组", value: "analysis-medium" },
];

const AGGREGATION_FUNCTION_OPTIONS: Array<{ value: AggregationFunction; label: string; hint: string }> = [
	{ value: "SUM", label: "SUM", hint: "求和" },
	{ value: "AVG", label: "AVG", hint: "平均值" },
	{ value: "COUNT", label: "COUNT", hint: "总行数" },
	{ value: "COUNT_DISTINCT", label: "COUNT_DISTINCT", hint: "去重计数" },
	{ value: "MAX", label: "MAX", hint: "最大值" },
	{ value: "MIN", label: "MIN", hint: "最小值" },
	{ value: "STDDEV_SAMP", label: "STDDEV_SAMP", hint: "样本标准差" },
	{ value: "STDDEV_POP", label: "STDDEV_POP", hint: "总体标准差" },
	{ value: "VARIANCE_SAMP", label: "VARIANCE_SAMP", hint: "样本方差" },
	{ value: "VARIANCE_POP", label: "VARIANCE_POP", hint: "总体方差" },
	{ value: "COLLECT_SET", label: "COLLECT_SET", hint: "去重列表" },
	{ value: "COLLECT_LIST", label: "COLLECT_LIST", hint: "全量列表" },
];

function createDefaultVisualState(): VisualQueryState {
	const fields: string[] = [];
	return {
		fields,
		groupings: [],
		aggregations: [],
		filters: [],
		sorters: [],
		limit: 100,
	};
}

function classificationBadge(level: Classification) {
	const meta = CLASSIFICATION_META[level];
	return (
		<span className={`inline-flex items-center rounded px-1.5 py-0.5 text-xs font-semibold ${meta.tone}`}>
			{meta.label}
		</span>
	);
}

function buildTableReference(dataset: Dataset) {
	const segments = [dataset.database, dataset.schema, dataset.name]
		.map((segment) => (segment ?? "").trim())
		.filter((segment) => segment.length > 0);
	if (segments.length === 0) {
		return "";
	}
	return segments.join(".");
}

function sanitizeAliasCore(raw: string) {
	return raw.replace(/[^a-zA-Z0-9_]/g, "_").replace(/^_+|_+$/g, "") || "col";
}

function buildAggregationExpression(aggregation: Aggregation, fallbackField: string): string {
	const rawField = aggregation.field && aggregation.field.trim().length ? aggregation.field.trim() : "*";
	const fallbackCandidate = fallbackField && fallbackField.trim().length ? fallbackField.trim() : "1";
	const fallback = fallbackCandidate === "*" ? "1" : fallbackCandidate;
	const expressionField = rawField === "*" ? fallback : rawField;
	const aliasSeed = rawField === "*" ? (fallback === "1" ? "value" : fallback) : rawField;
	const alias = `${aggregation.fn.toLowerCase()}_${sanitizeAliasCore(aliasSeed === "*" ? "all" : aliasSeed)}`;

	switch (aggregation.fn) {
		case "COUNT":
			return `${rawField === "*" ? "COUNT(*)" : `COUNT(${expressionField})`} AS ${alias}`;
		case "COUNT_DISTINCT": {
			if (rawField === "*" || expressionField.trim().length === 0) {
				return `COUNT(*) AS ${alias}`;
			}
			return `COUNT(DISTINCT ${expressionField}) AS ${alias}`;
		}
		case "COLLECT_SET":
			return `COLLECT_SET(${expressionField === "*" ? fallback : expressionField}) AS ${alias}`;
		case "COLLECT_LIST":
			return `COLLECT_LIST(${expressionField === "*" ? fallback : expressionField}) AS ${alias}`;
		case "STDDEV_SAMP":
		case "STDDEV_POP":
		case "VARIANCE_SAMP":
		case "VARIANCE_POP":
			return `${aggregation.fn}(${expressionField === "*" ? fallback : expressionField}) AS ${alias}`;
		default:
			return `${aggregation.fn}(${expressionField === "*" ? fallback : expressionField}) AS ${alias}`;
	}
}

function generateSQLFromVisual(state: VisualQueryState, dataset?: Dataset) {
	if (!dataset) return "";
	const tableRef = buildTableReference(dataset) || dataset.name || dataset.schema || "";
	const selectFragments = state.fields.length ? state.fields : ["*"];
	const fallbackForAgg =
		state.fields[0] ??
		state.groupings[0] ??
		(dataset.fields.find((field) => field.name)?.name ?? dataset.fields[0]?.name ?? "1");
	const aggFragments = state.aggregations.map((aggregation) => buildAggregationExpression(aggregation, fallbackForAgg || "*"));
	const selectClause = [...selectFragments, ...aggFragments].join(", \n    ");
	const filters = state.filters.map((filter) => `${filter.field} ${filter.operator} '${filter.value}'`).join(" AND ");
	const orderClause = state.sorters.map((sorter) => `${sorter.field} ${sorter.direction}`).join(", ");
	const groupSource = state.groupings.length ? state.groupings : state.fields;
	const groupClause =
		state.aggregations.length && groupSource.length
			? Array.from(
					new Set(
						groupSource
							.map((item) => item.trim())
							.filter((item) => item.length > 0)
							.filter((item) => item !== "*"),
					),
			  )
			: [];
	if (!tableRef) {
		return "SELECT 1";
	}
	return [
		`SELECT\n    ${selectClause || "*"}`,
		`FROM ${tableRef}`,
		filters ? `WHERE ${filters}` : null,
		groupClause.length ? `GROUP BY ${groupClause.join(", ")}` : null,
		orderClause ? `ORDER BY ${orderClause}` : null,
		`LIMIT ${state.limit}`,
	]
		.filter(Boolean)
		.join("\n");
}

function normalizeValue(value: unknown): string | number | boolean | null {
	if (value === null || value === undefined) return null;
	if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
		return value;
	}
	if (value instanceof Date) {
		return value.toISOString();
	}
	if (typeof value === "object") {
		try {
			return JSON.stringify(value);
		} catch {
			return String(value);
		}
	}
	return String(value);
}

function normalizeRow(row: any): ResultRow {
	const normalized: ResultRow = {};
	if (!row || typeof row !== "object") {
		return normalized;
	}
	Object.entries(row as Record<string, unknown>).forEach(([column, value]) => {
		normalized[column] = normalizeValue(value);
	});
	return normalized;
}

function toNumber(value: unknown): number | undefined {
	if (typeof value === "number" && Number.isFinite(value)) {
		return value;
	}
	if (typeof value === "string") {
		const parsed = Number(value);
		if (!Number.isNaN(parsed)) {
			return parsed;
		}
	}
	return undefined;
}

function extractErrorInfo(error: any): { message: string; detail?: string } {
	if (!error) {
		return { message: "查询失败，请稍后重试" };
	}
	const response = (error as any)?.response;
	const data = response?.data;
	const messageSources = [
		data?.message,
		data?.error,
		(error as any)?.message,
	];
	const message =
		messageSources.find((item) => typeof item === "string" && item.trim().length > 0)?.trim() ??
		"查询失败，请稍后重试";
	const detailSources = [data?.detail, data?.cause, data?.stack, response?.statusText];
	const detail = detailSources.find((item) => typeof item === "string" && item.trim().length > 0)?.trim();
	return { message, detail };
}

type VisualBuilderProps = {
	fields: DatasetField[];
	dataset?: Dataset | null;
	state: VisualQueryState;
	onToggleField: (field: string, checked: boolean) => void;
	onAggregationChange: (id: string, patch: Partial<Aggregation>) => void;
	onAggregationAdd: () => void;
	onAggregationRemove: (id: string) => void;
	onFilterAdd: () => void;
	onFilterChange: (id: string, patch: Partial<VisualFilter>) => void;
	onFilterRemove: (id: string) => void;
	onSortAdd: () => void;
	onSortChange: (id: string, patch: Partial<VisualSort>) => void;
	onSortRemove: (id: string) => void;
	onLimitChange: (limit: number) => void;
	onGroupingToggle: (field: string, checked: boolean) => void;
	onGroupingsSync: () => void;
	onGroupingsClear: () => void;
};

function VisualQueryBuilder({
	fields,
	dataset,
	state,
	onToggleField,
	onAggregationAdd,
	onAggregationChange,
	onAggregationRemove,
	onFilterAdd,
	onFilterChange,
	onFilterRemove,
	onSortAdd,
	onSortChange,
	onSortRemove,
	onLimitChange,
	onGroupingToggle,
	onGroupingsSync,
	onGroupingsClear,
}: VisualBuilderProps) {
	const [fieldKeyword, setFieldKeyword] = useState("");
	const normalizedKeyword = fieldKeyword.trim().toLowerCase();
	const filteredFields = useMemo(() => {
		if (!normalizedKeyword) {
			return fields;
		}
		return fields.filter((field) => {
			const nameHit = field.name.toLowerCase().includes(normalizedKeyword);
			const typeHit = field.type.toLowerCase().includes(normalizedKeyword);
			const descHit = field.description?.toLowerCase().includes(normalizedKeyword);
			const termHit = field.term?.toLowerCase().includes(normalizedKeyword);
			return Boolean(nameHit || typeHit || descHit || termHit);
		});
	}, [fields, normalizedKeyword]);
	const focusFields = normalizedKeyword ? filteredFields : fields;
	const selectedInFocus = focusFields.filter((field) => state.fields.includes(field.name)).length;
	const allSelected = focusFields.length > 0 && selectedInFocus === focusFields.length;
	const hasSelection = selectedInFocus > 0;
	const toggleAllCheckedState = allSelected ? true : hasSelection ? "indeterminate" : false;
	const aggregationFieldOptions = useMemo(() => {
		const options = new Set<string>();
		options.add("*");
		fields.forEach((field) => options.add(field.name));
		state.aggregations.forEach((aggregation) => {
			if (aggregation.field) {
				options.add(aggregation.field);
			}
		});
		return Array.from(options);
	}, [fields, state.aggregations]);

	const handleToggleAllFields = (checked: boolean | "indeterminate") => {
		const nextChecked = checked === "indeterminate" ? true : Boolean(checked);
		const targetFields = focusFields;
		if (nextChecked) {
			targetFields.forEach((field) => {
				if (!state.fields.includes(field.name)) {
					onToggleField(field.name, true);
				}
			});
			return;
		}
		targetFields.forEach((field) => {
			if (state.fields.includes(field.name)) {
				onToggleField(field.name, false);
			}
		});
	};

	const generatedSql = useMemo(() => generateSQLFromVisual(state, dataset ?? undefined), [state, dataset]);
	const canCopySql = generatedSql.trim().length > 0;
	const datasetClassificationLabel =
		dataset && dataset.classification ? CLASSIFICATION_META[dataset.classification]?.label ?? dataset.classification : null;
	const handleCopySql = useCallback(async () => {
		if (!canCopySql) {
			return;
		}
		try {
			if (typeof navigator !== "undefined" && navigator.clipboard?.writeText) {
				await navigator.clipboard.writeText(generatedSql);
			} else if (typeof document !== "undefined") {
				const textarea = document.createElement("textarea");
				textarea.value = generatedSql;
				textarea.setAttribute("readonly", "");
				textarea.style.position = "absolute";
				textarea.style.left = "-9999px";
				document.body.appendChild(textarea);
				textarea.select();
				document.execCommand("copy");
				document.body.removeChild(textarea);
			}
			toast.success("SQL 已复制到剪贴板");
		} catch (error) {
			console.error(error);
			toast.error("复制失败，请手动复制");
		}
	}, [canCopySql, generatedSql]);

	return (
		<div className="grid gap-4 lg:grid-cols-2">
			<Card>
				<CardHeader className="flex flex-row items-center justify-between gap-4">
					<CardTitle className="text-sm font-semibold">字段选择</CardTitle>
					<label className="flex items-center gap-2 text-xs text-muted-foreground">
						<Checkbox
							checked={toggleAllCheckedState}
							onCheckedChange={handleToggleAllFields}
							aria-label="选择全部字段"
						/>
						<span>选择全部</span>
					</label>
				</CardHeader>
				<CardContent>
					<div className="mb-3 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
						<Input
							placeholder="搜索字段 / 类型 / 业务术语"
							value={fieldKeyword}
							onChange={(event) => setFieldKeyword(event.target.value)}
							className="h-8 w-full sm:w-64"
						/>
						<div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
							<Badge variant="outline" className="font-normal">
								已选字段 {state.fields.length}
							</Badge>
							<Badge variant="outline" className="font-normal">
								聚合 {state.aggregations.length}
							</Badge>
							<Badge variant="outline" className="font-normal">
								筛选 {state.filters.length}
							</Badge>
							<Badge variant="outline" className="font-normal">
								排序 {state.sorters.length}
							</Badge>
							<Badge variant="outline" className="font-normal">
								分组 {state.groupings.length || state.fields.length || 0}
							</Badge>
						</div>
					</div>
					<div className="rounded-md border border-dashed border-primary/30 bg-primary/5 p-3">
						<ScrollArea className="h-72 lg:h-[420px]">
							{filteredFields.length ? (
								<div className="space-y-2">
									{filteredFields.map((field) => (
										<label key={field.name} className="flex items-start gap-2">
											<Checkbox
												checked={state.fields.includes(field.name)}
												onCheckedChange={(checked) => onToggleField(field.name, Boolean(checked))}
											/>
											<div className="space-y-1">
												<div className="flex items-center gap-2 text-sm font-medium text-text-primary">
													{field.name}
													<span className="text-xs text-muted-foreground">{field.type}</span>
												</div>
												<p className="text-xs text-muted-foreground line-clamp-2">
													{field.description ?? field.term ?? "暂无字段描述"}
												</p>
											</div>
										</label>
									))}
								</div>
							) : (
								<div className="flex h-32 items-center justify-center text-xs text-muted-foreground">
									未匹配到字段，请调整检索条件
								</div>
							)}
						</ScrollArea>
					</div>
				</CardContent>
			</Card>
			<div className="space-y-4">
				<Card>
					<CardHeader>
						<CardTitle className="text-sm font-semibold">聚合配置</CardTitle>
					</CardHeader>
					<CardContent className="space-y-3">
						{state.aggregations.map((aggregation) => (
							<div key={aggregation.id} className="grid gap-2 lg:grid-cols-[1fr,1fr,auto]">
								<Select
									value={aggregation.field}
									onValueChange={(value) => onAggregationChange(aggregation.id, { field: value })}
								>
									<SelectTrigger>
										<SelectValue placeholder="字段" />
									</SelectTrigger>
									<SelectContent>
										{aggregationFieldOptions.map((fieldName) => (
											<SelectItem key={fieldName} value={fieldName}>
												{fieldName === "*" ? "* (全部)" : fieldName}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
								<Select
									value={aggregation.fn}
									onValueChange={(value) => onAggregationChange(aggregation.id, { fn: value as Aggregation["fn"] })}
								>
									<SelectTrigger>
										<SelectValue placeholder="选择聚合函数" />
									</SelectTrigger>
									<SelectContent>
										{AGGREGATION_FUNCTION_OPTIONS.map((option) => (
											<SelectItem key={option.value} value={option.value}>
												{option.label} · {option.hint}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
								<Button variant="ghost" size="sm" onClick={() => onAggregationRemove(aggregation.id)}>
									删除
								</Button>
							</div>
						))}
						<Button variant="outline" size="sm" onClick={onAggregationAdd}>
							新增聚合
						</Button>
						<div className="space-y-2 rounded-md border border-dashed border-primary/30 bg-primary/5 p-3">
							<div className="flex flex-wrap items-center justify-between gap-2 text-sm font-medium text-text-primary">
								<span>分组维度（GROUP BY）</span>
								<div className="flex flex-wrap items-center gap-2 text-xs">
									<Button variant="ghost" size="sm" onClick={onGroupingsSync} className="h-7 px-2">
										与已选字段同步
									</Button>
									<Button variant="ghost" size="sm" onClick={onGroupingsClear} className="h-7 px-2">
										清空
									</Button>
								</div>
							</div>
							<ScrollArea className="h-32">
								<div className="space-y-2">
									{fields.length ? (
										fields.map((field) => (
											<label key={field.name} className="flex items-center gap-2 rounded-md border border-transparent px-2 py-1 hover:border-primary/40">
												<Checkbox
													checked={state.groupings.includes(field.name)}
													onCheckedChange={(checked) => onGroupingToggle(field.name, Boolean(checked))}
												/>
												<div className="flex flex-col">
													<span className="text-sm font-medium text-text-primary">{field.name}</span>
													<span className="text-xs text-muted-foreground">
														{field.term ?? field.description ?? "未登记字段描述"}
													</span>
												</div>
											</label>
										))
									) : (
										<div className="flex h-24 items-center justify-center text-xs text-muted-foreground">暂无字段可选</div>
									)}
								</div>
							</ScrollArea>
							<p className="text-xs text-muted-foreground">
								未选择时默认按照上方字段分组；如需单指标汇总，可清空分组仅保留聚合函数。
							</p>
						</div>
					</CardContent>
				</Card>
				<Card>
					<CardHeader>
						<CardTitle className="text-sm font-semibold">筛选 & 排序</CardTitle>
					</CardHeader>
					<CardContent className="space-y-3">
						<div className="space-y-2">
							<div className="flex items-center justify-between text-sm font-medium text-text-primary">
								<span>筛选条件</span>
								<Button variant="outline" size="sm" onClick={onFilterAdd}>
									新增筛选
								</Button>
							</div>
							{state.filters.map((filter) => (
								<div key={filter.id} className="grid gap-2 lg:grid-cols-[1fr,auto,1fr,auto]">
									<Select value={filter.field} onValueChange={(value) => onFilterChange(filter.id, { field: value })}>
										<SelectTrigger>
											<SelectValue placeholder="字段" />
										</SelectTrigger>
										<SelectContent>
											{fields.map((field) => (
												<SelectItem key={field.name} value={field.name}>
													{field.name}
												</SelectItem>
											))}
										</SelectContent>
									</Select>
									<Select
										value={filter.operator}
										onValueChange={(value) => onFilterChange(filter.id, { operator: value as FilterOperator })}
									>
                            <SelectTrigger>
                                <SelectValue placeholder="选择运算符" />
                            </SelectTrigger>
										<SelectContent>
											{["=", "<>", ">", ">=", "<", "<=", "LIKE"].map((operator) => (
												<SelectItem key={operator} value={operator}>
													{operator}
												</SelectItem>
											))}
										</SelectContent>
									</Select>
									<Input
										value={filter.value}
										onChange={(event) => onFilterChange(filter.id, { value: event.target.value })}
										placeholder="值"
									/>
									<Button variant="ghost" size="sm" onClick={() => onFilterRemove(filter.id)}>
										删除
									</Button>
								</div>
							))}
						</div>
						<div className="space-y-2">
							<div className="flex items-center justify-between text-sm font-medium text-text-primary">
								<span>排序</span>
								<Button variant="outline" size="sm" onClick={onSortAdd}>
									新增排序
								</Button>
							</div>
							{state.sorters.map((sorter) => (
								<div key={sorter.id} className="grid gap-2 lg:grid-cols-[1fr,auto,auto]">
									<Select value={sorter.field} onValueChange={(value) => onSortChange(sorter.id, { field: value })}>
										<SelectTrigger>
											<SelectValue placeholder="字段" />
										</SelectTrigger>
										<SelectContent>
											{fields.map((field) => (
												<SelectItem key={field.name} value={field.name}>
													{field.name}
												</SelectItem>
											))}
										</SelectContent>
									</Select>
									<Select
										value={sorter.direction}
										onValueChange={(value) => onSortChange(sorter.id, { direction: value as "ASC" | "DESC" })}
									>
                            <SelectTrigger>
                                <SelectValue placeholder="选择方向" />
                            </SelectTrigger>
										<SelectContent>
											<SelectItem value="ASC">升序</SelectItem>
											<SelectItem value="DESC">降序</SelectItem>
										</SelectContent>
									</Select>
									<Button variant="ghost" size="sm" onClick={() => onSortRemove(sorter.id)}>
										删除
									</Button>
								</div>
							))}
						</div>
						<div className="space-y-2">
							<Label className="text-xs text-muted-foreground">行数上限</Label>
							<Input
								type="number"
								min={1}
								value={state.limit}
								onChange={(event) => onLimitChange(Number(event.target.value) || 1)}
							/>
						</div>
					</CardContent>
				</Card>
			</div>
			<Card className="lg:col-span-2">
				<CardHeader className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
					<div className="space-y-1">
						<CardTitle className="text-sm font-semibold">SQL 预览</CardTitle>
						<p className="text-xs text-muted-foreground">实时映射当前的可视化配置，已适配 Hive 语法。</p>
					</div>
					<div className="flex flex-wrap items-center gap-2">
						{dataset ? (
							<>
								<Badge variant="outline" className="font-normal">
									数据集：{dataset.name}
								</Badge>
								{datasetClassificationLabel ? (
									<Badge variant="outline" className="font-normal">
										敏感度：{datasetClassificationLabel}
									</Badge>
								) : null}
							</>
						) : (
							<Badge variant="outline" className="font-normal text-muted-foreground">
								未选择数据集
							</Badge>
						)}
						<Badge variant="secondary" className="font-normal bg-emerald-500/10 text-emerald-600">
							Hive 兼容
						</Badge>
						<Button size="sm" variant="outline" onClick={handleCopySql} disabled={!canCopySql}>
							<Icon icon="Copy" size={14} className="mr-1" />
							复制 SQL
						</Button>
					</div>
				</CardHeader>
				<CardContent className="space-y-3">
					<div className="rounded-md border bg-muted/40">
						<ScrollArea className="max-h-[220px]">
							<pre className="whitespace-pre-wrap break-words p-3 font-mono text-xs leading-6 text-text-primary">
								{canCopySql ? generatedSql : "请选择数据集并配置字段后自动生成 SQL 语句"}
							</pre>
						</ScrollArea>
					</div>
						<div className="flex flex-wrap gap-3 text-xs text-muted-foreground">
							<span>字段 {state.fields.length}</span>
							<span>聚合 {state.aggregations.length}</span>
							<span>分组 {state.groupings.length ? state.groupings.length : state.fields.length}</span>
							<span>筛选 {state.filters.length}</span>
							<span>排序 {state.sorters.length}</span>
							<span>LIMIT {state.limit}</span>
							{dataset ? <span>目标表 {buildTableReference(dataset) || dataset.name}</span> : null}
						</div>
				</CardContent>
			</Card>
		</div>
	);
}

const pickFirstText = (...values: unknown[]): string | undefined => {
	for (const value of values) {
		if (typeof value === "string") {
			const trimmed = value.trim();
			if (trimmed.length > 0) {
				return trimmed;
			}
		}
	}
	return undefined;
};

const normalizeTagsText = (tags: unknown): string | undefined => {
	if (Array.isArray(tags)) {
		const joined = tags
			.map((item) => (typeof item === "string" ? item.trim() : String(item ?? "").trim()))
			.filter((token) => token.length > 0)
			.join(", ");
		return joined.length > 0 ? joined : undefined;
	}
	if (typeof tags === "string") {
		const trimmed = tags.trim();
		return trimmed.length > 0 ? trimmed : undefined;
	}
	return undefined;
};

export default function QueryWorkbenchPage() {
	if (GLOBAL_CONFIG.enableSqlWorkbench) {
		return <SqlWorkbenchExperimental />;
	}

	const [remoteDatasets, setRemoteDatasets] = useState<Dataset[]>([]);
	const [isDatasetsLoading, setDatasetsLoading] = useState<boolean>(false);
	const [datasetsError, setDatasetsError] = useState<string | null>(null);
	const hasRemoteDatasets = remoteDatasets.length > 0;
	const datasets = remoteDatasets;
	const defaultDataset = datasets[0]?.id ?? "";
	const datasetRows = useMemo(
		() =>
			[...datasets].sort((a, b) => {
				const sourceCompare = a.source.localeCompare(b.source);
				if (sourceCompare !== 0) return sourceCompare;
				return a.name.localeCompare(b.name);
			}),
		[datasets],
	);
	const [selectedDatasetId, setSelectedDatasetId] = useState(defaultDataset);
	const selectedDataset = useMemo(
		() => datasets.find((dataset) => dataset.id === selectedDatasetId),
		[datasets, selectedDatasetId],
	);
	const selectedTableReference = useMemo(
		() => (selectedDataset ? buildTableReference(selectedDataset) : ""),
		[selectedDataset],
	);
	const [tables, setTables] = useState<TableItem[]>([]);
	const [selectedTableId, setSelectedTableId] = useState<string>("");
	const [columns, setColumns] = useState<DatasetField[]>([]);
	const [tableColumnsMap, setTableColumnsMap] = useState<Record<string, DatasetField[]>>({});
	const [visualQuery, setVisualQuery] = useState<VisualQueryState>(() => createDefaultVisualState());
	const visualSql = useMemo(() => generateSQLFromVisual(visualQuery, selectedDataset), [visualQuery, selectedDataset]);
	const [isRunning, setIsRunning] = useState(false);
	const [runResult, setRunResult] = useState<RunResult | null>(null);
	const [pageIndex, setPageIndex] = useState(0);
	const [pageSize, setPageSize] = useState(10);
	const [exportDialogOpen, setExportDialogOpen] = useState(false);
	const showDatasetEmptyHint = !isDatasetsLoading && !hasRemoteDatasets;
	const [drawerOpen, setDrawerOpen] = useState(false);
	const [drawerTab, setDrawerTab] = useState("history");
	const [execHistory, setExecHistory] = useState<ExecRecord[]>([]);
const [savedList, setSavedList] = useState<SavedQueryItem[]>([]);
const [lastExecId, setLastExecId] = useState<string | undefined>(undefined);
const [saveTtlDays, setSaveTtlDays] = useState<string>("7");
	const [saveName, setSaveName] = useState<string>("");
	const location = useLocation();
	const navigate = useNavigate();
	const [runStatus, setRunStatus] = useState<{ type: "success" | "error"; message: string; sql?: string; detail?: string } | null>(null);

	useEffect(() => {
		if (!selectedDatasetId && defaultDataset) {
			setSelectedDatasetId(defaultDataset);
		}
	}, [defaultDataset, selectedDatasetId]);

    const activeDept = useActiveDept();

    const reloadDatasets = useCallback(async () => {
        setDatasetsLoading(true);
        setDatasetsError(null);
        try {
            const resp: any = await listDatasets({ page: 0, size: 100 });
            const list = Array.isArray(resp?.content) ? resp.content : [];
            const ui: Dataset[] = list.map(toUiDataset);
            setRemoteDatasets(ui);
            if (ui.length) {
                const matchedDataset = ui.find((item: Dataset) => item.id === selectedDatasetId);
                const nextDatasetId = matchedDataset?.id ?? ui[0].id;
                setSelectedDatasetId(nextDatasetId ?? "");
            } else {
                setSelectedDatasetId("");
            }
		} catch (e: any) {
			console.error(e);
			const message = typeof e?.message === "string" ? e.message : "数据集加载失败";
			setDatasetsError(message);
		} finally {
			setDatasetsLoading(false);
        }
    }, [selectedDatasetId, activeDept]);

	const runSql = useCallback(
		async (sql: string, dataset?: Dataset | null) => {
			if (!dataset) {
				toast.error("请选择数据集");
				return;
			}
			try {
				setRunStatus(null);
				setIsRunning(true);
				const payload = { datasetId: dataset.id, sqlText: sql };
				const resp: any = await executeExplore(payload as any);
				const rawHeaders = Array.isArray(resp?.headers) ? resp.headers : [];
				const headers = rawHeaders.map((item: any) => String(item));
				const rawRows = Array.isArray(resp?.rows) ? resp.rows : [];
				const rows = rawRows.map((row: any) => normalizeRow(row));
				const resolvedHeaders = headers.length ? headers : rows.length ? Object.keys(rows[0]) : [];
				const duration = toNumber(resp?.durationMs);
				const connectMillis = toNumber(resp?.connectMillis);
				const queryMillis = toNumber(resp?.queryMillis);
				const executionId = typeof resp?.executionId === "string" ? resp.executionId : undefined;
				const rowCount = toNumber(resp?.rowCount) ?? rows.length;
				const effectiveSql = typeof resp?.effectiveSql === "string" ? resp.effectiveSql : sql;
				setRunResult({
					timestamp: new Date(),
					durationMs: duration ?? connectMillis ?? queryMillis ?? 0,
					connectMillis: connectMillis ?? undefined,
					queryMillis: queryMillis ?? undefined,
					rowCount,
					headers: resolvedHeaders,
					rows,
					effectiveSql,
					executionId,
				});
				setPageIndex(0);
				setLastExecId(executionId);
				setRunStatus({
					type: "success",
					message: `查询成功 · 返回 ${rowCount} 行`,
					sql: effectiveSql,
				});
				toast.success("查询成功");
				try {
					const execs: any = await listQueryExecutions();
					const list: ExecRecord[] = (Array.isArray(execs) ? execs : (execs?.data ?? [])).map((e: any) => ({
						id: e.id,
						status: e.status,
						startedAt: e.startedAt,
						finishedAt: e.finishedAt,
						rowCount: e.rowCount,
						datasetName: e.datasetName,
						classification: e.classification,
						durationMs: e.durationMs,
						sqlText: e.sqlText,
						executionId: e.executionId ?? e.id,
					}));
					setExecHistory(list);
				} catch {
					// ignore history reload failure
				}
			} catch (e) {
				console.error(e);
				const errorInfo = extractErrorInfo(e);
				setRunStatus({
					type: "error",
					message: errorInfo.message,
					detail: errorInfo.detail,
					sql,
				});
				toast.error(errorInfo.message);
			} finally {
				setIsRunning(false);
			}
		},
		[listQueryExecutions, toast]
	);

useEffect(() => {
	reloadDatasets();
}, [reloadDatasets]);

	useEffect(() => {
		if (exportDialogOpen) {
			const timestamp = new Date().toLocaleString();
			const defaultName = selectedDataset ? `${selectedDataset.name}-${timestamp}` : `查询结果-${timestamp}`;
			setSaveName((prev) => (prev ? prev : defaultName));
		} else {
			setSaveName("");
		}
	}, [exportDialogOpen, selectedDataset]);

	useEffect(() => {
		const state = location.state as { runSavedQuery?: { id: string; sqlText: string; datasetId?: string; name?: string } } | null;
		const payload = state?.runSavedQuery;
		if (!payload) return;
		if (payload.datasetId && isDatasetsLoading) {
			return;
		}
	const datasetFromId = payload.datasetId ? datasets.find((d) => d.id === payload.datasetId) : null;
	const fallbackDataset: Dataset | null =
		!datasetFromId && payload.datasetId
			? {
				id: payload.datasetId,
				name: payload.name || payload.datasetId,
				source: "",
				database: "",
				schema: "",
				classification: "INTERNAL",
				rowCount: 0,
				description: undefined,
				fields: [],
			}
			: null;
	const targetDataset = datasetFromId ?? fallbackDataset ?? selectedDataset ?? null;
		(async () => {
			try {
				if (datasetFromId) {
					setSelectedDatasetId(datasetFromId.id);
					setVisualQuery(createDefaultVisualState());
				} else if (fallbackDataset) {
					setSelectedDatasetId(fallbackDataset.id);
					setVisualQuery(createDefaultVisualState());
				}
				if (!targetDataset) {
					toast.error("请选择可用的数据集后再执行查询");
					return;
				}
				await runSql(payload.sqlText, targetDataset);
				setDrawerOpen(false);
				if (payload.name) {
					toast.success(`已执行保存的查询：${payload.name}`);
				}
			} catch (error) {
				console.error(error);
				toast.error("执行保存的查询失败");
			} finally {
				navigate(location.pathname, { replace: true, state: null });
			}
		})();
	}, [location.state, datasets, isDatasetsLoading, runSql, navigate, location.pathname, selectedDataset]);

	const updateDatasetFields = useCallback(
		(datasetId: string, fields: DatasetField[]) => {
			setRemoteDatasets((prev) =>
				prev.map((dataset) => (dataset.id === datasetId ? { ...dataset, fields } : dataset)),
			);
		},
		[setRemoteDatasets],
	);

	useEffect(() => {
		let cancelled = false;
		(async () => {
			if (!selectedDatasetId) {
				if (!cancelled) {
					setTables([]);
					setSelectedTableId("");
					setColumns([]);
					setTableColumnsMap({});
				}
				return;
			}
			try {
				const detail: any = await getDataset(selectedDatasetId);
				const rawTables: any[] = Array.isArray(detail?.tables) ? detail.tables : [];
				const mappedTables: TableItem[] = [];
				const columnsMap: Record<string, DatasetField[]> = {};

				for (const table of rawTables) {
					const tableId = String(table?.id ?? table?.tableName ?? table?.name ?? "").trim();
					if (!tableId) continue;
					const tableName = String(table?.name ?? table?.tableName ?? tableId);
					mappedTables.push({ id: tableId, name: tableName });
					const columnList = Array.isArray(table?.columns) ? table.columns : [];
					const mappedColumns: DatasetField[] = columnList
						.map((col: any) => {
							const columnName = String(col?.name ?? col?.columnName ?? "").trim();
							if (!columnName) return null;
							const readableName = pickFirstText(
								col?.displayName,
								col?.alias,
								col?.label,
								col?.bizName,
								col?.bizLabel,
								col?.cnName,
								col?.zhName,
								col?.nameZh,
								col?.nameCn,
								col?.comment,
							);
							const tagsText = normalizeTagsText(col?.tags);
							return {
								name: columnName,
								type: String(col?.dataType ?? "string"),
								description: pickFirstText(readableName, tagsText),
							} satisfies DatasetField;
						})
						.filter(Boolean) as DatasetField[];
					columnsMap[tableId] = mappedColumns;
				}

				if (cancelled) {
					return;
				}

				setTables(mappedTables);
				setTableColumnsMap(columnsMap);
				const matched = mappedTables.find((item) => item.id === selectedTableId)?.id;
				const fallback = mappedTables[0]?.id ?? "";
				const activeTableId = String(matched ?? fallback ?? "");
				if (activeTableId) {
					if (activeTableId !== selectedTableId) {
						setSelectedTableId(activeTableId);
					}
					const activeColumns = columnsMap[activeTableId] ?? [];
					setColumns(activeColumns);
					updateDatasetFields(selectedDatasetId, activeColumns);
				} else {
					setSelectedTableId("");
					setColumns([]);
					updateDatasetFields(selectedDatasetId, []);
				}
			} catch (error: any) {
				if (cancelled) {
					return;
				}
				console.error(error);
				setTables([]);
				setSelectedTableId("");
				setColumns([]);
				setTableColumnsMap({});
				updateDatasetFields(selectedDatasetId, []);
				const status = error?.response?.status;
				if (status === 403) {
					toast.error("无权获取该数据集的列信息");
				} else {
					toast.error("加载列信息失败");
				}
			}
		})();
		return () => {
			cancelled = true;
		};
	}, [selectedDatasetId, selectedTableId, updateDatasetFields]);

	useEffect(() => {
		const currentColumns = tableColumnsMap[selectedTableId] ?? [];
		setColumns(currentColumns);
		if (selectedDatasetId) {
			updateDatasetFields(selectedDatasetId, currentColumns);
		}
	}, [selectedTableId, tableColumnsMap, selectedDatasetId, updateDatasetFields]);

	useEffect(() => {
		setVisualQuery(createDefaultVisualState());
	}, [selectedDataset]);

	useEffect(() => {
		if (runResult) {
			setPageIndex(0);
		}
	}, [runResult]);

	useEffect(() => {
		// load execution history and saved queries for drawer
		(async () => {
			try {
				const execs: any = await listQueryExecutions();
				const list: ExecRecord[] = (Array.isArray(execs) ? execs : (execs?.data ?? [])).map((e: any) => ({
					id: e.id,
					status: e.status,
					startedAt: e.startedAt,
					finishedAt: e.finishedAt,
					rowCount: e.rowCount,
					datasetName: e.datasetName,
					classification: e.classification,
					durationMs: e.durationMs,
					sqlText: e.sqlText,
					executionId: e.executionId ?? e.id,
				}));
				setExecHistory(list);
			} catch {}
			try {
				const saved: any = await listSavedQueries();
				const list: SavedQueryItem[] = (Array.isArray(saved) ? saved : (saved?.data ?? [])).map((it: any) => ({
					id: it.id,
					name: it.name || it.title,
					title: it.title,
					updatedAt: it.lastModifiedDate,
				}));
				setSavedList(list);
			} catch {}
		})();
	}, []);


	const visualFields = columns.length ? columns : (selectedDataset?.fields ?? []);

	useEffect(() => {
		if (!visualFields.length) {
			return;
		}
		const available = new Set(visualFields.map((field) => field.name));
		setVisualQuery((prev) => {
			const filteredFields = prev.fields.filter((name) => available.has(name));
			const filteredGroupings = prev.groupings.filter((name) => available.has(name));
			const fieldsChanged =
				filteredFields.length !== prev.fields.length ||
				filteredFields.some((name, index) => name !== prev.fields[index]);
			const groupingsChanged =
				filteredGroupings.length !== prev.groupings.length ||
				filteredGroupings.some((name, index) => name !== prev.groupings[index]);
			if (!fieldsChanged && !groupingsChanged) {
				return prev;
			}
			return {
				...prev,
				fields: filteredFields,
				groupings: filteredGroupings,
			};
		});
	}, [visualFields]);

	const currentPageRows = useMemo(() => {
		if (!runResult) return [];
		const start = pageIndex * pageSize;
		return runResult.rows.slice(start, start + pageSize);
	}, [runResult, pageIndex, pageSize]);

	const columnOrder = useMemo(() => {
		if (!runResult) return [];
		if (runResult.headers.length) {
			return runResult.headers;
		}
		return Object.keys(runResult.rows[0] ?? {});
	}, [runResult]);

	const handleToggleField = (field: string, checked: boolean) => {
		setVisualQuery((prev) => {
			const nextFields = checked ? [...prev.fields, field] : prev.fields.filter((item) => item !== field);
			const candidateGroups = checked
				? [...prev.groupings, field]
				: prev.groupings.filter((item) => item !== field);
			const deduped = Array.from(new Set(candidateGroups));
			const orderedGroups = [
				...nextFields.filter((item) => deduped.includes(item)),
				...deduped.filter((item) => !nextFields.includes(item)),
			];
			return {
				...prev,
				fields: nextFields,
				groupings: orderedGroups,
			};
		});
	};

	const handleToggleGrouping = (field: string, checked: boolean) => {
		setVisualQuery((prev) => {
			const candidate = checked ? [...prev.groupings, field] : prev.groupings.filter((item) => item !== field);
			const deduped = Array.from(new Set(candidate));
			const ordered = [
				...prev.fields.filter((item) => deduped.includes(item)),
				...deduped.filter((item) => !prev.fields.includes(item)),
			];
			return {
				...prev,
				groupings: ordered,
			};
		});
	};

	const handleSyncGroupings = () => {
		setVisualQuery((prev) => ({
			...prev,
			groupings: [...prev.fields],
		}));
	};

	const handleClearGroupings = () => {
		setVisualQuery((prev) => ({
			...prev,
			groupings: [],
		}));
	};

	const handleAggregationChange = (aggregationId: string, patch: Partial<Aggregation>) => {
		setVisualQuery((prev) => ({
			...prev,
			aggregations: prev.aggregations.map((aggregation) =>
				aggregation.id === aggregationId ? { ...aggregation, ...patch } : aggregation,
			),
		}));
	};

	const handleFilterChange = (filterId: string, patch: Partial<VisualFilter>) => {
		setVisualQuery((prev) => ({
			...prev,
			filters: prev.filters.map((filter) => (filter.id === filterId ? { ...filter, ...patch } : filter)),
		}));
	};

	const handleSortChange = (sortId: string, patch: Partial<VisualSort>) => {
		setVisualQuery((prev) => ({
			...prev,
			sorters: prev.sorters.map((sorter) => (sorter.id === sortId ? { ...sorter, ...patch } : sorter)),
		}));
	};

	const handleAddAggregation = () => {
		const defaultField = visualFields[0]?.name?.trim() ?? "*";
		setVisualQuery((prev) => ({
			...prev,
			aggregations: [
				...prev.aggregations,
				{ id: `agg-${Date.now()}`, field: defaultField === "" ? "*" : defaultField, fn: "SUM" },
			],
			groupings: prev.aggregations.length === 0 && prev.groupings.length === 0 ? [...prev.fields] : prev.groupings,
		}));
	};

	const handleRemoveAggregation = (id: string) => {
		setVisualQuery((prev) => ({
			...prev,
			aggregations: prev.aggregations.filter((aggregation) => aggregation.id !== id),
		}));
	};

	const handleAddFilter = () => {
		setVisualQuery((prev) => ({
			...prev,
			filters: [
				...prev.filters,
				{ id: `filter-${Date.now()}`, field: visualFields[0]?.name ?? "", operator: "=", value: "" },
			],
		}));
	};

	const handleRemoveFilter = (id: string) => {
		setVisualQuery((prev) => ({
			...prev,
			filters: prev.filters.filter((filter) => filter.id !== id),
		}));
	};

	const handleAddSort = () => {
		setVisualQuery((prev) => ({
			...prev,
			sorters: [...prev.sorters, { id: `sort-${Date.now()}`, field: visualFields[0]?.name ?? "", direction: "DESC" }],
		}));
	};

	const handleRemoveSort = (id: string) => {
		setVisualQuery((prev) => ({
			...prev,
			sorters: prev.sorters.filter((sorter) => sorter.id !== id),
		}));
	};

	const handleLimitChange = (limit: number) => {
		setVisualQuery((prev) => ({ ...prev, limit }));
	};

    const handleRunQuery = async () => {
		const sql = visualSql?.trim();
		if (!sql) {
			toast.error("请先通过可视化查询器生成查询条件");
			return;
		}
        await runSql(sql, selectedDataset);
    };

	const handleSaveResult = async () => {
		if (!lastExecId) {
			toast.error("请先运行查询");
			return;
		}
		if (!saveName.trim()) {
			toast.error("请输入结果集名称");
			return;
		}
		try {
			const ttl = parseInt(saveTtlDays || "7", 10);
			await saveExploreResult(lastExecId, {
				name: saveName.trim(),
				ttlDays: Number.isNaN(ttl) ? 7 : ttl,
			});
			toast.success("已保存结果集");
			setExportDialogOpen(false);
		} catch (e) {
			console.error(e);
			toast.error("保存失败");
		}
	};

	const totalPages = runResult ? Math.ceil(runResult.rows.length / pageSize) : 0;

	return (
		<TooltipProvider>
			<div className="space-y-4">
				<Card>
					<CardHeader className="space-y-4">
						<CardTitle className="flex items-center justify-between text-base font-semibold">
							<span>数据源与数据集</span>
							<div className="flex items-center gap-2">
								<Button variant="outline" size="sm" onClick={() => setDrawerOpen(true)}>
									历史 / 计划 / 配额
								</Button>
							</div>
						</CardTitle>
						<div className="space-y-2 text-xs text-muted-foreground">
							{isDatasetsLoading ? <p>正在加载可用数据集...</p> : null}
							{datasetsError ? (
								<div className="flex items-center justify-between rounded-md border border-amber-200 bg-amber-50 px-3 py-2">
									<span className="text-amber-600">{datasetsError}</span>
									<Button variant="ghost" size="sm" onClick={reloadDatasets}>
										重试
									</Button>
								</div>
							) : null}
							{showDatasetEmptyHint && !datasetsError && !isDatasetsLoading ? (
								<div className="flex items-center justify-between rounded-md border border-slate-200 bg-slate-50 px-3 py-2">
									<span>当前没有可用的数据集，请联系管理员完成数据源配置。</span>
									<Button variant="ghost" size="sm" onClick={reloadDatasets}>
										刷新
									</Button>
								</div>
							) : null}
						</div>
						<div className="space-y-4">
							<div className="space-y-1">
								<Label className="text-xs text-muted-foreground">数据源 / 可访问数据集</Label>
								<div className="overflow-hidden rounded-md border">
									<table className="w-full table-fixed text-xs">
										<thead className="bg-muted/40">
											<tr className="text-left">
												<th className="px-3 py-2 w-[140px]">数据源</th>
												<th className="px-3 py-2">名称</th>
												<th className="px-3 py-2 w-[90px]">密级</th>
												<th className="px-3 py-2">库 / 表</th>
												<th className="px-3 py-2 w-[72px] text-center">操作</th>
											</tr>
										</thead>
										<tbody>
											{datasetRows.map((dataset) => {
												const isActive = dataset.id === selectedDatasetId;
												const tableRef = buildTableReference(dataset) || dataset.name;
												return (
													<tr
														key={dataset.id}
														className={`cursor-pointer border-b last:border-b-0 transition-colors ${isActive ? "bg-primary/5" : "hover:bg-muted/30"}`}
														onClick={() => setSelectedDatasetId(dataset.id)}
													>
														<td className="px-3 py-2 text-xs font-medium text-muted-foreground">{dataset.source}</td>
														<td className="px-3 py-2 font-medium text-foreground">{dataset.name}</td>
														<td className="px-3 py-2">{classificationBadge(dataset.classification)}</td>
														<td className="px-3 py-2 text-xs text-muted-foreground">{tableRef || "-"}</td>
														<td className="px-3 py-2 text-center">
															<button
																type="button"
																onClick={(event) => {
																	event.stopPropagation();
																	setSelectedDatasetId(dataset.id);
																}}
																className={`inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium transition ${isActive ? "border-primary bg-primary/10 text-primary" : "border-transparent bg-muted/50 text-muted-foreground hover:border-primary/40 hover:text-primary"}`}
															>
																使用
															</button>
														</td>
													</tr>
												);
											})}
											{!datasetRows.length && (
												<tr>
													<td className="px-3 py-4 text-center text-xs text-muted-foreground" colSpan={5}>
														{isDatasetsLoading ? "加载中…" : "当前暂无可用数据集"}
													</td>
												</tr>
											)}
										</tbody>
									</table>
								</div>
								{selectedDataset ? (
									<p className="text-xs text-muted-foreground">
										已选：{selectedDataset.source} · {selectedDataset.name}（{selectedTableReference || "未登记"}）
									</p>
								) : null}
							</div>
							<div className="space-y-1">
								<Label className="text-xs text-muted-foreground">当前表</Label>
								<Select
									value={selectedTableId}
									onValueChange={setSelectedTableId}
									disabled={!tables.length}
								>
									<SelectTrigger>
										<SelectValue placeholder={tables.length ? "选择表" : "暂无可选表"} />
									</SelectTrigger>
									<SelectContent>
										{tables.map((table) => (
											<SelectItem key={table.id} value={table.id}>
												{table.name}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
							</div>
						</div>
					</CardHeader>
					<CardContent className="space-y-4">
						<div className="flex flex-wrap items-center justify-between gap-2">
							<div className="flex items-center gap-2">
								<Button
									size="sm"
									onClick={handleRunQuery}
									disabled={isRunning || !selectedDataset || isDatasetsLoading}
								>
									{isRunning ? "运行中..." : "运行"}
								</Button>
								<Button size="sm" variant="outline" onClick={() => setIsRunning(false)} disabled={!isRunning}>
									停止
								</Button>
								<Button
									size="sm"
									variant="outline"
									onClick={() => setExportDialogOpen(true)}
									disabled={!lastExecId}
								>
									保存结果集
								</Button>
							</div>
							<div className="flex items-center gap-2 text-xs text-muted-foreground">
								<span>并发配额：中等</span>
								<span>超时：120s</span>
							</div>
						</div>
						<VisualQueryBuilder
							fields={visualFields}
							dataset={selectedDataset}
							state={visualQuery}
							onToggleField={handleToggleField}
							onAggregationAdd={handleAddAggregation}
							onAggregationChange={handleAggregationChange}
							onAggregationRemove={handleRemoveAggregation}
							onFilterAdd={handleAddFilter}
							onFilterChange={handleFilterChange}
							onFilterRemove={handleRemoveFilter}
							onSortAdd={handleAddSort}
							onSortChange={handleSortChange}
							onSortRemove={handleRemoveSort}
							onLimitChange={handleLimitChange}
							onGroupingToggle={handleToggleGrouping}
							onGroupingsSync={handleSyncGroupings}
							onGroupingsClear={handleClearGroupings}
						/>
					</CardContent>
				</Card>

				<Card>
					<CardHeader className="flex flex-wrap items-center justify-between gap-2">
						<CardTitle className="text-base font-semibold">运行状态</CardTitle>
						<div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
							{selectedDataset ? (
								<>
									{classificationBadge(selectedDataset.classification)}
									<span>Hive 表：{selectedTableReference || "未登记"}</span>
								</>
							) : (
								<span>未选择数据集</span>
							)}
						</div>
					</CardHeader>
					<CardContent className="space-y-3">
						{runStatus ? (
							<div
								className={`rounded-md border px-3 py-2 text-sm ${
									runStatus.type === "success"
										? "border-emerald-200 bg-emerald-50 text-emerald-700"
										: "border-rose-200 bg-rose-50 text-rose-700"
									}`}
							>
								{runStatus.message}
							</div>
						) : (
							<p className="text-sm text-muted-foreground">尚未执行查询。</p>
						)}
						{runStatus?.detail ? (
							<div className="space-y-1">
								<p className="text-xs text-muted-foreground">失败详情</p>
								<pre className="whitespace-pre-wrap rounded-md bg-rose-50 px-3 py-2 text-xs text-rose-700">
									{runStatus.detail}
								</pre>
							</div>
						) : null}
						{runStatus?.sql ? (
							<div className="space-y-1">
								<p className="text-xs text-muted-foreground">
									{runStatus.type === "error" ? "最后执行 SQL" : "有效 SQL"}
								</p>
								<pre className="whitespace-pre-wrap rounded-md bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
									{runStatus.sql}
								</pre>
							</div>
						) : null}
					</CardContent>
				</Card>

				<Card>
				<CardHeader className="flex items-center justify-between">
					<CardTitle className="text-base font-semibold">结果集</CardTitle>
				</CardHeader>
					<CardContent className="space-y-3">
						{runResult ? (
							<>
								<div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
									<span>
										执行时间：{runResult.timestamp.toLocaleString()} · 耗时 {runResult.durationMs} ms
									</span>
									<span>
										连接耗时：
										{runResult.connectMillis !== undefined ? `${runResult.connectMillis} ms` : "--"}
									</span>
									<span>
										查询耗时：
										{runResult.queryMillis !== undefined ? `${runResult.queryMillis} ms` : "--"}
									</span>
									<span>
										结果行数：{runResult.rowCount}
										{runResult.rowCount !== runResult.rows.length
											? `（已加载 ${runResult.rows.length}）`
											: ""}
									</span>
								</div>
								<div className="overflow-hidden rounded-md border">
									<table className="w-full border-collapse text-sm">
										<thead className="bg-muted/50">
											<tr>
												{columnOrder.map((column) => (
													<th key={column} className="border-b px-3 py-2 text-left font-medium">
														<Tooltip>
															<TooltipTrigger className="cursor-help">{column}</TooltipTrigger>
															<TooltipContent>
																<div className="space-y-1">
																	<p className="text-xs font-medium">数据血缘</p>
																	<p className="text-xs text-muted-foreground">
																		{selectedDataset?.fields.find((field) => field.name === column)?.lineage ??
																			"血缘未登记"}
																	</p>
																	<p className="text-xs font-medium">业务术语</p>
																	<p className="text-xs text-muted-foreground">
																		{selectedDataset?.fields.find((field) => field.name === column)?.term ?? "暂未关联"}
																	</p>
																</div>
															</TooltipContent>
														</Tooltip>
													</th>
												))}
											</tr>
										</thead>
										<tbody>
											{currentPageRows.map((row, rowIndex) => (
												<tr key={`row-${rowIndex}`} className="border-b last:border-b-0">
													{columnOrder.map((column) => {
														const value = row[column];
														return (
															<td key={`${rowIndex}-${column}`} className="px-3 py-2 text-xs text-text-secondary">
																{value === null || value === undefined
																	? ""
																	: typeof value === "number"
																	?
																		value.toLocaleString()
																	: String(value)}
															</td>
														);
													})}
												</tr>
											))}
										</tbody>
									</table>
								</div>
								<div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
									<div>
										第 {totalPages ? pageIndex + 1 : 0} / {totalPages || 1} 页
									</div>
									<div className="flex items-center gap-2">
										<Button
											variant="outline"
											size="sm"
											disabled={pageIndex === 0}
											onClick={() => setPageIndex((prev) => Math.max(prev - 1, 0))}
										>
											上一页
										</Button>
										<Button
											variant="outline"
											size="sm"
											disabled={pageIndex >= totalPages - 1}
											onClick={() => setPageIndex((prev) => Math.min(prev + 1, Math.max(totalPages - 1, 0)))}
										>
											下一页
										</Button>
										<Select value={String(pageSize)} onValueChange={(value) => setPageSize(Number(value))}>
											<SelectTrigger className="h-8 w-24">
												<SelectValue />
											</SelectTrigger>
											<SelectContent>
												{[10, 20, 50, 100].map((size) => (
													<SelectItem key={size} value={String(size)}>
														每页 {size}
													</SelectItem>
												))}
											</SelectContent>
										</Select>
									</div>
								</div>
							</>
						) : (
							<div className="flex h-32 items-center justify-center text-sm text-muted-foreground">
								暂无结果，运行查询后展示。
							</div>
						)}
					</CardContent>
				</Card>

				<Dialog open={exportDialogOpen} onOpenChange={setExportDialogOpen}>
					<DialogContent className="max-w-md">
						<DialogHeader>
							<DialogTitle>保存结果集</DialogTitle>
						</DialogHeader>
						<div className="space-y-4 text-sm">
							<p>保存将登记结果集并设置有效期，支持预览与删除。</p>
							<div className="space-y-2">
								<Label>结果集名称</Label>
								<Input value={saveName} onChange={(e) => setSaveName(e.target.value)} placeholder="例如：销售看板-本周明细" />
							</div>
							<div className="space-y-2">
								<Label>保存天数（TTL）</Label>
								<Input type="number" min={1} value={saveTtlDays} onChange={(e) => setSaveTtlDays(e.target.value)} />
							</div>
							<div className="space-y-2">
								<Label>说明</Label>
								<Textarea placeholder="可选：用途说明" />
							</div>
						</div>
						<DialogFooter>
							<Button variant="ghost" onClick={() => setExportDialogOpen(false)}>
								取消
							</Button>
							<Button onClick={handleSaveResult}>提交保存</Button>
						</DialogFooter>
					</DialogContent>
				</Dialog>

				<Drawer open={drawerOpen} onOpenChange={setDrawerOpen}>
					<DrawerContent>
						<DrawerHeader>
							<DrawerTitle>历史记录 / 保存查询</DrawerTitle>
							<DrawerDescription>基于当前账号权限展示，仅供参考。</DrawerDescription>
						</DrawerHeader>
						<div className="px-6 pb-6">
							<Tabs value={drawerTab} onValueChange={setDrawerTab} className="space-y-4">
								<TabsList>
									<TabsTrigger value="history">历史记录</TabsTrigger>
									<TabsTrigger value="saved">已保存查询</TabsTrigger>
									<TabsTrigger value="quota">资源配额</TabsTrigger>
								</TabsList>
								<TabsContent value="history">
									<ScrollArea className="h-64">
										<ul className="space-y-3 text-sm">
											{execHistory.map((item) => (
												<li key={item.id} className="rounded-md border px-3 py-2">
													<p className="font-medium text-text-primary">
														执行 {item.id.slice(0, 8)} · {item.status}
													</p>
													<p className="text-xs text-muted-foreground">
														{item.startedAt ?? "-"} → {item.finishedAt ?? "-"}
													</p>
													<p className="text-xs text-muted-foreground">
														{item.datasetName ?? "临时查询"}
														{item.classification ? ` · ${item.classification}` : ""}
														· 行数 {item.rowCount ?? "-"} · {item.durationMs ?? "-"} ms
													</p>
													{item.sqlText ? (
														<p className="text-xs text-muted-foreground break-all">
															SQL: {item.sqlText.length > 160 ? `${item.sqlText.slice(0, 160)}…` : item.sqlText}
														</p>
													) : null}
												</li>
											))}
										</ul>
									</ScrollArea>
								</TabsContent>
								<TabsContent value="saved">
									<ScrollArea className="h-64">
										<ul className="space-y-3 text-sm">
											{savedList.map((query) => (
												<li key={query.id} className="rounded-md border px-3 py-2">
													<p className="font-medium text-text-primary">{query.name ?? query.title ?? query.id}</p>
													<p className="text-xs text-muted-foreground">更新于 {query.updatedAt ?? "-"}</p>
													<Button
														variant="ghost"
														size="sm"
														onClick={async () => {
															try {
																const detail = await getSavedQuery(query.id);
																const sql = (detail as any)?.sqlText ?? (detail as any)?.data?.sqlText;
																const dsId = (detail as any)?.datasetId ?? (detail as any)?.data?.datasetId;
																if (!sql) {
																	toast.error("未获取到 SQL 文本");
																	return;
																}
																let targetDataset: Dataset | null = selectedDataset ?? null;
																if (dsId) {
																	const matched = datasets.find((d) => d.id === dsId) ?? null;
																	if (matched) {
																		targetDataset = matched;
																		setSelectedDatasetId(matched.id);
																		setVisualQuery(createDefaultVisualState());
																	} else {
																		targetDataset = {
																			id: dsId,
																			name: query.name || dsId,
																			source: "",
																			database: "",
																			schema: "",
																			classification: "INTERNAL",
																			rowCount: 0,
																			description: undefined,
																			fields: [],
																		};
																	}
																}
																if (!targetDataset) {
																	toast.error("请先选择可用的数据集后再执行查询");
																	return;
																}
																await runSql(sql, targetDataset);
																toast.success("已执行保存的查询");
																setDrawerOpen(false);
																setDrawerTab("history");
															} catch (e) {
																console.error(e);
																toast.error("加载失败");
															}
														}}
													>
														应用
													</Button>
												</li>
											))}
										</ul>
									</ScrollArea>
								</TabsContent>
								<TabsContent value="quota">
									<ul className="space-y-3 text-sm">
										{QUOTA_USAGE.map((item) => (
											<li key={item.name} className="rounded-md border px-3 py-2">
												<p className="font-medium text-text-primary">{item.name}</p>
												<p className="text-xs text-muted-foreground">{item.value}</p>
											</li>
										))}
									</ul>
								</TabsContent>
							</Tabs>
						</div>
					</DrawerContent>
				</Drawer>
			</div>
		</TooltipProvider>
	);
}
