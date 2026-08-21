import { SearchOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Descriptions, Input, Progress, Select, Space, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { getQualityRun, listFailingRows, listQualityRules, listQualityRuns } from "@/api/platformApi";
import { CompactTable } from "@/components/table";
import { qualityLabel } from "@/utils/customerDisplayLabels";
import { formatTime } from "@/utils/textUtils";
import { QualityEmpty, QualityMetric, QualityPageHeading, QualityStatus, UnavailableCapability } from "./QualityShared";
import { qualityPath } from "./qualityRoutes";
import {
	displayName,
	getQualityRunCounts,
	type QualityRule,
	type QualityRun,
	qualityRuleNameLabel,
	toList,
} from "./qualityTypes";
import { RunIssueDisposition } from "./RunIssueDisposition";
import { useDefaultLakeDatasets } from "./useDefaultLakeDatasets";

export function RunListPage() {
	const navigate = useNavigate();
	const { datasets } = useDefaultLakeDatasets();
	const [runs, setRuns] = useState<QualityRun[]>([]);
	const [rules, setRules] = useState<QualityRule[]>([]);
	const [loading, setLoading] = useState(true);
	const [status, setStatus] = useState<string>();
	const [keyword, setKeyword] = useState("");

	const load = useCallback(async () => {
		setLoading(true);
		try {
			const [runResponse, ruleResponse] = await Promise.all([
				listQualityRuns({ limit: 300, ...(status ? { status } : {}) }),
				listQualityRules(),
			]);
			setRuns(toList<QualityRun>(runResponse));
			setRules(toList<QualityRule>(ruleResponse));
		} catch (error) {
			toast.error(error instanceof Error ? error.message : "运行记录加载失败");
		} finally {
			setLoading(false);
		}
	}, [status]);

	useEffect(() => {
		void load();
	}, [load]);

	const ruleNames = useMemo(
		() => new Map(rules.map((item) => [item.id, qualityRuleNameLabel(item.name || item.id)])),
		[rules],
	);
	const datasetNames = useMemo(() => new Map(datasets.map((item) => [item.id, item.name])), [datasets]);
	const filtered = useMemo(() => {
		const query = keyword.trim().toLowerCase();
		if (!query) return runs;
		return runs.filter((run) =>
			[run.id, ruleNames.get(String(run.ruleId)), datasetNames.get(String(run.datasetId))].some((value) =>
				String(value || "")
					.toLowerCase()
					.includes(query),
			),
		);
	}, [datasetNames, keyword, ruleNames, runs]);

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
		{ title: "失败行数", dataIndex: "failingRowCount", width: 110, render: (value) => value ?? "-" },
	];

	return (
		<div className="dq-page">
			<QualityPageHeading
				title="运行记录"
				description="检索手动、调度与试跑执行，进入运行详情核对指标和失败样本。"
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
						options={["QUEUED", "RUNNING", "PASSED", "SUCCESS", "SUCCEEDED", "FAILED", "ERROR", "SKIPPED"].map(
							(value) => ({
								value,
								label: displayName(value),
							}),
						)}
					/>
				</Space>
			</Card>
			<CompactTable
				rowKey="id"
				loading={loading}
				columns={columns}
				dataSource={filtered}
				pagination={{ pageSize: 10 }}
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
