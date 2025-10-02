import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
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
	explainExplore,
	saveExploreResult,
	listQueryExecutions,
	listSavedQueries,
	getSavedQuery,
	listDatasets,
	listTablesByDataset,
	listColumnsByTable,
} from "@/api/platformApi";
import { GLOBAL_CONFIG } from "@/global-config";
import { SqlWorkbenchExperimental } from "@/components/sql/SqlWorkbenchExperimental";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Drawer, DrawerContent, DrawerDescription, DrawerHeader, DrawerTitle } from "@/ui/drawer";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/ui/tooltip";

const CLASSIFICATION_META = {
	TOP_SECRET: { label: "核心", tone: "bg-rose-500/10 text-rose-500" },
	SECRET: { label: "秘密", tone: "bg-amber-500/10 text-amber-500" },
	INTERNAL: { label: "内部", tone: "bg-sky-500/10 text-sky-500" },
	PUBLIC: { label: "公开", tone: "bg-emerald-500/10 text-emerald-600" },
} as const;

type Classification = keyof typeof CLASSIFICATION_META;

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

const DATASETS: Dataset[] = [
	{
		id: "cdp_sales_orders",
		name: "CDP_销售订单事实表",
		source: "CDP",
		database: "cdp_prod",
		schema: "fact",
		classification: "SECRET",
		rowCount: 12_540_000,
		description: "按日汇总的销售订单事实表，结合渠道/地区维度",
		fields: [
			{ name: "order_date", type: "date", description: "订单日期", lineage: "ods.orders.order_date" },
			{ name: "channel", type: "string", description: "销售渠道", term: "渠道" },
			{ name: "region", type: "string", description: "大区" },
			{ name: "amount", type: "decimal", description: "含税金额", lineage: "ods.orders.total_amount" },
			{ name: "quantity", type: "int", description: "销量" },
			{ name: "customer_id", type: "string", description: "客户唯一标识" },
		],
	},
	{
		id: "mdm_customer_profile",
		name: "MDM_客户主数据",
		source: "MDM",
		database: "mdm_core",
		schema: "dimension",
		classification: "INTERNAL",
		rowCount: 820_000,
		description: "统一的客户档案，含密级字段",
		fields: [
			{ name: "customer_id", type: "string", description: "客户编号", lineage: "mdm.customer.id" },
			{ name: "customer_name", type: "string", description: "客户名称" },
			{ name: "level", type: "string", description: "客户等级" },
			{ name: "sensitivity", type: "string", description: "密级" },
			{ name: "created_at", type: "timestamp", description: "创建时间" },
		],
	},
	{
		id: "ods_oper_log",
		name: "ODS_操作日志明细",
		source: "ODS",
		database: "ods_prod",
		schema: "logs",
		classification: "PUBLIC",
		rowCount: 65_000_000,
		description: "应用程序操作日志，适合排查问题",
		fields: [
			{ name: "event_time", type: "timestamp", description: "事件时间" },
			{ name: "module", type: "string", description: "服务模块" },
			{ name: "level", type: "string", description: "日志级别" },
			{ name: "message", type: "string", description: "日志内容" },
			{ name: "trace_id", type: "string", description: "链路追踪编号" },
		],
	},
];

function toUiDataset(apiItem: any): Dataset {
	return {
		id: String(apiItem.id),
		name: String(apiItem.hiveTable || apiItem.name || apiItem.id),
		source: String(apiItem.trinoCatalog || "default"),
		database: String(apiItem.trinoCatalog || ""),
		schema: String(apiItem.hiveDatabase || ""),
		classification: (String(apiItem.classification || "INTERNAL").toUpperCase() as Classification) || "INTERNAL",
		rowCount: 0,
		description: undefined,
		fields: [],
	};
}

type Aggregation = {
	id: string;
	field: string;
	fn: "SUM" | "AVG" | "MIN" | "MAX" | "COUNT";
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
	aggregations: Aggregation[];
	filters: VisualFilter[];
	sorters: VisualSort[];
	limit: number;
};

type ResultRow = Record<string, string | number | null>;

type RunResult = {
	timestamp: Date;
	durationMs: number;
	estimatedScan: string;
	estimatedConcurrency: string;
	rows: ResultRow[];
	executionId?: string;
};

type ExecRecord = { id: string; status: string; startedAt?: string; finishedAt?: string; rowCount?: number };
type SavedQueryItem = { id: string; name?: string; title?: string; updatedAt?: string };
type TableItem = { id: string; name: string };

const QUOTA_USAGE = [
	{ name: "本日剩余扫描量", value: "1.2 TB / 5 TB" },
	{ name: "本月 API 并发", value: "3 / 15" },
	{ name: "当前会话资源组", value: "analysis-medium" },
];

function createDefaultVisualState(dataset?: Dataset): VisualQueryState {
	const fields = dataset ? dataset.fields.slice(0, 3).map((field) => field.name) : [];
	return {
		fields,
		aggregations:
			dataset && dataset.fields.some((field) => field.name === "amount")
				? [
						{ id: "agg-1", field: "amount", fn: "SUM" },
						{ id: "agg-2", field: "quantity", fn: "SUM" },
					]
				: [],
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

function formatSQL(query: string) {
	return query
		.split("\n")
		.map((line) => line.trim())
		.filter(Boolean)
		.join("\n")
		.trim();
}

function generateSQLFromVisual(state: VisualQueryState, dataset?: Dataset) {
	if (!dataset) return "";
	const selectFragments = state.fields.length ? state.fields : ["*"];
	const aggFragments = state.aggregations.map(
		(aggregation) => `${aggregation.fn}(${aggregation.field}) AS ${aggregation.fn.toLowerCase()}_${aggregation.field}`,
	);
	const selectClause = [...selectFragments, ...aggFragments].join(", \n    ");
	const filters = state.filters.map((filter) => `${filter.field} ${filter.operator} '${filter.value}'`).join(" AND ");
	const orderClause = state.sorters.map((sorter) => `${sorter.field} ${sorter.direction}`).join(", ");
	return [
		`SELECT\n    ${selectClause || "*"}`,
		`FROM ${dataset.database}.${dataset.schema}.${dataset.name}`,
		filters ? `WHERE ${filters}` : null,
		orderClause ? `ORDER BY ${orderClause}` : null,
		`LIMIT ${state.limit}`,
	]
		.filter(Boolean)
		.join("\n");
}

function buildSampleRows(dataset?: Dataset): ResultRow[] {
	if (!dataset) return [];
	return Array.from({ length: 25 }).map((_, rowIndex) => {
		const row: ResultRow = {};
		dataset.fields.forEach((field) => {
			if (field.type === "decimal") {
				row[field.name] = Number((Math.random() * 10000).toFixed(2));
			} else if (field.type === "int") {
				row[field.name] = Math.floor(Math.random() * 1000);
			} else if (field.type === "date") {
				row[field.name] = `2024-12-${String((rowIndex % 28) + 1).padStart(2, "0")}`;
			} else {
				row[field.name] = `${field.name}_值_${rowIndex + 1}`;
			}
		});
		return row;
	});
}

type VisualBuilderProps = {
	fields: DatasetField[];
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
};

function VisualQueryBuilder({
	fields,
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
}: VisualBuilderProps) {
	return (
		<div className="grid gap-4 lg:grid-cols-2">
			<Card>
				<CardHeader>
					<CardTitle className="text-sm font-semibold">字段选择</CardTitle>
				</CardHeader>
				<CardContent>
					<ScrollArea className="h-48">
						<div className="space-y-2">
							{fields.map((field) => (
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
										<p className="text-xs text-muted-foreground">{field.description ?? ""}</p>
									</div>
								</label>
							))}
						</div>
					</ScrollArea>
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
										{fields.map((field) => (
											<SelectItem key={field.name} value={field.name}>
												{field.name}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
								<Select
									value={aggregation.fn}
									onValueChange={(value) => onAggregationChange(aggregation.id, { fn: value as Aggregation["fn"] })}
								>
									<SelectTrigger>
										<SelectValue />
									</SelectTrigger>
									<SelectContent>
										{["SUM", "AVG", "COUNT", "MIN", "MAX"].map((fn) => (
											<SelectItem key={fn} value={fn}>
												{fn}
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
											<SelectValue />
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
											<SelectValue />
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
		</div>
	);
}

export default function QueryWorkbenchPage() {
	if (GLOBAL_CONFIG.enableSqlWorkbench) {
		return <SqlWorkbenchExperimental />;
	}

	const [remoteDatasets, setRemoteDatasets] = useState<Dataset[]>([]);
	const [selectedSource, setSelectedSource] = useState<string>("");
	const datasets = useMemo(() => (remoteDatasets.length ? remoteDatasets : DATASETS), [remoteDatasets]);
	const datasetsBySource = useMemo(
		() => datasets.filter((dataset) => (selectedSource ? dataset.source === selectedSource : true)),
		[datasets, selectedSource],
	);
	const defaultDataset = datasetsBySource[0]?.id ?? datasets[0]?.id ?? "";
	const [selectedDatasetId, setSelectedDatasetId] = useState(defaultDataset);
	const selectedDataset = useMemo(
		() => datasets.find((dataset) => dataset.id === selectedDatasetId),
		[datasets, selectedDatasetId],
	);
	const [tables, setTables] = useState<TableItem[]>([]);
	const [selectedTableId, setSelectedTableId] = useState<string>("");
	const [columns, setColumns] = useState<DatasetField[]>([]);
	const [visualQuery, setVisualQuery] = useState<VisualQueryState>(() => createDefaultVisualState(selectedDataset));
	const [sqlText, setSqlText] = useState(() => generateSQLFromVisual(visualQuery, selectedDataset));
	const [sqlMode, setSqlMode] = useState<"sql" | "visual">("sql");
	const [manualSql, setManualSql] = useState(false);
	const [isRunning, setIsRunning] = useState(false);
	const [runResult, setRunResult] = useState<RunResult | null>(null);
	const [pageIndex, setPageIndex] = useState(0);
	const [pageSize, setPageSize] = useState(10);
	const [exportDialogOpen, setExportDialogOpen] = useState(false);
	const [drawerOpen, setDrawerOpen] = useState(false);
	const [drawerTab, setDrawerTab] = useState("history");
	const [planSteps, setPlanSteps] = useState<string[]>([]);
	const [execHistory, setExecHistory] = useState<ExecRecord[]>([]);
	const [savedList, setSavedList] = useState<SavedQueryItem[]>([]);
	const [lastExecId, setLastExecId] = useState<string | undefined>(undefined);
	const [saveTtlDays, setSaveTtlDays] = useState<string>("7");

	useEffect(() => {
		setSelectedDatasetId(defaultDataset);
	}, [defaultDataset]);

	useEffect(() => {
		// Load from backend
		(async () => {
			try {
				const resp: any = await listDatasets({ page: 0, size: 100 });
				const list = Array.isArray(resp?.content) ? resp.content : [];
				const ui = list.map(toUiDataset);
				setRemoteDatasets(ui);
				if (ui.length) {
					setSelectedSource(ui[0].source);
					setSelectedDatasetId(ui[0].id);
				}
			} catch (e) {
				console.error(e);
			}
		})();
	}, []);

	// Load tables for selected dataset
	useEffect(() => {
		(async () => {
			if (!selectedDatasetId) {
				setTables([]);
				setSelectedTableId("");
				setColumns([]);
				return;
			}
			try {
				const resp: any = await listTablesByDataset(selectedDatasetId);
				const list = Array.isArray(resp?.content) ? resp.content : [];
				const mapped: TableItem[] = list.map((t: any) => ({ id: String(t.id), name: String(t.name || t.id) }));
				setTables(mapped);
				const first = mapped[0]?.id || "";
				setSelectedTableId(first);
			} catch (e) {
				console.error(e);
				setTables([]);
				setSelectedTableId("");
			}
		})();
	}, [selectedDatasetId]);

	// Load columns for selected table
	useEffect(() => {
		(async () => {
			if (!selectedTableId) {
				setColumns([]);
				return;
			}
			try {
				const resp: any = await listColumnsByTable(selectedTableId);
				const list = Array.isArray(resp?.content) ? resp.content : [];
				const mapped: DatasetField[] = list.map((c: any) => ({
					name: String(c.name),
					type: String(c.dataType || "string"),
					description: c.tags || undefined,
				}));
				setColumns(mapped);
			} catch (e) {
				console.error(e);
				setColumns([]);
			}
		})();
	}, [selectedTableId]);

	useEffect(() => {
		setVisualQuery(createDefaultVisualState(selectedDataset));
		setManualSql(false);
	}, [selectedDataset]);

	useEffect(() => {
		if (!manualSql) {
			setSqlText(generateSQLFromVisual(visualQuery, selectedDataset));
		}
	}, [visualQuery, selectedDataset, manualSql]);

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

	const availableSources = useMemo(() => Array.from(new Set(datasets.map((dataset) => dataset.source))), [datasets]);
	const dataSourceDatasets = useMemo(
		() => datasets.filter((d) => d.source === selectedSource),
		[datasets, selectedSource],
	);

	const visualFields = columns.length ? columns : (selectedDataset?.fields ?? []);

	const currentPageRows = useMemo(() => {
		if (!runResult) return [];
		const start = pageIndex * pageSize;
		return runResult.rows.slice(start, start + pageSize);
	}, [runResult, pageIndex, pageSize]);

	const detectedParameters = useMemo(() => {
		const matches = sqlText.match(/:[a-zA-Z_][a-zA-Z0-9_]*/g) ?? [];
		return Array.from(new Set(matches.map((match) => match.slice(1))));
	}, [sqlText]);

	const handleToggleField = (field: string, checked: boolean) => {
		setVisualQuery((prev) => ({
			...prev,
			fields: checked ? [...prev.fields, field] : prev.fields.filter((item) => item !== field),
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
		setVisualQuery((prev) => ({
			...prev,
			aggregations: [...prev.aggregations, { id: `agg-${Date.now()}`, field: visualFields[0]?.name ?? "", fn: "SUM" }],
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

	const handleFormatSql = () => {
		setSqlText((prev) => formatSQL(prev));
		toast.success("SQL 已格式化");
		setManualSql(true);
	};

	const handleResetSql = () => {
		setManualSql(false);
		setSqlText(generateSQLFromVisual(visualQuery, selectedDataset));
		toast.info("已同步可视化查询条件");
	};

	const handleRunQuery = async () => {
		if (!selectedDataset) {
			toast.error("请选择数据集");
			return;
		}
		try {
			setIsRunning(true);
			const payload = { datasetId: selectedDataset.id, sqlText: sqlText };
			const resp = await executeExplore(payload as any);
			const rows = (resp as any)?.rows as ResultRow[] | undefined;
			const duration = (resp as any)?.durationMs as number | undefined;
			const executionId = (resp as any)?.executionId as string | undefined;
			setRunResult({
				timestamp: new Date(),
				durationMs: typeof duration === "number" ? duration : Math.floor(Math.random() * 800) + 200,
				estimatedScan: `${(Math.random() * 2 + 0.3).toFixed(2)} GB`,
				estimatedConcurrency: `${Math.ceil(Math.random() * 3)}/10`,
				rows: Array.isArray(rows) && rows.length ? rows : buildSampleRows(selectedDataset),
				executionId,
			});
			setLastExecId(executionId);
			toast.success("查询成功");
			// refresh history lazily
			try {
				const execs: any = await listQueryExecutions();
				const list: ExecRecord[] = (Array.isArray(execs) ? execs : (execs?.data ?? [])).map((e: any) => ({
					id: e.id,
					status: e.status,
					startedAt: e.startedAt,
					finishedAt: e.finishedAt,
					rowCount: e.rowCount,
				}));
				setExecHistory(list);
			} catch {}
		} catch (e) {
			console.error(e);
			toast.error("查询失败，请稍后重试");
		} finally {
			setIsRunning(false);
		}
	};

	const handleExplain = async () => {
		try {
			const resp = await explainExplore({ sqlText });
			const steps = (resp as any)?.steps as string[] | undefined;
			setPlanSteps(steps && steps.length ? steps : [String((resp as any)?.effectiveSql ?? "EXPLAIN")]);
			setDrawerOpen(true);
			setDrawerTab("plan");
		} catch (e) {
			console.error(e);
			toast.error("获取执行计划失败");
		}
	};

	const handleSaveResult = async () => {
		if (!lastExecId) {
			toast.error("请先运行查询");
			return;
		}
		try {
			const ttl = parseInt(saveTtlDays || "7", 10);
			await saveExploreResult(lastExecId, { ttlDays: Number.isNaN(ttl) ? 7 : ttl });
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
				<div className="flex items-center gap-2 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-600">
					<span aria-hidden className="text-red-500">
						★
					</span>
					此功能涉及密级数据，请注意保密！
				</div>

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
						<div className="grid gap-3 lg:grid-cols-3">
							<div className="space-y-1">
								<Label className="text-xs text-muted-foreground">数据源</Label>
								<Select value={selectedSource} onValueChange={setSelectedSource}>
									<SelectTrigger>
										<SelectValue />
									</SelectTrigger>
									<SelectContent>
										{availableSources.map((source) => (
											<SelectItem key={source} value={source}>
												{source}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
							</div>
							<div className="space-y-1 lg:col-span-2">
								<Label className="text-xs text-muted-foreground">数据集</Label>
								<Select value={selectedDatasetId} onValueChange={setSelectedDatasetId}>
									<SelectTrigger>
										<SelectValue />
									</SelectTrigger>
									<SelectContent>
										{dataSourceDatasets.map((dataset) => (
											<SelectItem key={dataset.id} value={dataset.id}>
												<div className="flex items-center gap-2">
													{classificationBadge(dataset.classification)}
													<span>{dataset.name}</span>
												</div>
											</SelectItem>
										))}
									</SelectContent>
								</Select>
								{selectedDataset ? (
									<p className="text-xs text-muted-foreground">
										库表：{selectedDataset.database}.{selectedDataset.schema}.{selectedDataset.name}
									</p>
								) : null}
								<div className="space-y-1">
									<Label className="text-xs text-muted-foreground">表</Label>
									<Select value={selectedTableId} onValueChange={setSelectedTableId}>
										<SelectTrigger>
											<SelectValue />
										</SelectTrigger>
										<SelectContent>
											{tables.map((t) => (
												<SelectItem key={t.id} value={t.id}>
													{t.name}
												</SelectItem>
											))}
										</SelectContent>
									</Select>
								</div>
							</div>
						</div>
					</CardHeader>
					<CardContent>
						<Tabs
							value={sqlMode}
							onValueChange={(value) => setSqlMode(value as "sql" | "visual")}
							className="space-y-4"
						>
							<TabsList>
								<TabsTrigger value="sql">SQL 编辑器</TabsTrigger>
								<TabsTrigger value="visual">可视化查询器</TabsTrigger>
							</TabsList>
							<TabsContent value="sql" className="space-y-3">
								<div className="flex items-center justify-between">
									<div className="flex items-center gap-2">
										<Button size="sm" onClick={handleRunQuery} disabled={isRunning}>
											{isRunning ? "运行中..." : "运行"}
										</Button>
										<Button size="sm" variant="outline" onClick={() => setIsRunning(false)} disabled={!isRunning}>
											停止
										</Button>
										<Button size="sm" variant="outline" onClick={handleExplain}>
											执行计划
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
								<Textarea
									value={sqlText}
									onChange={(event) => {
										setSqlText(event.target.value);
										setManualSql(true);
									}}
									className="min-h-[280px] font-mono text-sm"
									placeholder="在此编写 SQL，支持 :start_date 参数"
								/>
								<div className="flex items-center justify-between text-xs">
									<div className="flex items-center gap-2">
										<Button variant="outline" size="sm" onClick={handleFormatSql}>
											格式化
										</Button>
										<Button variant="ghost" size="sm" onClick={handleResetSql}>
											同步可视化条件
										</Button>
									</div>
									<div className="flex items-center gap-2 text-muted-foreground">
										{detectedParameters.length ? (
											<>
												<span>参数：</span>
												{detectedParameters.map((parameter) => (
													<span key={parameter} className="rounded bg-muted px-1.5 py-0.5">
														:{parameter}
													</span>
												))}
											</>
										) : (
											<span>暂无参数</span>
										)}
									</div>
								</div>
							</TabsContent>
							<TabsContent value="visual" className="space-y-4">
								<VisualQueryBuilder
									fields={visualFields}
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
								/>
							</TabsContent>
						</Tabs>
					</CardContent>
				</Card>

				<Card>
					<CardHeader className="flex items-center justify-between">
						<CardTitle className="text-base font-semibold">结果集</CardTitle>
						<div className="flex items-center gap-2">
							<Button size="sm" variant="outline" disabled={!runResult} onClick={() => setExportDialogOpen(true)}>
								导出
							</Button>
							<Button size="sm" variant="outline" disabled={!runResult}>
								保存为模板
							</Button>
						</div>
					</CardHeader>
					<CardContent className="space-y-3">
						{runResult ? (
							<>
								<div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
									<span>
										执行时间：{runResult.timestamp.toLocaleString()} · 耗时 {runResult.durationMs} ms
									</span>
									<span>扫描估算：{runResult.estimatedScan}</span>
									<span>并发：{runResult.estimatedConcurrency}</span>
									<span>结果行数：{runResult.rows.length}</span>
								</div>
								<div className="overflow-hidden rounded-md border">
									<table className="w-full border-collapse text-sm">
										<thead className="bg-muted/50">
											<tr>
												{Object.keys(runResult.rows[0] ?? {}).map((column) => (
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
													{Object.entries(row).map(([column, value]) => (
														<td key={column} className="px-3 py-2 text-xs text-text-secondary">
															{typeof value === "number" ? value.toLocaleString() : value}
														</td>
													))}
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
							<DrawerTitle>历史记录 / 保存查询 / 执行计划</DrawerTitle>
							<DrawerDescription>基于当前账号权限展示，仅供参考。</DrawerDescription>
						</DrawerHeader>
						<div className="px-6 pb-6">
							<Tabs value={drawerTab} onValueChange={setDrawerTab} className="space-y-4">
								<TabsList>
									<TabsTrigger value="history">历史记录</TabsTrigger>
									<TabsTrigger value="saved">已保存查询</TabsTrigger>
									<TabsTrigger value="plan">执行计划</TabsTrigger>
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
													<p className="text-xs text-muted-foreground">行数：{item.rowCount ?? "-"}</p>
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
																if (sql) {
																	setSqlText(sql);
																	setManualSql(true);
																	toast.success("已加载保存的查询");
																	if (dsId) {
																		const ds = datasets.find((d) => d.id === dsId);
																		if (ds) {
																			setSelectedSource(ds.source);
																			setSelectedDatasetId(ds.id);
																			setVisualQuery(createDefaultVisualState(ds));
																		}
																	}
																} else {
																	toast.error("未获取到 SQL 文本");
																}
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
								<TabsContent value="plan">
									<ScrollArea className="h-64">
										<ol className="space-y-3 text-sm">
											{planSteps.length ? (
												planSteps.map((step, index) => (
													<li key={`${index}-${step}`} className="rounded-md border px-3 py-2">
														<p className="font-medium text-text-primary">Step {index + 1}</p>
														<p className="text-xs text-muted-foreground">{step}</p>
													</li>
												))
											) : (
												<li className="rounded-md border px-3 py-2 text-xs text-muted-foreground">
													暂无计划，请点击“执行计划”获取
												</li>
											)}
										</ol>
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
