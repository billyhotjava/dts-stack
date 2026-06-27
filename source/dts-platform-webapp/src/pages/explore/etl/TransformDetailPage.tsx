import { useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "@/routes/hooks";
import {
	Button,
	Card,
	Descriptions,
	Dropdown,
	Space,
	Tabs,
	Tag,
	message,
	Spin,
	Modal,
	Form,
	Input,
	Select,
	Typography,
	Drawer,
	Progress,
	Alert,
} from "antd";
import { CompactTable } from "@/components/table";
import { } from "@ant-design/icons";
import { useRouter } from "@/routes/hooks";
import {
	ingestionTaskAPI,
	type IngestionTaskDTO,
	type IngestionExecutionDTO,
	type IngestionExecutionLog,
	type IngestionIncrementalStateDTO,
	type IngestionRealtimeStatusDTO,
	type StagingErrorSummary,
	type StagingRuleErrorSummary,
} from "@/api/ingestion";
import dataSourcesService, { type InfraDataSource } from "@/api/services/dataSourcesService";
import { listSqlModels } from "@/api/platformApi";
import RollbackImpactModal, { type RollbackRequest } from "@/components/rollback/RollbackImpactModal";
import ExecutionHistoryTable from "./components/ExecutionHistoryTable";
import { resolveAsyncRunSubmitFeedback, mapExecutionToProgressView } from "./transformCreateAsyncRun.helpers";
import { normalizeText } from "@/utils/textUtils";

const { Text } = Typography;

type ExecutionProgressView = {
	percent: number;
	status: "active" | "success" | "exception";
	stage: string;
	detail: string;
	terminal: boolean;
};

export default function TransformDetailPage() {
	const { id } = useParams();
	const router = useRouter();
	const [activeTab, setActiveTab] = useState<string>("config");
	const [task, setTask] = useState<IngestionTaskDTO | null>(null);
	const [sourceDetail, setSourceDetail] = useState<InfraDataSource | null>(null);
	const [loading, setLoading] = useState(false);
	const [dbtModalOpen, setDbtModalOpen] = useState(false);
	const [dbtSaving, setDbtSaving] = useState(false);
	const [dbtModels, setDbtModels] = useState<any[]>([]);
	const [dbtModelsLoading, setDbtModelsLoading] = useState(false);
	const [selectedModelNames, setSelectedModelNames] = useState<string[]>([]);
	const [dbtForm] = Form.useForm();
	const [latestExecution, setLatestExecution] = useState<IngestionExecutionDTO | null>(null);
	const [latestExecutionLoading, setLatestExecutionLoading] = useState(false);
	const [logVisible, setLogVisible] = useState(false);
	const [logLoading, setLogLoading] = useState(false);
	const [logContent, setLogContent] = useState("");
	const [logMeta, setLogMeta] = useState<IngestionExecutionLog | null>(null);
	const [executeSubmitting, setExecuteSubmitting] = useState(false);
	const [incrementalStates, setIncrementalStates] = useState<IngestionIncrementalStateDTO[]>([]);
	const [incrementalStatesLoading, setIncrementalStatesLoading] = useState(false);
	const [realtimeStatus, setRealtimeStatus] = useState<IngestionRealtimeStatusDTO | null>(null);
	const [realtimeStatusLoading, setRealtimeStatusLoading] = useState(false);
	const [badRowSummary, setBadRowSummary] = useState<StagingErrorSummary | null>(null);
	const [badRowSummaryLoading, setBadRowSummaryLoading] = useState(false);
	const [badRowDownloading, setBadRowDownloading] = useState(false);
	const [incrementalStale, setIncrementalStale] = useState(false);
	const [realtimeStale, setRealtimeStale] = useState(false);
	const [executeProgressOpen, setExecuteProgressOpen] = useState(false);
	const [executeProgress, setExecuteProgress] = useState<ExecutionProgressView>({
		percent: 0,
		status: "active",
		stage: "等待提交",
		detail: "",
		terminal: false,
	});
	const [rollbackOpen, setRollbackOpen] = useState(false);
	const [rollbackRequest, setRollbackRequest] = useState<RollbackRequest | null>(null);
	const executePollTimerRef = useRef<number | null>(null);
	const executeStartedAtRef = useRef<number>(0);

	useEffect(() => {
		if (id) {
			loadTask();
		}
	}, [id]);

	useEffect(() => {
		if (!task?.id) {
			setIncrementalStates([]);
			return;
		}
		if (normalizeText(task.syncMode).toLowerCase() !== "incremental") {
			setIncrementalStates([]);
			return;
		}
		void loadIncrementalStates(Number(task.id));
	}, [task?.id, task?.syncMode]);

	useEffect(() => {
		if (!task?.id) {
			setRealtimeStatus(null);
			return;
		}
		if (normalizeText(task.syncMode).toLowerCase() !== "cdc") {
			setRealtimeStatus(null);
			return;
		}
		void loadRealtimeStatus(Number(task.id), true);
	}, [task?.id, task?.syncMode]);

	useEffect(() => {
		if (!task?.id || !normalizeText(task.stagingTableName)) {
			setBadRowSummary(null);
			return;
		}
		void loadBadRowSummary(Number(task.id), true);
	}, [task?.id, task?.stagingTableName]);

	useEffect(() => {
		return () => {
			if (executePollTimerRef.current !== null) {
				window.clearTimeout(executePollTimerRef.current);
				executePollTimerRef.current = null;
			}
		};
	}, []);

	useEffect(() => {
		if (!task?.sourceDataSourceId) {
			setSourceDetail(null);
			return;
		}
		const loadSource = async () => {
			try {
				const detail = await dataSourcesService.detail(String(task.sourceDataSourceId));
				setSourceDetail(detail);
			} catch {
				setSourceDetail(null);
			}
		};
		loadSource();
	}, [task?.sourceDataSourceId]);

	const loadTask = async () => {
		setLoading(true);
		try {
			const result = await ingestionTaskAPI.getTask(Number(id));
			setTask(result);
		} catch (error: any) {
			message.error("加载任务详情失败: " + (error.message || "未知错误"));
		} finally {
			setLoading(false);
		}
	};

	const loadLatestExecution = async (silent?: boolean) => {
		if (!task?.id) return;
		try {
			if (!silent) {
				setLatestExecutionLoading(true);
			}
			const execution = await ingestionTaskAPI.getLatestExecution(Number(task.id));
			setLatestExecution(execution);
		} catch (error: any) {
			setLatestExecution(null);
			message.error("获取最新执行记录失败: " + (error.message || "未知错误"));
		} finally {
			if (!silent) {
				setLatestExecutionLoading(false);
			}
		}
	};

	const loadIncrementalStates = async (taskId: number, silent?: boolean) => {
		try {
			if (!silent) {
				setIncrementalStatesLoading(true);
			}
			const states = await ingestionTaskAPI.getIncrementalStates(taskId);
			setIncrementalStates(Array.isArray(states) ? states : []);
			setIncrementalStale(false);
		} catch {
			if (silent) {
				setIncrementalStale(true);
			} else {
				setIncrementalStates([]);
			}
			// error already shown by global interceptor
		} finally {
			if (!silent) {
				setIncrementalStatesLoading(false);
			}
		}
	};

	const loadRealtimeStatus = async (taskId: number, silent?: boolean) => {
		try {
			if (!silent) {
				setRealtimeStatusLoading(true);
			}
			const status = await ingestionTaskAPI.getRealtimeStatus(taskId);
			setRealtimeStatus(status);
			setRealtimeStale(false);
		} catch {
			if (silent) {
				setRealtimeStale(true);
			} else {
				setRealtimeStatus(null);
			}
			// error already shown by global interceptor
		} finally {
			if (!silent) {
				setRealtimeStatusLoading(false);
			}
		}
	};

	const loadBadRowSummary = async (taskId: number, silent?: boolean) => {
		try {
			if (!silent) {
				setBadRowSummaryLoading(true);
			}
			const summary = await ingestionTaskAPI.getStagingErrorSummary(taskId, 10);
			setBadRowSummary(summary);
		} catch {
			setBadRowSummary(null);
			// error already shown by global interceptor
		} finally {
			if (!silent) {
				setBadRowSummaryLoading(false);
			}
		}
	};

	const downloadBadRows = async () => {
		if (!task?.id) return;
		setBadRowDownloading(true);
		try {
			const blob = await ingestionTaskAPI.downloadStagingErrors(Number(task.id));
			const url = URL.createObjectURL(blob);
			const anchor = document.createElement("a");
			anchor.href = url;
			anchor.download = `ingestion-task-${task.id}-bad-rows.csv`;
			document.body.appendChild(anchor);
			anchor.click();
			anchor.remove();
			URL.revokeObjectURL(url);
			message.success("坏行 CSV 已下载");
		} catch (error: any) {
			message.error(error?.message || "坏行 CSV 下载失败");
		} finally {
			setBadRowDownloading(false);
		}
	};

	const openLatestLog = async () => {
		if (!task?.id) return;
		setLogVisible(true);
		setLogLoading(true);
		setLogContent("");
		setLogMeta(null);
		try {
			let execution = latestExecution;
			if (!execution) {
				execution = await ingestionTaskAPI.getLatestExecution(Number(task.id));
				setLatestExecution(execution);
			}
			if (!execution) {
				setLogContent("暂无执行记录");
				return;
			}
			const result = await ingestionTaskAPI.getExecutionLog(Number(task.id), execution.id, { tryNumber: 1 });
			setLogMeta(result);
			const content = String(result?.log || result?.message || "");
			setLogContent(content);
		} catch (error: any) {
			message.error("获取日志失败: " + (error.message || "未知错误"));
		} finally {
			setLogLoading(false);
		}
	};

	const showRealtimeStatusCard = normalizeText(task?.syncMode).toLowerCase() === "cdc";
	const showBadRowsCard = Boolean(normalizeText(task?.stagingTableName));

	const renderPreCheckStatus = (status?: string) => {
		const normalized = normalizeText(status).toUpperCase();
		const statusMap: Record<string, { color: string; text: string }> = {
			PENDING: { color: "default", text: "待预检" },
			CHECKING: { color: "processing", text: "预检中" },
			PASSED: { color: "success", text: "通过" },
			FAILED: { color: "error", text: "未通过" },
		};
		const config = statusMap[normalized] ?? { color: "default", text: status || "-" };
		return <Tag color={config.color}>{config.text}</Tag>;
	};

	const parseModelNames = (selector?: string) => {
		const raw = normalizeText(selector);
		if (!raw) return [];
		return raw
			.split(/\s+/)
			.map((token) => token.trim())
			.filter((token) => token.toLowerCase().startsWith("model:"))
			.map((token) => token.slice(6))
			.filter(Boolean);
	};

	const buildModelSelectorFromNames = (names: string[]) =>
		(names || [])
			.filter(Boolean)
			.map((name) => `model:${name}`)
			.join(" ");

	const loadDbtModels = async () => {
		setDbtModelsLoading(true);
		try {
			const resp: any = await listSqlModels({ size: 200 });
			const list = Array.isArray(resp) ? resp : [];
			setDbtModels(list);
		} catch (error: any) {
			message.error(error?.message || "模型列表加载失败");
		} finally {
			setDbtModelsLoading(false);
		}
	};

	const openDbtModal = () => {
		const selector = normalizeText(task?.dbtModelSelector);
		const dagSelector = normalizeText(task?.dbtDagSelector);
		const selected = parseModelNames(selector);
		setSelectedModelNames(selected);
		dbtForm.setFieldsValue({
			dbtModelSelector: selector || undefined,
			dbtDagSelector: dagSelector || undefined,
		});
		setDbtModalOpen(true);
		if (!dbtModels.length) {
			void loadDbtModels();
		}
	};

	const applyModelSelection = (names: string[]) => {
		setSelectedModelNames(names);
		if (!names.length) return;
		const selector = buildModelSelectorFromNames(names);
		dbtForm.setFieldsValue({ dbtModelSelector: selector });
	};

	const handleSaveDbtBinding = async () => {
		if (!task?.id) return;
		const values = dbtForm.getFieldsValue();
		const modelSelector = normalizeText(values.dbtModelSelector);
		const dagSelector = normalizeText(values.dbtDagSelector);
		setDbtSaving(true);
		try {
			const payload: IngestionTaskDTO = {
				...task,
				dbtModelSelector: modelSelector || undefined,
				dbtDagSelector: dagSelector || undefined,
			};
			await ingestionTaskAPI.updateTask(Number(task.id), payload);
			message.success("DBT 绑定已更新");
			setDbtModalOpen(false);
			loadTask();
		} catch (error: any) {
			message.error(error?.message || "DBT 绑定更新失败");
		} finally {
			setDbtSaving(false);
		}
	};

	const modelOptions = useMemo(
		() =>
			dbtModels.map((model) => ({
				label: model.name || model.alias || model.modelName || "未命名模型",
				value: model.name || model.alias || model.modelName || "",
			})),
		[dbtModels],
	);

	const handleExecute = async () => {
		if (!task?.id) return;
		setExecuteSubmitting(true);
		try {
			const submit = await ingestionTaskAPI.executeTaskAsync(Number(task.id));
			message.success("任务已提交，后台正在触发执行");
			startExecuteProgressPolling(Number(task.id), submit?.pollIntervalMs);
		} catch (error: any) {
			const feedback = resolveAsyncRunSubmitFeedback(error, "execute");
			if (feedback.level === "warning") {
				message.warning(feedback.message);
				return;
			}
			message.error(feedback.message);
		} finally {
			setExecuteSubmitting(false);
		}
	};

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

	const startExecuteProgressPolling = (taskId: number, _pollIntervalMs?: number) => {
		stopExecutePolling();
		const startTime = Date.now();
		executeStartedAtRef.current = startTime;
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
				setLatestExecution(execution);
				const next = mapExecutionProgress(execution, elapsed);
				setExecuteProgress(next);
				if (next.terminal) {
					stopExecutePolling();
					void loadTask();
					void loadLatestExecution(true);
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

	const handleRebuildDag = async () => {
		Modal.confirm({
			title: "强制重建 DAG",
			content: `确定要重建任务 "${task?.name}" 的 DAG 文件吗？`,
			onOk: async () => {
				try {
					await ingestionTaskAPI.rebuildDag(Number(id));
					message.success("DAG 已重建");
					loadTask();
				} catch (error: any) {
					message.error("重建失败: " + (error.message || "未知错误"));
				}
			},
		});
	};

	const openRollback = (level: number) => {
		setRollbackRequest({
			level,
			scope: "task",
			taskId: Number(task?.id),
		});
		setRollbackOpen(true);
	};

	const handleRollbackSuccess = () => {
		loadTask();
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

	if (loading || !task) {
		return (
			<div className="flex justify-center items-center h-96">
				<Spin size="large" />
			</div>
		);
	}

	return (
		<div className="space-y-6" data-testid="platform-transform-detail-page">
			<Card
				title={task.name}
				extra={
					<Space wrap>
						<Button onClick={() => router.push("/explore/etl/transform")}>
							返回
						</Button>
						<Button onClick={() => router.push(`/explore/etl/transform/${id}/executions`)}>
							执行历史
						</Button>
						<Button
							onClick={openLatestLog}
							disabled={!task.lastExecutedAt}
							data-testid="platform-transform-open-log"
						>
							最新日志
						</Button>
						<Button
							onClick={() => router.push(`/explore/etl/transform/${id}/edit`)}
							disabled={task.status === "deleted"}
						>
							编辑
						</Button>
						<Button
							onClick={handleRebuildDag}
							disabled={task.status === "deleted" || task.airflowEnabled === false}
						>
							重建 DAG
						</Button>
						<Dropdown
							menu={{
								items: [
									{ key: "1", label: "Level 1 — 清空数据", onClick: () => openRollback(1) },
									{ key: "2", label: "Level 2 — 重建表结构", onClick: () => openRollback(2), danger: false },
									{ key: "3", label: "Level 3 — 全链路回退", onClick: () => openRollback(3), danger: true },
								],
							}}
							disabled={task.status === "deleted"}
						>
							<Button danger>
								数据回退
							</Button>
						</Dropdown>
						<Button
							type="primary"
							onClick={handleExecute}
							loading={executeSubmitting || (executeProgressOpen && !executeProgress.terminal)}
							disabled={
								task.status === "deleted" ||
								executeSubmitting ||
								(executeProgressOpen && !executeProgress.terminal) ||
								["preparing", "running"].includes((task.lastExecutionStatus || "").toLowerCase())
							}
							data-testid="platform-transform-execute"
						>
							{executeSubmitting
								? "提交中..."
								: (task.lastExecutionStatus || "").toLowerCase() === "preparing"
									? "准备中"
									: executeProgressOpen && !executeProgress.terminal
										? "执行中"
										: "执行任务"}
						</Button>
					</Space>
				}
			/>

			<Tabs
				activeKey={activeTab}
				onChange={setActiveTab}
				items={[
					{
						key: "config",
						label: "任务配置",
						children: (
							<div className="space-y-6">
								<div className="grid gap-6 xl:grid-cols-[1.05fr_0.95fr]">
									<Card title="基本信息">
										<Descriptions column={2} bordered>
											<Descriptions.Item label="任务名称">{task.name}</Descriptions.Item>
											<Descriptions.Item label="状态">{renderStatus(task.status)}</Descriptions.Item>
											<Descriptions.Item label="数据源连接">
												{sourceDetail
													? `${sourceDetail.name} (${sourceDetail.type || "unknown"})`
													: task.sourceDataSourceId || "-"}
											</Descriptions.Item>
											<Descriptions.Item label="Reader 类型">{task.sourceType}</Descriptions.Item>
											<Descriptions.Item label="目标类型">
												{task.destinationType || "postgresqlwriter"}
											</Descriptions.Item>
											<Descriptions.Item label="同步模式">{task.syncMode}</Descriptions.Item>
											<Descriptions.Item label="调度配置">{task.syncSchedule || "手动触发"}</Descriptions.Item>
											<Descriptions.Item label="创建人">{task.createdBy}</Descriptions.Item>
											<Descriptions.Item label="创建时间">
												{task.createdDate ? new Date(task.createdDate).toLocaleString("zh-CN") : "-"}
											</Descriptions.Item>
											<Descriptions.Item label="最后修改人">{task.lastModifiedBy || "-"}</Descriptions.Item>
											<Descriptions.Item label="最后修改时间">
												{task.lastModifiedDate ? new Date(task.lastModifiedDate).toLocaleString("zh-CN") : "-"}
											</Descriptions.Item>
										</Descriptions>
									</Card>

									<Card
										title="执行与编排"
										extra={
											<Button
												onClick={() => loadLatestExecution()}
												loading={latestExecutionLoading}
											>
												刷新执行记录
											</Button>
										}
									>
										<Descriptions column={2} bordered>
											<Descriptions.Item label="Airflow 启用">
												{task.airflowEnabled ? <Tag color="success">已启用</Tag> : <Tag>未启用</Tag>}
											</Descriptions.Item>
											<Descriptions.Item label="编排模板">
												{task.airflowDagId ? "系统自动生成" : "系统默认"}
											</Descriptions.Item>
											<Descriptions.Item label="最后执行时间">
												{task.lastExecutedAt ? new Date(task.lastExecutedAt).toLocaleString("zh-CN") : "从未执行"}
											</Descriptions.Item>
											<Descriptions.Item label="最后执行状态">
												{task.lastExecutionStatus ? (
													<Tag
														color={
															task.lastExecutionStatus === "success"
																? "success"
																: task.lastExecutionStatus === "failed"
																	? "error"
																	: "processing"
														}
													>
														{task.lastExecutionStatus === "preparing" ? "准备中"
															: task.lastExecutionStatus === "running" ? "运行中"
															: task.lastExecutionStatus === "success" ? "成功"
															: task.lastExecutionStatus === "failed" ? "失败"
															: task.lastExecutionStatus}
													</Tag>
												) : (
													"-"
												)}
											</Descriptions.Item>
											<Descriptions.Item label="Addax Job路径" span={2}>
												{task.addaxJobPath || "-"}
											</Descriptions.Item>
										</Descriptions>
										<div className="mt-4 flex items-center gap-3">
											<Button onClick={openLatestLog} disabled={!task.lastExecutedAt}>
												查看最新日志
											</Button>
											{latestExecution ? (
												<Text type="secondary">
													执行ID：{latestExecution.executionId || latestExecution.id} · 状态：{latestExecution.status}
												</Text>
											) : (
												<Text type="secondary">暂无执行记录</Text>
											)}
										</div>
									</Card>
								</div>

								{showBadRowsCard ? (
									<Card
										title="文件预检"
										extra={
											<Space wrap>
												<Button
													onClick={() => task?.id && loadBadRowSummary(Number(task.id))}
													loading={badRowSummaryLoading}
												>
													刷新摘要
												</Button>
												<Button
													onClick={downloadBadRows}
													loading={badRowDownloading}
													disabled={!badRowSummary || !badRowSummary.errorRows}
												>
													下载坏行
												</Button>
											</Space>
										}
									>
										{badRowSummaryLoading && !badRowSummary ? (
											<div className="flex h-24 items-center justify-center">
												<Spin />
											</div>
										) : (
											<>
												<Descriptions column={4} bordered>
													<Descriptions.Item label="预检开关">
														{task.qualityPreCheckEnabled ? <Tag color="success">已启用</Tag> : <Tag>未启用</Tag>}
													</Descriptions.Item>
													<Descriptions.Item label="预检状态">
														{renderPreCheckStatus(task.preCheckStatus)}
													</Descriptions.Item>
													<Descriptions.Item label="总行数">{badRowSummary?.totalRows ?? "-"}</Descriptions.Item>
													<Descriptions.Item label="坏行数">
														{!badRowSummary ? (
															"-"
														) : badRowSummary.errorRows ? (
															<Tag color="error">{badRowSummary.errorRows}</Tag>
														) : (
															<Tag color="success">0</Tag>
														)}
													</Descriptions.Item>
													<Descriptions.Item label="暂存表" span={4}>
														<Text code>{task.stagingTableName}</Text>
													</Descriptions.Item>
												</Descriptions>
												{!badRowSummary ? (
													<Alert className="mt-4" type="warning" showIcon message="暂无预检摘要" />
												) : badRowSummary.errorRows ? (
													<>
														<CompactTable<StagingRuleErrorSummary>
															className="mt-4"
															rowKey={(record) => record.ruleName}
															size="small"
															pagination={false}
															dataSource={badRowSummary.errorsByRule || []}
															columns={[
																{
																	title: "规则",
																	dataIndex: "ruleName",
																	sorter: (a, b) => (a.ruleName || "").localeCompare(b.ruleName || ""),
																	key: "ruleName",
																	render: (value?: string) => value || "-",
																},
																{
																	title: "坏行数",
																	dataIndex: "failCount",
																	key: "failCount",
																	width: 140,
																	render: (value?: number) => <Tag color="error">{value ?? 0}</Tag>,
																},
															]}
														/>
														{badRowSummary.sampleRows?.length ? (
															<pre className="mt-4 max-h-64 overflow-auto rounded-[16px] bg-muted/35 p-4 text-xs leading-6">
																{JSON.stringify(badRowSummary.sampleRows.slice(0, 5), null, 2)}
															</pre>
														) : null}
													</>
												) : (
													<Alert className="mt-4" type="success" showIcon message="暂无坏行" />
												)}
											</>
										)}
									</Card>
								) : null}

								<div className="grid gap-6 xl:grid-cols-2">
									<Card title="源端覆盖参数">
										<pre className="overflow-auto rounded-[24px] bg-muted/35 p-4 text-xs leading-6">
											{JSON.stringify(task.sourceConfig || {}, null, 2)}
										</pre>
									</Card>

									{task.destinationConfig ? (
										<Card title="目标配置">
											<pre className="overflow-auto rounded-[24px] bg-muted/35 p-4 text-xs leading-6">
												{JSON.stringify(task.destinationConfig, null, 2)}
											</pre>
										</Card>
									) : null}
								</div>

								{task.tableMapping && task.tableMapping.length > 0 ? (
									<Card title="表映射配置">
										<pre className="overflow-auto rounded-[24px] bg-muted/35 p-4 text-xs leading-6">
											{JSON.stringify(task.tableMapping, null, 2)}
										</pre>
									</Card>
								) : null}

								<Card
									title="DBT 绑定"
									extra={
										<Button type="link" onClick={openDbtModal}>
											绑定模型 / DAG 族
										</Button>
									}
								>
									<Descriptions column={2} bordered>
										<Descriptions.Item label="模型选择器">
											{task.dbtModelSelector ? <Text code>{task.dbtModelSelector}</Text> : "未绑定"}
										</Descriptions.Item>
										<Descriptions.Item label="DAG 族选择器">
											{task.dbtDagSelector ? <Text code>{task.dbtDagSelector}</Text> : "默认 DAG"}
										</Descriptions.Item>
									</Descriptions>
									<div className="mt-2 text-xs text-muted-foreground">
										可直接输入 selector（如：model:xxx、tag:xxx），或从模型列表快速生成。
									</div>
								</Card>

								{showRealtimeStatusCard ? (
									<Card
										title="实时链路状态"
										extra={
											<Button
												onClick={() => task?.id && loadRealtimeStatus(Number(task.id))}
												loading={realtimeStatusLoading}
											>
												刷新实时状态
											</Button>
										}
									>
										{realtimeStale && (
											<Alert type="warning" banner message="数据可能已过期，请点击刷新按钮重新加载" className="mb-2" />
										)}
										<Descriptions column={2} bordered>
											<Descriptions.Item label="连接器">{realtimeStatus?.connectorType || "-"}</Descriptions.Item>
											<Descriptions.Item label="链路状态">
												{realtimeStatus?.status ? (
													<Tag
														color={
															realtimeStatus.status === "RUNNING"
																? "processing"
																: realtimeStatus.status === "ERROR"
																	? "error"
																	: "default"
														}
													>
														{realtimeStatus.status}
													</Tag>
												) : (
													"-"
												)}
											</Descriptions.Item>
											<Descriptions.Item label="主题">{realtimeStatus?.topicName || "-"}</Descriptions.Item>
											<Descriptions.Item label="消费者组">
												{realtimeStatus?.consumerGroup || "-"}
											</Descriptions.Item>
											<Descriptions.Item label="检查点">{realtimeStatus?.checkpointToken || "-"}</Descriptions.Item>
											<Descriptions.Item label="最新心跳">
												{realtimeStatus?.lastHeartbeat
													? new Date(realtimeStatus.lastHeartbeat).toLocaleString("zh-CN")
													: "-"}
											</Descriptions.Item>
											<Descriptions.Item label="延迟(ms)">{realtimeStatus?.lagMs ?? "-"}</Descriptions.Item>
											<Descriptions.Item label="吞吐(rps)">{realtimeStatus?.throughputRps ?? "-"}</Descriptions.Item>
											<Descriptions.Item label="堆积量" span={2}>
												{realtimeStatus?.backlogCount ?? "-"}
											</Descriptions.Item>
										</Descriptions>
										<div className="mt-2 text-xs text-muted-foreground">
											当前展示范围仅限 `cdc` 任务，用于现场排查链路堆积、心跳缺失和消费延迟。
										</div>
									</Card>
								) : null}

								{normalizeText(task.syncMode).toLowerCase() === "incremental" ? (
									<Card
										title="增量检查点"
										extra={
											<Button
												onClick={() => task?.id && loadIncrementalStates(Number(task.id))}
												loading={incrementalStatesLoading}
											>
												刷新检查点
											</Button>
										}
									>
										{incrementalStale && (
											<Alert type="warning" banner message="数据可能已过期，请点击刷新按钮重新加载" className="mb-2" />
										)}
										<CompactTable<IngestionIncrementalStateDTO>
											rowKey={(record) => `${record.taskId}-${record.sourceTable}`}
											size="small"
											loading={incrementalStatesLoading}
											pagination={false}
											dataSource={incrementalStates}
											locale={{ emptyText: "暂无检查点（首次成功执行后会写入）" }}
											columns={[
												{
													title: "源表",
													dataIndex: "sourceTable",
													key: "sourceTable",
													render: (value: string) => <Text code>{value || "-"}</Text>,
												},
												{
													title: "最新水位",
													dataIndex: "lastSuccessWatermark",
													key: "lastSuccessWatermark",
													render: (value?: string) => value || "-",
												},
												{
													title: "最近运行ID",
													dataIndex: "lastRunId",
													key: "lastRunId",
													render: (value?: string) => value || "-",
												},
												{
													title: "更新时间",
													dataIndex: "updatedAt",
													sorter: (a, b) => {
														const ta = a.updatedAt ? new Date(a.updatedAt as any).getTime() : 0;
														const tb = b.updatedAt ? new Date(b.updatedAt as any).getTime() : 0;
														return ta - tb;
													},
													key: "updatedAt",
													render: (value?: string) => (value ? new Date(value).toLocaleString("zh-CN") : "-"),
												},
											]}
										/>
									</Card>
								) : null}
							</div>
						),
					},
					{
						key: "history",
						label: "执行历史",
						children: <ExecutionHistoryTable taskId={Number(id)} />,
					},
				]}
			/>

			<Modal
				open={dbtModalOpen}
				title="绑定 DBT 模型与 DAG 族"
				onCancel={() => setDbtModalOpen(false)}
				onOk={handleSaveDbtBinding}
				okButtonProps={{ loading: dbtSaving }}
			>
				<Form layout="vertical" form={dbtForm}>
					<Form.Item label="模型选择" tooltip="选中后可自动生成模型选择器（model:xxx）">
						<Select
							mode="multiple"
							placeholder="从模型库选择"
							value={selectedModelNames}
							onChange={applyModelSelection}
							options={modelOptions}
							loading={dbtModelsLoading}
							allowClear
						/>
					</Form.Item>
					<Form.Item label="模型选择器" name="dbtModelSelector">
						<Input.TextArea rows={2} placeholder="例如：model:order_detail model:user_profile 或 tag:crm" />
					</Form.Item>
					<Form.Item label="DAG 族选择器" name="dbtDagSelector">
						<Input placeholder="例如：tab:crm 或 tag:crm" />
					</Form.Item>
					<div className="text-xs text-muted-foreground">
						不填写 DAG 族选择器将使用默认 DAG。模型选择器为空则不会触发 dbt。
					</div>
				</Form>
			</Modal>

			<Modal
				title="执行进度"
				open={executeProgressOpen}
				maskClosable={false}
				onCancel={() => {
					stopExecutePolling();
					setExecuteProgressOpen(false);
				}}
				footer={
					<Space wrap>
						<Button
							onClick={() => {
								stopExecutePolling();
								setExecuteProgressOpen(false);
								router.push(`/explore/etl/transform/${id}/executions`);
							}}
						>
							查看执行历史
						</Button>
						{executeProgress.terminal && executeProgress.status === "success" && (
							<Button
								onClick={() => {
									stopExecutePolling();
									setExecuteProgressOpen(false);
									router.push("/catalog/lineage/import");
								}}
							>
								同步 Addax 血缘
							</Button>
						)}
						<Button
							type="primary"
							onClick={() => {
								stopExecutePolling();
								setExecuteProgressOpen(false);
							}}
						>
							{executeProgress.terminal ? "关闭" : "最小化"}
						</Button>
					</Space>
				}
				width={620}
			>
				<Space direction="vertical" size={16} className="w-full" data-testid="platform-transform-detail-progress">
					<Text>任务：{task?.name || "-"}</Text>
					<Progress percent={executeProgress.percent} status={executeProgress.status} />
					<Alert
						showIcon
						type={
							executeProgress.status === "success"
								? "success"
								: executeProgress.status === "exception"
									? "error"
									: "info"
						}
						message={executeProgress.stage}
						description={executeProgress.detail}
					/>
					{latestExecution ? (
						<Text type="secondary">
							执行ID：{latestExecution.executionId || latestExecution.id}，状态：{latestExecution.status}
						</Text>
					) : null}
				</Space>
			</Modal>

			<RollbackImpactModal
				open={rollbackOpen}
				request={rollbackRequest}
				onClose={() => setRollbackOpen(false)}
				onSuccess={handleRollbackSuccess}
			/>

			<Drawer
				title="执行日志"
				placement="right"
				width={720}
				open={logVisible}
				onClose={() => setLogVisible(false)}
				extra={
					<Space>
						<Button onClick={() => openLatestLog()} loading={logLoading}>
							刷新日志
						</Button>
					</Space>
				}
			>
				<div data-testid="platform-transform-log-drawer">
					<div className="mb-3">
						{logMeta?.dagId ? (
							<Text type="secondary">
								DAG: {logMeta.dagId}
								{logMeta?.dagRunId ? ` · Run: ${logMeta.dagRunId}` : ""}
							</Text>
						) : null}
					</div>
					<pre className="whitespace-pre-wrap break-words text-xs bg-muted p-3 rounded border border-border">
						{logLoading ? "日志加载中..." : logContent || "暂无日志"}
					</pre>
				</div>
			</Drawer>
		</div>
	);
}
