import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Input, Select, Space, Tag, Typography } from "antd";
import type { LucideIcon } from "lucide-react";
import {
	Activity,
	ArrowRight,
	BarChart3,
	Boxes,
	CheckCircle2,
	ClipboardCheck,
	Database,
	FileText,
	GitBranch,
	ShieldCheck,
} from "lucide-react";
import { PageHeader } from "@/components/page-header";
import {
	JourneyContextBar,
	createDataProductArtifactValidator,
	extractJourneyContextParams,
	resolveArtifactValidations,
} from "@/components/journey";
import { useRouter, useSearchParams } from "@/routes/hooks";
import { getStandardBindingDraftSnapshot } from "@/api/platformApi";
import { validateGrainApi } from "@/api/sprint64GovernanceApi";
import {
	buildPlanningRoute,
	resolveWarehousePlanningContext,
} from "../governance/warehousePlanningContext";
import { resolveDimensionCandidateGate } from "./dimensionCandidateGate";
import { resolveGrainDeclaration, type GrainDeclaration } from "./grainDeclaration";
import {
	buildStandardBindingDraftSummary,
	getStandardBindingDraft,
	isBackendStandardBindingDraftId,
	type StandardBindingDraft,
	withStandardDraftRoute,
} from "./standardBindingDraft";

const { Text } = Typography;

type LowCodeStep = {
	key:
		| "data_ready"
		| "object_confirmed"
		| "metric_designed"
		| "model_candidate"
		| "publish_ready"
		| "run_evidence";
	title: string;
	status: string;
	summary: string;
	owner: string;
	primaryAction: string;
	primaryRoute: string;
	secondaryAction?: string;
	secondaryRoute?: string;
	icon: LucideIcon;
	tone: string;
};

const LOW_CODE_STEPS: LowCodeStep[] = [
	{
		key: "data_ready",
		title: "数据准备",
		status: "选择业务表或提交接入需求",
		summary: "确认数据来源、同步状态和可见权限；连接配置由专业用户处理。",
		owner: "业务用户确认用途，数据工程师保障接入",
		primaryAction: "选择数据源",
		primaryRoute: "/foundation/data-sources",
		secondaryAction: "创建入湖任务",
		secondaryRoute: "/explore/etl/transform/new",
		icon: Database,
		tone: "blue",
	},
	{
		key: "object_confirmed",
		title: "业务对象确认",
		status: "补齐字段含义、负责人和主题域",
		summary: "把表字段翻译成业务对象、主键、业务时间、敏感字段和标准字段。",
		owner: "数据管家确认语义，平台记录治理缺口",
		primaryAction: "补齐元数据",
		primaryRoute: "/catalog/metadata-management",
		secondaryAction: "查看资产",
		secondaryRoute: "/catalog/assets",
		icon: Boxes,
		tone: "cyan",
	},
	{
		key: "metric_designed",
		title: "指标设计",
		status: "定义指标、维度、粒度和统计周期",
		summary: "用业务语言描述口径，指标工作台承接业务对象和目标场景。",
		owner: "业务用户定义口径，平台校验缺口",
		primaryAction: "进入指标设计",
		primaryRoute:
			"/modeling/metric-workbench?journey=low-code-development&target=report&sourceDatasetId=business-table&businessObjectId=business-object",
		icon: BarChart3,
		tone: "green",
	},
	{
		key: "model_candidate",
		title: "汇总与应用模型",
		status: "生成候选模型并等待审核",
		summary: "由指标、维度、粒度和消费目标生成汇总模型或报表数据集候选。",
		owner: "平台生成候选，数据工程师处理复杂关联",
		primaryAction: "查看模型候选",
		primaryRoute: "/modeling/semantic/models?journey=low-code-development&target=report",
		secondaryAction: "高级建模",
		secondaryRoute: "/studio/sql-modeling",
		icon: GitBranch,
		tone: "purple",
	},
	{
		key: "publish_ready",
		title: "发布审核",
		status: "检查质量、权限和口径门禁",
		summary: "确认负责人、密级、主题域、质量规则、权限范围和报表数据集说明。",
		owner: "数据管家审核，平台阻断风险发布",
		primaryAction: "提交发布审核",
		primaryRoute: "/modeling/semantic/publish?journey=low-code-development&target=report",
		icon: ShieldCheck,
		tone: "gold",
	},
	{
		key: "run_evidence",
		title: "运行证据",
		status: "查看调度、运行、失败和补数",
		summary: "发布后的任务实例、运行日志、失败恢复和补数入口统一回到运维中心。",
		owner: "运维保障运行，业务用户查看结果",
		primaryAction: "查看运行证据",
		primaryRoute: "/ops/instances?entryKey=DBT_RUN&journey=low-code-development",
		secondaryAction: "运行概览",
		secondaryRoute: "/ops/overview",
		icon: Activity,
		tone: "geekblue",
	},
];

const CONSUMPTION_TARGETS = [
	{ title: "报表数据集", route: "/bi/report-factory", desc: "给报表工厂使用" },
	{ title: "大屏数据源", route: "/bi/screens", desc: "给驾驶舱和大屏使用" },
	{ title: "数据 API", route: "/services/apis", desc: "给系统接口调用" },
	{ title: "数据产品", route: "/services/products", desc: "给固定场景交付" },
];

const ADVANCED_LINKS = [
	{ title: "业务过程管理", route: "/studio/projects" },
	{ title: "逻辑建模（SQL）", route: "/studio/sql-modeling" },
	{ title: "脚本开发", route: "/explore/etl/scripts" },
	{ title: "任务编排", route: "/explore/etl/orchestration" },
	{ title: "项目文件浏览", route: "/modeling/dbt-files" },
];

export default function LowCodeDevelopmentPage() {
	const router = useRouter();
	const searchParams = useSearchParams();
	const standardDraftId = useMemo(() => {
		return searchParams.get("standardDraftId") || "";
	}, [searchParams]);
	const [standardDraft, setStandardDraft] = useState<StandardBindingDraft | null>(() => getStandardBindingDraft(standardDraftId));
	const routeWithStandardDraft = (route: string) => withStandardDraftRoute(route, standardDraftId);
	const planningResolution = useMemo(() => resolveWarehousePlanningContext(searchParams), [searchParams]);
	const planningContext = planningResolution.context;
	const dimensionMode = searchParams.get("modelingMode") === "dimension" || planningContext?.modelingMode === "dimension";
	const routeWithPlanningContext = (route: string) => {
		const routeWithDraft = routeWithStandardDraft(route);
		return planningContext ? buildPlanningRoute(routeWithDraft, planningContext) : routeWithDraft;
	};
	const ingestionReadiness = useMemo(() => {
		const sourceId = searchParams.get("sourceId") || "";
		const status = String(searchParams.get("ingestionStatus") || "").toLowerCase();
		if (!sourceId) return { label: "未接入", description: "请先选择数据源并完成连接测试。", ready: false };
		if (["success", "succeeded", "ready"].includes(status)) {
			return { label: "同步成功", description: "数据已具备建模前置条件。", ready: true };
		}
		if (["running", "syncing"].includes(status)) {
			return { label: "同步中", description: "同步任务仍在运行，完成后再生成模型草稿。", ready: false };
		}
		if (["failed", "error"].includes(status)) {
			return { label: "接入失败", description: "请查看运行记录并修复接入后再进入建模。", ready: false };
		}
		return { label: "待确认", description: "已选择数据源，请完成一次同步验证。", ready: false };
	}, [searchParams]);
	const standardDraftSummary = useMemo(() => buildStandardBindingDraftSummary(standardDraft), [standardDraft]);
	const processId = planningContext?.processId || searchParams.get("processId") || "";
	const grainFieldNames = useMemo(
		() => (standardDraft?.fields || []).map((field) => String(field.columnName || "").trim()).filter(Boolean),
		[standardDraft],
	);
	const [grainStatement, setGrainStatement] = useState("");
	const [grainKeys, setGrainKeys] = useState<string[]>([]);
	const [grainValidationMessage, setGrainValidationMessage] = useState("");
	const grainDeclaration: GrainDeclaration = { statement: grainStatement, grainKeys };
	const grainGate = useMemo(() => resolveGrainDeclaration(grainDeclaration, grainFieldNames), [grainFieldNames, grainKeys, grainStatement]);
	const candidateGate = useMemo(
		() =>
			resolveDimensionCandidateGate({
				planningContext,
				planningSource: planningResolution.source,
				planningBlockedReason: planningResolution.status === "blocked" ? planningResolution.reason : undefined,
				standardDraftId,
				standardFieldCount: standardDraft?.fields.length ?? 0,
				grainRequired: dimensionMode,
				grainDeclaration,
				grainFieldNames,
			}),
		[dimensionMode, grainDeclaration, grainFieldNames, planningContext, planningResolution.reason, planningResolution.source, planningResolution.status, standardDraft, standardDraftId],
	);
	const candidateRepairRoute = candidateGate.repairRoute;
	const dimensionModelReady = candidateGate.status === "ready" && (!dimensionMode || grainGate.status === "ready");
	const checkGrainDeclaration = async () => {
		if (!grainFieldNames.length) {
			setGrainValidationMessage("请先接入标准落标草稿，系统才能校验粒度键。");
			return;
		}
		try {
			const result = await validateGrainApi({ warehouseLayer: "DWD", statement: grainStatement, grainKeys });
			setGrainValidationMessage(result.message);
		} catch {
			setGrainValidationMessage(grainGate.reason);
		}
	};

	useEffect(() => {
		let cancelled = false;
		const loadDraft = async () => {
			if (!standardDraftId) {
				setStandardDraft(null);
				return;
			}
			const sessionDraft = getStandardBindingDraft(standardDraftId);
			if (!isBackendStandardBindingDraftId(standardDraftId)) {
				setStandardDraft(sessionDraft);
				return;
			}
			try {
				const draft = (await getStandardBindingDraftSnapshot(standardDraftId)) as StandardBindingDraft;
				if (!cancelled) setStandardDraft(draft || sessionDraft);
			} catch {
				if (!cancelled) setStandardDraft(sessionDraft);
			}
		};
		void loadDraft();
		return () => {
			cancelled = true;
		};
	}, [standardDraftId]);

	return (
		<div className="space-y-5 px-6 py-5" data-testid="low-code-development-page">
			<PageHeader
				title="数据开发 / 低代码开发向导"
				actions={
					<Space wrap>
						<Button onClick={() => router.push("/workbench?section=data-management&journey=first-report")}>
							<FileText size={16} />
							首张报表
						</Button>
						<Button type="primary" onClick={() => router.push("/foundation/data-sources")}>
							<Database size={16} />
							选择业务表
						</Button>
					</Space>
				}
			/>
			<JourneyContextBar
				stage="modeling"
				validations={resolveArtifactValidations(
					extractJourneyContextParams(
						typeof window === "undefined" ? new URLSearchParams() : new URLSearchParams(window.location.search),
					),
					createDataProductArtifactValidator({
						standardDraft: { findDraft: getStandardBindingDraft, isBackendDraftId: isBackendStandardBindingDraftId },
					}),
				)}
			/>

			<Alert
				type="info"
				showIcon
				message="从业务表到指标和报表数据集"
				description="低代码入口负责表达业务意图和串联状态；连接配置、基础明细模型发布和复杂关联仍由专业用户审核。"
			/>
			{dimensionMode ? (
				<Alert
					showIcon
					data-testid="dimension-modeling-context"
					type={candidateGate.status === "ready" ? "success" : candidateGate.status === "blocked" ? "error" : "warning"}
					message="DWD 维度模型候选"
					description={`主题域：${planningContext?.domainName || planningContext?.domainId || "-"} · 标准来源：${standardDraft ? standardDraftSummary.sourceLabel : "未接入"} · ${candidateGate.reason}`}
					action={
						candidateGate.status !== "ready" && candidateRepairRoute ? (
							<Button size="small" onClick={() => router.push(candidateRepairRoute)}>
								修复前置条件
							</Button>
						) : undefined
					}
				/>
			) : null}
			{dimensionMode ? (
				<Card size="small" data-testid="grain-declaration" title="DWD 粒度声明">
					<Space direction="vertical" className="w-full" size="small">
						<Text type="secondary">
							业务过程：{processId || "未绑定"} · 粒度是模型发布的硬门禁，必须说明“一行代表什么”并绑定字段键。
						</Text>
						<Input.TextArea
							rows={2}
							placeholder="粒度语句，例如：一行代表一个节点在一个计划周期内的当前状态"
							value={grainStatement}
							onChange={(event) => setGrainStatement(event.target.value)}
						/>
						<Select
							mode="multiple"
							className="w-full"
							placeholder="选择粒度键"
							value={grainKeys}
							onChange={setGrainKeys}
							options={grainFieldNames.map((field) => ({ label: field, value: field }))}
						/>
						<Space wrap>
							<Tag color={grainGate.status === "ready" ? "green" : grainGate.status === "blocked" ? "red" : "orange"}>
								{grainGate.status === "ready" ? "粒度已声明" : grainGate.reason}
							</Tag>
							<Button size="small" onClick={() => void checkGrainDeclaration()}>
								校验粒度
							</Button>
							{grainValidationMessage ? <Text type="secondary">{grainValidationMessage}</Text> : null}
						</Space>
					</Space>
				</Card>
			) : null}
			{standardDraft ? (
				<Alert
					type="success"
					showIcon
					data-testid="low-code-standard-binding-draft-ready"
					message="标准落标草稿已接入"
					description={`已接收 ${standardDraft.fields.length} 个字段标准，后续建模会携带字段名、标准类型、码表和密级，进入 SQL 建模后可生成模型草稿和可微调 SQL。`}
					action={
						<Space wrap>
							<Button size="small" type="primary" onClick={() => router.push(routeWithPlanningContext("/studio/sql-modeling"))}>
								进入 SQL 建模
							</Button>
							<Button size="small" onClick={() => router.push(routeWithPlanningContext("/modeling/semantic/models?journey=low-code-development"))}>
								查看模型候选
							</Button>
						</Space>
					}
				/>
			) : null}
			<Card size="small" data-testid="ingestion-readiness" title="建模前置条件">
				<Space wrap>
					<Tag color={ingestionReadiness.ready ? "green" : "orange"}>{ingestionReadiness.label}</Tag>
					<Text type="secondary">{ingestionReadiness.description}</Text>
					{standardDraft ? (
						<Text type="secondary">
							标准来源：{standardDraftSummary.sourceLabel} · 字段 {standardDraftSummary.fieldCount} · 待补标准 {standardDraftSummary.missingStandardCount} · 创建时间 {standardDraftSummary.createdAt || "-"}
						</Text>
					) : null}
				</Space>
			</Card>

			<div className="grid gap-3 md:grid-cols-3">
				<Card size="small" className="rounded-lg">
					<div className="flex items-center gap-2">
						<CheckCircle2 size={18} className="text-emerald-600" />
						<div>
							<div className="text-lg font-semibold">3</div>
							<Text type="secondary">可自助推进节点</Text>
						</div>
					</div>
				</Card>
				<Card size="small" className="rounded-lg">
					<div className="flex items-center gap-2">
						<ClipboardCheck size={18} className="text-amber-600" />
						<div>
							<div className="text-lg font-semibold">2</div>
							<Text type="secondary">需要工程审核节点</Text>
						</div>
					</div>
				</Card>
				<Card size="small" className="rounded-lg">
					<div className="flex items-center gap-2">
						<ShieldCheck size={18} className="text-blue-600" />
						<div>
							<div className="text-lg font-semibold">1</div>
							<Text type="secondary">发布与运行证据闭环</Text>
						</div>
					</div>
				</Card>
			</div>

			<div className="grid gap-4 xl:grid-cols-3 md:grid-cols-2">
				{LOW_CODE_STEPS.map((step, index) => {
					const Icon = step.icon;
					return (
						<Card
							key={step.key}
							className="rounded-lg"
							data-testid={`low-code-development-step-${step.key}`}
							title={
								<div className="flex items-center gap-2">
									<Tag color={step.tone}>{index + 1}</Tag>
									<span>{step.title}</span>
								</div>
							}
						>
							<div className="flex min-h-[236px] flex-col gap-4">
								<div className="flex items-start gap-3">
									<span className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-700">
										<Icon size={20} />
									</span>
									<div className="min-w-0">
										<div className="font-medium text-gray-900">{step.status}</div>
										<Text type="secondary">{step.summary}</Text>
									</div>
								</div>
								<Tag className="w-fit" color="default">
									{step.owner}
								</Tag>
								<div className="mt-auto flex flex-wrap gap-2">
									<Button
										type="primary"
											disabled={step.key === "model_candidate" && (!ingestionReadiness.ready || (dimensionMode && !dimensionModelReady))}
									title={
										step.key === "model_candidate"
											? !ingestionReadiness.ready
												? ingestionReadiness.description
													: dimensionMode && !dimensionModelReady
														? candidateGate.status !== "ready" ? candidateGate.reason : grainGate.reason
													: undefined
											: undefined
									}
									onClick={() => router.push(routeWithPlanningContext(step.primaryRoute))}
								>
									{step.key === "model_candidate" && dimensionMode ? "查看维度模型候选" : step.primaryAction}
										<ArrowRight size={14} />
									</Button>
									{step.secondaryRoute && step.secondaryAction ? (
									<Button onClick={() => router.push(routeWithPlanningContext(step.secondaryRoute!))}>{step.secondaryAction}</Button>
									) : null}
								</div>
							</div>
						</Card>
					);
				})}
			</div>

			<Card title="消费目标" className="rounded-lg">
				<div className="grid gap-3 md:grid-cols-4">
					{CONSUMPTION_TARGETS.map((target) => (
						<Button key={target.title} className="h-auto justify-start py-3" onClick={() => router.push(routeWithPlanningContext(target.route))}>
							<div className="text-left">
								<div className="font-medium">{target.title}</div>
								<div className="text-xs text-gray-500">{target.desc}</div>
							</div>
						</Button>
					))}
				</div>
			</Card>

			<Card title="高级开发入口" className="rounded-lg" data-testid="advanced-development-links">
				<Space wrap>
					{ADVANCED_LINKS.map((item) => (
						<Button key={item.route} onClick={() => router.push(routeWithPlanningContext(item.route))}>
							{item.title}
						</Button>
					))}
				</Space>
			</Card>
		</div>
	);
}
