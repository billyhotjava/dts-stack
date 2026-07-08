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

const TONE_COLOR: Record<DataManagementTone, string> = {
	default: "default",
	processing: "blue",
	success: "green",
	warning: "orange",
	danger: "red",
};

const statusTag = (label: string, tone: DataManagementTone) => <Tag color={TONE_COLOR[tone]}>{label}</Tag>;

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
	const sectionTitle = isConsumptionFocus ? "消费发布" : "数据管理";
	const sectionDescription = isConsumptionFocus
		? "从数据产品、资产授权、报表/API 发布到客户验收的消费闭环。"
		: "从数据源、黄金链路、治理阻断到消费发布的现场配置闭环。";
	const headerActions = (
		<Space wrap>
			<Button onClick={() => router.push("/foundation/data-sources")}>配置数据源</Button>
			<Button type={blockedThemes.length > 0 ? "primary" : "default"} onClick={() => router.push("/workbench/todo")}>
				处理阻断项
			</Button>
			<Button onClick={() => router.push("/governance/quality")}>治理检查</Button>
			<Button onClick={() => router.push("/catalog/assets")}>查看资产</Button>
			<Button onClick={() => router.push("/catalog/lineage/graph")}>查看血缘</Button>
			<Button onClick={() => router.push("/governance/standards/reference")}>字典管理</Button>
			<Button onClick={() => router.push("/bi/dashboards")}>创建报表</Button>
			<Button onClick={() => router.push("/services/apis")}>发布数据 API</Button>
			<Button onClick={() => router.push("/ops/overview")}>查看运行</Button>
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
				<PageHeader title="数据管理工作台" actions={headerActions} />
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
						description="数据管理工作台不内置演示场景。请在客户现场确认主题口径，并完成数据源、资产和交付链路配置后再展示主题卡片。"
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
