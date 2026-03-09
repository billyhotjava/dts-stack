import { useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "@/routes/hooks";
import { Button, Card, Descriptions, Space, Tag, message, Spin, Modal, Form, Input, Select, Typography, Drawer, Progress, Alert, Table } from "antd";
import { PlayCircleOutlined, EditOutlined, HistoryOutlined, ArrowLeftOutlined, SyncOutlined, FileTextOutlined, ReloadOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import { useRouter } from "@/routes/hooks";
import {
	ingestionTaskAPI,
	type IngestionTaskDTO,
	type IngestionExecutionDTO,
	type IngestionExecutionLog,
	type IngestionIncrementalStateDTO,
	type IngestionRealtimeStatusDTO,
	resolveExecutionPollIntervalMs,
} from "@/api/ingestion";
import dataSourcesService, { type InfraDataSource } from "@/api/services/dataSourcesService";
import { listSqlModels } from "@/api/platformApi";

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
	const [executeProgressOpen, setExecuteProgressOpen] = useState(false);
	const [executeProgress, setExecuteProgress] = useState<ExecutionProgressView>({
		percent: 0,
		status: "active",
		stage: "等待提交",
		detail: "",
		terminal: false,
	});
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
		return () => {
			if (executePollTimerRef.current !== null) {
				window.clearInterval(executePollTimerRef.current);
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
		} catch (error: any) {
			setIncrementalStates([]);
			message.error("获取增量检查点失败: " + (error.message || "未知错误"));
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
		} catch (error: any) {
			setRealtimeStatus(null);
			if (!silent) {
				message.error("获取实时状态失败: " + (error.message || "未知错误"));
			}
		} finally {
			if (!silent) {
				setRealtimeStatusLoading(false);
			}
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

	const normalizeText = (value?: string) => String(value || "").trim();
	const showRealtimeStatusCard = normalizeText(task?.syncMode).toLowerCase() === "cdc";

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
		(names || []).filter(Boolean).map((name) => `model:${name}`).join(" ");

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
			message.error("执行失败: " + (error.message || "未知错误"));
		} finally {
			setExecuteSubmitting(false);
		}
	};

	const stopExecutePolling = () => {
		if (executePollTimerRef.current !== null) {
			window.clearInterval(executePollTimerRef.current);
			executePollTimerRef.current = null;
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
				detail: normalizeText(execution.errorMessage) || "执行失败，请查看任务日志。",
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

	const startExecuteProgressPolling = (taskId: number, pollIntervalMs?: number) => {
		stopExecutePolling();
		executeStartedAtRef.current = Date.now();
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
					detail: "等待超时，请进入执行历史继续查看状态。",
					terminal: true,
				});
				return;
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

	if (loading || !task) {
		return (
			<div className="flex justify-center items-center h-96">
				<Spin size="large" />
			</div>
		);
	}

	return (
		<div className="flex flex-col gap-6">
			<PageHeader
				title={task.name}
				description={task.description || "入湖任务详情"}
				actions={
					<Space>
						<Button icon={<ArrowLeftOutlined />} onClick={() => router.push("/explore/etl/transform")}>
							返回
						</Button>
						<Button icon={<HistoryOutlined />} onClick={() => router.push(`/explore/etl/transform/${id}/executions`)}>
							执行历史
						</Button>
						<Button icon={<FileTextOutlined />} onClick={openLatestLog} disabled={!task.lastExecutedAt}>
							最新日志
						</Button>
						<Button
							icon={<EditOutlined />}
							onClick={() => router.push(`/explore/etl/transform/${id}/edit`)}
							disabled={task.status === "deleted"}
						>
							编辑
						</Button>
						<Button
							icon={<SyncOutlined />}
							onClick={handleRebuildDag}
							disabled={task.status === "deleted" || task.airflowEnabled === false}
						>
							重建 DAG
						</Button>
						<Button
							type="primary"
							icon={<PlayCircleOutlined />}
							onClick={handleExecute}
							loading={executeSubmitting}
							disabled={task.status === "deleted"}
						>
							执行任务
						</Button>
					</Space>
				}
			/>

			<Card title="基本信息">
				<Descriptions column={2} bordered>
					<Descriptions.Item label="任务名称">{task.name}</Descriptions.Item>
					<Descriptions.Item label="状态">{renderStatus(task.status)}</Descriptions.Item>
					<Descriptions.Item label="数据源连接">
						{sourceDetail ? `${sourceDetail.name} (${sourceDetail.type || "unknown"})` : task.sourceDataSourceId || "-"}
					</Descriptions.Item>
					<Descriptions.Item label="Reader 类型">{task.sourceType}</Descriptions.Item>
					<Descriptions.Item label="目标类型">{task.destinationType || "postgresqlwriter"}</Descriptions.Item>
					<Descriptions.Item label="同步模式">{task.syncMode}</Descriptions.Item>
					<Descriptions.Item label="调度配置">{task.syncSchedule || "手动触发"}</Descriptions.Item>
					<Descriptions.Item label="创建人">{task.createdBy}</Descriptions.Item>
					<Descriptions.Item label="创建时间">{task.createdDate ? new Date(task.createdDate).toLocaleString("zh-CN") : "-"}</Descriptions.Item>
					<Descriptions.Item label="最后修改人">{task.lastModifiedBy || "-"}</Descriptions.Item>
					<Descriptions.Item label="最后修改时间">
						{task.lastModifiedDate ? new Date(task.lastModifiedDate).toLocaleString("zh-CN") : "-"}
					</Descriptions.Item>
				</Descriptions>
			</Card>

			<Card title="源端覆盖参数">
				<pre className="bg-muted p-4 rounded overflow-auto">{JSON.stringify(task.sourceConfig || {}, null, 2)}</pre>
			</Card>

			{task.destinationConfig && (
				<Card title="目标配置">
					<pre className="bg-muted p-4 rounded overflow-auto">{JSON.stringify(task.destinationConfig, null, 2)}</pre>
				</Card>
			)}

			{task.tableMapping && task.tableMapping.length > 0 && (
				<Card title="表映射配置">
					<pre className="bg-muted p-4 rounded overflow-auto">{JSON.stringify(task.tableMapping, null, 2)}</pre>
				</Card>
			)}

			<Card title="Airflow集成">
				<Descriptions column={2} bordered>
					<Descriptions.Item label="启用状态">{task.airflowEnabled ? <Tag color="success">已启用</Tag> : <Tag>未启用</Tag>}</Descriptions.Item>
					<Descriptions.Item label="编排模板">{task.airflowDagId ? "系统自动生成" : "系统默认"}</Descriptions.Item>
				</Descriptions>
			</Card>

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

			<Card title="执行信息">
				<Descriptions column={2} bordered>
					<Descriptions.Item label="最后执行时间">
						{task.lastExecutedAt ? new Date(task.lastExecutedAt).toLocaleString("zh-CN") : "从未执行"}
					</Descriptions.Item>
					<Descriptions.Item label="最后执行状态">
						{task.lastExecutionStatus ? (
							<Tag color={task.lastExecutionStatus === "success" ? "success" : task.lastExecutionStatus === "failed" ? "error" : "processing"}>
								{task.lastExecutionStatus}
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
					<Button icon={<FileTextOutlined />} onClick={openLatestLog} disabled={!task.lastExecutedAt}>
						查看最新日志
					</Button>
					<Button icon={<ReloadOutlined />} onClick={() => loadLatestExecution()} loading={latestExecutionLoading}>
						刷新执行记录
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

			{showRealtimeStatusCard ? (
				<Card
					title="实时状态（预留）"
					extra={
						<Button
							icon={<ReloadOutlined />}
							onClick={() => task?.id && loadRealtimeStatus(Number(task.id))}
							loading={realtimeStatusLoading}
						>
							刷新实时状态
						</Button>
					}
				>
					<Descriptions column={2} bordered>
						<Descriptions.Item label="连接器">{realtimeStatus?.connectorType || "-"}</Descriptions.Item>
						<Descriptions.Item label="链路状态">
							{realtimeStatus?.status ? <Tag color={realtimeStatus.status === "RUNNING" ? "processing" : realtimeStatus.status === "ERROR" ? "error" : "default"}>{realtimeStatus.status}</Tag> : "-"}
						</Descriptions.Item>
						<Descriptions.Item label="Topic">{realtimeStatus?.topicName || "-"}</Descriptions.Item>
						<Descriptions.Item label="Consumer Group">{realtimeStatus?.consumerGroup || "-"}</Descriptions.Item>
						<Descriptions.Item label="Checkpoint">{realtimeStatus?.checkpointToken || "-"}</Descriptions.Item>
						<Descriptions.Item label="最新心跳">{realtimeStatus?.lastHeartbeat ? new Date(realtimeStatus.lastHeartbeat).toLocaleString("zh-CN") : "-"}</Descriptions.Item>
						<Descriptions.Item label="延迟(ms)">{realtimeStatus?.lagMs ?? "-"}</Descriptions.Item>
						<Descriptions.Item label="吞吐(rps)">{realtimeStatus?.throughputRps ?? "-"}</Descriptions.Item>
						<Descriptions.Item label="堆积量" span={2}>{realtimeStatus?.backlogCount ?? "-"}</Descriptions.Item>
					</Descriptions>
					<div className="mt-2 text-xs text-muted-foreground">
						该区域用于后续实时链路（Kafka/CDC）监控；当前展示范围仅限 `cdc` 任务。
					</div>
				</Card>
			) : null}

			{normalizeText(task.syncMode).toLowerCase() === "incremental" ? (
				<Card
					title="增量检查点"
					extra={
						<Button
							icon={<ReloadOutlined />}
							onClick={() => task?.id && loadIncrementalStates(Number(task.id))}
							loading={incrementalStatesLoading}
						>
							刷新检查点
						</Button>
					}
				>
					<Table<IngestionIncrementalStateDTO>
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
								key: "updatedAt",
								render: (value?: string) => (value ? new Date(value).toLocaleString("zh-CN") : "-"),
							},
						]}
					/>
				</Card>
			) : null}

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
						<Input.TextArea
							rows={2}
							placeholder="例如：model:order_detail model:user_profile 或 tag:crm"
						/>
					</Form.Item>
					<Form.Item label="DAG 族选择器" name="dbtDagSelector">
						<Input
							placeholder="例如：tab:crm 或 tag:crm"
						/>
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
					<Space>
						<Button
							onClick={() => {
								stopExecutePolling();
								setExecuteProgressOpen(false);
								router.push(`/explore/etl/transform/${id}/executions`);
							}}
						>
							查看执行历史
						</Button>
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
				<Space direction="vertical" size={16} className="w-full">
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

			<Drawer
				title="执行日志"
				placement="right"
				width={720}
				open={logVisible}
				onClose={() => setLogVisible(false)}
				extra={
					<Space>
						<Button icon={<ReloadOutlined />} onClick={() => openLatestLog()} loading={logLoading}>
							刷新日志
						</Button>
					</Space>
				}
			>
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
			</Drawer>
		</div>
	);
}
