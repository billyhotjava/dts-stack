import type { MenuProps } from "antd";
import { Alert, Button, Card, Dropdown, Input, Select, Space, Tag, Tooltip, Typography } from "antd";
import type { ColumnsType, TablePaginationConfig } from "antd/es/table";
import type { FilterValue, SorterResult } from "antd/es/table/interface";
import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { type IngestionTaskDTO, ingestionTaskAPI } from "@/api/ingestion";
import { PageHeader } from "@/components/page-header";
import { actionColumn, CompactTable, toSpringSort } from "@/components/table";
import { formatDateTime } from "@/utils/textUtils";

const { Text } = Typography;

type TaskView = "design" | "runs";

type Props = {
	onOpenTask: (taskId: number, view: TaskView) => void;
};

type TaskStatusFilter = "all" | "active" | "draft" | "paused";
type SourceKindFilter = "all" | "database" | "api" | "file";
type HealthFilter = "all" | "healthy" | "running" | "attention" | "not_evaluated";

const DEFAULT_SORT = "lastModifiedDate,desc";

const SORT_FIELDS: Record<string, string> = {
	name: "name",
	status: "status",
	lastRun: "lastExecutedAt",
};

const STATUS_OPTIONS = [
	{ value: "all", label: "全部在管状态" },
	{ value: "active", label: "运行中" },
	{ value: "draft", label: "草稿" },
	{ value: "paused", label: "已暂停" },
];

const SOURCE_KIND_OPTIONS = [
	{ value: "all", label: "全部来源" },
	{ value: "database", label: "数据库" },
	{ value: "api", label: "API" },
	{ value: "file", label: "离线文件" },
];

const HEALTH_OPTIONS = [
	{ value: "all", label: "全部运行状态" },
	{ value: "healthy", label: "健康" },
	{ value: "running", label: "运行中" },
	{ value: "attention", label: "需关注" },
	{ value: "not_evaluated", label: "未运行" },
];

function normalized(value?: string): string {
	return (value || "").trim().toUpperCase();
}

function lifecycleTag(status?: string) {
	const value = normalized(status);
	if (value === "ACTIVE") return <Tag color="green">运行中</Tag>;
	if (value === "DRAFT") return <Tag color="blue">草稿</Tag>;
	if (value === "PAUSED") return <Tag color="gold">已暂停</Tag>;
	return <Tag>{status || "状态未识别"}</Tag>;
}

function revisionTag(task: IngestionTaskDTO) {
	if (normalized(task.revisionState) === "LEGACY_UNSEALED") return <Tag color="orange">历史任务待迁移</Tag>;
	if (!task.revisionNumber) return <Tag color="orange">待形成受控版本</Tag>;
	return (
		<Space size={4} wrap>
			<Text>{`R${task.revisionNumber}`}</Text>
			<Tag color={normalized(task.revisionState) === "ACTIVE" ? "green" : "blue"}>
				{normalized(task.revisionState) === "ACTIVE" ? "已发布" : task.revisionState || "版本状态未知"}
			</Tag>
		</Space>
	);
}

function runStatusTag(status?: string) {
	const value = normalized(status);
	if (["SUCCESS", "SUCCEEDED", "COMPLETED"].includes(value)) return <Tag color="green">成功</Tag>;
	if (["FAILED", "ERROR", "EXHAUSTED"].includes(value)) return <Tag color="red">失败</Tag>;
	if (["PREPARING", "PENDING", "QUEUED", "RUNNING", "RETRY_WAIT"].includes(value)) {
		return <Tag color="processing">运行中</Tag>;
	}
	if (["CANCELLED", "CANCELED"].includes(value)) return <Tag>已取消</Tag>;
	return <Tag>尚未运行</Tag>;
}

export default function OrchestrationTaskList({ onOpenTask }: Props) {
	const navigate = useNavigate();
	const [rows, setRows] = useState<IngestionTaskDTO[]>([]);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string>();
	const [queryInput, setQueryInput] = useState("");
	const [query, setQuery] = useState("");
	const [status, setStatus] = useState<TaskStatusFilter>("all");
	const [sourceKind, setSourceKind] = useState<SourceKindFilter>("all");
	const [health, setHealth] = useState<HealthFilter>("all");
	const [sort, setSort] = useState(DEFAULT_SORT);
	const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
	const requestIdRef = useRef(0);

	const load = useCallback(async () => {
		const requestId = ++requestIdRef.current;
		setLoading(true);
		setError(undefined);
		try {
			const result = await ingestionTaskAPI.getTasks({
				page: pagination.current - 1,
				size: pagination.pageSize,
				sort,
				status: status === "all" ? undefined : status,
				sourceKind: sourceKind === "all" ? undefined : sourceKind,
				health: health === "all" ? undefined : health,
				query: query.trim() || undefined,
			});
			if (requestIdRef.current !== requestId) return;
			const content = Array.isArray(result.content) ? result.content : [];
			setRows(content);
			setPagination((previous) => ({
				...previous,
				total: Number.isFinite(result.totalElements) ? result.totalElements : content.length,
			}));
		} catch {
			if (requestIdRef.current !== requestId) return;
			setRows([]);
			setPagination((previous) => ({ ...previous, total: 0 }));
			setError("编排任务列表加载失败，请检查服务状态后重试。");
		} finally {
			if (requestIdRef.current === requestId) setLoading(false);
		}
	}, [health, pagination.current, pagination.pageSize, query, sort, sourceKind, status]);

	useEffect(() => {
		void load();
		return () => {
			requestIdRef.current += 1;
		};
	}, [load]);

	const applyQuery = (value: string) => {
		setQueryInput(value);
		setQuery(value.trim());
		setPagination((previous) => ({ ...previous, current: 1 }));
	};

	const handleTableChange = (
		next: TablePaginationConfig,
		_filters: Record<string, FilterValue | null>,
		sorter: SorterResult<IngestionTaskDTO> | SorterResult<IngestionTaskDTO>[],
	) => {
		const nextSort = toSpringSort(sorter, SORT_FIELDS, DEFAULT_SORT) ?? DEFAULT_SORT;
		const nextPageSize = next.pageSize || 10;
		const keepPage = nextPageSize === pagination.pageSize && nextSort === sort;
		setSort(nextSort);
		setPagination({
			current: keepPage ? next.current || 1 : 1,
			pageSize: nextPageSize,
			total: pagination.total,
		});
	};

	const columns: ColumnsType<IngestionTaskDTO> = [
		{
			title: "任务名称",
			dataIndex: "name",
			key: "name",
			width: 210,
			sorter: true,
			render: (value: string, task) => (
				<Space direction="vertical" size={0}>
					<Button
						type="link"
						style={{ padding: 0, height: "auto" }}
						disabled={!task.id}
						onClick={() => task.id && onOpenTask(task.id, "design")}
					>
						{value}
					</Button>
					<Text type="secondary" ellipsis={{ tooltip: task.description }} style={{ maxWidth: 195 }}>
						{task.description || `任务 #${task.id || "-"}`}
					</Text>
				</Space>
			),
		},
		{
			title: "生命周期",
			dataIndex: "status",
			key: "status",
			width: 105,
			sorter: true,
			render: lifecycleTag,
		},
		{
			title: "来源",
			dataIndex: "sourceType",
			key: "sourceType",
			width: 145,
			ellipsis: true,
			render: (value?: string) => value || "来源类型未记录",
		},
		{
			title: "目标资产",
			dataIndex: "targetDatasetId",
			key: "targetDatasetId",
			width: 160,
			render: (value?: string) =>
				value ? (
					<Tooltip title={value}>
						<Space size={4}>
							<Tag color="green">已关联</Tag>
							<Text>{value.slice(-8)}</Text>
						</Space>
					</Tooltip>
				) : (
					<Tag color="red">待关联</Tag>
				),
		},
		{
			title: "任务版本",
			key: "revision",
			width: 175,
			render: (_, task) => revisionTag(task),
		},
		{
			title: "调度",
			key: "schedule",
			width: 150,
			render: (_, task) => (
				<Space direction="vertical" size={0}>
					<Text>{task.syncSchedule || "手动执行"}</Text>
					<Text type="secondary">{task.airflowEnabled === false ? "调度未启用" : "DTS 托管运行"}</Text>
				</Space>
			),
		},
		{
			title: "质量验证",
			key: "quality",
			width: 125,
			render: (_, task) => (task.qualityPolicyRef ? <Tag color="green">接入后验证</Tag> : <Tag>未配置</Tag>),
		},
		{
			title: "最近运行",
			key: "lastRun",
			width: 175,
			sorter: true,
			defaultSortOrder: "descend",
			render: (_, task) => (
				<Space direction="vertical" size={0}>
					{runStatusTag(task.lastExecutionStatus)}
					<Text type="secondary">{task.lastExecutedAt ? formatDateTime(task.lastExecutedAt) : "-"}</Text>
				</Space>
			),
		},
		actionColumn<IngestionTaskDTO>(
			(task) => {
				const taskId = task.id;
				return [
					{ key: "design", label: "设计", disabled: !taskId, onClick: () => taskId && onOpenTask(taskId, "design") },
					{ key: "runs", label: "运行实例", disabled: !taskId, onClick: () => taskId && onOpenTask(taskId, "runs") },
				];
			},
			{ width: 170, fixed: false },
		),
	];

	const createMenu: MenuProps = {
		items: [
			{ key: "database", label: "新建数据库接入任务" },
			{ key: "api", label: "新建 API 接入任务" },
			{ key: "file", label: "新建离线文件接入任务" },
		],
		onClick: ({ key }) => navigate(`/foundation/data-sources/access/new?kind=${key}`),
	};

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据集成流程"
				actions={
					<Dropdown menu={createMenu}>
						<Button type="primary">新建接入任务</Button>
					</Dropdown>
				}
			/>
			<Alert
				type="info"
				showIcon
				message="这里只展示纳入 DTS 编排治理且当前账号有权访问的接入任务；未关联接入任务的 Airflow DAG 不会进入本列表。"
			/>
			<Card size="small" title="编排任务">
				{error ? <Alert style={{ marginBottom: 12 }} type="error" showIcon message={error} /> : null}
				<Space wrap style={{ width: "100%", marginBottom: 12 }}>
					<Input.Search
						allowClear
						style={{ width: 320 }}
						value={queryInput}
						onChange={(event) => {
							const value = event.target.value;
							setQueryInput(value);
							if (!value) applyQuery("");
						}}
						onSearch={applyQuery}
						placeholder="搜索任务名称、业务说明、来源类型或负责人"
					/>
					<Select
						value={status}
						style={{ width: 150 }}
						options={STATUS_OPTIONS}
						onChange={(value: TaskStatusFilter) => {
							setStatus(value);
							setPagination((previous) => ({ ...previous, current: 1 }));
						}}
					/>
					<Select
						value={sourceKind}
						style={{ width: 130 }}
						options={SOURCE_KIND_OPTIONS}
						onChange={(value: SourceKindFilter) => {
							setSourceKind(value);
							setPagination((previous) => ({ ...previous, current: 1 }));
						}}
					/>
					<Select
						value={health}
						style={{ width: 145 }}
						options={HEALTH_OPTIONS}
						onChange={(value: HealthFilter) => {
							setHealth(value);
							setPagination((previous) => ({ ...previous, current: 1 }));
						}}
					/>
					<Button onClick={() => void load()} disabled={loading}>
						刷新
					</Button>
				</Space>
				<CompactTable<IngestionTaskDTO>
					rowKey={(task) => String(task.id || task.name)}
					columns={columns}
					dataSource={rows}
					loading={loading}
					autoSort={false}
					scroll={{ x: 1390 }}
					pagination={{
						current: pagination.current,
						pageSize: pagination.pageSize,
						total: pagination.total,
						showSizeChanger: true,
					}}
					onChange={handleTableChange}
				/>
			</Card>
		</div>
	);
}
