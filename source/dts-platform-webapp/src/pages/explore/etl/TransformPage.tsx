import { useEffect, useRef, useState } from "react";
import { Button, Card, Space, Table, Tag, message, Modal, Alert, Progress } from "antd";
import {
	PlayCircleOutlined,
	EditOutlined,
	DeleteOutlined,
	HistoryOutlined,
	ReloadOutlined,
	SyncOutlined,
} from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import { useRouter } from "@/routes/hooks";
import {
	ingestionTaskAPI,
	type IngestionTaskDTO,
	type IngestionExecutionDTO,
	resolveExecutionPollIntervalMs,
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

	const stopExecutePolling = () => {
		if (executePollTimerRef.current !== null) {
			window.clearInterval(executePollTimerRef.current);
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
		} catch (error: any) {
			message.error("加载任务列表失败: " + (error.message || "未知错误"));
			setTasks([]);
		} finally {
			setLoading(false);
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
		pollIntervalMs?: number
	) => {
		stopExecutePolling();
		executeStartedAtRef.current = Date.now();
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

		const poll = async () => {
			const elapsed = Date.now() - executeStartedAtRef.current;
			if (elapsed > 5 * 60 * 1000) {
				stopExecutePolling();
				setExecuteProgress({
					percent: 100,
					status: "exception",
					stage: "状态同步超时",
					detail: "等待超时，请刷新列表或进入执行历史查看状态。",
					terminal: true,
				});
				return;
			}
			try {
				const execution = await ingestionTaskAPI.getLatestExecution(taskId);
				const next = mapExecutionProgress(execution, elapsed);
				setExecuteProgress(next);
				if (next.terminal) {
					stopExecutePolling();
					void loadTasks();
				}
			} catch {
				setExecuteProgress((prev) => ({ ...prev, detail: "状态同步中，稍后自动重试。" }));
			}
		};

		void poll();
		executePollTimerRef.current = window.setInterval(() => {
			void poll();
		}, resolveExecutionPollIntervalMs(pollIntervalMs));
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
				} catch (error: any) {
					message.error("执行失败: " + (error.message || "未知错误"));
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
				} catch (error: any) {
					message.error("重建失败: " + (error.message || "未知错误"));
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
				} catch (error: any) {
					message.error("删除失败: " + (error.message || "未知错误"));
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

	return (
		<div className="flex flex-col gap-6">
			<PageHeader
				title="入湖任务管理"
				description="使用 Addax 生成作业配置，Airflow 编排执行"
				actions={
					<Space>
						<Button icon={<ReloadOutlined />} onClick={() => void loadTasks()} loading={loading}>
							刷新
						</Button>
						<Button type="primary" onClick={() => router.push("/explore/etl/transform/new")}>
							创建入湖任务
						</Button>
					</Space>
				}
			/>

			<Card>
				<Space style={{ marginBottom: 16 }}>
					<span>状态筛选：</span>
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
				</Space>

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
			</Card>

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
