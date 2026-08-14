import { DatabaseOutlined, FileTextOutlined, LinkOutlined } from "@ant-design/icons";
import { Alert, Button, Input, Select, Space, Tag, Tooltip, Typography } from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import type { FilterValue, SorterResult } from "antd/es/table/interface";
import { type ReactNode, useCallback, useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { ClassificationTag } from "@/analytics/pages/screens/components/ClassificationTag";
import { ingestionTaskAPI } from "@/api/ingestion";
import dataSourcesService from "@/api/services/dataSourcesService";
import { JourneyContextBar } from "@/components/journey";
import { PageHeader } from "@/components/page-header";
import { actionColumn, toSpringSort, CompactTable } from "@/components/table";
import { formatTimestamp } from "@/utils/format";
import styles from "./AccessWorkspace.module.css";
import {
	type AccessHealth,
	type AccessKind,
	type AccessLifecycle,
	type AccessSourceKind,
	type AccessWorkspaceRow,
	parseAccessKind,
	toAccessWorkspaceRows,
} from "./accessWorkspaceAdapter";

const { Text } = Typography;

const KIND_META: Record<AccessKind, { label: string; description: string }> = {
	overview: { label: "接入概览", description: "统一查看数据库、API 与离线文件接入任务。" },
	database: { label: "数据库接入", description: "管理数据库表抽取、增量策略与入湖运行。" },
	api: { label: "API 接入", description: "管理 HTTP 资源、鉴权引用、分页游标与运行状态。" },
	file: { label: "离线文件接入", description: "管理文件批次、解析结果与发布前检查。" },
};

const LIFECYCLE_OPTIONS = [
	{ value: "all", label: "全部生命周期" },
	{ value: "active", label: "活跃" },
	{ value: "draft", label: "草稿" },
	{ value: "paused", label: "暂停" },
	{ value: "deleted", label: "已删除" },
	{ value: "unknown", label: "状态未识别" },
];

const HEALTH_OPTIONS = [
	{ value: "all", label: "全部健康状态" },
	{ value: "healthy", label: "健康" },
	{ value: "running", label: "运行中" },
	{ value: "attention", label: "需关注" },
	{ value: "not_evaluated", label: "未评估" },
];

const KIND_ICON: Record<AccessSourceKind, ReactNode> = {
	database: <DatabaseOutlined />,
	api: <LinkOutlined />,
	file: <FileTextOutlined />,
};

const renderLifecycleTag = (value: AccessLifecycle) => {
	const meta: Record<AccessLifecycle, { color?: string; label: string }> = {
		active: { color: "success", label: "活跃" },
		draft: { label: "草稿" },
		paused: { color: "warning", label: "暂停" },
		deleted: { color: "error", label: "已删除" },
		unknown: { label: "未识别" },
	};
	return <Tag color={meta[value].color}>{meta[value].label}</Tag>;
};

const renderHealthTag = (value: AccessHealth, reason: string) => {
	const meta: Record<AccessHealth, { color?: string; label: string }> = {
		healthy: { color: "success", label: "健康" },
		running: { color: "processing", label: "运行中" },
		attention: { color: "error", label: "需关注" },
		not_evaluated: { label: "未评估" },
	};
	return (
		<Tooltip title={reason}>
			<Tag color={meta[value].color}>{meta[value].label}</Tag>
		</Tooltip>
	);
};

const syncModeLabel = (value: string) => {
	const normalized = String(value || "").toLowerCase();
	if (normalized.includes("incremental")) return "增量同步";
	if (normalized.includes("full")) return "全量同步";
	return value || "未记录";
};

export const accessKeywordFromSearch = (search: string) => (new URLSearchParams(search).get("keyword") || "").trim();

/** 列表默认排序，同时也是用户取消表头排序后的回退值。 */
const DEFAULT_SORT = "lastModifiedDate,desc";

/**
 * 表头列 key → IngestionTask 实体字段。
 * 服务端分页下只有当前页数据，排序必须由后端完成，因此仅暴露实体上真实存在的字段；
 * 「来源 / 资源」「负责人 / 密级」「健康状态」由前端拼装派生，无法在后端排序，故不开放。
 */
export const ACCESS_SORT_FIELDS: Record<string, string> = {
	name: "name",
	syncMode: "syncMode",
	lifecycle: "status",
	lastRun: "lastExecutedAt",
};

export default function AccessWorkspace() {
	const navigate = useNavigate();
	const location = useLocation();
	const kind = parseAccessKind(location.pathname.split("/").filter(Boolean).at(-1));
	const initialKeyword = accessKeywordFromSearch(location.search);
	const [rows, setRows] = useState<AccessWorkspaceRow[]>([]);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string>();
	const [queryInput, setQueryInput] = useState(initialKeyword);
	const [query, setQuery] = useState(initialKeyword);
	const [lifecycle, setLifecycle] = useState<AccessLifecycle | "all">("all");
	const [health, setHealth] = useState<AccessHealth | "all">("all");
	const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
	const [sort, setSort] = useState(DEFAULT_SORT);
	const loadRequestIdRef = useRef(0);

	const load = useCallback(async () => {
		const requestId = ++loadRequestIdRef.current;
		setLoading(true);
		setError(undefined);
		try {
			const status = lifecycle !== "all" ? lifecycle : undefined;
			const [sourceSelections, taskPage] = await Promise.all([
				dataSourcesService.selections({ capability: "INGESTION_SOURCE" }),
				ingestionTaskAPI.getTasks({
					page: pagination.current - 1,
					size: pagination.pageSize,
					sort,
					status,
					sourceKind: kind === "overview" ? undefined : kind,
					query: query.trim() || undefined,
					health: health === "all" ? undefined : health,
				}),
			]);
			if (loadRequestIdRef.current !== requestId) return;
			const tasks = Array.isArray(taskPage.content) ? taskPage.content : [];
			const sources = Array.isArray(sourceSelections?.items) ? sourceSelections.items : [];
			setRows(toAccessWorkspaceRows(tasks, sources));
			setPagination((previous) => ({
				...previous,
				total: Number.isFinite(taskPage.totalElements) ? taskPage.totalElements : tasks.length,
			}));
		} catch {
			if (loadRequestIdRef.current !== requestId) return;
			setRows([]);
			setPagination((previous) => ({ ...previous, total: 0 }));
			setError("数据接入概览加载失败");
		} finally {
			if (loadRequestIdRef.current === requestId) setLoading(false);
		}
	}, [health, kind, lifecycle, pagination.current, pagination.pageSize, query, sort]);

	// biome-ignore lint/correctness/useExhaustiveDependencies: changing the route access kind must restart that workspace at page one.
	useEffect(() => {
		setPagination((previous) => ({ ...previous, current: 1 }));
	}, [kind]);

	useEffect(() => {
		const keyword = accessKeywordFromSearch(location.search);
		setQueryInput(keyword);
		setQuery(keyword);
		setPagination((previous) => ({ ...previous, current: 1 }));
	}, [location.search]);

	useEffect(() => {
		void load();
		return () => {
			loadRequestIdRef.current += 1;
		};
	}, [load]);

	const openCreate = (sourceKind: AccessSourceKind) => {
		navigate(`/foundation/data-sources/access/new?kind=${sourceKind}`);
	};

	const openDetail = (row: AccessWorkspaceRow) => {
		if (row.taskId != null) navigate(`/foundation/data-sources/access/${row.taskId}`);
	};

	const syncKeyword = (value: string) => {
		const keyword = value.trim();
		setQueryInput(keyword);
		setQuery(keyword);
		setPagination((previous) => ({ ...previous, current: 1 }));
		const params = new URLSearchParams(location.search);
		if (keyword) params.set("keyword", keyword);
		else params.delete("keyword");
		navigate({ pathname: location.pathname, search: params.toString() }, { replace: true });
	};

	const columns: ColumnsType<AccessWorkspaceRow> = [
		{
			title: "接入名称",
			dataIndex: "name",
			key: "name",
			width: 220,
			sorter: true,
			render: (_, row) => (
				<Button
					className={styles.nameButton}
					type="link"
					onClick={() => openDetail(row)}
					disabled={row.taskId == null}
					title={row.taskId == null ? undefined : `任务 #${row.taskId}`}
				>
					{row.name}
				</Button>
			),
		},
		{
			title: "接入方式",
			dataIndex: "kind",
			key: "kind",
			width: 125,
			render: (value: AccessSourceKind) => (
				<span className={`${styles.kindMark} ${styles[value]}`}>
					<span className={styles.kindDot} />
					{KIND_ICON[value]}
					{KIND_META[value].label.replace("接入", "")}
				</span>
			),
		},
		{
			title: "来源 / 资源",
			key: "source",
			width: 220,
			ellipsis: true,
			render: (_, row) => (
				<Space size={6}>
					<Text strong>{row.sourceName}</Text>
					<Tooltip title={`${row.sourceType} · ${row.resourceSummary}`}>
						<Text type="secondary">{row.resourceSummary}</Text>
					</Tooltip>
				</Space>
			),
		},
		{
			title: "同步模式",
			dataIndex: "syncMode",
			key: "syncMode",
			width: 110,
			sorter: true,
			render: syncModeLabel,
		},
		{
			title: "生命周期",
			dataIndex: "lifecycle",
			key: "lifecycle",
			width: 105,
			sorter: true,
			render: renderLifecycleTag,
		},
		{
			title: "健康状态",
			dataIndex: "health",
			key: "health",
			width: 105,
			render: (value: AccessHealth, row) => renderHealthTag(value, row.healthReason),
		},
		{
			title: "最近运行",
			key: "lastRun",
			width: 165,
			sorter: true,
			defaultSortOrder: "descend",
			render: (_, row) => (
				<Space size={6}>
					<Text>{row.lastExecutionStatus || "尚未运行"}</Text>
					<Text type="secondary">{row.lastExecutedAt ? formatTimestamp(row.lastExecutedAt) : "-"}</Text>
				</Space>
			),
		},
		{
			title: "负责人 / 密级",
			key: "owner",
			width: 150,
			render: (_, row) => (
				<Space size={6}>
					<Text>{row.owner}</Text>
					{row.classification ? (
						<ClassificationTag value={row.classification} size="small" />
					) : (
						<Text type="secondary">普通流程</Text>
					)}
				</Space>
			),
		},
		actionColumn<AccessWorkspaceRow>((row) => [
			{ key: "detail", label: "详情", disabled: row.taskId == null, onClick: () => openDetail(row) },
		]),
	];

	const onTableChange = (
		next: TablePaginationConfig,
		_filters: Record<string, FilterValue | null>,
		sorter: SorterResult<AccessWorkspaceRow> | SorterResult<AccessWorkspaceRow>[],
	) => {
		// 服务端分页：表头排序必须转成 Spring Data 的 sort 参数交给后端，
		// 否则只会把当前页的 10 条重新排列。
		const nextSort = toSpringSort(sorter, ACCESS_SORT_FIELDS, DEFAULT_SORT) ?? DEFAULT_SORT;
		const nextPageSize = next.pageSize || 10;
		// 换页长度或换排序都要回到第一页，否则会停在一个不存在的页码上。
		const keepPage = nextPageSize === pagination.pageSize && nextSort === sort;
		setSort(nextSort);
		setPagination({
			current: keepPage ? next.current || 1 : 1,
			pageSize: nextPageSize,
			total: pagination.total,
		});
	};

	const createKind: AccessSourceKind = kind === "overview" ? "database" : kind;
	const createAction = (
		<Space>
			<Button onClick={() => navigate("/foundation/connections")}>连接管理</Button>
			<Button type="primary" onClick={() => openCreate(createKind)}>
				{`新建${KIND_META[createKind].label}`}
			</Button>
		</Space>
	);

	return (
		<div className={styles.workspace}>
			<PageHeader title={KIND_META[kind].label} actions={createAction} />
			<JourneyContextBar stage="integration" />

			<section className={styles.tableShell}>
				{error ? <Alert className="mb-3" type="error" showIcon message="接入任务加载失败" description={error} /> : null}
				<div className={styles.toolbar}>
					<Input.Search
						className={styles.search}
						allowClear
						value={queryInput}
						onChange={(event) => {
							const next = event.target.value;
							setQueryInput(next);
							if (!next) syncKeyword("");
						}}
						onSearch={syncKeyword}
						placeholder="搜索任务名称、描述、接入类型或负责人"
					/>
					<Select
						className={styles.select}
						value={lifecycle}
						onChange={(value: AccessLifecycle | "all") => {
							setLifecycle(value);
							setPagination((previous) => ({ ...previous, current: 1 }));
						}}
						options={LIFECYCLE_OPTIONS}
					/>
					<Select
						className={styles.select}
						value={health}
						onChange={(value: AccessHealth | "all") => {
							setHealth(value);
							setPagination((previous) => ({ ...previous, current: 1 }));
						}}
						options={HEALTH_OPTIONS}
					/>
					<span className={styles.toolbarSpacer} />
					<Button onClick={() => void load()} disabled={loading}>
						刷新
					</Button>
				</div>
				<CompactTable<AccessWorkspaceRow>
					rowKey="key"
					columns={columns}
					dataSource={rows}
					loading={loading}
					pagination={{
						current: pagination.current,
						pageSize: pagination.pageSize,
						total: pagination.total,
					}}
					onChange={onTableChange}
				/>
			</section>
		</div>
	);
}
