import { useEffect, useMemo, useState } from "react";
import { Button, Progress, Space, Table, Tag, Timeline, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { Activity, Boxes, CheckCircle2, Clock3, DatabaseZap, GitBranch, RefreshCw, ShieldCheck } from "lucide-react";
import { useNavigate } from "react-router";
import {
	PlatformPageHero,
	PlatformSectionCard,
	PlatformSummaryCards,
} from "@/components/console-page";
import {
	ingestionTaskAPI,
	type IngestionExecutionObservabilityDTO,
	type IngestionGovernanceOverviewDTO,
} from "@/api/ingestion";

type PipelineStage = {
	key: string;
	title: string;
	status: "processing" | "success" | "warning" | "default";
	count: number;
	path: string;
};

type ChainItem = {
	key: string;
	asset: string;
	stage: string;
	owner: string;
	status: "success" | "warning" | "processing";
	path: string;
};

const FALLBACK_OBSERVABILITY: IngestionExecutionObservabilityDTO = {
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

const FALLBACK_GOVERNANCE: IngestionGovernanceOverviewDTO = {
	running: 0,
	preparing: 0,
	queueLength: 0,
	blockedByPolicy: 0,
	avgExecutionSeconds: 0,
	avgQueueWaitSeconds: 0,
	maxQueueWaitSeconds: 0,
	sourceLoads: [],
	projectLoads: [],
};

const formatDuration = (seconds?: number) => {
	if (!seconds || seconds <= 0) return "-";
	if (seconds < 60) return `${Math.round(seconds)}s`;
	if (seconds < 3600) return `${Math.round(seconds / 60)}m`;
	return `${(seconds / 3600).toFixed(1)}h`;
};

const formatPercent = (value?: number) => `${Math.round((value || 0) * 100)}%`;

const buildFailureName = (category?: string) => {
	const labels: Record<string, string> = {
		CONNECTIVITY: "连接异常",
		SCHEMA: "结构变更",
		QUALITY: "质量阻断",
		TIMEOUT: "执行超时",
		UNKNOWN: "未分类",
	};
	return labels[String(category || "UNKNOWN").toUpperCase()] || category || "未分类";
};

export default function EltConsolePage() {
	const navigate = useNavigate();
	const [loading, setLoading] = useState(false);
	const [observability, setObservability] = useState<IngestionExecutionObservabilityDTO>(FALLBACK_OBSERVABILITY);
	const [governance, setGovernance] = useState<IngestionGovernanceOverviewDTO>(FALLBACK_GOVERNANCE);

	const loadSnapshot = async () => {
		setLoading(true);
		try {
			const [nextObservability, nextGovernance] = await Promise.all([
				ingestionTaskAPI.getExecutionsObservability({ days: 7 }),
				ingestionTaskAPI.getGovernanceOverview({ hours: 24 }),
			]);
			setObservability(nextObservability || FALLBACK_OBSERVABILITY);
			setGovernance(nextGovernance || FALLBACK_GOVERNANCE);
		} catch {
			setObservability(FALLBACK_OBSERVABILITY);
			setGovernance(FALLBACK_GOVERNANCE);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadSnapshot();
	}, []);

	const successRate = observability.successRate ?? (observability.total ? observability.success / observability.total : 0);
	const timeoutRate = observability.timeoutRate ?? (observability.total ? observability.timeout / observability.total : 0);

	const stages = useMemo<PipelineStage[]>(
		() => [
			{ key: "source", title: "数据接入", status: governance.running ? "processing" : "success", count: governance.running, path: "/explore/etl/transform" },
			{ key: "queue", title: "队列调度", status: governance.queueLength ? "warning" : "success", count: governance.queueLength, path: "/explore/etl/orchestration" },
			{ key: "transform", title: "加工转换", status: observability.running ? "processing" : "success", count: observability.running, path: "/explore/etl/transform" },
			{ key: "quality", title: "质量校验", status: governance.blockedByPolicy ? "warning" : "success", count: governance.blockedByPolicy, path: "/governance/quality" },
			{ key: "lineage", title: "血缘影响", status: "default", count: observability.terminal, path: "/catalog/lineage/impact" },
		],
		[governance, observability],
	);

	const chainItems = useMemo<ChainItem[]>(
		() => [
			{ key: "task", asset: "采集任务", stage: "接入", owner: "dts-ingestion", status: governance.running ? "processing" : "success", path: "/explore/etl/transform" },
			{ key: "model", asset: "转换模型", stage: "加工", owner: "dts-platform", status: observability.failed ? "warning" : "success", path: "/modeling/dbt-files" },
			{ key: "metric", asset: "指标口径", stage: "消费", owner: "dts-platform", status: "success", path: "/metrics/center" },
			{ key: "bi", asset: "分析看板", stage: "发布", owner: "dts-analytics", status: "success", path: "/bi/project-cockpit" },
		],
		[governance.running, observability.failed],
	);

	const summaryCards = [
		{
			label: "近 7 天执行",
			value: observability.total,
			note: `成功率 ${formatPercent(successRate)}`,
			icon: <Activity className="h-5 w-5" />,
			tone: "info" as const,
		},
		{
			label: "运行中",
			value: observability.running + governance.preparing,
			note: `队列 ${governance.queueLength}`,
			icon: <Clock3 className="h-5 w-5" />,
		},
		{
			label: "失败/超时",
			value: `${observability.failed}/${observability.timeout}`,
			note: `超时率 ${formatPercent(timeoutRate)}`,
			icon: <ShieldCheck className="h-5 w-5" />,
			tone: observability.failed || observability.timeout ? "warning" as const : "success" as const,
		},
		{
			label: "平均耗时",
			value: formatDuration(observability.avgDurationSeconds || governance.avgExecutionSeconds),
			note: `平均排队 ${formatDuration(governance.avgQueueWaitSeconds)}`,
			icon: <DatabaseZap className="h-5 w-5" />,
			tone: "success" as const,
		},
	];

	const chainColumns: ColumnsType<ChainItem> = [
		{ title: "资产", dataIndex: "asset", key: "asset" },
		{ title: "阶段", dataIndex: "stage", key: "stage", width: 100 },
		{ title: "责任域", dataIndex: "owner", key: "owner", width: 150 },
		{
			title: "状态",
			dataIndex: "status",
			key: "status",
			width: 120,
			render: (status: ChainItem["status"]) => {
				const map = {
					success: { color: "green", label: "正常" },
					warning: { color: "orange", label: "关注" },
					processing: { color: "blue", label: "运行中" },
				};
				return <Tag color={map[status].color}>{map[status].label}</Tag>;
			},
		},
		{
			title: "操作",
			key: "action",
			width: 100,
			render: (_, record) => (
				<Button type="link" size="small" onClick={() => navigate(record.path)}>
					进入
				</Button>
			),
		},
	];

	return (
		<div className="space-y-6">
			<PlatformPageHero
				title="ELT 控制台"
				actions={
					<Space wrap>
						<Button icon={<RefreshCw className="h-4 w-4" />} loading={loading} onClick={() => void loadSnapshot()}>
							刷新
						</Button>
						<Button type="primary" onClick={() => navigate("/explore/etl/transform/new")}>
							新建任务
						</Button>
					</Space>
				}
			/>

			<PlatformSummaryCards items={summaryCards} />

			<div className="grid gap-4 xl:grid-cols-[1.2fr_0.8fr]">
				<PlatformSectionCard
					title="链路态势"
					action={<Button size="small" onClick={() => navigate("/catalog/lineage/graph")}>血缘图谱</Button>}
				>
					<div className="grid gap-3 md:grid-cols-5">
						{stages.map((stage) => (
							<button
								key={stage.key}
								type="button"
								onClick={() => navigate(stage.path)}
								className="rounded-lg border border-border/70 bg-background p-4 text-left transition hover:border-primary/60 hover:bg-primary/5"
							>
								<div className="mb-3 flex items-center justify-between gap-2">
									<span className="text-sm font-medium text-foreground">{stage.title}</span>
									<Tag color={stage.status === "warning" ? "orange" : stage.status === "processing" ? "blue" : stage.status === "success" ? "green" : "default"}>
										{stage.count}
									</Tag>
								</div>
								<Progress
									percent={stage.status === "warning" ? 68 : stage.status === "processing" ? 46 : 100}
									showInfo={false}
									status={stage.status === "warning" ? "exception" : stage.status === "processing" ? "active" : "success"}
								/>
							</button>
						))}
					</div>
				</PlatformSectionCard>

				<PlatformSectionCard
					title="运行诊断"
					action={<Button size="small" onClick={() => navigate("/explore/etl/transform")}>任务列表</Button>}
				>
					{observability.failureTop?.length ? (
						<Timeline
							items={observability.failureTop.slice(0, 5).map((item) => ({
								color: item.count > 0 ? "red" : "green",
								children: (
									<div className="flex items-center justify-between gap-3">
										<Typography.Text>{buildFailureName(item.category)}</Typography.Text>
										<Tag color="red">{item.count}</Tag>
									</div>
								),
							}))}
						/>
					) : (
						<div className="flex min-h-32 items-center justify-center rounded-lg border border-dashed border-border/70 text-sm text-muted-foreground">
							暂无失败分类
						</div>
					)}
				</PlatformSectionCard>
			</div>

			<div className="grid gap-4 xl:grid-cols-[0.9fr_1.1fr]">
				<PlatformSectionCard title="治理闭环">
					<Space direction="vertical" size={12} className="w-full">
						<Button block icon={<Boxes className="h-4 w-4" />} onClick={() => navigate("/explore/etl/orchestration")}>
							编排与调度
						</Button>
						<Button block icon={<CheckCircle2 className="h-4 w-4" />} onClick={() => navigate("/governance/quality")}>
							质量门禁
						</Button>
						<Button block icon={<GitBranch className="h-4 w-4" />} onClick={() => navigate("/catalog/lineage/impact")}>
							影响分析
						</Button>
					</Space>
				</PlatformSectionCard>

				<PlatformSectionCard title="资产链路" bodyClassName="pt-0">
					<Table
						rowKey="key"
						size="small"
						columns={chainColumns}
						dataSource={chainItems}
						pagination={false}
					/>
				</PlatformSectionCard>
			</div>
		</div>
	);
}
