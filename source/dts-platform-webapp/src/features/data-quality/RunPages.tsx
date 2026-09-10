import { qualityDiagnosticText } from "./qualityExecutionContract";
import { SearchOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Descriptions, Input, Progress, Select, Space, Tabs, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import {
	cancelQualityWorkflow,
	getQualityRun,
	getQualityWorkflow,
	listFailingRows,
	listQualityRules,
	listQualityRuns,
	listQualityWorkflows,
	retryQualityWorkflow,
} from "@/api/platformApi";
import { actionColumn, CompactTable } from "@/components/table";
import { qualityLabel } from "@/utils/customerDisplayLabels";
import { formatTime } from "@/utils/textUtils";
import { QualityEmpty, QualityMetric, QualityPageHeading, QualityStatus, UnavailableCapability } from "./QualityShared";
import {
	hasRetryCapacity,
	isActiveWorkflow,
	isRetryableWorkflow,
	QualityWorkflowEvidenceDrawer,
	workflowTriggerLabel,
} from "./QualityWorkflowEvidenceDrawer";
import { qualityPath } from "./qualityRoutes";
import {
	displayName,
	getQualityRunCounts,
	type QualityRule,
	type QualityRun,
	type QualityWorkflowRun,
	qualityRuleNameLabel,
	toList,
} from "./qualityTypes";
import { RunIssueDisposition } from "./RunIssueDisposition";
import { useDefaultLakeDatasets } from "./useDefaultLakeDatasets";
import { useQualityMaintainerAccess } from "./useQualityAccess";

export function RunListPage() {
	const navigate = useNavigate();
	const [searchParams, setSearchParams] = useSearchParams();
	const canManage = useQualityMaintainerAccess();
	const { datasets } = useDefaultLakeDatasets();
	const [runs, setRuns] = useState<QualityRun[]>([]);
	const [workflows, setWorkflows] = useState<QualityWorkflowRun[]>([]);
	const [rules, setRules] = useState<QualityRule[]>([]);
	const [selectedWorkflow, setSelectedWorkflow] = useState<QualityWorkflowRun>();
	const [workflowLoading, setWorkflowLoading] = useState(false);
	const [actingId, setActingId] = useState("");
	const [loading, setLoading] = useState(true);
	const [status, setStatus] = useState<string>();
	const [keyword, setKeyword] = useState("");
	const [view, setView] = useState<"workflow" | "legacy">("workflow");

	const load = useCallback(async (silent = false) => {
		if (!silent) setLoading(true);
		try {
			const [workflowResponse, runResponse, ruleResponse] = await Promise.all([
				listQualityWorkflows({ limit: 200 }),
				listQualityRuns({ limit: 300 }),
				listQualityRules(),
			]);
			setWorkflows(toList<QualityWorkflowRun>(workflowResponse));
			setRuns(toList<QualityRun>(runResponse));
			setRules(toList<QualityRule>(ruleResponse));
		} catch (error) {
			if (!silent) toast.error(error instanceof Error ? error.message : "运行记录加载失败");
		} finally {
			if (!silent) setLoading(false);
		}
	}, []);

	useEffect(() => {
		void load();
	}, [load]);

	const ruleNames = useMemo(
		() => new Map(rules.map((item) => [item.id, qualityRuleNameLabel(item.name || item.id)])),
		[rules],
	);
	const datasetNames = useMemo(() => new Map(datasets.map((item) => [item.id, item.name])), [datasets]);
	const filteredRuns = useMemo(() => {
		const query = keyword.trim().toLowerCase();
		return runs
			.filter((run) => !run.jobId)
			.filter((run) => !status || String(run.status || "").toUpperCase() === status)
			.filter(
				(run) =>
					!query ||
					[run.id, ruleNames.get(String(run.ruleId)), datasetNames.get(String(run.datasetId))].some((value) =>
						String(value || "")
							.toLowerCase()
							.includes(query),
					),
			);
	}, [datasetNames, keyword, ruleNames, runs, status]);
	const filteredWorkflows = useMemo(() => {
		const query = keyword.trim().toLowerCase();
		return workflows
			.filter((workflow) => !status || String(workflow.status || "").toUpperCase() === status)
			.filter(
				(workflow) =>
					!query ||
					[datasetNames.get(String(workflow.datasetId)), workflowTriggerLabel(workflow.triggerType)].some((value) =>
						String(value || "")
							.toLowerCase()
							.includes(query),
					),
			);
	}, [datasetNames, keyword, status, workflows]);

	const openWorkflow = useCallback(async (workflowId: string, silent = false) => {
		if (!silent) setWorkflowLoading(true);
		try {
			setSelectedWorkflow((await getQualityWorkflow(workflowId)) as QualityWorkflowRun);
		} catch (error) {
			if (!silent) toast.error(error instanceof Error ? error.message : "验证详情加载失败");
		} finally {
			if (!silent) setWorkflowLoading(false);
		}
	}, []);

	useEffect(() => {
		const workflowId = searchParams.get("workflowId");
		if (workflowId) void openWorkflow(workflowId);
	}, [openWorkflow, searchParams]);

	const hasActiveWorkflow = workflows.some((workflow) => isActiveWorkflow(workflow.status));
	useEffect(() => {
		if (!hasActiveWorkflow && !isActiveWorkflow(selectedWorkflow?.status)) return undefined;
		const timer = window.setInterval(() => {
			void load(true);
			if (selectedWorkflow?.id) void openWorkflow(selectedWorkflow.id, true);
		}, 5_000);
		return () => window.clearInterval(timer);
	}, [hasActiveWorkflow, load, openWorkflow, selectedWorkflow?.id, selectedWorkflow?.status]);

	const selectWorkflow = (workflowId: string) => {
		const next = new URLSearchParams(searchParams);
		next.set("workflowId", workflowId);
		setSearchParams(next);
	};

	const closeWorkflow = () => {
		setSelectedWorkflow(undefined);
		const next = new URLSearchParams(searchParams);
		next.delete("workflowId");
		setSearchParams(next, { replace: true });
	};

	const retryWorkflow = async (workflow: QualityWorkflowRun) => {
		if (!canManage || !isRetryableWorkflow(workflow.status) || !hasRetryCapacity(workflow)) return;
		setActingId(workflow.id);
		try {
			const retried = (await retryQualityWorkflow(
				workflow.id,
				`quality-workflow:retry:${workflow.id}`,
			)) as QualityWorkflowRun;
			toast.success("重新验证已启动");
			await load();
			setSelectedWorkflow(retried);
		} catch (error) {
			toast.error(error instanceof Error ? error.message : "重新验证启动失败");
		} finally {
			setActingId("");
		}
	};

	const cancelWorkflow = async (workflow: QualityWorkflowRun) => {
		if (!canManage || !isActiveWorkflow(workflow.status)) return;
		setActingId(workflow.id);
		try {
			const cancelled = (await cancelQualityWorkflow(workflow.id)) as QualityWorkflowRun;
			toast.success("本次质量验证已取消，已生成的规则证据继续保留");
			await load(true);
			setSelectedWorkflow(cancelled);
		} catch (error) {
			toast.error(error instanceof Error ? error.message : "取消质量验证失败");
		} finally {
			setActingId("");
		}
	};

	const columns: ColumnsType<QualityRun> = [
		{
			title: "运行 ID",
			dataIndex: "id",
			minWidth: 190,
			ellipsis: true,
			render: (value, row) => (
				<Button type="link" onClick={() => navigate(qualityPath("run-detail", { runId: row.id }))}>
					{displayName(value)}
				</Button>
			),
		},
		{ title: "状态", dataIndex: "status", width: 110, render: (value) => <QualityStatus status={value} /> },
		{ title: "触发方式", dataIndex: "triggerType", width: 110, render: (value) => <Tag>{displayName(value)}</Tag> },
		{
			title: "规则",
			dataIndex: "ruleId",
			minWidth: 160,
			ellipsis: true,
			render: (value) => ruleNames.get(String(value)) || displayName(value),
		},
		{
			title: "数据资产",
			dataIndex: "datasetId",
			minWidth: 160,
			ellipsis: true,
			render: (value) => datasetNames.get(String(value)) || displayName(value),
		},
		{ title: "开始时间", dataIndex: "startedAt", width: 180, render: formatTime },
		{
			title: "耗时",
			dataIndex: "durationMs",
			width: 110,
			render: (value) => (value == null ? "-" : `${Number(value).toLocaleString()} 毫秒`),
		},
		{
			title: "失败行数",
			dataIndex: "failingRowCount",
			width: 110,
			render: (_value, run) =>
				getQualityRunCounts(run).hasStatistics ? getQualityRunCounts(run).failed.toLocaleString() : "暂无统计",
		},
	];
	const workflowColumns: ColumnsType<QualityWorkflowRun> = [
		{ title: "触发方式", dataIndex: "triggerType", width: 150, render: workflowTriggerLabel },
		{ title: "状态", dataIndex: "status", width: 110, render: (value) => <QualityStatus status={value} /> },
		{
			title: "数据资产",
			dataIndex: "datasetId",
			minWidth: 180,
			ellipsis: true,
			render: (value) => datasetNames.get(String(value)) || "当前数据资产",
		},
		{
			title: "规则进度",
			width: 120,
			render: (_, row) => `${row.completedRunCount || 0}/${row.expectedRunCount || 0}`,
		},
		{ title: "开始时间", dataIndex: "startedAt", width: 180, render: formatTime },
		{ title: "完成时间", dataIndex: "finishedAt", width: 180, render: formatTime },
		actionColumn<QualityWorkflowRun>(
			(row) => [
				{ key: "view", label: "查看", onClick: () => selectWorkflow(row.id) },
				{
					key: "cancel",
					label: "取消",
					danger: true,
					hidden: !isActiveWorkflow(row.status),
					disabled: !canManage,
					confirm: "确认取消本次质量验证？已生成的规则记录会保留。",
					loading: actingId === row.id,
					onClick: () => void cancelWorkflow(row),
				},
				{
					key: "retry",
					label: "重新验证",
					disabled: !canManage || !isRetryableWorkflow(row.status) || !hasRetryCapacity(row),
					loading: actingId === row.id,
					onClick: () => void retryWorkflow(row),
				},
			],
			{ width: 220, fixed: false },
		),
	];

	return (
		<div className="dq-page">
			<QualityPageHeading
				title="运行记录"
				description="先查看一次完整验证，再进入规则明细核对指标、失败样本和恢复记录。"
				actions={
					<Button loading={loading} onClick={() => void load()}>
						刷新
					</Button>
				}
			/>
			<Card size="small">
				<Space wrap>
					<Input
						prefix={<SearchOutlined />}
						allowClear
						placeholder="搜索运行、规则或数据资产"
						value={keyword}
						onChange={(event) => setKeyword(event.target.value)}
						style={{ width: 360 }}
					/>
					<Select
						allowClear
						placeholder="全部状态"
						value={status}
						onChange={setStatus}
						style={{ width: 160 }}
						options={["QUEUED", "RUNNING", "PASSED", "SUCCEEDED", "FAILED", "BLOCKED", "CANCELLED", "SKIPPED"].map(
							(value) => ({
								value,
								label: displayName(value),
							}),
						)}
					/>
				</Space>
			</Card>
			<Tabs
				activeKey={view}
				onChange={(key) => {
					setView(key as "workflow" | "legacy");
					setStatus(undefined);
				}}
				items={[
					{
						key: "workflow",
						label: `验证工作流（${workflows.length}）`,
						children: (
							<CompactTable
								rowKey="id"
								loading={loading}
								columns={workflowColumns}
								dataSource={filteredWorkflows}
								pagination={{ pageSize: 10 }}
							/>
						),
					},
					{
						key: "legacy",
						label: `历史规则运行（${runs.filter((run) => !run.jobId).length}）`,
						children: (
							<CompactTable
								rowKey="id"
								loading={loading}
								columns={columns}
								dataSource={filteredRuns}
								pagination={{ pageSize: 10 }}
							/>
						),
					},
				]}
			/>
			<QualityWorkflowEvidenceDrawer
				workflow={selectedWorkflow}
				loading={workflowLoading}
				canManage={canManage}
				retrying={Boolean(selectedWorkflow && actingId === selectedWorkflow.id)}
				cancelling={Boolean(selectedWorkflow && actingId === selectedWorkflow.id)}
				ruleNames={ruleNames}
				onClose={closeWorkflow}
				onRetry={(workflow) => void retryWorkflow(workflow)}
				onCancel={(workflow) => void cancelWorkflow(workflow)}
			/>
		</div>
	);
}

export function RunDetailPage() {
	const { runId = "" } = useParams();
	const navigate = useNavigate();
	const { datasets } = useDefaultLakeDatasets();
	const [run, setRun] = useState<QualityRun>();
	const [failingRows, setFailingRows] = useState<Array<Record<string, unknown>>>([]);
	const [loading, setLoading] = useState(true);
	const [loadError, setLoadError] = useState("");
	const loadSequence = useRef(0);
	const activeRunId = useRef(runId);
	activeRunId.current = runId;

	const load = useCallback(async () => {
		const requestedRunId = runId;
		const sequence = ++loadSequence.current;
		setLoading(true);
		setLoadError("");
		setRun(undefined);
		setFailingRows([]);
		try {
			const detail = (await getQualityRun(requestedRunId)) as QualityRun;
			if (!detail?.id || String(detail.id) !== requestedRunId) throw new Error("未找到该质量运行");
			let nextFailingRows: Array<Record<string, unknown>>;
			try {
				nextFailingRows = toList<Record<string, unknown>>(
					await listFailingRows(requestedRunId, { page: 0, size: 100 }),
				);
			} catch {
				nextFailingRows = detail?.failingRowsSample || [];
			}
			if (sequence !== loadSequence.current || activeRunId.current !== requestedRunId) return;
			setRun(detail);
			setFailingRows(nextFailingRows);
		} catch (error) {
			if (sequence !== loadSequence.current || activeRunId.current !== requestedRunId) return;
			const message = error instanceof Error ? error.message : "运行详情加载失败";
			setLoadError(message);
			toast.error(message);
		} finally {
			if (sequence === loadSequence.current && activeRunId.current === requestedRunId) setLoading(false);
		}
	}, [runId]);

	useEffect(() => {
		void load();
	}, [load]);

	const { total, passed, failed, passRate, hasStatistics } = getQualityRunCounts(run);
	const datasetName = datasets.find((item) => item.id === run?.datasetId)?.name || run?.datasetId;
	const sampleColumns = useMemo<ColumnsType<Record<string, unknown>>>(() => {
		const rawKeys = Array.from(
			new Set(
				failingRows.flatMap((row) =>
					row.rowData && typeof row.rowData === "object" ? Object.keys(row.rowData as Record<string, unknown>) : [],
				),
			),
		).slice(0, 10);
		return [
			{ title: "行标识", dataIndex: "rowId", key: "rowId", width: 120, ellipsis: true },
			{ title: "字段", dataIndex: "columnName", key: "columnName", width: 140, ellipsis: true },
			{ title: "实际值", dataIndex: "actualValue", key: "actualValue", width: 140, ellipsis: true },
			{ title: "失败原因", dataIndex: "failReason", key: "failReason", width: 180, ellipsis: true },
			...rawKeys.map((key) => ({
				title: key,
				key: `rowData.${key}`,
				ellipsis: true,
				render: (_: unknown, row: Record<string, unknown>) =>
					displayName((row.rowData as Record<string, unknown> | undefined)?.[key]),
			})),
		];
	}, [failingRows]);

	if (!loading && loadError) return <QualityEmpty description={`运行详情加载失败：${loadError}`} />;

	return (
		<div className="dq-page">
			<QualityPageHeading
				title="运行详情"
				description="核对单次质量运行的执行上下文、指标、失败样本与错误信息。"
				actions={[
					<Button key="back" onClick={() => navigate(qualityPath("run-records"))}>
						返回运行记录
					</Button>,
					run?.jobId ? (
						<Button
							key="workflow"
							onClick={() =>
								navigate(`${qualityPath("run-records")}?workflowId=${encodeURIComponent(run.jobId || "")}`)
							}
						>
							查看完整验证
						</Button>
					) : null,
					<Button key="reload" loading={loading} onClick={() => void load()}>
						刷新
					</Button>,
				]}
			/>
			{run?.status && ["FAILED", "ERROR"].includes(run.status.toUpperCase()) ? (
				<Alert
					showIcon
					type="error"
					message="质量检测未通过"
					description={run.message || qualityLabel(run.errorCategory, "请检查失败样本与执行 SQL。")}
				/>
			) : null}
			{run?.outcome ? <Card title="检测结论">
                <p>业务结论：{run.outcome.qualityOutcome === "PASSED" ? "检查通过" : run.outcome.qualityOutcome === "VIOLATION" ? "发现违规数据" : "暂无有效结论"}</p>
                <p>执行状态：{run.outcome.executionOutcome === "OK" ? "正常完成" : "执行未完成"}</p>
                {run.outcome.violationOccurrences != null && run.outcome.qualityOutcome === "VIOLATION" ? <p>已确认违规记录次数：{run.outcome.violationOccurrences}（多语句可能重复）</p> : null}
                {run.outcome.statisticsStatus !== "EXACT" ? <p>精确统计不可用，不计入通过率和质量评分。</p> : null}
                {run.outcome.diagnostics.map((item, index) => <p key={index}>{qualityDiagnosticText(item)}</p>)}
            </Card> : null}
			<div className="dq-metric-grid">
				<QualityMetric label="扫描行数" value={hasStatistics ? total.toLocaleString() : "暂无统计"} />
				<QualityMetric label="通过行数" value={hasStatistics ? passed.toLocaleString() : "暂无统计"} color="#52c41a" />
				<QualityMetric label="失败行数" value={hasStatistics ? failed.toLocaleString() : "暂无统计"} color="#ff4d4f" />
				<QualityMetric
					label="通过率"
					value={hasStatistics ? `${passRate}%` : "暂无统计"}
					note={hasStatistics ? <Progress percent={passRate} showInfo={false} size="small" /> : undefined}
					color="#13c2c2"
				/>
			</div>
			<Card
				loading={loading}
				title={
					<Space>
						执行上下文 <QualityStatus status={run?.status} />
					</Space>
				}
			>
				<Descriptions column={{ xs: 1, md: 2, xl: 3 }} size="small">
					<Descriptions.Item label="运行编号">{runId}</Descriptions.Item>
					<Descriptions.Item label="规则编号">{displayName(run?.ruleId)}</Descriptions.Item>
					<Descriptions.Item label="数据资产">{displayName(datasetName)}</Descriptions.Item>
					<Descriptions.Item label="触发方式">{displayName(run?.triggerType)}</Descriptions.Item>
					<Descriptions.Item label="开始时间">{formatTime(run?.startedAt)}</Descriptions.Item>
					<Descriptions.Item label="完成时间">{formatTime(run?.finishedAt)}</Descriptions.Item>
					<Descriptions.Item label="耗时">{run?.durationMs == null ? "-" : `${run.durationMs} 毫秒`}</Descriptions.Item>
					<Descriptions.Item label="错误分类">{displayName(run?.errorCategory)}</Descriptions.Item>
				</Descriptions>
			</Card>
			{run?.executedSql ? (
				<Card title="执行 SQL">
					<pre className="dq-code-block">{run.executedSql}</pre>
				</Card>
			) : null}
			<RunIssueDisposition runId={runId} datasetId={run?.datasetId} runStatus={run?.status} />
			<Card title="数据修复与清洗">
				<Alert
					showIcon
					type="info"
					message="SQL 修复与数据清洗暂未开放"
					description="当前后端无法把修复或清洗写入安全绑定到本次运行的实际数据源，因此仅保留问题单处置，不发起预览或执行请求。"
				/>
				<Space wrap style={{ marginTop: 16 }}>
					<Button disabled>执行 SQL 修复（暂未开放）</Button>
					<Button disabled>执行数据清洗（暂未开放）</Button>
				</Space>
			</Card>
			<Card title={`失败样本（${failingRows.length}）`}>
				<CompactTable
					rowKey={(row, index) => String(row.id || row.rowId || index)}
					columns={sampleColumns}
					dataSource={failingRows}
					pagination={{ pageSize: 20 }}
				/>
			</Card>
		</div>
	);
}

export function NoiseManagementPage() {
	return (
		<div className="dq-page">
			<QualityPageHeading title="去噪管理" description="管理误报抑制、静默窗口和异常合并策略。" />
			<UnavailableCapability capability="noise-management" title="去噪管理暂未开放" />
			<Card title="能力边界">
				<p className="dq-muted">
					当前质量后端未提供去噪策略的查询、保存、启停和生效范围合同，因此本页不提供模拟保存或假提交。
				</p>
			</Card>
		</div>
	);
}
