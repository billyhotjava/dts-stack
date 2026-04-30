import { useEffect, useRef, useState } from "react";
import { Alert, Button, Card, Modal, Progress, Space, Table, Tag, Typography, message } from "antd";
import {
	PlayCircleOutlined,
	EditOutlined,
	DeleteOutlined,
	HistoryOutlined,
	ReloadOutlined,
	SyncOutlined,
} from "@ant-design/icons";
import { useRouter } from "@/routes/hooks";
import {
	ingestionTaskAPI,
	type IngestionExecutionDTO,
	type IngestionExecutionLog,
	type IngestionExecutionObservabilityDTO,
	type IngestionGovernanceOverviewDTO,
	type IngestionTaskDTO,
	type PreCheckResult,
} from "@/api/ingestion";
import { resolveAsyncRunSubmitFeedback, mapExecutionToProgressView } from "./transformCreateAsyncRun.helpers";
import { formatTimestamp } from "@/utils/format";

const { Text } = Typography;

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
	const [overviewLoading, setOverviewLoading] = useState(false);
	const [preCheckingId, setPreCheckingId] = useState<number | null>(null);
	const [retryingKey, setRetryingKey] = useState<string | null>(null);
	const [latestExecutions, setLatestExecutions] = useState<Record<number, IngestionExecutionDTO | null>>({});
	const [observability, setObservability] = useState<IngestionExecutionObservabilityDTO | null>(null);
	const [governanceOverview, setGovernanceOverview] = useState<IngestionGovernanceOverviewDTO | null>(null);
	const [logOpen, setLogOpen] = useState(false);
	const [logLoading, setLogLoading] = useState(false);
	const [logTitle, setLogTitle] = useState("");
	const [logDetail, setLogDetail] = useState<IngestionExecutionLog | null>(null);
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
		void loadRunCenterOverview();
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
			void loadLatestExecutions(content);
			const total = typeof result?.totalElements === "number" ? result.totalElements : content.length;
			setPagination((prev) => ({ ...prev, total }));
		} catch {
			setTasks([]);
		} finally {
			setLoading(false);
		}
	};

	const loadRunCenterOverview = async () => {
		setOverviewLoading(true);
		try {
			const [nextObservability, nextGovernance] = await Promise.all([
				ingestionTaskAPI.getExecutionsObservability({ days: 7 }),
				ingestionTaskAPI.getGovernanceOverview({ hours: 24 }),
			]);
			setObservability(nextObservability);
			setGovernanceOverview(nextGovernance);
		} catch {
			setObservability(null);
			setGovernanceOverview(null);
		} finally {
			setOverviewLoading(false);
		}
	};

	const loadLatestExecutions = async (items: IngestionTaskDTO[]) => {
		const pairs = await Promise.all(
			items
				.filter((task) => task.id)
				.map(async (task) => {
					try {
						const latest = await ingestionTaskAPI.getLatestExecution(task.id!);
						return [task.id!, latest] as const;
					} catch {
						return [task.id!, null] as const;
					}
				})
		);
		const next: Record<number, IngestionExecutionDTO | null> = {};
		pairs.forEach(([taskId, execution]) => {
			next[taskId] = execution;
		});
		setLatestExecutions(next);
	};

	const mapExecutionProgress = (execution: IngestionExecutionDTO | null, elapsedMs: number): ExecutionProgressView => {
		const view = mapExecutionToProgressView(execution, elapsedMs);
		return {
			percent: view.progress,
			status: view.status,
			stage: view.stage,
			detail: view.detail,
			terminal: view.terminal,
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
					void loadRunCenterOverview();
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
					void loadRunCenterOverview();
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

	const submitLatestRetry = async (record: IngestionTaskDTO, mode: "FAILED_ONLY" | "FULL_RERUN") => {
		if (!record.id) return;
		let latest = latestExecutions[record.id];
		if (!latest) {
			latest = await ingestionTaskAPI.getLatestExecution(record.id);
		}
		if (!latest?.id) {
			message.info("该任务暂无可重跑的执行记录");
			return;
		}
		const status = String(latest.status || "").toLowerCase();
		if (status === "running" || status === "preparing") {
			message.warning("最新执行仍在运行中，暂不能重跑");
			return;
		}
		const key = `${record.id}:${mode}`;
		setRetryingKey(key);
		try {
			const submit = await ingestionTaskAPI.retryExecutionAsync(record.id, latest.id, { mode });
			message.success(mode === "FULL_RERUN" ? "已提交整批重跑任务" : "已提交失败重试任务");
			startExecuteProgressPolling(record.id, record.name, submit?.pollIntervalMs);
			void loadTasks();
			void loadRunCenterOverview();
		} catch (error: any) {
			const feedback = resolveAsyncRunSubmitFeedback(error, "retry");
			if (feedback.level === "warning") {
				message.warning(feedback.message);
				return;
			}
			message.error(feedback.message);
		} finally {
			setRetryingKey(null);
		}
	};

	const handleRetryLatest = (record: IngestionTaskDTO, mode: "FAILED_ONLY" | "FULL_RERUN") => {
		const title = mode === "FULL_RERUN" ? "确认整批重跑" : "确认失败重试";
		const content =
			mode === "FULL_RERUN"
				? `将按当前配置重新执行任务 "${record.name}" 的整批作业，确认继续？`
				: `将基于任务 "${record.name}" 的最新失败执行提交失败重试，确认继续？`;
		Modal.confirm({
			title,
			content,
			okText: mode === "FULL_RERUN" ? "确认重跑" : "确认重试",
			onOk: () => submitLatestRetry(record, mode),
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

	const handleViewLog = async (record: IngestionTaskDTO) => {
		if (!record.id) return;
		let latest = latestExecutions[record.id];
		if (!latest) {
			latest = await ingestionTaskAPI.getLatestExecution(record.id);
		}
		if (!latest?.id) {
			message.info("该任务暂无执行日志");
			return;
		}
		setLogTitle(`${record.name} · 执行 #${latest.id}`);
		setLogDetail(null);
		setLogOpen(true);
		setLogLoading(true);
		try {
			const log = await ingestionTaskAPI.getExecutionLog(record.id, latest.id, { scope: "all" });
			setLogDetail(log);
		} catch {
			setLogDetail({
				taskId: record.id,
				executionId: latest.id,
				message: latest.errorMessage || "日志读取失败",
				failureCategory: latest.failureCategory,
				failureAdvice: latest.failureAdvice,
				log: latest.errorMessage,
			});
		} finally {
			setLogLoading(false);
		}
	};

	const handlePreCheck = async (record: IngestionTaskDTO) => {
		if (!record.id) return;
		setPreCheckingId(record.id);
		try {
			const result: PreCheckResult = await ingestionTaskAPI.preCheck(record.id);
			const failedRows = result?.failedRows || 0;
			const totalRows = result?.totalRows || 0;
			const status = failedRows > 0 ? "WARN" : "PASS";
			const details = (result?.errorsByRule || [])
				.slice(0, 5)
				.map((item) => `${item.ruleName || item.ruleType}: ${item.failCount}`)
				.join("；");
			Modal[failedRows > 0 ? "warning" : "success"]({
				title: `预检 ${status}`,
				content: (
					<div className="space-y-2">
						<div>
							总行数 {totalRows.toLocaleString()}，通过 {(result?.passedRows || 0).toLocaleString()}，失败 {failedRows.toLocaleString()}。
						</div>
						{details ? <div className="text-xs text-slate-500">{details}</div> : null}
					</div>
				),
			});
			void loadTasks();
		} catch (error: any) {
			message.error(error?.message || "预检执行失败");
		} finally {
			setPreCheckingId(null);
		}
	};

	const renderPreCheckStatus = (status?: string) => {
		const normalized = String(status || "").trim().toUpperCase();
		if (!normalized) return <Tag>未预检</Tag>;
		if (["PASS", "PASSED", "SUCCESS"].includes(normalized)) return <Tag color="success">PASS</Tag>;
		if (["WARN", "WARNING"].includes(normalized)) return <Tag color="warning">WARN</Tag>;
		if (["FAIL", "FAILED", "ERROR"].includes(normalized)) return <Tag color="error">FAIL</Tag>;
		return <Tag>{status}</Tag>;
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

	const renderNumber = (value?: number) => (typeof value === "number" ? value.toLocaleString() : "-");

	const renderDuration = (execution?: IngestionExecutionDTO | null) => {
		if (!execution?.startTime || !execution?.endTime) return "-";
		const start = new Date(execution.startTime).getTime();
		const end = new Date(execution.endTime).getTime();
		if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return "-";
		const seconds = Math.round((end - start) / 1000);
		if (seconds < 60) return `${seconds}s`;
		return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
	};

	const renderTargets = (mapping?: IngestionTaskDTO["tableMapping"]) => {
		if (!Array.isArray(mapping) || mapping.length === 0) return "-";
		const targets = mapping.map((item) => item.target || item.source).filter(Boolean);
		if (targets.length <= 2) return targets.join(", ");
		return `${targets.slice(0, 2).join(", ")} 等 ${targets.length} 表`;
	};

	const parseTaskSyncConfig = (record: IngestionTaskDTO): Record<string, any> => {
		const config = (record.syncConfig ?? {}) as any;
		if (typeof config === "string") {
			try {
				return JSON.parse(config) || {};
			} catch {
				return {};
			}
		}
		return config && typeof config === "object" ? config : {};
	};

	const supportsTimeWindowBackfill = (record: IngestionTaskDTO): boolean => {
		if (String(record.syncMode || "").toLowerCase() !== "incremental") return false;
		const type = String(parseTaskSyncConfig(record).incrementalType || "").trim().toLowerCase();
		return !["number", "numeric", "integer", "bigint", "long", "int"].includes(type);
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
			title: "目标表",
			dataIndex: "tableMapping",
			key: "tableMapping",
			width: 220,
			render: renderTargets,
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
			title: "预检",
			dataIndex: "preCheckStatus",
			key: "preCheckStatus",
			width: 100,
			render: renderPreCheckStatus,
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
			title: "行数",
			key: "rows",
			width: 150,
			render: (_: any, record: IngestionTaskDTO) => {
				const latest = record.id ? latestExecutions[record.id] : null;
				return `${renderNumber(latest?.rowsRead)} / ${renderNumber(latest?.rowsWritten)}`;
			},
		},
		{
			title: "耗时",
			key: "duration",
			width: 100,
			render: (_: any, record: IngestionTaskDTO) => renderDuration(record.id ? latestExecutions[record.id] : null),
		},
		{
			title: "错误摘要",
			key: "errorSummary",
			width: 240,
			render: (_: any, record: IngestionTaskDTO) => {
				const latest = record.id ? latestExecutions[record.id] : null;
				if (!latest?.errorMessage && !latest?.failureCategory) return "-";
				return (
					<div className="max-w-[220px]">
						{latest.failureCategory ? <Tag color="error">{latest.failureCategory}</Tag> : null}
						<div className="truncate text-xs text-slate-500">{latest.failureAdvice || latest.errorMessage}</div>
					</div>
				);
			},
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
				width: 620,
				render: (_: any, record: IngestionTaskDTO) => {
					const latest = record.id ? latestExecutions[record.id] : null;
					const latestStatus = String(latest?.status || "").toLowerCase();
					const canRetryFailed = latestStatus === "failed";
					const canFullRerun = Boolean(latest?.id) && !["running", "preparing"].includes(latestStatus);
					const canBackfill = supportsTimeWindowBackfill(record);
					return (
						<Space size="small" wrap>
							<Button
								size="small"
								type="primary"
								icon={<PlayCircleOutlined />}
								onClick={() => handleExecute(record.id!, record.name)}
								loading={isTaskBusy(record) && !executeProgress.terminal}
								disabled={record.status === "deleted" || isTaskBusy(record)}
							>
								{isTaskBusy(record)
									? (record.lastExecutionStatus || "").toLowerCase() === "preparing" ? "准备中" : "执行中"
									: "执行"}
							</Button>
							<Button
								size="small"
								loading={preCheckingId === record.id}
								onClick={() => handlePreCheck(record)}
								disabled={record.status === "deleted" || isTaskBusy(record)}
							>
								预检
							</Button>
							{canRetryFailed ? (
								<Button
									size="small"
									loading={retryingKey === `${record.id}:FAILED_ONLY`}
									onClick={() => handleRetryLatest(record, "FAILED_ONLY")}
									disabled={record.status === "deleted" || isTaskBusy(record)}
								>
									失败重试
								</Button>
							) : null}
							<Button
								size="small"
								loading={retryingKey === `${record.id}:FULL_RERUN`}
								onClick={() => handleRetryLatest(record, "FULL_RERUN")}
								disabled={record.status === "deleted" || isTaskBusy(record) || !canFullRerun}
							>
								整批重跑
							</Button>
							{canBackfill ? (
								<Button
									size="small"
									icon={<ReloadOutlined />}
									onClick={() => router.push(`/explore/etl/transform/${record.id}/executions`)}
									disabled={record.status === "deleted" || isTaskBusy(record)}
								>
									回填
								</Button>
							) : null}
							<Button
								size="small"
								icon={<HistoryOutlined />}
								onClick={() => router.push(`/explore/etl/transform/${record.id}/executions`)}
							>
								历史
							</Button>
							<Button size="small" onClick={() => handleViewLog(record)}>
								日志
							</Button>
							<Button size="small" onClick={() => router.push("/catalog/lineage")}>
								血缘
							</Button>
							<Button
								size="small"
								icon={<EditOutlined />}
								onClick={() => router.push(`/explore/etl/transform/${record.id}/edit`)}
								disabled={record.status === "deleted" || isTaskBusy(record)}
							>
								编辑
							</Button>
							<Button
								size="small"
								icon={<SyncOutlined />}
								onClick={() => handleRebuildDag(record.id!, record.name)}
								disabled={record.status === "deleted" || record.airflowEnabled === false || isTaskBusy(record)}
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
					);
				},
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
								onClick={() => {
									void loadTasks();
									void loadRunCenterOverview();
								}}
								loading={loading || overviewLoading}
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
				<div className="mb-4 grid gap-3 md:grid-cols-4">
					<div className="rounded border border-slate-200 p-3">
						<Text type="secondary">7日执行</Text>
						<div className="mt-1 text-xl font-semibold">{renderNumber(observability?.total)}</div>
						<div className="text-xs text-slate-500">
							成功率 {observability?.successRate != null ? `${Math.round(observability.successRate)}%` : "-"}
						</div>
					</div>
					<div className="rounded border border-slate-200 p-3">
						<Text type="secondary">失败 / 运行中</Text>
						<div className="mt-1 text-xl font-semibold">
							{renderNumber(observability?.failed)} / {renderNumber(observability?.running)}
						</div>
						<div className="text-xs text-slate-500">
							平均耗时 {observability?.avgDurationSeconds != null ? `${Math.round(observability.avgDurationSeconds)}s` : "-"}
						</div>
					</div>
					<div className="rounded border border-slate-200 p-3">
						<Text type="secondary">调度状态</Text>
						<div className="mt-1 text-xl font-semibold">
							{renderNumber(governanceOverview?.running)} / {renderNumber(governanceOverview?.preparing)}
						</div>
						<div className="text-xs text-slate-500">运行中 / 准备中</div>
					</div>
					<div className="rounded border border-slate-200 p-3">
						<Text type="secondary">治理阻塞</Text>
						<div className="mt-1 text-xl font-semibold">{renderNumber(governanceOverview?.blockedByPolicy)}</div>
						<div className="text-xs text-slate-500">队列 {renderNumber(governanceOverview?.queueLength)}</div>
					</div>
				</div>
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

			<Modal
				open={logOpen}
				title={logTitle || "执行日志"}
				width={900}
				onCancel={() => setLogOpen(false)}
				footer={[
					<Button key="close" onClick={() => setLogOpen(false)}>
						关闭
					</Button>,
				]}
			>
				{logLoading ? (
					<Progress percent={30} status="active" />
				) : (
					<div className="space-y-3">
						{logDetail?.failureCategory || logDetail?.failureAdvice ? (
							<Alert
								type="error"
								showIcon
								message={logDetail?.failureCategory || "执行失败"}
								description={logDetail?.failureAdvice || logDetail?.message}
							/>
						) : null}
						<pre className="max-h-[520px] overflow-auto rounded bg-slate-950 p-3 text-xs text-slate-50">
							{logDetail?.log || logDetail?.message || "暂无日志内容"}
						</pre>
					</div>
				)}
			</Modal>
		</div>
	);
}
