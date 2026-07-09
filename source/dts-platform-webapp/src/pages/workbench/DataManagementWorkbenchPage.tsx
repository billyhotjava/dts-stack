import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Progress, Space, Spin, Tag, Typography } from "antd";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";
import { useRouter } from "@/routes/hooks";
import goldenChainService, {
	type GoldenChainDetail,
	type GoldenChainSummary,
} from "@/api/services/goldenChainService";
import {
	buildDataManagementThemes,
	type DataManagementThemeState,
	type DataManagementTone,
} from "./dataManagementThemeModel";

const { Text } = Typography;
const E2E_DATA_PRODUCT_JOURNEY = "e2e-data-product";

const TONE_COLOR: Record<DataManagementTone, string> = {
	default: "default",
	processing: "blue",
	success: "green",
	warning: "orange",
	danger: "red",
};

const statusTag = (label: string, tone: DataManagementTone) => <Tag color={TONE_COLOR[tone]}>{label}</Tag>;

const withE2EJourney = (route: string) => {
	const [path, query = ""] = route.split("?");
	const params = new URLSearchParams(query);
	params.set("journey", E2E_DATA_PRODUCT_JOURNEY);
	return `${path}?${params.toString()}`;
};

const firstReportAcceptanceItems = ["连接成功", "任务可运行", "报表可查看", "证据可追溯"];

const firstReportJourneySteps = [
	{
		title: "接入一张业务表",
		desc: "从连接器目录或数据源连接开始，完成连接测试。",
		route: "/foundation/data-sources",
		action: "配置数据源",
	},
	{
		title: "选择业务表",
		desc: "探测表结构，选择要进入治理链路的业务表和字段。",
		route: "/foundation/data-sources",
		action: "探测业务表",
	},
	{
		title: "生成同步任务",
		desc: "确认字段、同步方式和预检结果，生成可运行的同步任务。",
		route: "/explore/etl/transform",
		action: "查看同步任务",
	},
	{
		title: "生成报表",
		desc: "进入语义探索，选择指标、维度、筛选条件和图形。",
		route: "/bi/explore",
		action: "创建分析卡片",
	},
	{
		title: "查看运行证据",
		desc: "到运行概览确认任务成功率、告警和补数记录。",
		route: "/ops/overview",
		action: "查看运行证据",
	},
];

type ProductJourneyStage = {
	key: string;
	title: string;
	desc: string;
	result: string;
	evidence: string;
	owner: string;
	gap: string;
	nextStep: string;
	route: string;
	action: string;
	supportingRoute: string;
	supportingAction: string;
	tagColor: string;
};

const productJourneyStages: ProductJourneyStage[] = [
	{
		key: "integration",
		title: "数据集成",
		desc: "接入业务系统或文件，完成连接、结构探测和同步任务。",
		result: "拿到可运行的数据链路",
		evidence: "连接测试、字段探测、同步预检",
		owner: "数据工程师",
		gap: "未选择数据源或同步任务未预检",
		nextStep: "选择业务系统并完成连接测试",
		route: "/foundation/data-sources",
		action: "配置数据源",
		supportingRoute: "/explore/etl/transform",
		supportingAction: "生成同步任务",
		tagColor: "blue",
	},
	{
		key: "planning",
		title: "数仓规划",
		desc: "定义主题域、分层、数据域和业务过程，决定数据进仓后的组织方式。",
		result: "确认数据应该落到哪个业务主题",
		evidence: "主题域、业务过程、资产归属",
		owner: "数据架构师",
		gap: "ODS/DWD/DWS/ADS 分层草稿待确认",
		nextStep: "确认主题域、业务过程和分层策略",
		route: "/governance/subjects",
		action: "确认数仓规划",
		supportingRoute: "/catalog/metadata-management",
		supportingAction: "核对资产目录",
		tagColor: "purple",
	},
	{
		key: "standards",
		title: "数据标准",
		desc: "把业务术语、数据元、码表和标准模板绑定到字段。",
		result: "让字段命名、类型和口径可复用",
		evidence: "标准包、数据元、落标覆盖率",
		owner: "数据管家",
		gap: "标准包、数据元或码表覆盖率待补齐",
		nextStep: "套用标准包并生成字段落标草稿",
		route: "/foundation/standard-package",
		action: "套用标准包",
		supportingRoute: "/governance/standards/elements",
		supportingAction: "维护数据元",
		tagColor: "cyan",
	},
	{
		key: "modeling",
		title: "维度建模",
		desc: "从标准字段生成模型草稿，再在低代码或 SQL 建模里微调。",
		result: "形成事实、维度和汇总模型",
		evidence: "模型字段、血缘、校验结果",
		owner: "建模工程师",
		gap: "模型草稿未创建或字段标准未应用",
		nextStep: "进入低代码建模并生成 SQL 草稿",
		route: "/studio/low-code-development",
		action: "进入低代码建模",
		supportingRoute: "/studio/sql-modeling",
		supportingAction: "高级建模",
		tagColor: "geekblue",
	},
	{
		key: "metrics",
		title: "数据指标",
		desc: "基于模型定义原子指标、派生指标和口径说明。",
		result: "把业务问题沉淀为指标资产",
		evidence: "指标口径、计算逻辑、责任人",
		owner: "业务分析师",
		gap: "指标口径、粒度或责任人待绑定",
		nextStep: "基于模型字段设计指标口径",
		route: "/modeling/metric-workbench",
		action: "设计指标",
		supportingRoute: "/modeling/semantic/metrics",
		supportingAction: "查看指标库",
		tagColor: "green",
	},
	{
		key: "development",
		title: "数据开发",
		desc: "把同步、清洗、模型生成和指标汇总编排成可运行任务。",
		result: "让数据产品能按调度稳定产出",
		evidence: "任务 DAG、运行日志、补数记录",
		owner: "数据开发工程师",
		gap: "脚本、调度或补数策略待确认",
		nextStep: "编排开发任务并执行编译测试",
		route: "/explore/etl/scripts",
		action: "编排数据开发",
		supportingRoute: "/explore/etl/orchestration",
		supportingAction: "查看调度",
		tagColor: "orange",
	},
	{
		key: "service",
		title: "数据服务",
		desc: "把可信资产发布为报表、API 或数据产品，纳入权限和消费验收。",
		result: "交付业务可用的数据消费入口",
		evidence: "API、报表、授权记录",
		owner: "服务发布人",
		gap: "API、报表或数据产品入口未发布",
		nextStep: "选择消费目标并配置授权",
		route: "/services/apis",
		action: "发布数据 API",
		supportingRoute: "/bi/dashboards",
		supportingAction: "创建报表",
		tagColor: "magenta",
	},
	{
		key: "evidence",
		title: "运行证据",
		desc: "回看任务、质量、服务调用和告警，把交付状态变成客户可验收证据。",
		result: "证明链路持续可用",
		evidence: "实例、告警、质量检查、服务日志",
		owner: "运维与数据管家",
		gap: "运行、质量、权限或审计证据未汇总",
		nextStep: "查看实例日志并形成验收证据",
		route: "/ops/instances",
		action: "查看运行证据",
		supportingRoute: "/ops/overview",
		supportingAction: "运行概览",
		tagColor: "red",
	},
];

export type DataManagementWorkbenchPageProps = {
	embedded?: boolean;
	focus?: "data-management" | "consumption";
	firstReportActive?: boolean;
	productId?: string | null;
};

export default function Page({
	embedded = false,
	focus = "data-management",
	firstReportActive = false,
	productId = null,
}: DataManagementWorkbenchPageProps) {
	const router = useRouter();
	const [chains, setChains] = useState<GoldenChainSummary[]>([]);
	const [detailsByChainKey, setDetailsByChainKey] = useState<Record<string, GoldenChainDetail | undefined>>({});
	const [selectedThemeKey, setSelectedThemeKey] = useState<string>("");
	const [loading, setLoading] = useState(false);

	useEffect(() => {
		let cancelled = false;
		const load = async () => {
			setLoading(true);
			try {
				const list = await goldenChainService.list();
				const safeChains = Array.isArray(list) ? list : [];
				if (cancelled) return;
				setChains(safeChains);
				const detailEntries = await Promise.all(
					safeChains.map(async (chain) => {
						try {
							const detail = await goldenChainService.detail(chain.chainKey);
							return [chain.chainKey, detail] as const;
						} catch {
							return [chain.chainKey, undefined] as const;
						}
					}),
				);
				if (!cancelled) {
					setDetailsByChainKey(Object.fromEntries(detailEntries));
				}
			} catch {
				if (!cancelled) {
					setChains([]);
					setDetailsByChainKey({});
				}
			} finally {
				if (!cancelled) setLoading(false);
			}
		};
		void load();
		return () => {
			cancelled = true;
		};
	}, []);

	const themes = useMemo(() => buildDataManagementThemes(chains, detailsByChainKey), [chains, detailsByChainKey]);
	const selectedTheme = useMemo<DataManagementThemeState | undefined>(
		() => themes.find((theme) => theme.key === selectedThemeKey) || themes[0],
		[themes, selectedThemeKey],
	);
	const blockedThemes = themes.filter(
		(theme) => theme.governance.tone === "warning" || theme.operation.tone === "warning",
	);

	const hasThemes = themes.length > 0;
	const themeSummary = hasThemes ? "已加载现场配置主题" : "待现场定义业务主题";
	const failureReason = selectedTheme?.failureReason || "";
	const nextAction = selectedTheme?.nextAction || selectedTheme?.primaryAction.label;
	const evidenceRefs = selectedTheme?.evidenceRefs || [];
	const isConsumptionFocus = focus === "consumption";
	const sectionTitle = isConsumptionFocus ? "消费发布" : "端到端数据产品";
	const sectionDescription = isConsumptionFocus
		? "从数据产品、资产授权、报表/API 发布到客户验收的消费闭环。"
		: "从集成、数仓规划、标准、建模、指标、开发到服务发布的现场配置闭环。";
	const headerActions = (
		<Space wrap>
			<Button onClick={() => router.push(withE2EJourney("/foundation/data-sources"))}>配置数据源</Button>
			<Button onClick={() => router.push(withE2EJourney("/governance/subjects"))}>数仓规划</Button>
			<Button onClick={() => router.push(withE2EJourney("/foundation/standard-package"))}>标准包</Button>
			<Button onClick={() => router.push(withE2EJourney("/studio/low-code-development"))}>低代码建模</Button>
			<Button onClick={() => router.push(withE2EJourney("/modeling/metric-workbench"))}>指标设计</Button>
			<Button onClick={() => router.push(withE2EJourney("/explore/etl/scripts"))}>数据开发</Button>
			<Button type={blockedThemes.length > 0 ? "primary" : "default"} onClick={() => router.push(withE2EJourney("/workbench/todo"))}>
				处理阻断项
			</Button>
			<Button onClick={() => router.push(withE2EJourney("/governance/quality"))}>治理检查</Button>
			<Button onClick={() => router.push(withE2EJourney("/catalog/assets"))}>查看资产</Button>
			<Button onClick={() => router.push(withE2EJourney("/catalog/lineage/graph"))}>查看血缘</Button>
			<Button onClick={() => router.push(withE2EJourney("/governance/standards/reference"))}>字典管理</Button>
			<Button onClick={() => router.push(withE2EJourney("/bi/dashboards"))}>创建报表</Button>
			<Button onClick={() => router.push(withE2EJourney("/services/apis"))}>发布数据 API</Button>
			<Button onClick={() => router.push(withE2EJourney("/ops/overview"))}>查看运行</Button>
		</Space>
	);

	return (
		<div className="space-y-6" data-testid={isConsumptionFocus ? "data-consumption-workbench-section" : "data-management-workbench-section"}>
			{embedded ? (
				<div className="flex flex-wrap items-start justify-between gap-4">
					<div>
						<Typography.Title level={4} style={{ margin: 0 }}>
							{sectionTitle}
						</Typography.Title>
						<Text type="secondary">{sectionDescription}</Text>
					</div>
					{headerActions}
				</div>
			) : (
				<PageHeader title="端到端数据产品工作台" actions={headerActions} />
			)}

			{isConsumptionFocus && (
				<Alert
					type="info"
					showIcon
					message="消费发布入口已并入个人工作台"
					description={
						productId
							? `已带入数据产品 ${productId}，请从资产、权限、报表、数据 API 和运行证据核对是否满足客户验收。`
							: "请从资产、权限、报表、数据 API 和运行证据核对是否满足客户验收。"
					}
				/>
			)}

			<Alert
				type={blockedThemes.length > 0 ? "warning" : "info"}
				showIcon
				message={blockedThemes.length > 0 ? "存在需要处理的主题" : themeSummary}
				description="业务主题需在客户现场按组织、报表和资产口径定义，产品不内置演示场景；配置完成后再展示数据可用、治理状态、消费状态和运行健康。"
			/>

			<Card
				title="端到端数据产品工作台"
				extra={
					<Space wrap>
						<Tag color="blue">统一旅程</Tag>
						<Tag>已有页面承接</Tag>
					</Space>
				}
				data-testid="end-to-end-data-product-journey"
			>
				<div className="mb-4 flex flex-wrap items-start justify-between gap-3">
					<div className="max-w-3xl">
						<Typography.Title level={5} style={{ margin: 0 }}>
							从一张业务表到可交付数据服务
						</Typography.Title>
						<Text type="secondary">
							客户不需要在菜单里猜下一步；每个阶段都给出业务目标、验收证据和下一步动作，专业页面只作为当前阶段的工作台。
						</Text>
					</div>
					<Space wrap>
						<Button
							type="primary"
							data-testid="end-to-end-journey-continue"
							onClick={() => router.push(withE2EJourney(productJourneyStages[0].route))}
						>
							开始接入
						</Button>
						<Button onClick={() => router.push(withE2EJourney("/ops/instances"))}>查看运行证据</Button>
					</Space>
				</div>
				<div className="grid gap-3 2xl:grid-cols-4 xl:grid-cols-3 md:grid-cols-2">
					{productJourneyStages.map((stage, index) => (
						<div
							key={stage.key}
							className="flex min-h-[336px] flex-col justify-between rounded border border-gray-200 p-3"
							data-testid={`end-to-end-stage-${stage.key}`}
						>
							<div className="space-y-2">
								<div className="flex items-center justify-between gap-2">
									<Tag color={stage.tagColor}>{String(index + 1).padStart(2, "0")}</Tag>
									<Text type="secondary">{stage.title}</Text>
								</div>
								<div className="text-base font-medium">{stage.result}</div>
								<Text type="secondary">{stage.desc}</Text>
								<div className="rounded border border-dashed border-gray-200 p-2 text-sm">
									<div className="font-medium">验收证据</div>
									<Text type="secondary">{stage.evidence}</Text>
								</div>
								<div className="grid gap-2 text-sm">
									<div>
										<div className="text-muted-foreground">负责角色</div>
										<Text>{stage.owner}</Text>
									</div>
									<div>
										<div className="text-muted-foreground">当前缺口</div>
										<Text type="secondary">{stage.gap}</Text>
									</div>
									<div>
										<div className="text-muted-foreground">下一步</div>
										<Text type="secondary">{stage.nextStep}</Text>
									</div>
								</div>
							</div>
							<Space wrap className="mt-3">
								<Button
									type={index === 0 ? "primary" : "default"}
									data-testid={`end-to-end-stage-${stage.key}-primary`}
									onClick={() => router.push(withE2EJourney(stage.route))}
								>
									{stage.action}
								</Button>
								<Button
									data-testid={`end-to-end-stage-${stage.key}-secondary`}
									onClick={() => router.push(withE2EJourney(stage.supportingRoute))}
								>
									{stage.supportingAction}
								</Button>
							</Space>
						</div>
					))}
				</div>
			</Card>

			<Card
				title={firstReportActive ? "当前首单：接入业务表生成报表" : "新手首单：接入业务表生成报表"}
				extra={
					<Space wrap>
						{firstReportActive ? <Tag color="green">当前路径</Tag> : null}
						<Tag color="blue">业务默认路径</Tag>
					</Space>
				}
				data-testid="first-report-journey-guide"
				data-journey-active={firstReportActive}
				style={firstReportActive ? { borderColor: "#1677ff" } : undefined}
			>
				{firstReportActive && (
					<Alert
						type="success"
						showIcon
						className="mb-3"
						message="首单目标"
						description="从一张业务表完成连接、同步、报表和运行证据核对。"
					/>
				)}
				<div className="mb-3 flex flex-wrap gap-2">
					{firstReportAcceptanceItems.map((item) => (
						<Tag key={item} color={firstReportActive ? "green" : "default"}>
							{item}
						</Tag>
					))}
				</div>
				<div className="grid gap-3 xl:grid-cols-5 md:grid-cols-2">
					{firstReportJourneySteps.map((step, index) => (
						<div key={step.title} className="flex min-h-[176px] flex-col justify-between rounded border border-gray-200 p-3">
							<div className="space-y-2">
								<Tag color={index === 0 ? "blue" : "default"}>{index + 1}</Tag>
								<div className="font-medium">{step.title}</div>
								<Text type="secondary">{step.desc}</Text>
							</div>
							<Button block onClick={() => router.push(step.route)}>
								{step.action}
							</Button>
						</div>
					))}
				</div>
			</Card>

			<Spin spinning={loading}>
				{hasThemes ? (
					<div className="grid gap-4 xl:grid-cols-4 md:grid-cols-2">
						{themes.map((theme) => (
							<Card
								key={theme.key}
								hoverable
								title={theme.title}
								extra={<Tag>{theme.chainCount} 条链路</Tag>}
								className={selectedTheme?.key === theme.key ? "border-primary" : undefined}
								onClick={() => setSelectedThemeKey(theme.key)}
							>
								<div className="flex min-h-[248px] flex-col justify-between gap-4">
									<Text type="secondary">{theme.description}</Text>
									<Progress percent={theme.progressPercent} size="small" />
									<div className="grid grid-cols-2 gap-2 text-sm">
										<div>
											<div className="mb-1 text-muted-foreground">数据可用</div>
											{statusTag(theme.dataAvailability.label, theme.dataAvailability.tone)}
										</div>
										<div>
											<div className="mb-1 text-muted-foreground">治理状态</div>
											{statusTag(theme.governance.label, theme.governance.tone)}
										</div>
										<div>
											<div className="mb-1 text-muted-foreground">消费状态</div>
											{statusTag(theme.consumption.label, theme.consumption.tone)}
										</div>
										<div>
											<div className="mb-1 text-muted-foreground">运行健康</div>
											{statusTag(theme.operation.label, theme.operation.tone)}
										</div>
									</div>
									<Button block onClick={(event) => {
										event.stopPropagation();
										router.push(theme.primaryAction.route);
									}}>
										{theme.primaryAction.label}
									</Button>
								</div>
							</Card>
						))}
					</div>
				) : (
					<EmptyState
						compact
						title="待现场定义业务主题"
						description="端到端数据产品工作台不内置演示场景。请在客户现场确认主题口径，并完成数据源、资产和交付链路配置后再展示主题卡片。"
						actions={
							<Space wrap>
								<Button type="primary" onClick={() => router.push("/foundation/data-sources")}>
									配置数据源
								</Button>
								<Button onClick={() => router.push("/catalog/assets")}>查看资产</Button>
							</Space>
						}
					/>
				)}
			</Spin>

			<Card
				title={selectedTheme?.title || "现场主题配置"}
				extra={selectedTheme ? statusTag(selectedTheme.consumption.label, selectedTheme.consumption.tone) : null}
			>
				{selectedTheme && selectedTheme.chainCount > 0 ? (
					<div className="grid gap-4 lg:grid-cols-[minmax(0,1.2fr)_minmax(320px,0.8fr)]">
						<div className="space-y-3">
							{selectedTheme.relatedChains.map((chain) => (
								<div key={chain.chainKey} className="rounded border border-gray-200 p-3">
									<div className="flex flex-wrap items-center justify-between gap-3">
										<div>
											<div className="font-medium">{chain.displayName}</div>
											<Text type="secondary">负责人：{chain.owner || "-"}</Text>
										</div>
										{statusTag(chain.currentStageLabel || chain.status, chain.status === "BLOCKED" ? "warning" : "success")}
									</div>
								</div>
							))}
						</div>
						<div className="space-y-3">
							<div className="rounded border border-dashed border-gray-200 p-3">
								<div className="font-medium">下一步动作</div>
								<Text type={failureReason ? "danger" : "secondary"}>{nextAction}</Text>
								{failureReason ? <div className="mt-2 text-sm text-red-600">{failureReason}</div> : null}
							</div>
							<div className="rounded border border-dashed border-gray-200 p-3">
								<div className="font-medium">验收证据</div>
								{evidenceRefs.length > 0 ? (
									<div className="mt-2 flex flex-wrap gap-2">
										{evidenceRefs.map((item) => (
											<Tag key={item}>{item}</Tag>
										))}
									</div>
								) : (
									<Text type="secondary">链路运行后自动汇总证据编号。</Text>
								)}
							</div>
							<Space wrap>
								<Button onClick={() => router.push("/foundation/data-sources")}>配置数据源</Button>
								<Button onClick={() => router.push("/workbench/todo")}>处理阻断项</Button>
								<Button onClick={() => router.push("/governance/quality")}>治理检查</Button>
								<Button onClick={() => router.push("/catalog/assets")}>查看资产</Button>
								<Button onClick={() => router.push("/catalog/lineage/graph")}>查看血缘</Button>
								<Button onClick={() => router.push("/governance/standards/reference")}>字典管理</Button>
								<Button onClick={() => router.push("/bi/dashboards")}>创建报表</Button>
								<Button onClick={() => router.push("/services/apis")}>发布数据 API</Button>
								<Button onClick={() => router.push("/ops/overview")}>查看运行</Button>
							</Space>
						</div>
					</div>
				) : (
					<EmptyState
						compact
						title="暂无现场业务主题"
						description="当前环境还没有客户现场确认的业务主题。完成主题口径、数据来源和交付链路配置后，这里才会展示主题状态和证据。"
						actions={
							<Space wrap>
								<Button type="primary" onClick={() => router.push("/foundation/data-sources")}>
									配置数据源
								</Button>
								<Button onClick={() => router.push("/workbench/todo")}>处理阻断项</Button>
							</Space>
						}
					/>
				)}
			</Card>
		</div>
	);
}
