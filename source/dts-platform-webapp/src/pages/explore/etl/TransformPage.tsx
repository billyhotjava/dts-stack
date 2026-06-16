import { useEffect, useRef, useState } from "react";
import { Alert, Button, Card, Modal, Progress, Space, Tag, Typography, message } from "antd";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { } from "@ant-design/icons";
import { useRouter } from "@/routes/hooks";
import { PageHeader } from "@/components/page-header";
import {
	ingestionTaskAPI,
	type IngestionExecutionDTO,
	type IngestionExecutionObservabilityDTO,
	type IngestionGovernanceOverviewDTO,
	type IngestionTaskDTO,
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
	const [latestExecutions, setLatestExecutions] = useState<Record<number, IngestionExecutionDTO | null>>({});
	const [observability, setObservability] = useState<IngestionExecutionObservabilityDTO | null>(null);
	const [governanceOverview, setGovernanceOverview] = useState<IngestionGovernanceOverviewDTO | null>(null);
	const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
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
	}, [pagination.current, pagination.pageSize, statusFilter]);

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

	/** Whether a task is currently in a non-interruptible execution phase. */
	const isTaskBusy = (record: IngestionTaskDTO): boolean => {
		const lastStatus = (record.lastExecutionStatus || "").toLowerCase();
		return lastStatus === "preparing" || lastStatus === "running" || executingTaskId === record.id;
	};

	const columns: ColumnsType<IngestionTaskDTO> = [
		{
			title: "任务名称",
			dataIndex: "name",
			key: "name",
			width: 200,
			sorter: (a, b) => (a.name || "").localeCompare(b.name || ""),
			render: (text: string, record: IngestionTaskDTO) => (
				<a onClick={() => router.push(`/explore/etl/transform/${record.id}`)}>{text}</a>
			),
		},
		{
			title: "数据源类型",
			dataIndex: "sourceType",
			key: "sourceType",
			width: 150,
			sorter: (a, b) => (a.sourceType || "").localeCompare(b.sourceType || ""),
		},
		{
			title: "目标表",
			dataIndex: "tableMapping",
			key: "tableMapping",
			width: 220,
			sorter: (a, b) => (a.tableMapping?.length || 0) - (b.tableMapping?.length || 0),
			render: renderTargets,
		},
		{
			title: "同步模式",
			dataIndex: "syncMode",
			key: "syncMode",
			width: 120,
			sorter: (a, b) => (a.syncMode || "").localeCompare(b.syncMode || ""),
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
			sorter: (a, b) => (a.preCheckStatus || "").localeCompare(b.preCheckStatus || ""),
			render: renderPreCheckStatus,
		},
		{
			title: "状态",
			dataIndex: "status",
			key: "status",
			width: 100,
			sorter: (a, b) => (a.status || "").localeCompare(b.status || ""),
			render: renderStatus,
		},
		{
			title: "最后执行状态",
			dataIndex: "lastExecutionStatus",
			key: "lastExecutionStatus",
			width: 120,
			sorter: (a, b) => (a.lastExecutionStatus || "").localeCompare(b.lastExecutionStatus || ""),
			render: renderExecutionStatus,
		},
		{
			title: "最后执行时间",
			dataIndex: "lastExecutedAt",
			key: "lastExecutedAt",
			width: 180,
			sorter: (a, b) => {
				const ta = a.lastExecutedAt ? new Date(a.lastExecutedAt).getTime() : 0;
				const tb = b.lastExecutedAt ? new Date(b.lastExecutedAt).getTime() : 0;
				return ta - tb;
			},
			render: (text: string) => (text ? formatTimestamp(text) : "-"),
		},
		{
			title: "行数",
			key: "rows",
			width: 150,
			sorter: (a, b) => {
				const va = (a.id ? latestExecutions[a.id]?.rowsWritten : 0) || 0;
				const vb = (b.id ? latestExecutions[b.id]?.rowsWritten : 0) || 0;
				return va - vb;
			},
			render: (_: any, record: IngestionTaskDTO) => {
				const latest = record.id ? latestExecutions[record.id] : null;
				return `${renderNumber(latest?.rowsRead)} / ${renderNumber(latest?.rowsWritten)}`;
			},
		},
		{
			title: "耗时",
			key: "duration",
			width: 100,
			sorter: (a, b) => {
				const ea = a.id ? latestExecutions[a.id] : null;
				const eb = b.id ? latestExecutions[b.id] : null;
				const da = ea?.startTime && ea?.endTime
					? new Date(ea.endTime).getTime() - new Date(ea.startTime).getTime()
					: 0;
				const db = eb?.startTime && eb?.endTime
					? new Date(eb.endTime).getTime() - new Date(eb.startTime).getTime()
					: 0;
				return da - db;
			},
			render: (_: any, record: IngestionTaskDTO) => renderDuration(record.id ? latestExecutions[record.id] : null),
		},
		{
			title: "错误摘要",
			key: "errorSummary",
			width: 240,
			sorter: (a, b) => {
				const ea = a.id ? latestExecutions[a.id] : null;
				const eb = b.id ? latestExecutions[b.id] : null;
				const va = ea?.failureCategory || ea?.errorMessage || "";
				const vb = eb?.failureCategory || eb?.errorMessage || "";
				return va.localeCompare(vb);
			},
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
			sorter: (a, b) => {
				const ta = a.createdDate ? new Date(a.createdDate).getTime() : 0;
				const tb = b.createdDate ? new Date(b.createdDate).getTime() : 0;
				return ta - tb;
			},
			render: (text: string) => (text ? formatTimestamp(text) : "-"),
		},
		{
			title: "创建人",
			dataIndex: "createdBy",
			key: "createdBy",
			width: 120,
			sorter: (a, b) => (a.createdBy || "").localeCompare(b.createdBy || ""),
		},
			{
			title: "操作",
				dataIndex: "actions",
				key: "action",
				width: 520,
				fixed: "right",
				render: (_: any, record: IngestionTaskDTO) => (
					<Space size="small" wrap>
						<Button
							size="small"
							type="primary"
							onClick={() => handleExecute(record.id!, record.name)}
							loading={isTaskBusy(record) && !executeProgress.terminal}
							disabled={record.status === "deleted" || isTaskBusy(record)}
						>
							{isTaskBusy(record)
								? (record.lastExecutionStatus || "").toLowerCase() === "preparing" ? "准备中" : "执行中"
								: "运行"}
						</Button>
						<Button size="small" disabled title="当前入湖任务接口未开放中断动作，运行中任务会自动轮询到终态">
							停止
						</Button>
						<Button
							size="small"
							onClick={() => handleExecute(record.id!, record.name)}
							disabled={record.status === "deleted" || isTaskBusy(record)}
						>
							重跑
						</Button>
						<Button size="small" onClick={() => router.push(`/ops/backfill?taskId=${record.id}`)}>
							补数
						</Button>
						<Button
							size="small"
							onClick={() => router.push(`/explore/etl/transform/${record.id}/executions`)}
						>
							查看日志
						</Button>
						<Button size="small" onClick={() => router.push(`/ops/instances?taskId=${record.id}`)}>
							查看实例
						</Button>
						<Button
							size="small"
							onClick={() => router.push(`/explore/etl/transform/${record.id}/edit`)}
							disabled={record.status === "deleted" || isTaskBusy(record)}
						>
							编辑
						</Button>
						<Button
							size="small"
							danger
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
			<PageHeader
				title="数据开发中心 / ETL 转换与入湖任务"
				actions={
					<Space wrap>
						<Button onClick={() => router.push("/ops/overview")}>查看运维</Button>
						<Button onClick={() => router.push("/explore/etl/transform/new")}>新建转换</Button>
						<Button type="primary" onClick={() => router.push("/explore/etl/transform/new")}>
							创建入湖任务
						</Button>
					</Space>
				}
			/>
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
					<CompactTable
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
								setPagination((prev) => ({
									...prev,
									// 切换每页条数时回到第 1 页，避免当前页超出新页数范围
									current: pageSize !== prev.pageSize ? 1 : page,
									pageSize: pageSize || 10,
								}));
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
