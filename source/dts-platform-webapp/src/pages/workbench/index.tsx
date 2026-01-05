import {
	AlertOutlined,
	ApartmentOutlined,
	BarChartOutlined,
	CheckCircleOutlined,
	ClockCircleOutlined,
	DatabaseOutlined,
	ExclamationCircleOutlined,
	FlagOutlined,
	LineChartOutlined,
	MonitorOutlined,
	ProfileOutlined,
	ProjectOutlined,
	SafetyCertificateOutlined,
	SettingOutlined,
	ThunderboltOutlined,
} from "@ant-design/icons";
import {
	Badge,
	Button,
	Card,
	Col,
	Collapse,
	List,
	Progress,
	Row,
	Space,
	Statistic,
	Table,
	Tag,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { useMemo } from "react";
import { Chart } from "@/components/chart/chart";
import { useChart } from "@/components/chart/useChart";
import { useRouter } from "@/routes/hooks";

type KpiTone = "success" | "warning" | "danger" | "default";

type KpiItem = {
	key: string;
	title: string;
	value: number;
	suffix?: string;
	prefix?: React.ReactNode;
	tone?: KpiTone;
	change?: { value: number; label: string };
	onClick?: () => void;
};

type TrendPoint = { date: string; business: number; dqScore: number; failedJobs: number };

type QualityIssue = {
	key: string;
	dataset: string;
	rule: string;
	issueType: string;
	impactRows: number;
	severity: "P0" | "P1" | "P2";
	owner: string;
	status: "OPEN" | "ACK" | "FIXING";
	occurredAt: string;
};

type HotAsset = {
	key: string;
	name: string;
	kind: "数据集" | "报表" | "指标";
	views: number;
	updatedAt: string;
	status: "OK" | "STALE" | "ERROR";
};

type HotReport = {
	key: string;
	name: string;
	views: number;
	updatedAt: string;
	series: number[];
};

type WorkItem = {
	key: string;
	kind: "告警" | "待办";
	title: string;
	level: "高" | "中" | "低";
	time: string;
	target: string;
};

const WINDOW_DAYS: 7 | 14 | 30 = 14;

const toneColor = (tone: KpiTone | undefined): string => {
	if (tone === "success") return "#52c41a";
	if (tone === "warning") return "#faad14";
	if (tone === "danger") return "#ff4d4f";
	return "#1677ff";
};

const severityTag = (severity: QualityIssue["severity"]) => {
	if (severity === "P0") return <Tag color="red">P0</Tag>;
	if (severity === "P1") return <Tag color="orange">P1</Tag>;
	return <Tag color="gold">P2</Tag>;
};

const statusTag = (status: QualityIssue["status"]) => {
	if (status === "OPEN") return <Tag color="red">未处理</Tag>;
	if (status === "ACK") return <Tag color="blue">已确认</Tag>;
	return <Tag color="purple">修复中</Tag>;
};

const assetStatusTag = (status: HotAsset["status"]) => {
	if (status === "OK") return <Tag color="green">正常</Tag>;
	if (status === "STALE") return <Tag color="gold">待更新</Tag>;
	return <Tag color="red">异常</Tag>;
};

export default function WorkbenchPage() {
	const router = useRouter();
	const today = useMemo(() => dayjs(), []);

	const trend = useMemo<TrendPoint[]>(() => {
		const points: TrendPoint[] = [];
		for (let i = WINDOW_DAYS - 1; i >= 0; i -= 1) {
			const d = today.subtract(i, "day");
			const t = WINDOW_DAYS - 1 - i;
			points.push({
				date: d.format("MM-DD"),
				business: 980 + t * 12 + (t % 3) * 28 + (t % 5) * 7,
				dqScore: Math.max(60, Math.min(99, 92 - (t % 7) * 2 + (t % 4))),
				failedJobs: Math.max(0, 6 - (t % 6) + (t % 2)),
			});
		}
		return points;
	}, [today]);

	const distribution = useMemo(() => ({ labels: ["ODS", "DWD", "DWS", "ADS"], series: [18, 42, 27, 13] }), []);

	const hotReports = useMemo<HotReport[]>(
		() => [
			{
				key: "r1",
				name: "经营总览",
				views: 1823,
				updatedAt: today.subtract(1, "hour").format("YYYY-MM-DD HH:mm"),
				series: [62, 64, 63, 65, 66, 64, 68, 70, 69, 71, 72, 73],
			},
			{
				key: "r2",
				name: "财务看板（预算执行）",
				views: 934,
				updatedAt: today.subtract(6, "hour").format("YYYY-MM-DD HH:mm"),
				series: [52, 51, 53, 54, 55, 56, 57, 56, 58, 59, 60, 61],
			},
			{
				key: "r3",
				name: "供应链看板（周转与库存）",
				views: 612,
				updatedAt: today.subtract(2, "day").format("YYYY-MM-DD HH:mm"),
				series: [78, 76, 75, 77, 74, 73, 72, 74, 75, 76, 78, 79],
			},
			{
				key: "r4",
				name: "人力看板（编制与效率）",
				views: 488,
				updatedAt: today.subtract(1, "day").format("YYYY-MM-DD HH:mm"),
				series: [41, 42, 43, 44, 43, 45, 46, 47, 46, 48, 49, 50],
			},
		],
		[today],
	);

	const qualityIssues = useMemo<QualityIssue[]>(
		() => [
			{
				key: "q1",
				dataset: "dwd_sales_order",
				rule: "订单金额非空",
				issueType: "完整性",
				impactRows: 1284,
				severity: "P0",
				owner: "张三",
				status: "OPEN",
				occurredAt: today.subtract(2, "hour").format("YYYY-MM-DD HH:mm"),
			},
			{
				key: "q2",
				dataset: "dws_customer_profile",
				rule: "主键唯一性",
				issueType: "一致性",
				impactRows: 217,
				severity: "P1",
				owner: "李四",
				status: "ACK",
				occurredAt: today.subtract(5, "hour").format("YYYY-MM-DD HH:mm"),
			},
			{
				key: "q3",
				dataset: "ads_finance_kpi",
				rule: "环比波动阈值",
				issueType: "波动",
				impactRows: 36,
				severity: "P2",
				owner: "王五",
				status: "FIXING",
				occurredAt: today.subtract(1, "day").format("YYYY-MM-DD HH:mm"),
			},
			{
				key: "q4",
				dataset: "ods_hr_employee",
				rule: "入职日期合法",
				issueType: "规范",
				impactRows: 12,
				severity: "P2",
				owner: "赵六",
				status: "OPEN",
				occurredAt: today.subtract(2, "day").format("YYYY-MM-DD HH:mm"),
			},
		],
		[today],
	);

	const hotAssets = useMemo<HotAsset[]>(
		() => [
			{
				key: "h1",
				name: "销售总览",
				kind: "报表",
				views: 1823,
				updatedAt: today.subtract(1, "hour").format("YYYY-MM-DD HH:mm"),
				status: "OK",
			},
			{
				key: "h2",
				name: "客户画像数据集",
				kind: "数据集",
				views: 934,
				updatedAt: today.subtract(6, "hour").format("YYYY-MM-DD HH:mm"),
				status: "OK",
			},
			{
				key: "h3",
				name: "预算执行率",
				kind: "指标",
				views: 612,
				updatedAt: today.subtract(2, "day").format("YYYY-MM-DD HH:mm"),
				status: "STALE",
			},
			{
				key: "h4",
				name: "供应链周转分析",
				kind: "报表",
				views: 488,
				updatedAt: today.subtract(1, "day").format("YYYY-MM-DD HH:mm"),
				status: "ERROR",
			},
		],
		[today],
	);

	const workItems = useMemo<WorkItem[]>(
		() => [
			{
				key: "a1",
				kind: "告警",
				title: "SLA 超时：dws_customer_profile",
				level: "高",
				time: "10:12",
				target: "/foundation/task-scheduling",
			},
			{
				key: "a2",
				kind: "告警",
				title: "数据质量下降：ads_finance_kpi",
				level: "中",
				time: "09:20",
				target: "/governance/rules",
			},
			{
				key: "t1",
				kind: "待办",
				title: "待审核：新增质量规则 2 条",
				level: "中",
				time: "昨天",
				target: "/governance/rules",
			},
			{
				key: "t2",
				kind: "待办",
				title: "待发布：数据集 1 个（密级 INTERNAL）",
				level: "低",
				time: "2 天前",
				target: "/catalog/assets",
			},
		],
		[],
	);

	const leaderKpis = useMemo<KpiItem[]>(() => {
		const common: KpiItem[] = [
			{
				key: "dq-score",
				title: "数据质量评分",
				value: trend[trend.length - 1]?.dqScore ?? 0,
				suffix: "分",
				prefix: <SafetyCertificateOutlined />,
				tone: (trend[trend.length - 1]?.dqScore ?? 0) >= 90 ? "success" : "warning",
				change: { value: -1.2, label: "较昨日" },
				onClick: () => router.push("/governance/rules"),
			},
			{
				key: "alerts",
				title: "今日告警",
				value: 6,
				suffix: "条",
				prefix: <AlertOutlined />,
				tone: "warning",
				change: { value: 2, label: "较昨日" },
				onClick: () => router.push("/governance/rules"),
			},
			{
				key: "assets",
				title: "数据资产",
				value: 1280,
				suffix: "个",
				prefix: <DatabaseOutlined />,
				tone: "default",
				change: { value: 12, label: "本周新增" },
				onClick: () => router.push("/catalog/assets"),
			},
		];
		return [
			{
				key: "orders",
				title: "今日订单",
				value: trend[trend.length - 1]?.business ?? 0,
				suffix: "单",
				prefix: <ProjectOutlined />,
				tone: "default",
				change: { value: 3.1, label: "环比" },
				onClick: () => router.push("/visualization/reports"),
			},
			{
				key: "delivery",
				title: "交付达成率",
				value: 92.4,
				suffix: "%",
				prefix: <FlagOutlined />,
				tone: "success",
				change: { value: -0.8, label: "较昨日" },
				onClick: () => router.push("/visualization/reports"),
			},
			...common,
		];
	}, [router, trend]);

	const analystKpis = useMemo<KpiItem[]>(
		() => [
			{
				key: "hot-reports",
				title: "热门报表",
				value: 18,
				suffix: "个",
				prefix: <MonitorOutlined />,
				tone: "default",
				change: { value: 2, label: "本周" },
				onClick: () => router.push("/visualization/reports"),
			},
			{
				key: "hot-datasets",
				title: "热门数据集",
				value: 28,
				suffix: "个",
				prefix: <ApartmentOutlined />,
				tone: "default",
				change: { value: 4, label: "本周" },
				onClick: () => router.push("/catalog/assets"),
			},
			{
				key: "dq-score",
				title: "数据质量评分",
				value: trend[trend.length - 1]?.dqScore ?? 0,
				suffix: "分",
				prefix: <SafetyCertificateOutlined />,
				tone: (trend[trend.length - 1]?.dqScore ?? 0) >= 90 ? "success" : "warning",
				change: { value: -1.2, label: "较昨日" },
				onClick: () => router.push("/governance/rules"),
			},
			{
				key: "alerts",
				title: "今日告警",
				value: 6,
				suffix: "条",
				prefix: <AlertOutlined />,
				tone: "warning",
				change: { value: 2, label: "较昨日" },
				onClick: () => router.push("/governance/rules"),
			},
		],
		[router, trend],
	);

	const engineerKpis = useMemo<KpiItem[]>(
		() => [
			{
				key: "failed",
				title: "失败任务",
				value: trend[trend.length - 1]?.failedJobs ?? 0,
				suffix: "个",
				prefix: <ThunderboltOutlined />,
				tone: (trend[trend.length - 1]?.failedJobs ?? 0) > 0 ? "danger" : "success",
				change: { value: -2, label: "较昨日" },
				onClick: () => router.push("/foundation/task-scheduling"),
			},
			{
				key: "delay",
				title: "延迟任务",
				value: 3,
				suffix: "个",
				prefix: <ClockCircleOutlined />,
				tone: "warning",
				change: { value: 1, label: "较昨日" },
				onClick: () => router.push("/foundation/task-scheduling"),
			},
			{
				key: "success-rate",
				title: "调度成功率",
				value: 98.6,
				suffix: "%",
				prefix: <CheckCircleOutlined />,
				tone: "success",
				change: { value: 0.2, label: "较昨日" },
				onClick: () => router.push("/foundation/task-scheduling"),
			},
			{
				key: "dq-score",
				title: "数据质量评分",
				value: trend[trend.length - 1]?.dqScore ?? 0,
				suffix: "分",
				prefix: <SafetyCertificateOutlined />,
				tone: (trend[trend.length - 1]?.dqScore ?? 0) >= 90 ? "success" : "warning",
				change: { value: -1.2, label: "较昨日" },
				onClick: () => router.push("/governance/rules"),
			},
		],
		[router, trend],
	);

	const trendOptions = useChart({
		xaxis: { categories: trend.map((p) => p.date) },
		yaxis: [{ title: { text: "业务量" } }, { opposite: true, max: 100, min: 0, title: { text: "DQ Score" } }],
		legend: { show: true },
		stroke: { curve: "smooth", width: 2.5 },
		chart: {
			events: {
				dataPointSelection: (_e, _ctx, config) => {
					const idx = typeof config?.dataPointIndex === "number" ? config.dataPointIndex : -1;
					if (idx >= 0) router.push("/governance/rules");
				},
			},
		},
	});

	const trendSeries = useMemo(() => {
		return [
			{ name: "业务量", data: trend.map((p) => p.business) },
			{ name: "DQ Score", data: trend.map((p) => p.dqScore) },
		];
	}, [trend]);

	const donutOptions = useChart({
		labels: distribution.labels,
		legend: { show: true },
		chart: {
			events: {
				dataPointSelection: () => router.push("/catalog/assets"),
			},
		},
		plotOptions: {
			pie: {
				donut: {
					size: "68%",
				},
			},
		},
	});

	const sparklineOptions = useChart({
		chart: { sparkline: { enabled: true } as any },
		stroke: { curve: "smooth", width: 2 },
		grid: { show: false } as any,
		xaxis: { labels: { show: false }, axisBorder: { show: false }, axisTicks: { show: false } } as any,
		yaxis: { labels: { show: false } } as any,
		tooltip: { enabled: false } as any,
	});

	const issueColumns = useMemo<ColumnsType<QualityIssue>>(
		() => [
			{ title: "数据集/表", dataIndex: "dataset", key: "dataset", ellipsis: true },
			{ title: "规则", dataIndex: "rule", key: "rule", ellipsis: true },
			{ title: "类型", dataIndex: "issueType", key: "issueType", width: 90 },
			{
				title: "影响行数",
				dataIndex: "impactRows",
				key: "impactRows",
				width: 110,
				align: "right",
				render: (v: number) => v.toLocaleString(),
			},
			{
				title: "级别",
				dataIndex: "severity",
				key: "severity",
				width: 80,
				render: (v: QualityIssue["severity"]) => severityTag(v),
			},
			{ title: "责任人", dataIndex: "owner", key: "owner", width: 90 },
			{
				title: "状态",
				dataIndex: "status",
				key: "status",
				width: 90,
				render: (v: QualityIssue["status"]) => statusTag(v),
			},
			{ title: "最近发生", dataIndex: "occurredAt", key: "occurredAt", width: 150 },
		],
		[],
	);

	const hotColumns = useMemo<ColumnsType<HotAsset>>(
		() => [
			{ title: "名称", dataIndex: "name", key: "name", ellipsis: true },
			{ title: "类型", dataIndex: "kind", key: "kind", width: 90 },
			{
				title: "访问量",
				dataIndex: "views",
				key: "views",
				width: 110,
				align: "right",
				render: (v: number) => v.toLocaleString(),
			},
			{ title: "最近更新", dataIndex: "updatedAt", key: "updatedAt", width: 160 },
			{
				title: "状态",
				dataIndex: "status",
				key: "status",
				width: 90,
				render: (v: HotAsset["status"]) => assetStatusTag(v),
			},
		],
		[],
	);

	return (
		<div className="space-y-4">
			<Card
				title={
					<Space size={8}>
						<MonitorOutlined />
						<span>热门报表</span>
					</Space>
				}
				extra={
					<Space wrap size={8}>
						<Button
							size="small"
							type="primary"
							icon={<LineChartOutlined />}
							onClick={() => router.push("/visualization/reports")}
						>
							进入报表中心
						</Button>
					</Space>
				}
				bodyStyle={{ paddingTop: 8 }}
			>
				<Row gutter={[12, 12]}>
					{hotReports.map((r) => (
						<Col key={r.key} xs={24} sm={12} lg={6}>
							<Card hoverable onClick={() => router.push("/visualization/reports")} bodyStyle={{ padding: 12 }}>
								<div className="flex items-center justify-between gap-2">
									<div className="text-sm font-medium">{r.name}</div>
									<Tag color="blue">报表</Tag>
								</div>
								<div className="mt-2">
									<Chart
										type="line"
										height={84}
										series={[{ name: "trend", data: r.series }] as any}
										options={sparklineOptions}
									/>
								</div>
								<div className="mt-2 flex items-center justify-between text-xs text-muted-foreground">
									<span>访问 {r.views.toLocaleString()}</span>
									<span>{r.updatedAt}</span>
								</div>
							</Card>
						</Col>
					))}
				</Row>
			</Card>

			<Card
				title={
					<Space size={8}>
						<LineChartOutlined />
						<span>核心指标</span>
					</Space>
				}
				bodyStyle={{ paddingTop: 12 }}
			>
				<Row gutter={[12, 12]}>
					{leaderKpis.slice(0, 6).map((k) => (
						<Col key={k.key} xs={24} sm={12} lg={8} xl={4}>
							<Card
								hoverable
								size="small"
								onClick={k.onClick}
								bodyStyle={{ cursor: k.onClick ? "pointer" : "default" }}
							>
								<Space direction="vertical" size={6} className="w-full">
									<div className="flex items-center justify-between">
										<span className="text-xs text-muted-foreground">{k.title}</span>
										<span className="text-xs" style={{ color: toneColor(k.tone) }}>
											{k.prefix}
										</span>
									</div>
									<Statistic value={k.value} suffix={k.suffix} valueStyle={{ fontSize: 20 }} />
									{k.change ? (
										<div className="text-xs text-muted-foreground">
											{k.change.label} {k.change.value}%
										</div>
									) : null}
								</Space>
							</Card>
						</Col>
					))}
				</Row>
			</Card>

			<Row gutter={[12, 12]}>
				<Col xs={24} lg={16}>
					<Card
						title={
							<Space size={8}>
								<LineChartOutlined />
								<span>业务趋势与质量评分对照</span>
							</Space>
						}
						extra={
							<Button size="small" type="link" onClick={() => router.push("/governance/rules")}>
								查看明细
							</Button>
						}
					>
						<Chart type="line" height={280} series={trendSeries as any} options={trendOptions} />
					</Card>
				</Col>
				<Col xs={24} lg={8}>
					<Space direction="vertical" size={12} style={{ width: "100%" }}>
						<Card
							title={
								<Space size={8}>
									<DatabaseOutlined />
									<span>结构分布（示例）</span>
								</Space>
							}
							extra={
								<Button size="small" type="link" onClick={() => router.push("/catalog/assets")}>
									资产清单
								</Button>
							}
						>
							<Chart type="donut" height={240} series={distribution.series as any} options={donutOptions} />
							<div className="mt-3 grid grid-cols-2 gap-2">
								<Card size="small" bodyStyle={{ padding: 10 }}>
									<div className="text-xs text-muted-foreground">资产覆盖率</div>
									<Progress percent={86} size="small" strokeColor={toneColor("success")} />
								</Card>
								<Card size="small" bodyStyle={{ padding: 10 }}>
									<div className="text-xs text-muted-foreground">密级 INTERNAL</div>
									<Progress percent={62} size="small" strokeColor={toneColor("warning")} />
								</Card>
							</div>
						</Card>

						<Card
							size="small"
							title={
								<Space size={8}>
									<SafetyCertificateOutlined />
									<span>实时质量扫描</span>
								</Space>
							}
							extra={
								<Button size="small" type="link" onClick={() => router.push("/governance/rules")}>
									质量中心
								</Button>
							}
						>
							<div className="flex items-center gap-4">
								<div className="tech-scan">
									<div className="tech-scan-center">
										<div className="text-lg font-semibold text-foreground">{trend[trend.length - 1]?.dqScore ?? 0}</div>
										<div className="text-xs text-muted-foreground">DQ</div>
									</div>
								</div>
								<Space direction="vertical" size={8} className="flex-1">
									<div className="flex items-center justify-between">
										<span className="text-xs text-muted-foreground">今日告警</span>
										<span className="text-sm font-medium">6</span>
									</div>
									<div className="flex items-center justify-between">
										<span className="text-xs text-muted-foreground">延迟任务</span>
										<span className="text-sm font-medium">3</span>
									</div>
									<div className="flex items-center justify-between">
										<span className="text-xs text-muted-foreground">治理闭环率</span>
										<span className="text-sm font-medium">82%</span>
									</div>
								</Space>
							</div>
						</Card>
					</Space>
				</Col>
			</Row>

			<Row gutter={[12, 12]}>
				<Col xs={24} lg={16}>
					<Card
						title={
							<Space size={8}>
								<ExclamationCircleOutlined />
								<span>质量问题 TopN</span>
							</Space>
						}
						extra={
							<Space>
								<Button size="small" icon={<SettingOutlined />} onClick={() => router.push("/governance/rules")}>
									规则
								</Button>
								<Button
									size="small"
									type="primary"
									icon={<ProfileOutlined />}
									onClick={() => router.push("/governance/tasks")}
								>
									任务
								</Button>
							</Space>
						}
					>
						<Table
							size="small"
							rowKey="key"
							columns={issueColumns}
							dataSource={qualityIssues}
							pagination={false}
							onRow={() => ({
								onClick: () => router.push("/governance/rules"),
							})}
						/>
					</Card>
				</Col>
				<Col xs={24} lg={8}>
					<Card
						title={
							<Space size={8}>
								<AlertOutlined />
								<span>告警与待办</span>
							</Space>
						}
						extra={
							<Button size="small" type="link" onClick={() => router.push("/governance/rules")}>
								查看全部
							</Button>
						}
					>
						<List
							size="small"
							dataSource={workItems}
							renderItem={(item) => (
								<List.Item key={item.key} className="cursor-pointer" onClick={() => router.push(item.target)}>
									<List.Item.Meta
										title={
											<Space size={8}>
												{item.kind === "告警" ? <Badge color="red" text="告警" /> : <Badge color="blue" text="待办" />}
												<span className="text-sm">{item.title}</span>
											</Space>
										}
										description={
											<Space size={8}>
												<Tag color={item.level === "高" ? "red" : item.level === "中" ? "gold" : "blue"}>
													{item.level}
												</Tag>
												<span className="text-xs text-muted-foreground">{item.time}</span>
											</Space>
										}
									/>
								</List.Item>
							)}
						/>
					</Card>
				</Col>
			</Row>

			<Collapse
				defaultActiveKey={[]}
				items={[
					{
						key: "analyst",
						label: (
							<Space size={8}>
								<BarChartOutlined />
								<span>分析概览</span>
							</Space>
						),
						children: (
							<div className="space-y-3">
								<Row gutter={[12, 12]}>
									{analystKpis.map((k) => (
										<Col key={k.key} xs={24} sm={12} lg={6}>
											<Card
												hoverable
												onClick={k.onClick}
												size="small"
												bodyStyle={{ cursor: k.onClick ? "pointer" : "default" }}
											>
												<div className="flex items-center justify-between">
													<span className="text-xs text-muted-foreground">{k.title}</span>
													<span style={{ color: toneColor(k.tone) }}>{k.prefix}</span>
												</div>
												<Statistic value={k.value} suffix={k.suffix} valueStyle={{ fontSize: 18 }} />
											</Card>
										</Col>
									))}
								</Row>
								<Card
									title={
										<Space size={8}>
											<BarChartOutlined />
											<span>热门资产 TopN</span>
										</Space>
									}
									extra={
										<Button size="small" type="primary" onClick={() => router.push("/catalog/assets")}>
											查看资产
										</Button>
									}
								>
									<Table size="small" rowKey="key" columns={hotColumns} dataSource={hotAssets} pagination={false} />
								</Card>
							</div>
						),
					},
					{
						key: "engineer",
						label: (
							<Space size={8}>
								<SettingOutlined />
								<span>工程与运维</span>
							</Space>
						),
						children: (
							<div className="space-y-3">
								<Row gutter={[12, 12]}>
									{engineerKpis.map((k) => (
										<Col key={k.key} xs={24} sm={12} lg={6}>
											<Card
												hoverable
												onClick={k.onClick}
												size="small"
												bodyStyle={{ cursor: k.onClick ? "pointer" : "default" }}
											>
												<div className="flex items-center justify-between">
													<span className="text-xs text-muted-foreground">{k.title}</span>
													<span style={{ color: toneColor(k.tone) }}>{k.prefix}</span>
												</div>
												<Statistic value={k.value} suffix={k.suffix} valueStyle={{ fontSize: 18 }} />
											</Card>
										</Col>
									))}
								</Row>
								<Card
									title={
										<Space size={8}>
											<ThunderboltOutlined />
											<span>告警与排障入口</span>
										</Space>
									}
									extra={
										<Space>
											<Button size="small" onClick={() => router.push("/foundation/task-scheduling")}>
												调度监控
											</Button>
											<Button size="small" type="primary" onClick={() => router.push("/governance/rules")}>
												质量中心
											</Button>
										</Space>
									}
								>
									<List
										size="small"
										dataSource={workItems}
										renderItem={(item) => (
											<List.Item key={item.key} className="cursor-pointer" onClick={() => router.push(item.target)}>
												<List.Item.Meta
													title={
														<Space size={8}>
															{item.kind === "告警" ? (
																<Badge color="red" text="告警" />
															) : (
																<Badge color="blue" text="待办" />
															)}
															<span className="text-sm">{item.title}</span>
														</Space>
													}
													description={
														<Space size={8}>
															<Tag color={item.level === "高" ? "red" : item.level === "中" ? "gold" : "blue"}>
																{item.level}
															</Tag>
															<span className="text-xs text-muted-foreground">{item.time}</span>
														</Space>
													}
												/>
											</List.Item>
										)}
									/>
								</Card>
							</div>
						),
					},
				]}
			/>

			<Card size="small">
				<Typography.Text type="secondary">
					提示：本页面为可落地的首页信息架构示例（假数据），后续对接平台 API 替换数据源。
				</Typography.Text>
			</Card>
		</div>
	);
}
