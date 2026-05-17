import { useEffect, useMemo, useState } from "react";
import { Button, Progress, Space, Tag, Timeline, Typography } from "antd";
import { CompactTable, RecordDetailDrawer, appendDetailAction } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { BarChart3, BookOpenCheck, Boxes, CheckCircle2, Database, GitBranch, RadioTower, RefreshCw, Rocket } from "lucide-react";
import { useNavigate } from "react-router";
import {
	PlatformPageHero,
	PlatformSectionCard,
	PlatformSummaryCards,
} from "@/components/console-page";
import {
	getSprint27MetricOperations,
	type Sprint27SourceStatus,
} from "@/api/platformApi";
import {
	type SemanticBusinessObject,
	type SemanticMetric,
	type SemanticModel,
	type SemanticModelRun,
	type SemanticSubjectDomain,
} from "@/api/semanticModelingApi";

type IndicatorOpsOverview = {
	hours: number;
	total: number;
	published: number;
	draft: number;
	archived: number;
	validationSuccess: number;
	validationFailed: number;
	validationNever: number;
	successRate: number;
	failedRate: number;
	failureTop: Array<{ category?: string; count?: number }>;
};

type ConsumptionItem = {
	key: string;
	name: string;
	layer: string;
	owner: string;
	status: "success" | "warning" | "processing";
	path: string;
};

const EMPTY_OVERVIEW: IndicatorOpsOverview = {
	hours: 168,
	total: 0,
	published: 0,
	draft: 0,
	archived: 0,
	validationSuccess: 0,
	validationFailed: 0,
	validationNever: 0,
	successRate: 0,
	failedRate: 0,
	failureTop: [],
};

const normalizeList = <T,>(value: any): T[] => {
	if (Array.isArray(value)) return value;
	if (Array.isArray(value?.content)) return value.content;
	if (Array.isArray(value?.data)) return value.data;
	return [];
};

const normalizeOverview = (payload: any): IndicatorOpsOverview => {
	const statusDist = payload?.statusDistribution || {};
	const validation = payload?.validation || {};
	return {
		hours: Number(payload?.hours ?? EMPTY_OVERVIEW.hours),
		total: Number(payload?.totalIndicators ?? payload?.total ?? 0),
		published: Number(statusDist?.published ?? 0),
		draft: Number(statusDist?.draft ?? 0),
		archived: Number(statusDist?.archived ?? 0),
		validationSuccess: Number(validation?.success ?? 0),
		validationFailed: Number(validation?.failed ?? 0),
		validationNever: Number(validation?.never ?? 0),
		successRate: Number(validation?.successRate ?? 0),
		failedRate: Number(validation?.failedRate ?? 0),
		failureTop: Array.isArray(payload?.failureTop) ? payload.failureTop : [],
	};
};

const percent = (value?: number) => `${Math.round((Number(value) || 0) * 100)}%`;

const formatFailureName = (category?: string) => {
	const labels: Record<string, string> = {
		DATASET_MISSING: "数据集缺失",
		PERMISSION_DENIED: "权限不足",
		FORMULA_INVALID: "公式异常",
		PUBLISH_BLOCKED: "发布阻断",
		VALIDATION_FAILED: "校验失败",
		UNKNOWN: "未分类",
	};
	const key = String(category || "UNKNOWN").toUpperCase();
	return labels[key] || category || "未分类";
};

export default function MetricOperationsPage() {
	const navigate = useNavigate();
	const [loading, setLoading] = useState(false);
	const [overview, setOverview] = useState<IndicatorOpsOverview>(EMPTY_OVERVIEW);
	const [trendRows, setTrendRows] = useState<any[]>([]);
	const [domains, setDomains] = useState<SemanticSubjectDomain[]>([]);
	const [objects, setObjects] = useState<SemanticBusinessObject[]>([]);
	const [metrics, setMetrics] = useState<SemanticMetric[]>([]);
	const [models, setModels] = useState<SemanticModel[]>([]);
	const [runs, setRuns] = useState<SemanticModelRun[]>([]);
	const [sources, setSources] = useState<Record<string, Sprint27SourceStatus>>({});
	const [detailRow, setDetailRow] = useState<ConsumptionItem | null>(null);

	const loadSnapshot = async () => {
		setLoading(true);
		try {
			const snapshot = await getSprint27MetricOperations({ hours: 168, bucketHours: 24 });
			const nextModels = normalizeList<SemanticModel>(snapshot?.models);
			setOverview(snapshot?.overview ? normalizeOverview(snapshot.overview) : EMPTY_OVERVIEW);
			setTrendRows(normalizeList<any>(snapshot?.trendRows));
			setDomains(normalizeList<SemanticSubjectDomain>(snapshot?.domains));
			setObjects(normalizeList<SemanticBusinessObject>(snapshot?.objects));
			setMetrics(normalizeList<SemanticMetric>(snapshot?.metrics));
			setModels(nextModels);
			setRuns(normalizeList<SemanticModelRun>(snapshot?.runs).slice(0, 8));
			setSources(snapshot?.sources || {});
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadSnapshot();
	}, []);

	const publishedRate = overview.total ? overview.published / overview.total : 0;
	const semanticReadyRate = metrics.length ? models.length / Math.max(metrics.length, 1) : 0;
	const failedRuns = runs.filter((run) => ["FAILED", "ERROR", "TIMEOUT"].includes(String(run.status || "").toUpperCase())).length;

	const summaryCards = [
		{
			label: "指标总数",
			value: overview.total,
			note: `发布率 ${percent(publishedRate)}`,
			icon: <BarChart3 className="h-5 w-5" />,
			tone: "info" as const,
		},
		{
			label: "语义指标",
			value: metrics.length,
			note: `${domains.length} 个主题域，${objects.length} 个业务对象`,
			icon: <BookOpenCheck className="h-5 w-5" />,
		},
		{
			label: "可消费模型",
			value: models.length,
			note: `模型覆盖 ${percent(Math.min(semanticReadyRate, 1))}`,
			icon: <Database className="h-5 w-5" />,
			tone: "success" as const,
		},
		{
			label: "校验失败",
			value: overview.validationFailed + failedRuns,
			note: `指标失败率 ${percent(overview.failedRate)}`,
			icon: <CheckCircle2 className="h-5 w-5" />,
			tone: overview.validationFailed || failedRuns ? "warning" as const : "success" as const,
		},
	];

	const flowItems = [
		{ key: "dictionary", title: "指标字典", count: overview.total, path: "/bi-apps/metrics/dictionary", status: overview.draft ? "warning" : "success" },
		{ key: "semantic", title: "语义建模", count: metrics.length, path: "/bi-apps/metrics/semantic", status: metrics.length ? "success" : "default" },
		{ key: "models", title: "DWS/ADS", count: models.length, path: "/bi-apps/metrics/semantic/models", status: models.length ? "success" : "default" },
		{ key: "publish", title: "发布治理", count: overview.published, path: "/bi-apps/metrics/publish", status: overview.published ? "success" : "default" },
		{ key: "consume", title: "BI 消费", count: trendRows.length, path: "/bi/metrics", status: failedRuns ? "warning" : "success" },
	];

	const consumptionItems = useMemo<ConsumptionItem[]>(
		() => [
			{ key: "dictionary", name: "指标字典", layer: "Governance", owner: "dts-platform", status: overview.validationFailed ? "warning" : "success", path: "/bi-apps/metrics/dictionary" },
			{ key: "semantic", name: "语义模型", layer: "Semantic", owner: "dts-platform", status: metrics.length ? "success" : "warning", path: "/bi-apps/metrics/semantic" },
			{ key: "dataset", name: "公共数据集", layer: "DWS/ADS", owner: "dts-platform", status: models.length ? "success" : "warning", path: "/bi-apps/metrics/semantic/models" },
			{ key: "analytics", name: "BI 指标消费", layer: "BI", owner: "dts-analytics", status: failedRuns ? "warning" : "success", path: "/bi/metrics" },
		],
		[failedRuns, metrics.length, models.length, overview.validationFailed],
	);

	const consumptionBaseColumns: ColumnsType<ConsumptionItem> = [
		{ title: "能力", dataIndex: "name", key: "name" , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
		{ title: "层级", dataIndex: "layer", key: "layer", width: 110 },
		{ title: "责任域", dataIndex: "owner", key: "owner", width: 150 },
		{
			title: "状态",
			dataIndex: "status",
			key: "status",
			width: 110,
			render: (status: ConsumptionItem["status"]) => {
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
			dataIndex: "action",
			key: "action",
			width: 160,
			fixed: "right",
			render: (_, record) => <Button type="link" size="small" onClick={() => navigate(record.path)}>进入</Button>,
		},
	];

	const consumptionColumns = useMemo(
		() => appendDetailAction(consumptionBaseColumns, (row) => setDetailRow(row)),
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[],
	);

	return (
		<div className="space-y-6">
			<PlatformPageHero
				title="指标运营台"
				actions={
					<Space wrap>
						<Button icon={<RadioTower className="h-4 w-4" />} onClick={() => navigate("/ops/events")}>
							事件观测
						</Button>
						<Button onClick={() => navigate("/ops/release-governance")}>
							发布治理
						</Button>
						<Button icon={<RefreshCw className="h-4 w-4" />} loading={loading} onClick={() => void loadSnapshot()}>
							刷新
						</Button>
						<Button type="primary" onClick={() => navigate("/bi-apps/metrics/dictionary")}>
							管理指标
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

			<div className="grid gap-4 xl:grid-cols-[1.2fr_0.8fr]">
				<PlatformSectionCard
					title="指标链路"
					action={<Button size="small" onClick={() => navigate("/bi-apps/metrics/semantic")}>语义建模</Button>}
				>
					<div className="grid gap-3 md:grid-cols-5">
						{flowItems.map((item) => (
							<button
								key={item.key}
								type="button"
								onClick={() => navigate(item.path)}
								className="rounded-lg border border-border/70 bg-background p-4 text-left transition hover:border-primary/60 hover:bg-primary/5"
							>
								<div className="mb-3 flex items-center justify-between gap-2">
									<span className="text-sm font-medium text-foreground">{item.title}</span>
									<Tag color={item.status === "warning" ? "orange" : item.status === "success" ? "green" : "default"}>
										{item.count}
									</Tag>
								</div>
								<Progress
									percent={item.status === "warning" ? 64 : item.status === "success" ? 100 : 30}
									showInfo={false}
									status={item.status === "warning" ? "exception" : item.status === "success" ? "success" : "normal"}
								/>
							</button>
						))}
					</div>
				</PlatformSectionCard>

				<PlatformSectionCard
					title="异常分布"
					action={<Button size="small" onClick={() => navigate("/bi-apps/metrics/dictionary")}>校验记录</Button>}
				>
					{overview.failureTop.length ? (
						<Timeline
							items={overview.failureTop.slice(0, 5).map((item) => ({
								color: Number(item.count || 0) > 0 ? "red" : "green",
								children: (
									<div className="flex items-center justify-between gap-3">
										<Typography.Text>{formatFailureName(item.category)}</Typography.Text>
										<Tag color="red">{item.count || 0}</Tag>
									</div>
								),
							}))}
						/>
					) : (
						<div className="flex min-h-32 items-center justify-center rounded-lg border border-dashed border-border/70 text-sm text-muted-foreground">
							暂无异常分类
						</div>
					)}
				</PlatformSectionCard>
			</div>

			<div className="grid gap-4 xl:grid-cols-[0.9fr_1.1fr]">
				<PlatformSectionCard title="发布闭环">
					<Space direction="vertical" size={12} className="w-full">
						<Button block icon={<Boxes className="h-4 w-4" />} onClick={() => navigate("/bi-apps/metrics/semantic/metrics")}>
							指标配置
						</Button>
						<Button block icon={<Rocket className="h-4 w-4" />} onClick={() => navigate("/bi-apps/metrics/publish")}>
							审核发布
						</Button>
						<Button block icon={<GitBranch className="h-4 w-4" />} onClick={() => navigate("/catalog/lineage/impact")}>
							血缘影响
						</Button>
						<Button block icon={<RadioTower className="h-4 w-4" />} onClick={() => navigate("/ops/audit-evidence")}>
							审计证据链
						</Button>
					</Space>
				</PlatformSectionCard>

				<PlatformSectionCard title="消费链路" bodyClassName="pt-0">
					<CompactTable<ConsumptionItem>
						rowKey="key"
						size="small"
						columns={consumptionColumns}
						dataSource={consumptionItems}
						pagination={false}
					/>
				</PlatformSectionCard>
			</div>
			<RecordDetailDrawer<ConsumptionItem>
				open={detailRow !== null}
				onClose={() => setDetailRow(null)}
				record={detailRow}
				columns={consumptionBaseColumns}
				title="消费链路详情"
			/>
		</div>
	);
}
