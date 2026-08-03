import { DatabaseOutlined, FileTextOutlined, LinkOutlined, ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Input, Select, Space, Table, Tag, Tooltip, Typography } from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import { type ReactNode, useCallback, useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { ClassificationTag } from "@/analytics/pages/screens/components/ClassificationTag";
import { ingestionTaskAPI } from "@/api/ingestion";
import dataSourcesService from "@/api/services/dataSourcesService";
import { PageHeader } from "@/components/page-header";
import { JourneyContextBar } from "@/components/journey";
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
					sort: "lastModifiedDate,desc",
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
	}, [health, kind, lifecycle, pagination.current, pagination.pageSize, query]);

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

	const legacyCount = rows.filter((row) => row.versionState === "legacy-unversioned").length;

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
			render: (_, row) => (
				<div className={styles.nameCell}>
					<Button
						className={styles.nameButton}
						type="link"
						onClick={() => openDetail(row)}
						disabled={row.taskId == null}
					>
						{row.name}
					</Button>
					<span className={styles.secondary}>{row.taskId == null ? "任务编号未记录" : `任务 #${row.taskId}`}</span>
				</div>
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
			render: (_, row) => (
				<div className={styles.sourceCell}>
					<Text strong>{row.sourceName}</Text>
					<Tooltip title={`${row.sourceType} · ${row.resourceSummary}`}>
						<span className={styles.secondary}>
							{row.sourceType} · {row.resourceSummary}
						</span>
					</Tooltip>
				</div>
			),
		},
		{
			title: "有效配置",
			key: "version",
			width: 190,
			render: (_, row) => (
				<div className={styles.versionCell}>
					<Space size={4} wrap>
						<Tag>{row.versionLabel}</Tag>
						<Text>{syncModeLabel(row.syncMode)}</Text>
					</Space>
					<span className={styles.migrationHint}>{row.versionHint}</span>
				</div>
			),
		},
		{
			title: "生命周期",
			dataIndex: "lifecycle",
			key: "lifecycle",
			width: 105,
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
			render: (_, row) => (
				<div className={styles.runCell}>
					<Text>{row.lastExecutionStatus || "尚未运行"}</Text>
					<span className={styles.secondary}>{row.lastExecutedAt ? formatTimestamp(row.lastExecutedAt) : "-"}</span>
				</div>
			),
		},
		{
			title: "负责人 / 密级",
			key: "owner",
			width: 150,
			render: (_, row) => (
				<div className={styles.ownerCell}>
					<Text>{row.owner}</Text>
					{row.classification ? (
						<ClassificationTag value={row.classification} size="small" />
					) : (
						<Tag color="orange">密级未记录</Tag>
					)}
				</div>
			),
		},
		{
			title: "操作",
			key: "actions",
			fixed: "right",
			width: 90,
			render: (_, row) => (
				<Button type="link" onClick={() => openDetail(row)} disabled={row.taskId == null}>
					详情
				</Button>
			),
		},
	];

	const onTableChange = (next: TablePaginationConfig) => {
		const nextPageSize = next.pageSize || 10;
		setPagination({
			current: nextPageSize === pagination.pageSize ? next.current || 1 : 1,
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

			{legacyCount > 0 ? (
				<Alert
					type="warning"
					showIcon
					message={`当前页仍有 ${legacyCount} 个存量任务未版本化`}
					description="这些任务会保持“未版本化”标识，完成后台迁移后自动显示真实 Revision 与策略版本。"
				/>
			) : null}

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
					<Button icon={<ReloadOutlined />} onClick={() => void load()} disabled={loading}>
						刷新
					</Button>
				</div>
				<Table<AccessWorkspaceRow>
					rowKey="key"
					columns={columns}
					dataSource={rows}
					loading={loading}
					scroll={{ x: 1380 }}
					pagination={{
						current: pagination.current,
						pageSize: pagination.pageSize,
						total: pagination.total,
						showSizeChanger: true,
						pageSizeOptions: [10, 20, 50],
						showTotal: (total) => `服务器候选 ${total} 条`,
					}}
					onChange={(next) => onTableChange(next)}
				/>
			</section>
		</div>
	);
}
