import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Progress, Space, Spin, Tag, Typography } from "antd";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";
import {
	E2E_DATA_PRODUCT_JOURNEY,
	buildAcceptancePackageJson,
	buildAcceptancePackageMarkdown,
	buildAcceptancePrintMeta,
	buildDataProductAcceptancePackage,
	GateEvidenceSummary,
	buildGateEvidence,
	buildJourneyParamClearUrl,
	buildSnapshotResumeUrl,
	buildJourneyUrl,
	clearJourneySnapshot,
	createDataProductArtifactValidator,
	describeJourneySnapshot,
	extractJourneyContextParams,
	loadJourneySnapshot,
	resolveArtifactValidations,
	resolveDataProductJourneyStageStates,
	shouldOfferSnapshotResume,
	type JourneySnapshot,
} from "@/components/journey";
import { getStandardBindingDraft, isBackendStandardBindingDraftId } from "@/features/modeling/drafts/standardBindingDraft";
import { useRouter, useSearchParams } from "@/routes/hooks";
import goldenChainService, {
	type GoldenChainDetail,
	type GoldenChainSummary,
} from "@/api/services/goldenChainService";
import { listWarehouseLayersApi } from "@/api/sprint64GovernanceApi";
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

const JOURNEY_STATUS_COLOR: Record<string, string> = {
	not_started: "default",
	blocked: "orange",
	ready: "blue",
	in_progress: "geekblue",
	done: "green",
};

const JOURNEY_STATUS_LABEL: Record<string, string> = {
	not_started: "未开始",
	blocked: "有缺口",
	ready: "可开始",
	in_progress: "进行中",
	done: "已就绪",
};

const ACCEPTANCE_STATUS_COLOR: Record<string, string> = {
	ready: "green",
	missing: "orange",
	blocked: "red",
};

const ACCEPTANCE_STATUS_LABEL: Record<string, string> = {
	ready: "已具备",
	missing: "缺失项",
	blocked: "阻断项",
};

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
		route: "/foundation/data-sources",
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

const WAREHOUSE_LAYER_PLAN = [
	{ key: "ODS_RAW", title: "ODS_RAW", description: "保留源系统原始记录，保证可追溯和可重放。", route: "/foundation/data-sources", action: "确认接入" },
	{ key: "ODS_STANDARDIZED", title: "ODS_STANDARDIZED", description: "统一字段类型、命名和技术字段，形成可治理的入湖表。", route: "/data-modeling/standards/fields", action: "查看标准" },
	{ key: "STG", title: "STG", description: "dbt 技术过渡层：类型转换、重命名、去重和轻量清洗，不承载业务聚合。", route: "/data-modeling/dimensions/workbench", action: "进入模型工作台" },
	{ key: "DWD", title: "DWD", description: "按业务活动沉淀明细事实和维度关联，承接标准字段。", route: "/data-modeling/dimensions/workbench", action: "创建明细表" },
	{ key: "DWS", title: "DWS", description: "围绕主题域和公共粒度形成可复用汇总模型。", route: "/data-modeling/dimensions/workbench", action: "进入模型工作台" },
	{ key: "ADS", title: "ADS", description: "面向指标、报表和 API 消费交付应用数据集。", route: "/data-modeling/metrics/atomic", action: "绑定指标" },
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
	const searchParams = useSearchParams();
	const [chains, setChains] = useState<GoldenChainSummary[]>([]);
	const [detailsByChainKey, setDetailsByChainKey] = useState<Record<string, GoldenChainDetail | undefined>>({});
	const [selectedThemeKey, setSelectedThemeKey] = useState<string>("");
	const [loading, setLoading] = useState(false);
	const [warehouseLayerPlan, setWarehouseLayerPlan] = useState(WAREHOUSE_LAYER_PLAN);

	useEffect(() => {
		let cancelled = false;
		void listWarehouseLayersApi()
			.then((registry) => {
				if (cancelled || !registry.length) return;
				const routeMeta = new Map(WAREHOUSE_LAYER_PLAN.map((item) => [item.key, item]));
				setWarehouseLayerPlan(
					registry.map((item) => ({
						...(routeMeta.get(item.code) || {
							key: item.code,
							route: "/governance/subjects",
							action: "查看规划",
						}),
						title: item.title || item.code,
						description: `${item.responsibility} 命名前缀：${item.namingPrefixes.join("、") || "平台默认"}`,
					})),
				);
			})
			.catch(() => {
				// Keep the static plan as the offline fallback.
			});
		return () => {
				cancelled = true;
			};
	}, []);

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
	const journeyParam = searchParams.get("journey");
	const [resumeSnapshot, setResumeSnapshot] = useState<JourneySnapshot | null>(null);
	useEffect(() => {
		// 快照读取放在 effect 中，storage 异常时 loadJourneySnapshot 静默返回 null。
		setResumeSnapshot(loadJourneySnapshot());
	}, [journeyParam]);
	const offerResume = shouldOfferSnapshotResume(resumeSnapshot, journeyParam);
	const resumeDescription = offerResume && resumeSnapshot ? describeJourneySnapshot(resumeSnapshot) : null;
	const dismissResumeSnapshot = () => {
		clearJourneySnapshot();
		setResumeSnapshot(null);
	};
	const journeyContextParams = useMemo(() => extractJourneyContextParams(searchParams), [searchParams]);
	const artifactValidations = useMemo(
		() =>
			resolveArtifactValidations(
				journeyContextParams,
				createDataProductArtifactValidator({
					standardDraft: { findDraft: getStandardBindingDraft, isBackendDraftId: isBackendStandardBindingDraftId },
				}),
			),
		[journeyContextParams],
	);
	const productJourneyStages = useMemo(
		() => resolveDataProductJourneyStageStates(searchParams, artifactValidations),
		[searchParams, artifactValidations],
	);
	const gateEvidence = useMemo(
		() => buildGateEvidence(journeyContextParams, { validations: artifactValidations }),
		[journeyContextParams, artifactValidations],
	);
	const acceptancePackage = useMemo(
		() => buildDataProductAcceptancePackage(searchParams, { validations: artifactValidations }),
		[searchParams, artifactValidations],
	);
	const themeSummary = hasThemes ? "已加载现场配置主题" : "待现场定义业务主题";
	const failureReason = selectedTheme?.failureReason || "";
	const nextAction = selectedTheme?.nextAction || selectedTheme?.primaryAction.label;
	const evidenceRefs = selectedTheme?.evidenceRefs || [];
	const journeyRoute = (route: string) => buildJourneyUrl(route, searchParams);
	const isConsumptionFocus = focus === "consumption";
	const sectionTitle = isConsumptionFocus ? "消费发布" : "端到端数据产品";
	const sectionDescription = isConsumptionFocus
		? "从数据产品、资产授权、报表/API 发布到客户验收的消费闭环。"
		: "从集成、数仓规划、标准、建模、指标、开发到服务发布的现场配置闭环。";
	const headerActions = (
		<Space wrap>
			<Button onClick={() => router.push(withE2EJourney("/foundation/data-sources"))}>配置数据源</Button>
			<Button onClick={() => router.push(withE2EJourney("/data-modeling/planning/spaces"))}>数仓规划</Button>
			<Button onClick={() => router.push(withE2EJourney("/data-modeling/standards/fields"))}>数据标准</Button>
			<Button onClick={() => router.push(withE2EJourney("/data-modeling/dimensions/workbench"))}>模型中心</Button>
			<Button onClick={() => router.push(withE2EJourney("/data-modeling/metrics/atomic"))}>指标设计</Button>
			<Button onClick={() => router.push(withE2EJourney("/explore/etl/scripts"))}>数据开发</Button>
			<Button type={blockedThemes.length > 0 ? "primary" : "default"} onClick={() => router.push(withE2EJourney("/workbench/todo"))}>
				处理阻断项
			</Button>
			<Button onClick={() => router.push(withE2EJourney("/governance/rules/runs"))}>治理检查</Button>
			<Button onClick={() => router.push(withE2EJourney("/catalog/assets"))}>查看资产</Button>
			<Button onClick={() => router.push(withE2EJourney("/catalog/lineage/graph"))}>查看血缘</Button>
			<Button onClick={() => router.push(withE2EJourney("/governance/standards/reference"))}>字典管理</Button>
			<Button onClick={() => router.push(withE2EJourney("/bi/dashboards"))}>创建报表</Button>
			<Button onClick={() => router.push(withE2EJourney("/services/apis"))}>发布数据 API</Button>
			<Button onClick={() => router.push(withE2EJourney("/ops/overview"))}>查看运行</Button>
		</Space>
	);
	const copyAcceptancePackage = async () => {
		const content = buildAcceptancePackageMarkdown(acceptancePackage);
		await navigator.clipboard?.writeText(content);
	};
	const printAcceptancePackage = () => {
		if (typeof window !== "undefined") window.print();
	};
	const acceptancePrintMeta = buildAcceptancePrintMeta(acceptancePackage);
	const downloadAcceptancePackage = () => {
		const blob = new Blob([buildAcceptancePackageJson(acceptancePackage)], {
			type: "application/json;charset=utf-8",
		});
		const url = URL.createObjectURL(blob);
		const link = document.createElement("a");
		link.href = url;
		link.download = "dts-data-product-acceptance-package.json";
		link.click();
		URL.revokeObjectURL(url);
	};

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
				{resumeDescription && resumeSnapshot && (
					<Alert
						className="mb-4"
						type="info"
						showIcon
						data-testid="journey-resume-card"
						message={`继续上次旅程：${resumeDescription.stageTitle}`}
						description={
							[resumeDescription.contextSummary, resumeDescription.savedAgo && `保存于 ${resumeDescription.savedAgo}`]
								.filter(Boolean)
								.join(" · ") || "上次旅程尚未积累上下文参数"
						}
						action={
							<Space>
								<Button
									size="small"
									type="primary"
									data-testid="journey-resume-continue"
									onClick={() => router.push(buildSnapshotResumeUrl(resumeSnapshot))}
								>
									继续旅程
								</Button>
								<Button size="small" data-testid="journey-resume-clear" onClick={dismissResumeSnapshot}>
									清除
								</Button>
							</Space>
						}
					/>
				)}
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
									<Space size={4} wrap>
										<Text type="secondary">{stage.title}</Text>
										<Tag color={JOURNEY_STATUS_COLOR[stage.status]}>{JOURNEY_STATUS_LABEL[stage.status]}</Tag>
										{stage.status === "done" && stage.verification === "unverified" ? (
											<Tag color="gold" data-testid={`end-to-end-stage-${stage.key}-unverified`}>
												待确认
											</Tag>
										) : null}
									</Space>
								</div>
								<div className="text-base font-medium">{stage.result}</div>
								<Text type="secondary">{stage.desc}</Text>
								<div className="rounded border border-dashed border-gray-200 p-2 text-sm">
									<div className="font-medium">验收证据</div>
									<Text type="secondary">{stage.evidence}</Text>
									{stage.key === "development" || stage.key === "evidence" ? (
										<GateEvidenceSummary evidence={gateEvidence} compact className="mt-2" />
									) : null}
								</div>
								<div className="grid gap-2 text-sm">
									<div>
										<div className="text-muted-foreground">负责角色</div>
										<Text>{stage.owner}</Text>
									</div>
									<div>
										<div className="text-muted-foreground">当前缺口</div>
										<Text type="secondary">{stage.gap}</Text>
										{stage.blocker ? (
											<div className="space-y-1">
												<Tag color="orange">{stage.blocker.reason}</Tag>
												{stage.verification === "invalid" && stage.artifactParam ? (
													<Button
														size="small"
														danger
														data-testid={`end-to-end-stage-${stage.key}-clear-invalid`}
														onClick={() =>
															router.push(buildJourneyParamClearUrl("/workbench", searchParams, stage.artifactParam as NonNullable<typeof stage.artifactParam>))
														}
													>
														清除无效参数
													</Button>
												) : null}
											</div>
										) : null}
									</div>
									<div>
										<div className="text-muted-foreground">下一步</div>
										<Text type="secondary">{stage.nextAction.description || stage.nextStep}</Text>
									</div>
								</div>
							</div>
							<Space wrap className="mt-3">
								<Button
									type={index === 0 ? "primary" : "default"}
									data-testid={`end-to-end-stage-${stage.key}-primary`}
									onClick={() => router.push(stage.nextAction.url || withE2EJourney(stage.route))}
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

			<Card title="数仓分层规划" data-testid="warehouse-layer-planning">
				<Alert
					type="info"
					showIcon
					message="先确定数据进入哪一层，再生成模型和 SQL 草稿"
					description="分层规划只表达当前阶段和下一步，不会在接入未完成时伪造模型已就绪。"
				/>
				<div className="mt-4 grid gap-3 xl:grid-cols-5 md:grid-cols-2">
					{warehouseLayerPlan.map((layer) => (
						<div key={layer.key} className="flex min-h-[168px] flex-col justify-between rounded border border-gray-200 p-3">
							<div className="space-y-2">
								<Tag color="blue">{layer.title}</Tag>
								<div className="font-medium">{layer.description}</div>
							</div>
							<Button size="small" onClick={() => router.push(journeyRoute(layer.route))}>
								{layer.action}
							</Button>
						</div>
					))}
				</div>
			</Card>

			<Card
				title="客户验收包"
				data-testid="data-product-acceptance-package"
				data-print-root="true"
				extra={
					<Space wrap>
						<Tag color="green">已具备 {acceptancePackage.readyCount}</Tag>
						<Tag color={acceptancePackage.missingCount > 0 ? "orange" : "default"}>
							缺失项 {acceptancePackage.missingCount}
						</Tag>
						<Tag color={acceptancePackage.blockedCount > 0 ? "red" : "default"}>
							阻断项 {acceptancePackage.blockedCount}
						</Tag>
					</Space>
				}
			>
				<div className="mb-4 flex flex-wrap items-start justify-between gap-3">
					<div className="max-w-3xl">
						<Text type="secondary">
							{acceptancePackage.summary}
						</Text>
					</div>
					<Space wrap>
						<Button onClick={copyAcceptancePackage}>复制验收摘要</Button>
						<Button onClick={downloadAcceptancePackage}>下载 JSON</Button>
						<Button data-testid="acceptance-print-view" onClick={printAcceptancePackage}>
							打印视图
						</Button>
					</Space>
				</div>
				<div className="journey-print-only" style={{ display: "none" }} data-testid="acceptance-print-header">
					<h2 style={{ margin: 0 }}>{acceptancePrintMeta.title}</h2>
					<div>{acceptancePrintMeta.summary}</div>
					<div>上下文：{acceptancePrintMeta.contextSummary}</div>
					<div>生成时间：{acceptancePrintMeta.generatedAt}</div>
					<hr />
				</div>
				<div className="grid gap-3 xl:grid-cols-3 md:grid-cols-2">
					{acceptancePackage.groups.map((group) => (
						<div
							key={group.key}
							className="flex min-h-[148px] flex-col justify-between rounded border border-gray-200 p-3"
							data-testid={`acceptance-package-group-${group.key}`}
						>
							<div className="space-y-2">
								<div className="flex flex-wrap items-center justify-between gap-2">
									<div className="font-medium">{group.title}</div>
									<Tag color={ACCEPTANCE_STATUS_COLOR[group.status]}>
										{ACCEPTANCE_STATUS_LABEL[group.status]}
									</Tag>
								</div>
								<Text type="secondary">{group.description}</Text>
								{group.missingReason ? (
									<div className="text-sm text-orange-600">缺失项：{group.missingReason}</div>
								) : (
									<div className="text-sm text-green-600">证据上下文：{group.paramValue || "可直接查看"}</div>
								)}
							</div>
							<Space wrap className="mt-3">
								<Button size="small" onClick={() => router.push(group.url)}>
									查看证据
								</Button>
								{group.status !== "ready" ? <Tag>{group.recoveryLabel}</Tag> : null}
							</Space>
						</div>
					))}
				</div>
				<div className="journey-print-only" style={{ display: "none" }} data-testid="acceptance-print-signoff">
					<hr />
					<table style={{ width: "100%", borderCollapse: "collapse", marginTop: 12 }}>
						<tbody>
							<tr>
								{acceptancePrintMeta.signColumns.map((column) => (
									<td key={column} style={{ border: "1px solid #999", padding: "24px 8px 8px", width: `${100 / acceptancePrintMeta.signColumns.length}%` }}>
										{column}（签字/日期）：
									</td>
								))}
							</tr>
						</tbody>
					</table>
				</div>
				<style>{`
					@media print {
						body * { visibility: hidden !important; }
						[data-print-root], [data-print-root] * { visibility: visible !important; }
						[data-print-root] { position: absolute !important; left: 0; top: 0; width: 100%; }
						[data-print-root] button { display: none !important; }
						.journey-print-only { display: block !important; }
						[data-print-root] .rounded { page-break-inside: avoid; }
					}
				`}</style>
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
								<Button onClick={() => router.push("/governance/rules/runs")}>治理检查</Button>
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
