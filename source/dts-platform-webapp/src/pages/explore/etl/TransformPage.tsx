import { useEffect, useRef, useState } from "react";
import { Alert, Button, Card, Col, Empty, InputNumber, Modal, Progress, Row, Select, Space, Statistic, Table, Tag, message } from "antd";
import {
	PlayCircleOutlined,
	EditOutlined,
	DeleteOutlined,
	HistoryOutlined,
	ReloadOutlined,
	SyncOutlined,
} from "@ant-design/icons";
import { Activity, Clock3, ListChecks, ShieldAlert } from "lucide-react";
import {
	PlatformFilterBar,
	PlatformMetaPill,
	PlatformPageHero,
	PlatformSectionCard,
	PlatformSummaryCards,
} from "@/components/console-page";
import { useRouter } from "@/routes/hooks";
import {
	ingestionTaskAPI,
	type IngestionTaskDTO,
	type IngestionExecutionDTO,
	type IngestionGovernanceOverviewDTO,
	type IngestionExecutionObservabilityDTO,
} from "@/api/ingestion";
import { formatTimestamp } from "@/utils/format";

type ExecutionProgressView = {
	percent: number;
	status: "active" | "success" | "exception";
	stage: string;
	detail: string;
	terminal: boolean;
};

export default function TransformPage() {
	const router = useRouter();
	const [tasks, setTasks] = useState<IngestionTaskDTO[]>([]);
	const [loading, setLoading] = useState(false);
	const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });
	const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);
	const [executeProgressOpen, setExecuteProgressOpen] = useState(false);
	const [executeProgress, setExecuteProgress] = useState<ExecutionProgressView>({
		percent: 0,
		status: "active",
		stage: "等待提交",
		detail: "",
		terminal: false,
	});
	const [executingTaskName, setExecutingTaskName] = useState<string>("");
	const [executingTaskId, setExecutingTaskId] = useState<number | null>(null);
	const [observability, setObservability] = useState<IngestionExecutionObservabilityDTO | null>(null);
	const [observabilityLoading, setObservabilityLoading] = useState(false);
	const [governanceOverview, setGovernanceOverview] = useState<IngestionGovernanceOverviewDTO | null>(null);
	const [governanceLoading, setGovernanceLoading] = useState(false);
	const [governanceHours, setGovernanceHours] = useState<number>(24);
	const [obsTaskId, setObsTaskId] = useState<number | undefined>(undefined);
	const [obsSourceType, setObsSourceType] = useState<string | undefined>(undefined);
	const [obsDays, setObsDays] = useState<number>(7);
	const [obsTimeoutMinutes, setObsTimeoutMinutes] = useState<number>(10);
	const executePollTimerRef = useRef<number | null>(null);
	const executeStartedAtRef = useRef<number>(0);

	useEffect(() => {
		void loadTasks();
	}, [pagination.current, statusFilter]);

	useEffect(() => {
		return () => {
			stopExecutePolling();
		};
	}, []);

	useEffect(() => {
		void loadObservability();
	}, [obsTaskId, obsSourceType, obsDays, obsTimeoutMinutes]);

	useEffect(() => {
		void loadGovernanceOverview();
	}, [governanceHours]);

	/** Adaptive polling: starts fast, slows down over time. Never hard-stops. */
	const adaptivePollDelay = (elapsedMs: number): number => {
		if (elapsedMs < 30_000) return 3_000;    // first 30s: every 3s
		if (elapsedMs < 120_000) return 5_000;   // 30s-2min: every 5s
		if (elapsedMs < 300_000) return 10_000;  // 2-5min: every 10s
		return 30_000;                            // >5min: every 30s
	};

	const stopExecutePolling = () => {
		if (executePollTimerRef.current !== null) {
			window.clearTimeout(executePollTimerRef.current);
			executePollTimerRef.current = null;
		}
	};

	const loadTasks = async () => {
		setLoading(true);
		try {
			const params: Record<string, any> = {
				page: pagination.current - 1,
				size: pagination.pageSize,
			};
			if (statusFilter) {
				params.status = statusFilter;
			}
			const result = await ingestionTaskAPI.getTasks(params);
			const content = Array.isArray(result?.content) ? result.content : [];
			setTasks(content);
			const total = typeof result?.totalElements === "number" ? result.totalElements : content.length;
			setPagination((prev) => ({ ...prev, total }));
		} catch {
			setTasks([]);
		} finally {
			setLoading(false);
		}
	};

	const loadObservability = async () => {
		setObservabilityLoading(true);
		try {
			const result = await ingestionTaskAPI.getExecutionsObservability({
				taskId: obsTaskId,
				sourceType: obsSourceType || undefined,
				days: obsDays,
				timeoutMinutes: obsTimeoutMinutes,
			});
			setObservability(result);
		} catch {
			setObservability(null);
		} finally {
			setObservabilityLoading(false);
		}
	};

	const loadGovernanceOverview = async () => {
		setGovernanceLoading(true);
		try {
			const result = await ingestionTaskAPI.getGovernanceOverview({ hours: governanceHours });
			setGovernanceOverview(result);
		} catch {
			setGovernanceOverview(null);
		} finally {
			setGovernanceLoading(false);
		}
	};

	const normalizeText = (value?: string) => String(value || "").trim();

	const mapExecutionProgress = (execution: IngestionExecutionDTO | null, elapsedMs: number): ExecutionProgressView => {
		if (!execution) {
			const percent = Math.min(45, 15 + Math.floor(elapsedMs / 5000) * 5);
			return {
				percent,
				status: "active",
				stage: "等待执行记录",
				detail: "任务已提交，系统正在准备 DAG 和作业参数。",
				terminal: false,
			};
		}
		const normalized = normalizeText(execution.status).toLowerCase();
		if (normalized === "success") {
			return {
				percent: 100,
				status: "success",
				stage: "执行成功",
				detail: "任务已执行完成。",
				terminal: true,
			};
		}
		if (normalized === "failed" || normalized === "error") {
			return {
				percent: 100,
				status: "exception",
				stage: "执行失败",
				detail: normalizeText(execution.errorMessage) || "执行失败，请查看日志。",
				terminal: true,
			};
		}
		if (normalized === "preparing") {
			return {
				percent: 60,
				status: "active",
				stage: "准备执行",
				detail: "正在生成/校验 Addax 作业并等待 DAG 就绪。",
				terminal: false,
			};
		}
		return {
			percent: 85,
			status: "active",
			stage: "执行中",
			detail: "已触发执行，正在同步运行状态。",
			terminal: false,
		};
	};

	const startExecuteProgressPolling = (
		taskId: number,
		taskName: string,
		_pollIntervalMs?: number
	) => {
		stopExecutePolling();
		const startTime = Date.now();
		executeStartedAtRef.current = startTime;
		setExecutingTaskId(taskId);
		setExecutingTaskName(taskName);
		setExecuteProgressOpen(true);
		setExecuteProgress({
			percent: 10,
			status: "active",
			stage: "任务已提交",
			detail: "正在后台触发执行。",
			terminal: false,
		});
		let slowNotified = false;

		const pollOnce = async () => {
			const elapsed = Date.now() - startTime;
			if (elapsed > 5 * 60 * 1000 && !slowNotified) {
				slowNotified = true;
				message.info("执行时间较长，已切换为低频刷新");
			}
			try {
				const execution = await ingestionTaskAPI.getLatestExecution(taskId);
				const next = mapExecutionProgress(execution, elapsed);
				setExecuteProgress(next);
				if (next.terminal) {
					stopExecutePolling();
					void loadTasks();
					return;
				}
			} catch {
				setExecuteProgress((prev) => ({ ...prev, detail: "状态同步中，稍后自动重试。" }));
			}
			const elapsed2 = Date.now() - startTime;
			executePollTimerRef.current = window.setTimeout(pollOnce, adaptivePollDelay(elapsed2));
		};

		void pollOnce();
	};

	const handleExecute = async (id: number, name: string) => {
		Modal.confirm({
			title: "确认执行",
			content: `确定要执行任务 "${name}" 吗？`,
			onOk: async () => {
				try {
					const submit = await ingestionTaskAPI.executeTaskAsync(id);
					message.success("任务已提交，后台正在触发执行");
					startExecuteProgressPolling(id, name, submit?.pollIntervalMs);
					void loadTasks();
				} catch {
					// error already shown by global interceptor
				}
			},
		});
	};

	const handleRebuildDag = async (id: number, name: string) => {
		Modal.confirm({
			title: "强制重建 DAG",
			content: `确定要重建任务 "${name}" 的 DAG 文件吗？`,
			onOk: async () => {
				try {
					await ingestionTaskAPI.rebuildDag(id);
					message.success("DAG 已重建");
					void loadTasks();
				} catch {
					// error already shown by global interceptor
				}
			},
		});
	};

	const handleDelete = async (id: number, name: string) => {
		Modal.confirm({
			title: "确认删除",
			content: `确定要删除任务 "${name}" 吗？该操作会移除任务配置、DAG、执行记录与运行日志，且不可恢复。`,
			okText: "删除",
			okType: "danger",
			onOk: async () => {
				try {
					await ingestionTaskAPI.deleteTask(id);
					message.success("任务已删除");
					void loadTasks();
				} catch {
					// error already shown by global interceptor
				}
			},
		});
	};

	const renderStatus = (status?: string) => {
		const statusMap: Record<string, { color: string; text: string }> = {
			draft: { color: "default", text: "草稿" },
			active: { color: "success", text: "活跃" },
			paused: { color: "warning", text: "暂停" },
			deleted: { color: "error", text: "已删除" },
		};
		const config = statusMap[status || "draft"];
		return <Tag color={config.color}>{config.text}</Tag>;
	};

	const renderExecutionStatus = (status?: string) => {
		const statusMap: Record<string, { color: string; text: string }> = {
			running: { color: "processing", text: "运行中" },
			success: { color: "success", text: "成功" },
			failed: { color: "error", text: "失败" },
		};
		const config = statusMap[status || ""];
		return config ? <Tag color={config.color}>{config.text}</Tag> : <span>-</span>;
	};

	const columns = [
		{
			title: "任务名称",
			dataIndex: "name",
			key: "name",
			width: 200,
			render: (text: string, record: IngestionTaskDTO) => (
				<a onClick={() => router.push(`/explore/etl/transform/${record.id}`)}>{text}</a>
			),
		},
		{
			title: "数据源类型",
			dataIndex: "sourceType",
			key: "sourceType",
			width: 150,
		},
		{
			title: "同步模式",
			dataIndex: "syncMode",
			key: "syncMode",
			width: 120,
			render: (mode: string) => {
				if (mode === "full_refresh") return <Tag color="blue">全量</Tag>;
				if (mode === "incremental") return <Tag color="green">增量</Tag>;
				return <Tag>{mode || "-"}</Tag>;
			},
		},
		{
			title: "状态",
			dataIndex: "status",
			key: "status",
			width: 100,
			render: renderStatus,
		},
		{
			title: "最后执行状态",
			dataIndex: "lastExecutionStatus",
			key: "lastExecutionStatus",
			width: 120,
			render: renderExecutionStatus,
		},
		{
			title: "最后执行时间",
			dataIndex: "lastExecutedAt",
			key: "lastExecutedAt",
			width: 180,
			render: (text: string) => (text ? formatTimestamp(text) : "-"),
		},
		{
			title: "创建时间",
			dataIndex: "createdDate",
			key: "createdDate",
			width: 180,
			render: (text: string) => (text ? formatTimestamp(text) : "-"),
		},
		{
			title: "创建人",
			dataIndex: "createdBy",
			key: "createdBy",
			width: 120,
		},
		{
			title: "操作",
			key: "action",
			width: 250,
			fixed: "right" as const,
			render: (_: any, record: IngestionTaskDTO) => (
				<Space size="small">
					<Button
						size="small"
						type="primary"
						icon={<PlayCircleOutlined />}
						onClick={() => handleExecute(record.id!, record.name)}
						disabled={record.status === "deleted"}
					>
						执行
					</Button>
					<Button
						size="small"
						icon={<HistoryOutlined />}
						onClick={() => router.push(`/explore/etl/transform/${record.id}/executions`)}
					>
						历史
					</Button>
					<Button
						size="small"
						icon={<EditOutlined />}
						onClick={() => router.push(`/explore/etl/transform/${record.id}/edit`)}
						disabled={record.status === "deleted"}
					>
						编辑
					</Button>
					<Button
						size="small"
						icon={<SyncOutlined />}
						onClick={() => handleRebuildDag(record.id!, record.name)}
						disabled={record.status === "deleted" || record.airflowEnabled === false}
					>
						重建 DAG
					</Button>
					<Button
						size="small"
						danger
						icon={<DeleteOutlined />}
						onClick={() => handleDelete(record.id!, record.name)}
						disabled={record.status === "deleted"}
					>
						删除
					</Button>
				</Space>
			),
		},
	];

	const sourceTypeOptions = Array.from(
		new Set(
			tasks
				.map((item) => normalizeText(item.sourceType))
				.filter((item) => Boolean(item))
		)
	).map((item) => ({ label: item, value: item }));

	const taskOptions = tasks
		.filter((item) => typeof item.id === "number")
		.map((item) => ({ label: item.name, value: Number(item.id) }));

	const trendColumns = [
		{ title: "日期", dataIndex: "day", key: "day", width: 120 },
		{ title: "总执行", dataIndex: "total", key: "total", width: 90 },
		{ title: "成功", dataIndex: "success", key: "success", width: 90 },
		{ title: "失败", dataIndex: "failed", key: "failed", width: 90 },
		{ title: "超时", dataIndex: "timeout", key: "timeout", width: 90 },
	];

	const sourceLoadColumns = [
		{
			title: "来源数据源",
			dataIndex: "sourceDataSourceId",
			key: "sourceDataSourceId",
			render: (value: string | undefined) => value || "N/A",
		},
		{
			title: "来源类型",
			dataIndex: "sourceType",
			key: "sourceType",
			render: (value: string | undefined) => value || "unknown",
		},
		{ title: "运行中", dataIndex: "running", key: "running", width: 100 },
		{ title: "排队中", dataIndex: "preparing", key: "preparing", width: 100 },
	];
	const projectLoadColumns = [
		{
			title: "项目标识",
			dataIndex: "projectKey",
			key: "projectKey",
		},
		{ title: "运行中", dataIndex: "running", key: "running", width: 100 },
		{ title: "排队中", dataIndex: "preparing", key: "preparing", width: 100 },
	];

	const summaryCards = [
		{
			label: "任务总数",
			value: pagination.total || tasks.length,
			note: `活跃 ${tasks.filter((item) => item.status === "active").length} · 暂停 ${tasks.filter((item) => item.status === "paused").length}`,
			icon: <ListChecks className="h-5 w-5" />,
		},
		{
			label: "成功率",
			value: observability ? `${Number(observability.successRate || 0).toFixed(1)}%` : "--",
			note: `最近 ${obsDays} 天执行窗口`,
			icon: <Activity className="h-5 w-5" />,
			tone: "info" as const,
		},
		{
			label: "队列长度",
			value: governanceOverview?.queueLength ?? "--",
			note: `最近 ${governanceHours} 小时治理视角`,
			icon: <Clock3 className="h-5 w-5" />,
			tone: "warning" as const,
		},
		{
			label: "治理拒绝",
			value: governanceOverview?.blockedByPolicy ?? "--",
			note: "并发、窗口与限额策略命中次数",
			icon: <ShieldAlert className="h-5 w-5" />,
			tone: "success" as const,
		},
	];

	return (
		<div className="space-y-6">
			<PlatformPageHero
				title="入湖任务中心"
				description="把任务清单、运行可观测性和治理负载放到同一个工作面里，现场优先关注可执行、可追踪和可治理。"
				eyebrow="Ingestion Operations"
				actions={
					<Space wrap>
						<Button className="rounded-2xl" icon={<ReloadOutlined />} onClick={() => void loadTasks()} loading={loading}>
							刷新
						</Button>
						<Button className="rounded-2xl" type="primary" onClick={() => router.push("/explore/etl/transform/new")}>
							创建入湖任务
						</Button>
					</Space>
				}
				meta={
					<>
						<PlatformMetaPill>最近 {governanceHours} 小时治理窗口</PlatformMetaPill>
						<PlatformMetaPill>最近 {obsDays} 天执行可观测性</PlatformMetaPill>
						<PlatformMetaPill>任务列表保留执行、重建 DAG 与删除入口</PlatformMetaPill>
					</>
				}
			/>

			<PlatformSummaryCards items={summaryCards} />

			<PlatformFilterBar>
				<div>
					<div className="text-sm font-semibold text-foreground">先看运行体征，再处理任务明细</div>
					<div className="mt-1 text-sm text-muted-foreground">
						上半区聚焦 SLA、失败趋势和治理负载，下半区保留任务级执行、重建 DAG 和配置处理入口。
					</div>
				</div>
				<div className="flex flex-wrap items-center gap-2">
					{[
						{ label: "全部", value: undefined },
						{ label: "草稿", value: "draft" },
						{ label: "活跃", value: "active" },
						{ label: "暂停", value: "paused" },
						{ label: "已删除", value: "deleted" },
					].map((item) => (
						<Button
							key={item.label}
							type={statusFilter === item.value ? "primary" : "default"}
							size="small"
							onClick={() => setStatusFilter(item.value)}
						>
							{item.label}
						</Button>
					))}
				</div>
			</PlatformFilterBar>

			<PlatformSectionCard
				title="运行可观测性与治理负载"
				description="同一页面同时回答两个问题：任务当前跑得怎么样，治理限额有没有开始拦截现场流量。"
			>
				<Card
					size="small"
					title="资源与配额治理"
					loading={governanceLoading}
					className="mb-4 rounded-[24px]"
					extra={
						<Space wrap>
							<Select
								style={{ width: 140 }}
								value={governanceHours}
								options={[
									{ label: "最近 6 小时", value: 6 },
									{ label: "最近 24 小时", value: 24 },
									{ label: "最近 72 小时", value: 72 },
									{ label: "最近 168 小时", value: 168 },
								]}
								onChange={(value) => setGovernanceHours(value)}
							/>
							<Button
								className="rounded-2xl"
								icon={<ReloadOutlined />}
								onClick={() => void loadGovernanceOverview()}
								loading={governanceLoading}
							>
								刷新治理
							</Button>
						</Space>
					}
				>
					{governanceOverview ? (
						<Space direction="vertical" size={16} style={{ width: "100%" }}>
							{governanceOverview.blockedByPolicy > 0 ? (
								<Alert
									type="warning"
									showIcon
									message={`最近窗口内发生 ${governanceOverview.blockedByPolicy} 次治理拒绝`}
									description="建议检查任务并发上限、来源并发上限和执行窗口配置，必要时拆分批次或下调调度频率。"
								/>
							) : null}
							<Row gutter={[16, 16]}>
								<Col xs={12} md={6}>
									<Statistic title="运行中" value={governanceOverview.running || 0} />
								</Col>
								<Col xs={12} md={6}>
									<Statistic title="排队中" value={governanceOverview.preparing || 0} />
								</Col>
								<Col xs={12} md={6}>
									<Statistic title="队列长度" value={governanceOverview.queueLength || 0} />
								</Col>
								<Col xs={12} md={6}>
									<Statistic title="策略拒绝数" value={governanceOverview.blockedByPolicy || 0} />
								</Col>
								<Col xs={12} md={6}>
									<Statistic title="平均耗时(秒)" value={governanceOverview.avgExecutionSeconds || 0} precision={2} />
								</Col>
								<Col xs={12} md={6}>
									<Statistic title="平均排队(秒)" value={governanceOverview.avgQueueWaitSeconds || 0} precision={2} />
								</Col>
								<Col xs={12} md={6}>
									<Statistic title="最长排队(秒)" value={governanceOverview.maxQueueWaitSeconds || 0} precision={2} />
								</Col>
							</Row>
							<Table
								size="small"
								rowKey={(record) => `${record.sourceDataSourceId || "none"}-${record.sourceType || "unknown"}`}
								pagination={false}
								columns={sourceLoadColumns}
								dataSource={governanceOverview.sourceLoads || []}
								locale={{ emptyText: "暂无来源负载数据" }}
							/>
							<Table
								size="small"
								rowKey={(record) => record.projectKey || "default"}
								pagination={false}
								columns={projectLoadColumns}
								dataSource={governanceOverview.projectLoads || []}
								locale={{ emptyText: "暂无项目负载数据" }}
							/>
						</Space>
					) : (
						<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无治理指标" />
					)}
				</Card>

				<div className="mb-4 flex flex-wrap items-center gap-3">
					<Select
						allowClear
						placeholder="按任务过滤"
						style={{ width: 220 }}
						value={obsTaskId}
						options={taskOptions}
						onChange={(value) => setObsTaskId(value)}
					/>
					<Select
						allowClear
						placeholder="按来源类型过滤"
						style={{ width: 200 }}
						value={obsSourceType}
						options={sourceTypeOptions}
						onChange={(value) => setObsSourceType(value)}
					/>
					<Select
						style={{ width: 150 }}
						value={obsDays}
						options={[
							{ label: "最近 1 天", value: 1 },
							{ label: "最近 7 天", value: 7 },
							{ label: "最近 30 天", value: 30 },
							{ label: "最近 90 天", value: 90 },
						]}
						onChange={(value) => setObsDays(value)}
					/>
					<Space size={4}>
						<span>超时阈值(分钟)</span>
						<InputNumber
							min={1}
							max={1440}
							value={obsTimeoutMinutes}
							onChange={(value) => setObsTimeoutMinutes(Number(value || 10))}
						/>
					</Space>
					<Button className="rounded-2xl" icon={<ReloadOutlined />} onClick={() => void loadObservability()} loading={observabilityLoading}>
						刷新指标
					</Button>
				</div>
				{observability ? (
					<Space direction="vertical" size={16} style={{ width: "100%" }}>
						<Row gutter={[16, 16]}>
							<Col xs={12} md={6}>
								<Statistic title="总执行数" value={observability.total || 0} />
							</Col>
							<Col xs={12} md={6}>
								<Statistic title="成功率" value={observability.successRate || 0} suffix="%" precision={2} />
							</Col>
							<Col xs={12} md={6}>
								<Statistic title="超时率" value={observability.timeoutRate || 0} suffix="%" precision={2} />
							</Col>
							<Col xs={12} md={6}>
								<Statistic title="平均耗时(秒)" value={observability.avgDurationSeconds || 0} precision={2} />
							</Col>
							<Col xs={12} md={6}>
								<Statistic title="MTTR(秒)" value={observability.mttrSeconds || 0} precision={2} />
							</Col>
							<Col xs={12} md={6}>
								<Statistic title="运行中" value={observability.running || 0} />
							</Col>
							<Col xs={12} md={6}>
								<Statistic title="失败数" value={observability.failed || 0} />
							</Col>
							<Col xs={12} md={6}>
								<Statistic title="超时数" value={observability.timeout || 0} />
							</Col>
						</Row>
						<Row gutter={[16, 16]}>
							<Col xs={24} lg={10}>
								<Card size="small" title="失败分类 Top5" className="rounded-[24px]">
									<Space wrap>
										{(observability.failureTop || []).length ? (
											observability.failureTop.map((item) => (
												<Tag color="error" key={item.category}>
													{item.category}: {item.count}
												</Tag>
											))
										) : (
											<Tag>暂无失败数据</Tag>
										)}
									</Space>
								</Card>
							</Col>
							<Col xs={24} lg={14}>
								<Card size="small" title="日趋势" className="rounded-[24px]">
									{(observability.trend || []).length ? (
										<Table size="small" rowKey="day" pagination={false} columns={trendColumns} dataSource={observability.trend} />
									) : (
										<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前时间窗没有执行数据" />
									)}
								</Card>
							</Col>
						</Row>
					</Space>
				) : (
					<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无运行指标" />
				)}
			</PlatformSectionCard>

			<PlatformSectionCard
				title="任务清单"
				description="保留现场最常用的任务级操作：执行、查看历史、编辑、重建 DAG 和删除。"
				action={<Tag color="blue">{pagination.total || tasks.length} 条任务</Tag>}
			>
				<Table
					columns={columns}
					dataSource={tasks}
					rowKey="id"
					loading={loading}
					scroll={{ x: 1400 }}
					pagination={{
						current: pagination.current,
						pageSize: pagination.pageSize,
						total: pagination.total,
						showSizeChanger: true,
						showQuickJumper: true,
						showTotal: (total) => `共 ${total} 条`,
						onChange: (page, pageSize) => {
							setPagination((prev) => ({ ...prev, current: page, pageSize: pageSize || 20 }));
						},
					}}
				/>
			</PlatformSectionCard>

			<Modal
				open={executeProgressOpen}
				title={`执行进度${executingTaskName ? `：${executingTaskName}` : ""}`}
				mask={false}
				width={560}
				onCancel={() => {
					if (executeProgress.terminal) {
						stopExecutePolling();
					}
					setExecuteProgressOpen(false);
				}}
				footer={
					<Space>
						{executingTaskId ? (
							<Button onClick={() => router.push(`/explore/etl/transform/${executingTaskId}/executions`)}>
								查看历史
							</Button>
						) : null}
						<Button
							type="primary"
							onClick={() => {
								if (executeProgress.terminal) {
									stopExecutePolling();
								}
								setExecuteProgressOpen(false);
							}}
						>
							{executeProgress.terminal ? "关闭" : "最小化"}
						</Button>
					</Space>
				}
			>
				<Space direction="vertical" style={{ width: "100%" }} size={12}>
					<Progress percent={executeProgress.percent} status={executeProgress.status} />
					<Alert
						type={
							executeProgress.status === "success"
								? "success"
								: executeProgress.status === "exception"
									? "error"
									: "info"
						}
						message={executeProgress.stage}
						description={executeProgress.detail}
						showIcon
					/>
				</Space>
			</Modal>
		</div>
	);
}
