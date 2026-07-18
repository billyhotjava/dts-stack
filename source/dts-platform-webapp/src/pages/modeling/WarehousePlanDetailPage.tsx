import { Alert, Button, Card, Descriptions, Result, Segmented, Skeleton, Space, Tabs, Tag, Typography } from "antd";
import { ArrowLeft, ArrowRight, Boxes, Database, FileCheck2, Network, PackageCheck, Waypoints } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router";
import {
	getWarehousePlan,
	getWarehousePlanningBaseline,
	getWarehousePlanStageProjection,
	type PlanningBaseline,
	type WarehousePlanHeader,
	type WarehousePlanStageProjection,
} from "@/api/warehousePlanApi";
import { useSearchParams } from "@/routes/hooks";
import {
	buildWarehousePlanRoute,
	warehouseBlockerMessage,
	warehouseStageActionLabel,
	warehouseStageLabel,
	withWarehousePlanContext,
} from "./warehousePlanViewModel";

const { Paragraph, Text, Title } = Typography;

type DetailSection = "overview" | "baseline" | "architecture" | "models" | "implementation" | "deliverables";

const sectionPath: Record<DetailSection, string> = {
	overview: "",
	baseline: "/baseline",
	architecture: "/architecture",
	models: "/models",
	implementation: "/implementation",
	deliverables: "/deliverables",
};

const lifecycleLabel: Record<WarehousePlanHeader["lifecycleStatus"], string> = {
	DRAFT: "规划中",
	BASELINE_READY: "基线已确认",
	MODELING: "建模中",
	IMPLEMENTING: "实现中",
	PUBLISHED: "已发布",
	ARCHIVED: "已归档",
};

const missingLabel: Record<string, string> = {
	BUSINESS_SCOPE_INCOMPLETE: "业务范围尚未确认",
	DOMAIN_PROCESS_CONFIRMATION_INCOMPLETE: "主题域和业务过程尚未全部确认",
	SOURCE_INVENTORY_INCOMPLETE: "来源盘点尚未确认",
	SOURCE_BUSINESS_MAPPING_INCOMPLETE: "来源与业务映射尚未完成",
	PLANNING_POLICY_INCOMPLETE: "分层、命名或历史策略尚未补齐",
};

const resolveSection = (pathname: string): DetailSection => {
	const entry = (Object.entries(sectionPath) as Array<[DetailSection, string]>)
		.filter(([, suffix]) => suffix && pathname.endsWith(suffix))
		.at(0);
	return entry?.[0] || "overview";
};

export default function WarehousePlanDetailPage() {
	const { planId = "" } = useParams<{ planId: string }>();
	const location = useLocation();
	const navigate = useNavigate();
	const searchParams = useSearchParams();
	const [plan, setPlan] = useState<WarehousePlanHeader | null>(null);
	const [baseline, setBaseline] = useState<PlanningBaseline | null>(null);
	const [projection, setProjection] = useState<WarehousePlanStageProjection | null>(null);
	const [loading, setLoading] = useState(true);
	const [failed, setFailed] = useState(false);
	const activeSection = useMemo(() => resolveSection(location.pathname), [location.pathname]);
	const baselineTab = searchParams.get("tab") === "sources" ? "sources" : "business-scope";

	const load = useCallback(async () => {
		if (!planId) return;
		setLoading(true);
		setFailed(false);
		try {
			const [header, planningBaseline, stageProjection] = await Promise.all([
				getWarehousePlan(planId),
				getWarehousePlanningBaseline(planId),
				getWarehousePlanStageProjection(planId),
			]);
			setPlan(header);
			setBaseline(planningBaseline);
			setProjection(stageProjection);
		} catch {
			setFailed(true);
		} finally {
			setLoading(false);
		}
	}, [planId]);

	useEffect(() => {
		void load();
	}, [load]);

	const openSpecialist = (route: string) => navigate(withWarehousePlanContext(route, planId));
	const openSection = (section: DetailSection) => navigate(buildWarehousePlanRoute(planId, section));

	if (!planId) {
		return <Result status="404" title="缺少计划标识" extra={<Button onClick={() => navigate("/modeling/workbench")}>返回工作台</Button>} />;
	}
	if (loading) {
		return <div className="mx-auto max-w-[1480px] p-6"><Skeleton active paragraph={{ rows: 10 }} /></div>;
	}
	if (failed || !plan) {
		return (
			<div className="mx-auto max-w-[1180px] p-6">
				<Alert type="error" showIcon message="计划详情暂时不可用" description="读取失败不会改变计划或阶段状态。" action={<Button onClick={() => void load()}>重新加载</Button>} />
			</div>
		);
	}

	const tabItems = [
		{ key: "overview", label: "规划概览" },
		{ key: "baseline", label: "规划基线" },
		{ key: "architecture", label: "数仓架构" },
		{ key: "models", label: "事实与维度" },
		{ key: "implementation", label: "实现与验证" },
		{ key: "deliverables", label: "发布成果" },
	];

	return (
		<div className="mx-auto w-full max-w-[1480px] space-y-4 p-4 md:p-6" data-testid="warehouse-plan-detail">
			<header className="rounded-[22px] border border-slate-200 bg-white px-5 pt-5 md:px-7 md:pt-6">
				<div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
					<div>
						<Button type="link" className="-ml-3 mb-1 px-2 text-slate-500" icon={<ArrowLeft size={15} />} onClick={() => navigate(`/modeling/workbench?planId=${encodeURIComponent(planId)}`)}>
							返回数据建设工作台
						</Button>
						<div className="flex flex-wrap items-center gap-3">
							<Title level={2} style={{ margin: 0 }}>{plan.name}</Title>
							<Tag color="blue">{lifecycleLabel[plan.lifecycleStatus]}</Tag>
						</div>
						<div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-sm text-slate-500">
							<span>计划负责人：{plan.ownerId}</span>
							<span>开始方式：{plan.onboardingMode === "ASSET_FIRST" ? "从现有数据开始" : "从业务目标开始"}</span>
							<span>当前阶段：{projection?.currentStage ? warehouseStageLabel(projection.currentStage) : "证据已齐备"}</span>
						</div>
					</div>
					{projection?.nextAction ? (
						<Button type="primary" size="large" onClick={() => openSpecialist(projection.nextAction!.path)}>
							{projection.currentStage ? warehouseStageActionLabel(projection.currentStage) : projection.nextAction.label}<ArrowRight size={16} />
						</Button>
					) : null}
				</div>
				<Tabs className="mt-5" activeKey={activeSection} items={tabItems} onChange={(key) => openSection(key as DetailSection)} />
			</header>

			{projection?.primaryBlocker ? (
				<Alert
					type="warning"
					showIcon
					message={`首要阻塞 · ${warehouseStageLabel(projection.primaryBlocker.stageCode)}`}
					description={warehouseBlockerMessage(projection.primaryBlocker.code, projection.primaryBlocker.message)}
				/>
			) : null}

			{activeSection === "overview" ? (
				<div className="grid gap-4 lg:grid-cols-[1.2fr_0.8fr]">
					<Card title="为什么建设">
						<Descriptions column={1} size="small">
							<Descriptions.Item label="建设目标">{plan.objective || "待补充"}</Descriptions.Item>
							<Descriptions.Item label="初始范围">{plan.scope || "待补充"}</Descriptions.Item>
							<Descriptions.Item label="负责部门">{plan.ownerDepartmentId || "待补充"}</Descriptions.Item>
						</Descriptions>
					</Card>
					<Card title="规划基线">
						<div className="flex items-center justify-between gap-4">
							<div>
								<div className="text-lg font-semibold">{baseline?.ready ? "已具备建模基线" : "仍有基线缺口"}</div>
								<Text type="secondary">{baseline?.ready ? "业务范围、来源和策略均已确认" : `${baseline?.missingCodes.length || 0} 项待处理`}</Text>
							</div>
							<Button onClick={() => openSection("baseline")}>查看基线</Button>
						</div>
					</Card>
				</div>
			) : null}

			{activeSection === "baseline" ? (
				<Card>
					<div className="mb-5 flex flex-wrap items-center justify-between gap-3">
						<div>
							<Title level={4} style={{ margin: 0 }}>规划基线</Title>
							<Text type="secondary">确认业务范围、来源盘点以及两者映射，再进入模型设计。</Text>
						</div>
						<Segmented
							value={baselineTab}
							options={[{ label: "业务范围", value: "business-scope" }, { label: "来源盘点", value: "sources" }]}
							onChange={(value) => navigate(buildWarehousePlanRoute(planId, "baseline", { tab: String(value) }))}
						/>
					</div>
					{baseline?.ready ? <Alert type="success" showIcon message="规划基线已确认" /> : (
						<div className="space-y-2">
							{(baseline?.missingCodes || []).map((code) => <Alert key={code} type="warning" showIcon message={missingLabel[code] || code} />)}
						</div>
					)}
					<div className="mt-6 flex flex-wrap gap-2">
						{baselineTab === "business-scope" ? (
							<Button icon={<Waypoints size={16} />} onClick={() => openSpecialist("/governance/subjects")}>管理主题域与业务过程</Button>
						) : (
							<Button icon={<Database size={16} />} onClick={() => openSpecialist("/catalog/metadata-management")}>盘点现有数据</Button>
						)}
					</div>
				</Card>
			) : null}

			{activeSection === "architecture" ? (
				<SpecialistSection
					title="数仓架构"
					description="查看分层、命名、历史保留与来源映射；详细配置仍由规划和元数据模块负责。"
					actions={[
						{ label: "规划主题与分层", route: "/governance/subjects", icon: <Network size={17} /> },
						{ label: "核对元数据", route: "/catalog/metadata-management", icon: <Database size={17} /> },
					]}
					onOpen={openSpecialist}
				/>
			) : null}

			{activeSection === "models" ? (
				<SpecialistSection
					title="事实与维度"
					description="维护模型台账、粒度、时间语义和关系；计划详情只提供上下文和入口。"
					actions={[{ label: "进入模型中心", route: "/modeling/semantic/models", icon: <Boxes size={17} /> }]}
					onOpen={openSpecialist}
				/>
			) : null}

			{activeSection === "implementation" ? (
				<SpecialistSection
					title="实现与验证"
					description="在模型设计稳定后进入 SQL、dbt、测试和发布门禁。"
					actions={[
						{ label: "高级建模（SQL）", route: "/studio/sql-modeling", icon: <FileCheck2 size={17} /> },
						{ label: "高级 dbt", route: "/modeling/dbt-files", icon: <PackageCheck size={17} /> },
					]}
					onOpen={openSpecialist}
				/>
			) : null}

			{activeSection === "deliverables" ? (
				<SpecialistSection
					title="发布成果"
					description="从统一计划追踪资产、指标和运行证据，不在此维护第二套成果台账。"
					actions={[
						{ label: "查看数据资产", route: "/catalog/assets", icon: <Boxes size={17} /> },
						{ label: "查看指标系统", route: "/modeling/metric-workbench", icon: <Network size={17} /> },
						{ label: "查看运行记录", route: "/ops/instances", icon: <FileCheck2 size={17} /> },
					]}
					onOpen={openSpecialist}
				/>
			) : null}
		</div>
	);
}

function SpecialistSection({
	title,
	description,
	actions,
	onOpen,
}: {
	title: string;
	description: string;
	actions: Array<{ label: string; route: string; icon: React.ReactNode }>;
	onOpen: (route: string) => void;
}) {
	return (
		<Card>
			<div className="max-w-3xl">
				<Title level={4} style={{ margin: 0 }}>{title}</Title>
				<Paragraph className="mt-2 text-slate-500">{description}</Paragraph>
				<Space wrap>{actions.map((action) => <Button key={action.route} icon={action.icon} onClick={() => onOpen(action.route)}>{action.label}</Button>)}</Space>
			</div>
		</Card>
	);
}
