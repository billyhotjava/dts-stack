import { useEffect, useRef, useState } from "react";
import { Alert, Button, Card, Modal, Progress, Space, Table, Tag, message } from "antd";
import {
	PlayCircleOutlined,
	EditOutlined,
	DeleteOutlined,
	HistoryOutlined,
	ReloadOutlined,
	SyncOutlined,
} from "@ant-design/icons";
import { useRouter } from "@/routes/hooks";
import { ingestionTaskAPI, type IngestionTaskDTO, type IngestionExecutionDTO } from "@/api/ingestion";
import { resolveAsyncRunSubmitFeedback } from "./transformCreateAsyncRun.helpers";
import { formatTimestamp } from "@/utils/format";
import { normalizeText } from "@/utils/textUtils";

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

	/** Adaptive polling: starts fast, slows down over time. Never hard-stops. */
	const adaptivePollDelay = (elapsedMs: number): number => {
		if (elapsedMs < 30_000) return 3_000; // first 30s: every 3s
		if (elapsedMs < 120_000) return 5_000; // 30s-2min: every 5s
		if (elapsedMs < 300_000) return 10_000; // 2-5min: every 10s
		return 30_000; // >5min: every 30s
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
			const allContent = Array.isArray(result?.content) ? result.content : [];
			const content = statusFilter ? allContent : allContent.filter((t: any) => t.status !== "deleted");
			setTasks(content);
			const total = typeof result?.totalElements === "number" ? result.totalElements : content.length;
			setPagination((prev) => ({ ...prev, total }));
		} catch {
			setTasks([]);
		} finally {
			setLoading(false);
		}
	};

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

	const startExecuteProgressPolling = (taskId: number, taskName: string, _pollIntervalMs?: number) => {
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
				} catch (error: any) {
					const feedback = resolveAsyncRunSubmitFeedback(error, "execute");
					if (feedback.level === "warning") {
						message.warning(feedback.message);
						return;
					}
					message.error(feedback.message);
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
					setTasks((prev) => prev.filter((t) => t.id !== id));
					setPagination((prev) => ({ ...prev, total: Math.max(0, prev.total - 1) }));
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
		const config = statusMap[status || "draft"] ?? { color: "default", text: status || "unknown" };
		return <Tag color={config.color}>{config.text}</Tag>;
	};

	const renderExecutionStatus = (status?: string) => {
		const statusMap: Record<string, { color: string; text: string }> = {
			preparing: { color: "processing", text: "准备中" },
			running: { color: "processing", text: "运行中" },
			success: { color: "success", text: "成功" },
			failed: { color: "error", text: "失败" },
		};
		const config = statusMap[status || ""];
		return config ? <Tag color={config.color}>{config.text}</Tag> : <span>-</span>;
	};

	/** Whether a task is currently in a non-interruptible execution phase. */
	const isTaskBusy = (record: IngestionTaskDTO): boolean => {
		const lastStatus = (record.lastExecutionStatus || "").toLowerCase();
		return lastStatus === "preparing" || lastStatus === "running" || executingTaskId === record.id;
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
			width: 420,
			render: (_: any, record: IngestionTaskDTO) => (
				<Space size="small">
					<Button
						size="small"
						type="primary"
						icon={<PlayCircleOutlined />}
						onClick={() => handleExecute(record.id!, record.name)}
						loading={executingTaskId === record.id && !executeProgress.terminal}
						disabled={record.status === "deleted" || (executingTaskId === record.id && !executeProgress.terminal)}
					>
						{executingTaskId === record.id && !executeProgress.terminal ? "执行中" : "执行"}
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
		<div className="space-y-4" data-testid="platform-transform-page">
			<Card
				title="入湖任务中心"
				extra={
					<Space wrap>
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
						<Button
							className="rounded-2xl"
							icon={<ReloadOutlined />}
							onClick={() => void loadTasks()}
							loading={loading}
							data-testid="platform-transform-refresh"
						>
							刷新
						</Button>
						<Button
							className="rounded-2xl"
							type="primary"
							onClick={() => router.push("/explore/etl/transform/new")}
							data-testid="platform-transform-create"
						>
							创建入湖任务
						</Button>
					</Space>
				}
			/>

			<Card title="任务清单" extra={<Tag color="blue">{pagination.total || tasks.length} 条任务</Tag>}>
				<div data-testid="platform-transform-table">
					<Table
						columns={columns}
						dataSource={tasks}
						rowKey="id"
						loading={loading}
						scroll={{ x: 1600 }}
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
				</div>
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
				<Space direction="vertical" style={{ width: "100%" }} size={12} data-testid="platform-transform-progress">
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
