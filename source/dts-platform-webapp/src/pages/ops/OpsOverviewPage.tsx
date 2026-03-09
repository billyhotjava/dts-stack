import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import type { ApexOptions } from "apexcharts";
import { Alert, Button, Card, Col, Empty, InputNumber, Row, Select, Space, Statistic, Table, Tabs, Tag, Typography } from "antd";
import { ReloadOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import { Chart } from "@/components/chart/chart";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import opsService, {
	type OpsAlert,
	type OpsDevCenterMetrics,
	type OpsDevCenterTopFailure,
	type OpsOverview,
} from "@/api/services/opsService";
import {
	ingestionTaskAPI,
	type IngestionTaskDTO,
	type IngestionGovernanceOverviewDTO,
	type IngestionExecutionObservabilityDTO,
} from "@/api/ingestion";

// ─── Dev-center table columns ────────────────────────────────────────
const alertColumns: ColumnsType<OpsAlert> = [
	{ title: "类型", dataIndex: "type", width: 120 },
	{ title: "规则", dataIndex: "ruleName", render: (value) => value || "-" },
	{ title: "状态", dataIndex: "status", width: 120 },
	{ title: "严重性", dataIndex: "severity", width: 120, render: (value) => value || "-" },
	{ title: "描述", dataIndex: "message", render: (value) => value || "-" },
];

const topFailureColumns: ColumnsType<OpsDevCenterTopFailure> = [
	{ title: "入口", dataIndex: "entryKey", width: 160, render: (value) => value || "-" },
	{ title: "项目空间", dataIndex: "planName", width: 180, render: (value) => value || "-" },
	{ title: "作业", dataIndex: "artifactName", render: (value) => value || "-" },
	{ title: "总运行", dataIndex: "totalRuns", width: 110, align: "right" },
	{ title: "失败数", dataIndex: "failedRuns", width: 110, align: "right" },
	{ title: "失败率", dataIndex: "failureRate", width: 120, align: "right", render: (value) => (value == null ? "-" : `${value}%`) },
];

// ─── Ingestion observability table columns ───────────────────────────
const ingestionTrendColumns = [
	{ title: "日期", dataIndex: "day", key: "day", width: 120 },
	{ title: "总执行", dataIndex: "total", key: "total", width: 90 },
	{ title: "成功", dataIndex: "success", key: "success", width: 90 },
	{ title: "失败", dataIndex: "failed", key: "failed", width: 90 },
	{ title: "超时", dataIndex: "timeout", key: "timeout", width: 90 },
];

const sourceLoadColumns = [
	{
		title: "来源数据源",
		dataIndex: "sourceDataSourceId",
		key: "sourceDataSourceId",
		render: (value: string | undefined) => value || "N/A",
	},
	{
		title: "来源类型",
		dataIndex: "sourceType",
		key: "sourceType",
		render: (value: string | undefined) => value || "unknown",
	},
	{ title: "运行中", dataIndex: "running", key: "running", width: 100 },
	{ title: "排队中", dataIndex: "preparing", key: "preparing", width: 100 },
];

const projectLoadColumns = [
	{ title: "项目标识", dataIndex: "projectKey", key: "projectKey" },
	{ title: "运行中", dataIndex: "running", key: "running", width: 100 },
	{ title: "排队中", dataIndex: "preparing", key: "preparing", width: 100 },
];

// ─── Component ───────────────────────────────────────────────────────
export default function OpsOverviewPage() {
	// ── Dev-center state ──
	const [overview, setOverview] = useState<OpsOverview | null>(null);
	const [metrics, setMetrics] = useState<OpsDevCenterMetrics | null>(null);
	const [alerts, setAlerts] = useState<OpsAlert[]>([]);
	const [days, setDays] = useState<number>(7);
	const [entryKey, setEntryKey] = useState<string | undefined>(undefined);
	const [ownerDept, setOwnerDept] = useState<string | undefined>(undefined);
	const [planId, setPlanId] = useState<string | undefined>(undefined);
	const [loading, setLoading] = useState(false);

	// ── Ingestion observability state ──
	const [governanceOverview, setGovernanceOverview] = useState<IngestionGovernanceOverviewDTO | null>(null);
	const [governanceLoading, setGovernanceLoading] = useState(false);
	const [governanceHours, setGovernanceHours] = useState<number>(24);
	const [observability, setObservability] = useState<IngestionExecutionObservabilityDTO | null>(null);
	const [observabilityLoading, setObservabilityLoading] = useState(false);
	const [obsTaskId, setObsTaskId] = useState<number | undefined>(undefined);
	const [obsSourceType, setObsSourceType] = useState<string | undefined>(undefined);
	const [obsDays, setObsDays] = useState<number>(7);
	const [obsTimeoutMinutes, setObsTimeoutMinutes] = useState<number>(10);
	const [ingestionTasks, setIngestionTasks] = useState<IngestionTaskDTO[]>([]);

	// ── Dev-center data loading ──
	const loadData = async () => {
		setLoading(true);
		try {
			const [summary, alertList, metricPayload] = await Promise.all([
				opsService.overview(),
				opsService.alerts({ limit: 20 }),
				opsService.devCenterMetrics({ days, entryKey, ownerDept, planId }),
			]);
			setOverview(summary as OpsOverview);
			setMetrics(metricPayload as OpsDevCenterMetrics);
			setAlerts(Array.isArray(alertList) ? (alertList as OpsAlert[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "运维概览加载失败");
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadData();
	}, [days, entryKey, ownerDept, planId]);

	// ── Ingestion data loading ──
	const loadIngestionTasks = async () => {
		try {
			const result = await ingestionTaskAPI.getTasks({ size: 200 });
			setIngestionTasks(Array.isArray(result?.content) ? result.content : []);
		} catch {
			setIngestionTasks([]);
		}
	};

	const loadGovernanceOverview = async () => {
		setGovernanceLoading(true);
		try {
			const result = await ingestionTaskAPI.getGovernanceOverview({ hours: governanceHours });
			setGovernanceOverview(result);
		} catch {
			setGovernanceOverview(null);
		} finally {
			setGovernanceLoading(false);
		}
	};

	const loadObservability = async () => {
		setObservabilityLoading(true);
		try {
			const result = await ingestionTaskAPI.getExecutionsObservability({
				taskId: obsTaskId,
				sourceType: obsSourceType || undefined,
				days: obsDays,
				timeoutMinutes: obsTimeoutMinutes,
			});
			setObservability(result);
		} catch {
			setObservability(null);
		} finally {
			setObservabilityLoading(false);
		}
	};

	useEffect(() => {
		void loadIngestionTasks();
		void loadGovernanceOverview();
	}, []);

	useEffect(() => {
		void loadGovernanceOverview();
	}, [governanceHours]);

	useEffect(() => {
		void loadObservability();
	}, [obsTaskId, obsSourceType, obsDays, obsTimeoutMinutes]);

	// ── Derived data ──
	const trend = metrics?.trend ?? [];
	const topFailures = metrics?.topFailures ?? [];
	const ownerDeptOptions = (metrics?.availableOwnerDepts ?? []).map((dept) => ({ label: dept, value: dept }));
	const planOptions = (metrics?.availablePlans ?? []).map((plan) => ({
		label: plan.name ? `${plan.name} (${plan.id.slice(0, 8)})` : plan.id,
		value: plan.id,
	}));

	const taskOptions = ingestionTasks
		.filter((item) => typeof item.id === "number")
		.map((item) => ({ label: item.name, value: Number(item.id) }));
	const sourceTypeOptions = Array.from(
		new Set(ingestionTasks.map((item) => String(item.sourceType || "").trim()).filter(Boolean)),
	).map((item) => ({ label: item, value: item }));

	const trendSeries = useMemo(
		() => [
			{ name: "总运行", data: trend.map((item) => item.totalRuns ?? 0) },
			{ name: "成功", data: trend.map((item) => item.successRuns ?? 0) },
			{ name: "失败", data: trend.map((item) => item.failedRuns ?? 0) },
		],
		[trend],
	);

	const trendOptions = useMemo<ApexOptions>(
		() => ({
			chart: { type: "line", toolbar: { show: false } },
			stroke: { curve: "smooth", width: 2 },
			markers: { size: 3 },
			xaxis: { categories: trend.map((item) => item.date) },
			yaxis: { min: 0, forceNiceScale: true },
			legend: { position: "top", horizontalAlign: "left" },
			colors: ["#1677ff", "#52c41a", "#ff4d4f"],
			grid: { borderColor: "#f0f0f0" },
		}),
		[trend],
	);

	// ── Tab: 任务运行概览 (original content) ──
	const devCenterTab = (
		<div className="space-y-6">
			<Row gutter={[16, 16]} className="mb-2">
				<Col xs={24} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="近24小时任务总数" value={overview?.totalRuns ?? "--"} />
					</Card>
				</Col>
				<Col xs={24} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="成功率" value={overview?.successRate ?? "--"} suffix="%" />
					</Card>
				</Col>
				<Col xs={24} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="异常告警" value={overview?.alerts ?? "--"} />
					</Card>
				</Col>
				<Col xs={24} sm={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="正在运行" value={overview?.running ?? "--"} />
					</Card>
				</Col>
			</Row>

			<Row gutter={[16, 16]}>
				<Col xs={24} md={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="失败率" value={metrics?.summary?.failureRate ?? "--"} suffix="%" />
					</Card>
				</Col>
				<Col xs={24} md={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="重试率" value={metrics?.summary?.retryRate ?? "--"} suffix="%" />
					</Card>
				</Col>
				<Col xs={24} md={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="MTTR(分钟)" value={metrics?.summary?.mttrMinutes ?? "--"} />
					</Card>
				</Col>
				<Col xs={24} md={12} lg={6}>
					<Card className="rounded-xl shadow-sm">
						<Statistic title="发布成功率" value={metrics?.summary?.releaseSuccessRate ?? "--"} suffix="%" />
					</Card>
				</Col>
			</Row>

			<Card
				title="任务执行趋势（开发中心）"
				extra={
					<Space wrap>
						<Select
							style={{ width: 120 }}
							value={days}
							onChange={(value) => setDays(value)}
							options={[
								{ label: "近7天", value: 7 },
								{ label: "近14天", value: 14 },
								{ label: "近30天", value: 30 },
							]}
						/>
						<Select
							style={{ width: 160 }}
							allowClear
							placeholder="入口类型"
							value={entryKey}
							onChange={(value) => setEntryKey(value || undefined)}
							options={[
								{ label: "INGESTION_TASK", value: "INGESTION_TASK" },
								{ label: "AIRFLOW_DAG", value: "AIRFLOW_DAG" },
								{ label: "DBT_RUN", value: "DBT_RUN" },
							]}
						/>
						<Select
							style={{ width: 200 }}
							allowClear
							placeholder="所属部门"
							value={ownerDept}
							onChange={(value) => setOwnerDept(value || undefined)}
							options={ownerDeptOptions}
						/>
						<Select
							style={{ width: 260 }}
							allowClear
							showSearch
							placeholder="项目空间"
							value={planId}
							onChange={(value) => setPlanId(value || undefined)}
							options={planOptions}
							optionFilterProp="label"
						/>
					</Space>
				}
			>
				{trend.length ? (
					<Chart type="line" height={320} options={trendOptions} series={trendSeries} />
				) : (
					<EmptyState title="暂无趋势数据" description="当前筛选条件下没有可用运行数据。" />
				)}
			</Card>

			<Card title="失败作业 Top 10">
				<Table
					size="small"
					pagination={false}
					dataSource={topFailures}
					columns={topFailureColumns}
					rowKey={(row, idx) => `${row.entryKey || "unknown"}-${row.artifactName || "artifact"}-${idx}`}
					loading={loading}
					locale={{ emptyText: <EmptyState title="暂无失败作业" description="当前时间窗口内无失败记录。" /> }}
				/>
				<Typography.Text type="secondary">
					口径：失败率 = 失败数 / 总运行；重试率与 MTTR 基于同入口同作业（含项目空间）的连续运行记录估算。
				</Typography.Text>
			</Card>

			<Card title="告警概览">
				{alerts.length ? (
					<Table
						size="small"
						pagination={false}
						dataSource={alerts}
						columns={alertColumns}
						rowKey={(row, idx) => `${row.type}-${idx}`}
						loading={loading}
					/>
				) : (
					<EmptyState title="暂无告警" description="当前没有质量或任务告警。" />
				)}
			</Card>
		</div>
	);

	// ── Tab: 入湖可观测性 (migrated from TransformPage) ──
	const ingestionTab = (
		<div className="space-y-6">
			<Card
				size="small"
				title="资源与配额治理"
				loading={governanceLoading}
				extra={
					<Space wrap>
						<Select
							style={{ width: 140 }}
							value={governanceHours}
							options={[
								{ label: "最近 6 小时", value: 6 },
								{ label: "最近 24 小时", value: 24 },
								{ label: "最近 72 小时", value: 72 },
								{ label: "最近 168 小时", value: 168 },
							]}
							onChange={(value) => setGovernanceHours(value)}
						/>
						<Button
							className="rounded-2xl"
							icon={<ReloadOutlined />}
							onClick={() => void loadGovernanceOverview()}
							loading={governanceLoading}
						>
							刷新治理
						</Button>
					</Space>
				}
			>
				{governanceOverview ? (
					<Space direction="vertical" size={16} style={{ width: "100%" }}>
						{governanceOverview.blockedByPolicy > 0 ? (
							<Alert
								type="warning"
								showIcon
								message={`最近窗口内发生 ${governanceOverview.blockedByPolicy} 次治理拒绝`}
								description="建议检查任务并发上限、来源并发上限和执行窗口配置，必要时拆分批次或下调调度频率。"
							/>
						) : null}
						<Row gutter={[16, 16]}>
							<Col xs={12} md={6}><Statistic title="运行中" value={governanceOverview.running || 0} /></Col>
							<Col xs={12} md={6}><Statistic title="排队中" value={governanceOverview.preparing || 0} /></Col>
							<Col xs={12} md={6}><Statistic title="队列长度" value={governanceOverview.queueLength || 0} /></Col>
							<Col xs={12} md={6}><Statistic title="策略拒绝数" value={governanceOverview.blockedByPolicy || 0} /></Col>
							<Col xs={12} md={6}><Statistic title="平均耗时(秒)" value={governanceOverview.avgExecutionSeconds || 0} precision={2} /></Col>
							<Col xs={12} md={6}><Statistic title="平均排队(秒)" value={governanceOverview.avgQueueWaitSeconds || 0} precision={2} /></Col>
							<Col xs={12} md={6}><Statistic title="最长排队(秒)" value={governanceOverview.maxQueueWaitSeconds || 0} precision={2} /></Col>
						</Row>
						<Table
							size="small"
							rowKey={(record) => `${record.sourceDataSourceId || "none"}-${record.sourceType || "unknown"}`}
							pagination={false}
							columns={sourceLoadColumns}
							dataSource={governanceOverview.sourceLoads || []}
							locale={{ emptyText: "暂无来源负载数据" }}
						/>
						<Table
							size="small"
							rowKey={(record) => record.projectKey || "default"}
							pagination={false}
							columns={projectLoadColumns}
							dataSource={governanceOverview.projectLoads || []}
							locale={{ emptyText: "暂无项目负载数据" }}
						/>
					</Space>
				) : (
					<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无治理指标" />
				)}
			</Card>

			<Card
				size="small"
				title="执行可观测性"
				extra={
					<Button
						className="rounded-2xl"
						icon={<ReloadOutlined />}
						onClick={() => void loadObservability()}
						loading={observabilityLoading}
					>
						刷新指标
					</Button>
				}
			>
				<div className="mb-4 flex flex-wrap items-center gap-3">
					<Select
						allowClear
						placeholder="按任务过滤"
						style={{ width: 220 }}
						value={obsTaskId}
						options={taskOptions}
						onChange={(value) => setObsTaskId(value)}
					/>
					<Select
						allowClear
						placeholder="按来源类型过滤"
						style={{ width: 200 }}
						value={obsSourceType}
						options={sourceTypeOptions}
						onChange={(value) => setObsSourceType(value)}
					/>
					<Select
						style={{ width: 150 }}
						value={obsDays}
						options={[
							{ label: "最近 1 天", value: 1 },
							{ label: "最近 7 天", value: 7 },
							{ label: "最近 30 天", value: 30 },
							{ label: "最近 90 天", value: 90 },
						]}
						onChange={(value) => setObsDays(value)}
					/>
					<Space size={4}>
						<span>超时阈值(分钟)</span>
						<InputNumber
							min={1}
							max={1440}
							value={obsTimeoutMinutes}
							onChange={(value) => setObsTimeoutMinutes(Number(value || 10))}
						/>
					</Space>
				</div>
				{observability ? (
					<Space direction="vertical" size={16} style={{ width: "100%" }}>
						<Row gutter={[16, 16]}>
							<Col xs={12} md={6}><Statistic title="总执行数" value={observability.total || 0} /></Col>
							<Col xs={12} md={6}><Statistic title="成功率" value={observability.successRate || 0} suffix="%" precision={2} /></Col>
							<Col xs={12} md={6}><Statistic title="超时率" value={observability.timeoutRate || 0} suffix="%" precision={2} /></Col>
							<Col xs={12} md={6}><Statistic title="平均耗时(秒)" value={observability.avgDurationSeconds || 0} precision={2} /></Col>
							<Col xs={12} md={6}><Statistic title="MTTR(秒)" value={observability.mttrSeconds || 0} precision={2} /></Col>
							<Col xs={12} md={6}><Statistic title="运行中" value={observability.running || 0} /></Col>
							<Col xs={12} md={6}><Statistic title="失败数" value={observability.failed || 0} /></Col>
							<Col xs={12} md={6}><Statistic title="超时数" value={observability.timeout || 0} /></Col>
						</Row>
						<Row gutter={[16, 16]}>
							<Col xs={24} lg={10}>
								<Card size="small" title="失败分类 Top5">
									<Space wrap>
										{(observability.failureTop || []).length ? (
											observability.failureTop.map((item) => (
												<Tag color="error" key={item.category}>
													{item.category}: {item.count}
												</Tag>
											))
										) : (
											<Tag>暂无失败数据</Tag>
										)}
									</Space>
								</Card>
							</Col>
							<Col xs={24} lg={14}>
								<Card size="small" title="日趋势">
									{(observability.trend || []).length ? (
										<Table size="small" rowKey="day" pagination={false} columns={ingestionTrendColumns} dataSource={observability.trend} />
									) : (
										<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前时间窗没有执行数据" />
									)}
								</Card>
							</Col>
						</Row>
					</Space>
				) : (
					<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无运行指标" />
				)}
			</Card>
		</div>
	);

	return (
		<div className="mx-auto w-full max-w-none space-y-6 px-6 py-6">
			<PageHeader title="任务运行概览" />
			<Tabs
				defaultActiveKey="devCenter"
				items={[
					{ key: "devCenter", label: "任务运行概览", children: devCenterTab },
					{ key: "ingestion", label: "入湖可观测性", children: ingestionTab },
				]}
			/>
		</div>
	);
}
