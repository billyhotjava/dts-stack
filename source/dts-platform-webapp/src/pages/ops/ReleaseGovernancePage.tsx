import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Progress, Space, Tag, Timeline, Typography } from "antd";
import { CompactTable, RecordDetailDrawer, appendDetailAction } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { CheckCircle2, ClipboardCheck, DatabaseZap, RadioTower, RefreshCw, ShieldCheck } from "lucide-react";
import { useNavigate } from "react-router";
import {
	PlatformPageHero,
	PlatformSectionCard,
	PlatformSummaryCards,
} from "@/components/console-page";
import {
	getSprint27ReleaseGovernance,
	type PlatformEventSummary,
	type Sprint27SourceStatus,
} from "@/api/platformApi";
import type { IngestionExecutionObservabilityDTO } from "@/api/ingestion";

type GovernanceReleaseGate = {
	windowDays?: number;
	readyForRelease?: boolean;
	blockerFailed?: number;
	checkedAt?: string;
	checks?: Array<{
		code?: string;
		name?: string;
		passed?: boolean;
		actual?: unknown;
		threshold?: string;
		severity?: string;
	}>;
};

type CheckRow = {
	key: string;
	area: string;
	name: string;
	passed: boolean;
	actual: string;
	threshold: string;
	severity: string;
	path: string;
};

const EMPTY_INGESTION: IngestionExecutionObservabilityDTO = {
	total: 0,
	success: 0,
	failed: 0,
	running: 0,
	terminal: 0,
	timeout: 0,
	successRate: 0,
	timeoutRate: 0,
	avgDurationSeconds: 0,
	mttrSeconds: 0,
	failureTop: [],
	trend: [],
};

const percent = (value?: number) => `${Math.round((Number(value) || 0) * 100)}%`;

const formatDateTime = (value?: string) => {
	if (!value) return "-";
	const date = new Date(value);
	return Number.isNaN(date.valueOf()) ? value : date.toLocaleString();
};

const normalizeIndicatorOverview = (payload: any) => {
	const statusDist = payload?.statusDistribution || {};
	const validation = payload?.validation || {};
	return {
		total: Number(payload?.totalIndicators ?? payload?.total ?? 0),
		published: Number(statusDist?.published ?? 0),
		validationFailed: Number(validation?.failed ?? 0),
		failedRate: Number(validation?.failedRate ?? 0),
	};
};

export default function ReleaseGovernancePage() {
	const navigate = useNavigate();
	const [loading, setLoading] = useState(false);
	const [governanceGate, setGovernanceGate] = useState<GovernanceReleaseGate | null>(null);
	const [indicatorOverview, setIndicatorOverview] = useState(() => normalizeIndicatorOverview(null));
	const [ingestionOverview, setIngestionOverview] = useState<IngestionExecutionObservabilityDTO>(EMPTY_INGESTION);
	const [eventSummary, setEventSummary] = useState<PlatformEventSummary | null>(null);
	const [dbtGate, setDbtGate] = useState<any>(null);
	const [remoteReadyForRelease, setRemoteReadyForRelease] = useState<boolean | null>(null);
	const [remoteCheckRows, setRemoteCheckRows] = useState<CheckRow[]>([]);
	const [sources, setSources] = useState<Record<string, Sprint27SourceStatus>>({});
	const [detailRow, setDetailRow] = useState<CheckRow | null>(null);

	const loadData = async () => {
		setLoading(true);
		try {
			const snapshot = await getSprint27ReleaseGovernance();
			setGovernanceGate((snapshot?.governanceGate || null) as GovernanceReleaseGate | null);
			setIndicatorOverview(normalizeIndicatorOverview(snapshot?.indicatorOverview));
			setIngestionOverview((snapshot?.ingestionOverview || EMPTY_INGESTION) as IngestionExecutionObservabilityDTO);
			setEventSummary(snapshot?.eventSummary || null);
			setDbtGate(snapshot?.dbtGate);
			setRemoteReadyForRelease(typeof snapshot?.readyForRelease === "boolean" ? snapshot.readyForRelease : null);
			setRemoteCheckRows(
				Array.isArray(snapshot?.checks)
					? snapshot.checks.map((check: any) => ({
						key: String(check.key ?? check.code ?? check.name),
						area: String(check.area ?? "治理门禁"),
						name: String(check.name ?? check.code ?? "-"),
						passed: Boolean(check.passed),
						actual: String(check.actual ?? "-"),
						threshold: String(check.threshold ?? "-"),
						severity: String(check.severity ?? "BLOCKER"),
						path: String(check.path ?? "/governance"),
					}))
					: [],
			);
			setSources(snapshot?.sources || {});
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadData();
	}, []);

	const blockerFailed = Number(governanceGate?.blockerFailed || 0);
	const eventFailed = Number(eventSummary?.failed || 0);
	const ingestionFailed = Number(ingestionOverview.failed || 0) + Number(ingestionOverview.timeout || 0);
	const indicatorFailed = Number(indicatorOverview.validationFailed || 0);
	const dbtBlocked = dbtGate ? Boolean(dbtGate.blocking ?? dbtGate.blocked ?? dbtGate.failed) : false;
	const readyForRelease = remoteReadyForRelease ?? (!blockerFailed && !eventFailed && !ingestionFailed && !indicatorFailed && !dbtBlocked);

	const summaryCards = [
		{
			label: "发布状态",
			value: readyForRelease ? "可发布" : "待处理",
			note: governanceGate?.checkedAt ? `最近校验 ${formatDateTime(governanceGate.checkedAt)}` : "聚合最近 7 天快照",
			icon: <ClipboardCheck className="h-5 w-5" />,
			tone: readyForRelease ? "success" as const : "warning" as const,
		},
		{
			label: "治理阻断",
			value: blockerFailed,
			note: "质量、问题单、码表门禁",
			icon: <ShieldCheck className="h-5 w-5" />,
			tone: blockerFailed ? "warning" as const : "success" as const,
		},
		{
			label: "ELT 异常",
			value: ingestionFailed,
			note: `成功率 ${percent(ingestionOverview.successRate)}`,
			icon: <DatabaseZap className="h-5 w-5" />,
			tone: ingestionFailed ? "warning" as const : "success" as const,
		},
		{
			label: "事件失败",
			value: eventFailed,
			note: `${eventSummary?.pending ?? 0} 条待分发`,
			icon: <RadioTower className="h-5 w-5" />,
			tone: eventFailed ? "warning" as const : "success" as const,
		},
	];

	const checkRows = useMemo<CheckRow[]>(() => {
		if (remoteCheckRows.length) return remoteCheckRows;
		const rows: CheckRow[] = [];
		for (const check of governanceGate?.checks || []) {
			rows.push({
				key: `governance-${check.code || check.name}`,
				area: "治理门禁",
				name: check.name || check.code || "-",
				passed: Boolean(check.passed),
				actual: String(check.actual ?? "-"),
				threshold: check.threshold || "-",
				severity: check.severity || "BLOCKER",
				path: "/governance",
			});
		}
		rows.push(
			{
				key: "elt-execution",
				area: "ELT",
				name: "采集/转换执行异常",
				passed: ingestionFailed === 0,
				actual: String(ingestionFailed),
				threshold: "=0",
				severity: "BLOCKER",
				path: "/explore/etl",
			},
			{
				key: "metric-validation",
				area: "指标",
				name: "指标校验失败",
				passed: indicatorFailed === 0,
				actual: String(indicatorFailed),
				threshold: "=0",
				severity: "BLOCKER",
				path: "/metrics/operations",
			},
			{
				key: "event-dispatch",
				area: "事件",
				name: "事件分发失败",
				passed: eventFailed === 0,
				actual: String(eventFailed),
				threshold: "=0",
				severity: "WARN",
				path: "/ops/events",
			},
			{
				key: "dbt-release-gate",
				area: "dbt",
				name: "dbt release gate",
				passed: !dbtBlocked,
				actual: dbtGate ? String(dbtGate.status ?? dbtGate.result ?? dbtGate.blocking ?? "checked") : "未返回",
				threshold: "不阻断",
				severity: "BLOCKER",
				path: "/modeling/dbt-files",
			},
		);
		return rows;
	}, [dbtBlocked, dbtGate, eventFailed, governanceGate?.checks, indicatorFailed, ingestionFailed, remoteCheckRows]);

	const passedCount = checkRows.filter((row) => row.passed).length;

	const baseColumns: ColumnsType<CheckRow> = [
		{ title: "域", dataIndex: "area", key: "area", width: 110, render: (value) => <Tag>{value}</Tag> },
		{ title: "检查项", dataIndex: "name", key: "name" , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
		{ title: "结果", dataIndex: "passed", key: "passed", width: 100, render: (value) => <Tag color={value ? "green" : "red"}>{value ? "通过" : "未通过"}</Tag> },
		{ title: "当前值", dataIndex: "actual", key: "actual", width: 140 },
		{ title: "阈值", dataIndex: "threshold", key: "threshold", width: 120 },
		{ title: "级别", dataIndex: "severity", key: "severity", width: 120, render: (value) => <Tag color={value === "BLOCKER" ? "red" : "orange"}>{value}</Tag> },
		{
			title: "操作",
			dataIndex: "action",
			key: "action",
			width: 160,
			fixed: "right",
			render: (_, record) => (
				<Button type="link" size="small" onClick={() => navigate(record.path)}>
					进入
				</Button>
			),
		},
	];

	const columns = useMemo(
		() => appendDetailAction(baseColumns, (row) => setDetailRow(row)),
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[],
	);

	return (
		<div className="space-y-6">
			<PlatformPageHero
				title="发布治理"
				actions={
					<Space wrap>
						<Button onClick={() => navigate("/ops/audit-evidence")}>审计证据链</Button>
						<Button onClick={() => navigate("/ops/events")}>事件观测</Button>
						<Button icon={<RefreshCw className="h-4 w-4" />} loading={loading} onClick={() => void loadData()}>
							刷新
						</Button>
					</Space>
				}
			/>

			<PlatformSummaryCards items={summaryCards} />

			<PlatformSectionCard title="数据源状态">
				<Space wrap>
					{Object.entries(sources).length ? Object.entries(sources).map(([key, source]) => (
						<Tag key={key} color={source.status === "ERROR" ? "red" : source.status === "EMPTY" ? "default" : "green"}>
							{key}: {source.status}
						</Tag>
					)) : <Typography.Text type="secondary">暂无后端数据源状态</Typography.Text>}
				</Space>
			</PlatformSectionCard>

			<PlatformSectionCard title="发布结论">
				<Alert
					type={readyForRelease ? "success" : "warning"}
					showIcon
					message={readyForRelease ? "当前快照满足发布条件" : "当前快照存在待处理项"}
					description="发布治理只做检查和提示，不固化客户审批、端到端权限或脱敏策略。"
				/>
				<div className="mt-4">
					<Progress percent={checkRows.length ? Math.round((passedCount / checkRows.length) * 100) : 0} />
				</div>
			</PlatformSectionCard>

			<div className="grid gap-4 xl:grid-cols-[0.9fr_1.1fr]">
				<PlatformSectionCard title="发布路径">
					<Timeline
						items={[
							{ color: ingestionFailed ? "red" : "green", children: "ELT 执行和质量结果可观测。" },
							{ color: indicatorFailed ? "red" : "green", children: "指标定义、语义模型和校验结果可追溯。" },
							{ color: blockerFailed ? "red" : "green", children: "治理门禁提供质量、问题单和码表检查。" },
							{ color: eventFailed ? "red" : "green", children: "统一事件 outbox 提供发布证据链。" },
						]}
					/>
				</PlatformSectionCard>

				<PlatformSectionCard title="证据入口">
					<Space direction="vertical" size={12} className="w-full">
						<Button block icon={<DatabaseZap className="h-4 w-4" />} onClick={() => navigate("/explore/etl")}>
							ELT 控制台
						</Button>
						<Button block icon={<CheckCircle2 className="h-4 w-4" />} onClick={() => navigate("/metrics/operations")}>
							指标运营台
						</Button>
						<Button block icon={<RadioTower className="h-4 w-4" />} onClick={() => navigate("/ops/events")}>
							事件观测
						</Button>
					</Space>
				</PlatformSectionCard>
			</div>

			<PlatformSectionCard title="发布检查清单" bodyClassName="pt-0">
				<CompactTable<CheckRow>
					rowKey="key"
					loading={loading}
					columns={columns}
					dataSource={checkRows}
					pagination={false}
					scroll={{ x: 900 }}
				/>
				{checkRows.length ? null : <Typography.Text type="secondary">暂无检查项。</Typography.Text>}
			</PlatformSectionCard>
			<RecordDetailDrawer<CheckRow>
				open={detailRow !== null}
				onClose={() => setDetailRow(null)}
				record={detailRow}
				columns={baseColumns}
				title="检查项详情"
			/>
		</div>
	);
}
