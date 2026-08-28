import {
	Alert,
	Button,
	Card,
	Descriptions,
	Drawer,
	Empty,
	Modal,
	message,
	Select,
	Space,
	Statistic,
	Tag,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router";
import {
	type AsyncExecutionSubmitResult,
	type IngestionExecutionDTO,
	type IngestionExecutionLog,
	type IngestionQualityEvidence,
	ingestionTaskAPI,
	resolveExecutionPollIntervalMs,
} from "@/api/ingestion";
import { actionColumn, CompactTable } from "@/components/table";
import { formatDateTime } from "@/utils/textUtils";

const { Paragraph, Text } = Typography;

type Props = {
	taskId: number | null;
	executionId?: number | null;
	onExecutionSelect?: (executionId: number | null) => void;
};

const ACTIVE_STATUSES = new Set(["PREPARING", "PENDING", "QUEUED", "RUNNING", "RETRY_WAIT"]);
const RETRYABLE_STATUSES = new Set(["FAILED", "EXHAUSTED", "CANCELLED"]);

const STATUS_META: Record<string, { label: string; color: string }> = {
	PREPARING: { label: "准备中", color: "processing" },
	PENDING: { label: "待运行", color: "processing" },
	QUEUED: { label: "排队中", color: "processing" },
	RUNNING: { label: "运行中", color: "blue" },
	SUCCESS: { label: "成功", color: "green" },
	SUCCEEDED: { label: "成功", color: "green" },
	FAILED: { label: "失败", color: "red" },
	EXHAUSTED: { label: "重试耗尽", color: "red" },
	CANCELLED: { label: "已取消", color: "default" },
	RETRY_WAIT: { label: "等待重试", color: "orange" },
};

const EVIDENCE_LABELS: Record<string, string> = {
	CURRENT: "当前证据",
	STALE: "证据已过期",
	PENDING: "验证中",
	MISSING: "证据缺失",
	TRIGGER_FAILED: "验证触发失败",
	NOT_CONFIGURED: "未配置质量验证",
	TARGET_ASSET_UNRESOLVED: "目标资产未解析",
};

function normalized(value?: string): string {
	return (value || "").trim().toUpperCase();
}

function commandKey(kind: string, taskId: number, executionId?: number): string {
	return `${kind}:${taskId}:${executionId || "new"}:${Date.now()}:${Math.random().toString(36).slice(2)}`;
}

function errorMessage(error: unknown, fallback: string): string {
	const candidate = error as {
		message?: string;
		response?: { data?: { message?: string; detail?: string; title?: string } };
	};
	return (
		candidate?.response?.data?.detail ||
		candidate?.response?.data?.message ||
		candidate?.response?.data?.title ||
		candidate?.message ||
		fallback
	);
}

function EvidenceTags({ evidence }: { evidence?: IngestionQualityEvidence }) {
	if (!evidence) return <Tag>尚无质量证据</Tag>;
	const current = normalized(evidence.evidenceState) === "CURRENT";
	const passed = normalized(evidence.qualityStatus) === "PASSED";
	const eligible = normalized(evidence.consumptionEligibility) === "ELIGIBLE";
	return (
		<Space size={[4, 4]} wrap>
			<Tag color={current ? "green" : normalized(evidence.evidenceState) === "STALE" ? "orange" : "default"}>
				{EVIDENCE_LABELS[normalized(evidence.evidenceState)] || evidence.evidenceState}
			</Tag>
			<Tag color={passed ? "green" : normalized(evidence.qualityStatus) === "FAILED" ? "red" : "blue"}>
				质量：{evidence.qualityStatus || "UNKNOWN"}
			</Tag>
			<Tag color={eligible ? "green" : "gold"}>可消费性：{evidence.consumptionEligibility || "CONDITIONAL"}</Tag>
			<Tag color={evidence.trustedUsable ? "green" : "default"}>
				{evidence.trustedUsable ? "可信可用" : "未形成可信结论"}
			</Tag>
		</Space>
	);
}

export default function OrchestrationRunsTab({ taskId, executionId, onExecutionSelect }: Props) {
	const [rows, setRows] = useState<IngestionExecutionDTO[]>([]);
	const [total, setTotal] = useState(0);
	const [page, setPage] = useState(1);
	const [pageSize, setPageSize] = useState(10);
	const [statusFilter, setStatusFilter] = useState<string>("ALL");
	const [loading, setLoading] = useState(false);
	const [commandLoading, setCommandLoading] = useState<string | null>(null);
	const [detail, setDetail] = useState<IngestionExecutionDTO | null>(null);
	const [detailLoading, setDetailLoading] = useState(false);
	const [logPayload, setLogPayload] = useState<IngestionExecutionLog | null>(null);
	const [logOpen, setLogOpen] = useState(false);
	const [logLoading, setLogLoading] = useState(false);
	const submitKeyRef = useRef<string | null>(null);
	const retryKeysRef = useRef<Record<number, string>>({});

	const loadRuns = useCallback(
		async (silent = false) => {
			if (!taskId) {
				setRows([]);
				setTotal(0);
				return;
			}
			if (!silent) setLoading(true);
			try {
				const result = await ingestionTaskAPI.getExecutions(taskId, {
					page: page - 1,
					size: pageSize,
					sort: "createdAt,desc",
					status: statusFilter === "ALL" ? undefined : statusFilter,
				});
				setRows(Array.isArray(result.content) ? result.content : []);
				setTotal(result.totalElements || 0);
			} catch (error: unknown) {
				message.error(errorMessage(error, "运行实例加载失败"));
			} finally {
				if (!silent) setLoading(false);
			}
		},
		[page, pageSize, statusFilter, taskId],
	);

	const loadDetail = useCallback(
		async (selectedId: number, silent = false) => {
			if (!taskId) return;
			if (!silent) setDetailLoading(true);
			try {
				setDetail(await ingestionTaskAPI.getExecution(taskId, selectedId));
			} catch (error: unknown) {
				if (!silent) message.error(errorMessage(error, "运行详情加载失败"));
			} finally {
				if (!silent) setDetailLoading(false);
			}
		},
		[taskId],
	);

	useEffect(() => {
		void loadRuns();
	}, [loadRuns]);

	useEffect(() => {
		if (taskId && executionId) void loadDetail(executionId);
		else setDetail(null);
	}, [executionId, loadDetail, taskId]);

	const hasActiveExecution = useMemo(
		() =>
			rows.some((row) => ACTIVE_STATUSES.has(normalized(row.status))) ||
			Boolean(detail && ACTIVE_STATUSES.has(normalized(detail.status))),
		[detail, rows],
	);

	useEffect(() => {
		if (!taskId || !hasActiveExecution) return;
		const timer = window.setInterval(() => {
			void loadRuns(true);
			if (executionId) void loadDetail(executionId, true);
		}, resolveExecutionPollIntervalMs());
		return () => window.clearInterval(timer);
	}, [executionId, hasActiveExecution, loadDetail, loadRuns, taskId]);

	const handleRun = async () => {
		if (!taskId || commandLoading) return;
		const key = submitKeyRef.current || commandKey("execute", taskId);
		submitKeyRef.current = key;
		setCommandLoading("execute");
		try {
			const result: AsyncExecutionSubmitResult = await ingestionTaskAPI.executeTaskAsync(taskId, key);
			message.success(result.idempotent ? "已返回同一运行请求" : "运行请求已提交");
			await loadRuns();
			const nextId = result.executionId || result.retryExecutionId;
			if (nextId) onExecutionSelect?.(nextId);
			submitKeyRef.current = null;
		} catch (error: unknown) {
			message.error(errorMessage(error, "运行请求提交失败"));
		} finally {
			setCommandLoading(null);
		}
	};

	const handleRetry = async (record: IngestionExecutionDTO) => {
		if (!taskId || commandLoading) return;
		const key = retryKeysRef.current[record.id] || commandKey("retry", taskId, record.id);
		retryKeysRef.current[record.id] = key;
		setCommandLoading(`retry-${record.id}`);
		try {
			const result = await ingestionTaskAPI.retryExecutionAsync(taskId, record.id, { mode: "FAILED_ONLY" }, key);
			message.success(result?.idempotent ? "已返回同一重试请求" : "重试请求已提交");
			await loadRuns();
			const nextId = Number(result?.retryExecutionId || result?.executionId || 0);
			if (nextId > 0) onExecutionSelect?.(nextId);
			delete retryKeysRef.current[record.id];
		} catch (error: unknown) {
			message.error(errorMessage(error, "重试请求提交失败"));
		} finally {
			setCommandLoading(null);
		}
	};

	const handleCancel = async (record: IngestionExecutionDTO) => {
		if (!taskId || commandLoading) return;
		setCommandLoading(`cancel-${record.id}`);
		try {
			await ingestionTaskAPI.cancelExecution(taskId, record.id);
			message.success("取消请求已提交");
			await Promise.all([loadRuns(), loadDetail(record.id, true)]);
		} catch (error: unknown) {
			message.error(errorMessage(error, "取消运行失败"));
		} finally {
			setCommandLoading(null);
		}
	};

	const handleLogs = async (record: IngestionExecutionDTO) => {
		if (!taskId) return;
		setLogOpen(true);
		setLogLoading(true);
		setLogPayload(null);
		try {
			setLogPayload(await ingestionTaskAPI.getExecutionLog(taskId, record.id, { scope: "all" }));
		} catch (error: unknown) {
			message.error(errorMessage(error, "运行日志加载失败"));
		} finally {
			setLogLoading(false);
		}
	};

	const openDetail = (record: IngestionExecutionDTO) => {
		onExecutionSelect?.(record.id);
		void loadDetail(record.id);
	};

	const summary = useMemo(
		() => ({
			running: rows.filter((row) => ACTIVE_STATUSES.has(normalized(row.status))).length,
			success: rows.filter((row) => ["SUCCESS", "SUCCEEDED"].includes(normalized(row.status))).length,
			failed: rows.filter((row) => ["FAILED", "EXHAUSTED"].includes(normalized(row.status))).length,
			trusted: rows.filter((row) => row.qualityEvidence?.trustedUsable).length,
		}),
		[rows],
	);

	const columns: ColumnsType<IngestionExecutionDTO> = [
		{
			title: "实例",
			dataIndex: "id",
			width: 105,
			render: (value: number, record) => (
				<Button type="link" size="small" onClick={() => openDetail(record)}>
					#{value}
				</Button>
			),
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 120,
			render: (value: string) => {
				const meta = STATUS_META[normalized(value)];
				return <Tag color={meta?.color || "default"}>{meta?.label || value || "未知"}</Tag>;
			},
		},
		{
			title: "任务版本",
			dataIndex: "revisionNumber",
			width: 100,
			render: (value?: number) => (value ? `R${value}` : "-"),
		},
		{
			title: "触发方式",
			dataIndex: "triggerMode",
			width: 120,
			render: (value?: string) => value || "MANUAL",
		},
		{
			title: "开始时间",
			dataIndex: "startTime",
			width: 180,
			render: (value?: string) => formatDateTime(value),
		},
		{
			title: "读/写行数",
			key: "rows",
			width: 130,
			render: (_, record) => `${record.rowsRead ?? "-"} / ${record.rowsWritten ?? "-"}`,
		},
		{
			title: "资产、质量与可信状态",
			key: "qualityEvidence",
			width: 430,
			render: (_, record) => <EvidenceTags evidence={record.qualityEvidence} />,
		},
		actionColumn<IngestionExecutionDTO>(
			(record) => [
				{ key: "detail", label: "详情", onClick: () => openDetail(record) },
				{ key: "logs", label: "日志", onClick: () => void handleLogs(record) },
				{
					key: "retry",
					label: "重试",
					hidden: !RETRYABLE_STATUSES.has(normalized(record.status)),
					loading: commandLoading === `retry-${record.id}`,
					confirm: "按失败范围创建新的重试实例？原实例及其证据会保留。",
					onClick: () => void handleRetry(record),
				},
				{
					key: "cancel",
					label: "取消",
					danger: true,
					hidden: !ACTIVE_STATUSES.has(normalized(record.status)),
					loading: commandLoading === `cancel-${record.id}`,
					confirm: "确认取消该运行实例？",
					onClick: () => void handleCancel(record),
				},
			],
			{ maxActions: 4, width: 310, fixed: false },
		),
	];

	if (!taskId) {
		return <Empty description="请先选择接入任务，再查看运行实例" />;
	}

	const evidence = detail?.qualityEvidence;
	return (
		<Space direction="vertical" size={16} style={{ width: "100%" }}>
			<Card size="small">
				<Space size={28} wrap>
					<Statistic title="当前页运行中" value={summary.running} />
					<Statistic title="当前页成功" value={summary.success} />
					<Statistic title="当前页失败" value={summary.failed} />
					<Statistic title="当前页可信可用" value={summary.trusted} />
				</Space>
			</Card>

			<Card
				size="small"
				title="任务运行实例"
				extra={
					<Space wrap>
						<Select
							value={statusFilter}
							style={{ width: 150 }}
							onChange={(value) => {
								setStatusFilter(value);
								setPage(1);
							}}
							options={[
								{ value: "ALL", label: "全部状态" },
								{ value: "RUNNING", label: "运行中" },
								{ value: "SUCCESS", label: "成功" },
								{ value: "FAILED", label: "失败" },
								{ value: "CANCELLED", label: "已取消" },
							]}
						/>
						<Button onClick={() => void loadRuns()}>刷新</Button>
						<Button type="primary" loading={commandLoading === "execute"} onClick={() => void handleRun()}>
							立即运行
						</Button>
					</Space>
				}
			>
				<Alert
					style={{ marginBottom: 12 }}
					type="info"
					showIcon
					message="运行实例严格绑定任务版本；重复提交使用同一幂等命令，不会创建重复账本。"
				/>
				<CompactTable<IngestionExecutionDTO>
					rowKey="id"
					columns={columns}
					dataSource={rows}
					loading={loading}
					autoSort={false}
					scroll={{ x: 1395 }}
					pagination={{
						current: page,
						pageSize,
						total,
						showSizeChanger: true,
						onChange: (nextPage, nextSize) => {
							setPage(nextSize !== pageSize ? 1 : nextPage);
							setPageSize(nextSize);
						},
					}}
				/>
			</Card>

			<Drawer
				title={detail ? `运行实例 #${detail.id}` : "运行详情"}
				open={Boolean(executionId && detail)}
				onClose={() => onExecutionSelect?.(null)}
				width={720}
				loading={detailLoading}
			>
				{detail ? (
					<Space direction="vertical" size={16} style={{ width: "100%" }}>
						<Descriptions bordered size="small" column={2}>
							<Descriptions.Item label="运行状态">
								<Tag color={STATUS_META[normalized(detail.status)]?.color || "default"}>
									{STATUS_META[normalized(detail.status)]?.label || detail.status}
								</Tag>
							</Descriptions.Item>
							<Descriptions.Item label="任务版本">
								{detail.revisionNumber ? `R${detail.revisionNumber}` : "-"}
							</Descriptions.Item>
							<Descriptions.Item label="父实例">
								{detail.parentExecutionId ? `#${detail.parentExecutionId}` : "-"}
							</Descriptions.Item>
							<Descriptions.Item label="重试次数">
								{detail.retryCount ?? 0} / {detail.maxRetries ?? 0}
							</Descriptions.Item>
							<Descriptions.Item label="开始时间">{formatDateTime(detail.startTime)}</Descriptions.Item>
							<Descriptions.Item label="结束时间">{formatDateTime(detail.endTime)}</Descriptions.Item>
							<Descriptions.Item label="读取行数">{detail.rowsRead ?? "-"}</Descriptions.Item>
							<Descriptions.Item label="写入行数">{detail.rowsWritten ?? "-"}</Descriptions.Item>
							<Descriptions.Item label="计划校验值" span={2}>
								<Text copyable>{detail.effectiveConfigChecksum || "-"}</Text>
							</Descriptions.Item>
						</Descriptions>

						<Card title="资产、质量与可信证据" size="small">
							<EvidenceTags evidence={evidence} />
							{evidence ? (
								<Descriptions size="small" column={1} style={{ marginTop: 16 }}>
									<Descriptions.Item label="目标资产">
										{evidence.datasetId ? (
											<Link to={`/catalog/datasets/${encodeURIComponent(evidence.datasetId)}`}>
												{evidence.assetName || evidence.datasetId}
											</Link>
										) : (
											"未解析"
										)}
									</Descriptions.Item>
									<Descriptions.Item label="已发布质量规则">{evidence.qualityBindingCount ?? 0}</Descriptions.Item>
									<Descriptions.Item label="证据时效">
										{EVIDENCE_LABELS[normalized(evidence.evidenceState)] || evidence.evidenceState}
									</Descriptions.Item>
									<Descriptions.Item label="质量结果">{evidence.qualityStatus}</Descriptions.Item>
									<Descriptions.Item label="资产可消费性">{evidence.consumptionEligibility}</Descriptions.Item>
									<Descriptions.Item label="判定原因">
										{evidence.eligibilityReasons?.join("、") || "-"}
									</Descriptions.Item>
								</Descriptions>
							) : null}
							<Space wrap style={{ marginTop: 12 }}>
								{evidence?.workflowId ? (
									<Link to={`/governance/rules/runs?workflowId=${encodeURIComponent(evidence.workflowId)}`}>
										查看质量工作流
									</Link>
								) : null}
								{detail.qualityRunId ? (
									<Link to={`/governance/rules/runs/${encodeURIComponent(detail.qualityRunId)}`}>查看质量运行</Link>
								) : null}
							</Space>
						</Card>

						{detail.errorMessage ? (
							<Alert
								type="error"
								showIcon
								message={detail.failureCategory || "运行失败"}
								description={`${detail.errorMessage}${detail.failureAdvice ? `；建议：${detail.failureAdvice}` : ""}`}
							/>
						) : null}
						<Button onClick={() => void handleLogs(detail)}>查看完整日志</Button>
					</Space>
				) : (
					<Empty description="运行详情加载中" />
				)}
			</Drawer>

			<Modal title="运行日志" open={logOpen} onCancel={() => setLogOpen(false)} footer={null} width={900}>
				{logLoading ? (
					<Paragraph>日志加载中…</Paragraph>
				) : (
					<pre
						style={{
							maxHeight: "65vh",
							overflow: "auto",
							padding: 12,
							background: "#0f172a",
							color: "#e2e8f0",
							whiteSpace: "pre-wrap",
							wordBreak: "break-word",
						}}
					>
						{logPayload?.log || logPayload?.message || logPayload?.errorMessage || "暂无日志"}
					</pre>
				)}
			</Modal>
		</Space>
	);
}
