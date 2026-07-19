import {
	Alert,
	Button,
	Card,
	Descriptions,
	Form,
	Input,
	Result,
	Select,
	Skeleton,
	Space,
	Tabs,
	Tag,
	Typography,
} from "antd";
import {
	ArrowLeft,
	ArrowRight,
	Boxes,
	Database,
	FileCheck2,
	Network,
	PackageCheck,
	Plus,
	Trash2,
	Waypoints,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import { getDomainTree } from "@/api/platformApi";
import {
	getWarehousePlan,
	getWarehousePlanCategories,
	getWarehousePlanningBaseline,
	getWarehousePlanPolicy,
	getWarehousePlanStageProjection,
	type PlanningBaseline,
	saveWarehousePlanCategories,
	saveWarehousePlanPolicy,
	type VersionedWarehousePlanValue,
	type WarehousePlanCategoryBindingInput,
	type WarehousePlanCategoryScopeView,
	type WarehousePlanHeader,
	type WarehousePlanPolicyInput,
	type WarehousePlanPolicyView,
	type WarehousePlanStageProjection,
} from "@/api/warehousePlanApi";
import { useSearchParams } from "@/routes/hooks";
import { createLatestRequestGuard } from "./warehousePlanCreateFlow";
import {
	buildBusinessCategoryManagementRoute,
	buildWarehouseCategoryOptions,
	buildWarehousePlanRoute,
	resolveWarehousePlanConflictVersion,
	warehouseBlockerMessage,
	warehousePlanIssueMessage,
	warehousePlanMutationErrorMessage,
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
	DESIGNING: "设计中",
	VALIDATING: "验证中",
	READY_TO_PUBLISH: "待发布",
	PUBLISHED: "已发布",
	ARCHIVED: "已归档",
};

type BaselineTab = "categories" | "layers" | "sources";
type BaselineInputReplace = "none" | "categories" | "policy" | "all";
type CategoryFormValue = { domainBindings: WarehousePlanCategoryBindingInput[] };
type PolicyFormValue = WarehousePlanPolicyInput;
type DomainOptionNode = { id?: string; name?: string; code?: string; children?: DomainOptionNode[] };

const flattenDomainOptions = (nodes: DomainOptionNode[], result: DomainOptionNode[] = []): DomainOptionNode[] => {
	for (const node of nodes) {
		if (node.id) result.push(node);
		if (node.children?.length) flattenDomainOptions(node.children, result);
	}
	return result;
};

const categoryReadinessLabel: Record<WarehousePlanCategoryScopeView["readiness"], string> = {
	DRAFT: "待确认",
	READY: "可以进入模型设计",
	BLOCKED: "需要修复分类",
};

const policyReadinessLabel: Record<WarehousePlanPolicyView["readiness"], string> = {
	DRAFT: "待确认分层",
	MODEL_DESIGN_READY: "可以进入模型设计",
	IMPLEMENTATION_READY: "可以进入模型实现",
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
	const [categoryScope, setCategoryScope] =
		useState<VersionedWarehousePlanValue<WarehousePlanCategoryScopeView> | null>(null);
	const [planningPolicy, setPlanningPolicy] = useState<VersionedWarehousePlanValue<WarehousePlanPolicyView> | null>(
		null,
	);
	const [domainOptions, setDomainOptions] = useState<DomainOptionNode[]>([]);
	const [loading, setLoading] = useState(true);
	const [failed, setFailed] = useState(false);
	const [evidenceFailed, setEvidenceFailed] = useState(false);
	const [baselineInputsLoading, setBaselineInputsLoading] = useState(false);
	const [baselineInputsFailed, setBaselineInputsFailed] = useState(false);
	const [categorySaving, setCategorySaving] = useState(false);
	const [policySaving, setPolicySaving] = useState(false);
	const [categoryConflictVersion, setCategoryConflictVersion] = useState<number | null>(null);
	const [policyConflictVersion, setPolicyConflictVersion] = useState<number | null>(null);
	const [categoryDirty, setCategoryDirtyValue] = useState(false);
	const [policyDirty, setPolicyDirtyValue] = useState(false);
	const categoryDirtyRef = useRef(false);
	const policyDirtyRef = useRef(false);
	const [categoryForm] = Form.useForm<CategoryFormValue>();
	const [policyForm] = Form.useForm<PolicyFormValue>();
	const loadGuard = useMemo(() => createLatestRequestGuard(), []);
	const evidenceLoadGuard = useMemo(() => createLatestRequestGuard(), []);
	const baselineInputsLoadGuard = useMemo(() => createLatestRequestGuard(), []);
	const activeSection = useMemo(() => resolveSection(location.pathname), [location.pathname]);
	const requestedBaselineTab = searchParams.get("tab");
	const baselineTab: BaselineTab =
		requestedBaselineTab === "sources" || requestedBaselineTab === "layers" || requestedBaselineTab === "categories"
			? requestedBaselineTab
			: plan?.onboardingMode === "ASSET_FIRST"
				? "sources"
				: "categories";
	const setCategoryDirty = useCallback((dirty: boolean) => {
		categoryDirtyRef.current = dirty;
		setCategoryDirtyValue(dirty);
	}, []);
	const setPolicyDirty = useCallback((dirty: boolean) => {
		policyDirtyRef.current = dirty;
		setPolicyDirtyValue(dirty);
	}, []);

	const loadEvidence = useCallback(async () => {
		if (!planId) return;
		const isCurrent = evidenceLoadGuard.begin();
		setEvidenceFailed(false);
		const [baselineResult, projectionResult] = await Promise.allSettled([
			getWarehousePlanningBaseline(planId),
			getWarehousePlanStageProjection(planId),
		]);
		if (!isCurrent()) return;
		setBaseline(baselineResult.status === "fulfilled" ? baselineResult.value : null);
		setProjection(projectionResult.status === "fulfilled" ? projectionResult.value : null);
		setEvidenceFailed(baselineResult.status === "rejected" || projectionResult.status === "rejected");
	}, [evidenceLoadGuard, planId]);

	const loadBaselineInputs = useCallback(
		async (replace: BaselineInputReplace = "none") => {
			if (!planId) return;
			const isCurrent = baselineInputsLoadGuard.begin();
			setBaselineInputsLoading(true);
			setBaselineInputsFailed(false);
			const [categoryResult, policyResult, domainsResult] = await Promise.allSettled([
				getWarehousePlanCategories(planId),
				getWarehousePlanPolicy(planId),
				getDomainTree(),
			]);
			if (!isCurrent()) return;
			const replaceCategory = replace === "all" || replace === "categories" || !categoryDirtyRef.current;
			const replacePolicy = replace === "all" || replace === "policy" || !policyDirtyRef.current;
			if (categoryResult.status === "fulfilled" && replaceCategory) {
				setCategoryScope(categoryResult.value);
				categoryForm.setFieldsValue({
					domainBindings: categoryResult.value.value.domainBindings.map(({ domainId, confirmationStatus }) => ({
						domainId,
						confirmationStatus,
					})),
				});
				setCategoryDirty(false);
				setCategoryConflictVersion(null);
			}
			if (policyResult.status === "fulfilled" && replacePolicy) {
				setPlanningPolicy(policyResult.value);
				policyForm.setFieldsValue({
					layerScheme: policyResult.value.value.layerScheme,
					namingPolicy: policyResult.value.value.namingPolicy,
					historyPolicy: policyResult.value.value.historyPolicy,
					defaultTimeZone: policyResult.value.value.defaultTimeZone || null,
				});
				setPolicyDirty(false);
				setPolicyConflictVersion(null);
			}
			if (domainsResult.status === "fulfilled") {
				setDomainOptions(flattenDomainOptions(Array.isArray(domainsResult.value) ? domainsResult.value : []));
			}
			setBaselineInputsFailed(
				categoryResult.status === "rejected" ||
					policyResult.status === "rejected" ||
					domainsResult.status === "rejected",
			);
			setBaselineInputsLoading(false);
		},
		[baselineInputsLoadGuard, categoryForm, planId, policyForm, setCategoryDirty, setPolicyDirty],
	);

	const refreshBaselineWorkspace = useCallback(
		async (replace: BaselineInputReplace = "none") => {
			await Promise.all([loadBaselineInputs(replace), loadEvidence()]);
		},
		[loadBaselineInputs, loadEvidence],
	);

	const load = useCallback(async () => {
		if (!planId) return;
		const isCurrent = loadGuard.begin();
		evidenceLoadGuard.invalidate();
		baselineInputsLoadGuard.invalidate();
		setLoading(true);
		setFailed(false);
		setEvidenceFailed(false);
		setPlan(null);
		setBaseline(null);
		setProjection(null);
		setCategoryScope(null);
		setPlanningPolicy(null);
		setDomainOptions([]);
		setCategoryDirty(false);
		setPolicyDirty(false);
		setCategoryConflictVersion(null);
		setPolicyConflictVersion(null);
		categoryForm.resetFields();
		policyForm.resetFields();
		try {
			const header = await getWarehousePlan(planId);
			if (!isCurrent()) return;
			setPlan(header);
			setLoading(false);
			await refreshBaselineWorkspace("all");
		} catch {
			if (!isCurrent()) return;
			setFailed(true);
		} finally {
			if (isCurrent()) setLoading(false);
		}
	}, [
		baselineInputsLoadGuard,
		categoryForm,
		evidenceLoadGuard,
		loadGuard,
		planId,
		policyForm,
		refreshBaselineWorkspace,
		setCategoryDirty,
		setPolicyDirty,
	]);

	useEffect(() => {
		void load();
		return () => {
			loadGuard.invalidate();
			evidenceLoadGuard.invalidate();
			baselineInputsLoadGuard.invalidate();
		};
	}, [baselineInputsLoadGuard, evidenceLoadGuard, load, loadGuard]);

	const saveCategories = async (expectedVersion?: number) => {
		if (!categoryScope) return;
		try {
			const values = await categoryForm.validateFields();
			const bindings = values.domainBindings || [];
			if (new Set(bindings.map((item) => item.domainId)).size !== bindings.length) {
				categoryForm.setFields([{ name: "domainBindings", errors: ["同一业务分类不能重复添加"] }]);
				return;
			}
			setCategorySaving(true);
			await saveWarehousePlanCategories(planId, expectedVersion ?? categoryScope.version, bindings);
			setCategoryDirty(false);
			setCategoryConflictVersion(null);
			toast.success("业务分类已保存");
			await refreshBaselineWorkspace("categories");
		} catch (error: any) {
			if (error?.errorFields) return;
			const currentVersion = resolveWarehousePlanConflictVersion(error);
			if (currentVersion != null) {
				setCategoryConflictVersion(currentVersion);
			} else {
				toast.error(warehousePlanMutationErrorMessage(error, "业务分类保存失败"));
			}
		} finally {
			setCategorySaving(false);
		}
	};

	const savePolicy = async (expectedVersion?: number) => {
		if (!planningPolicy) return;
		try {
			const values = await policyForm.validateFields();
			setPolicySaving(true);
			await saveWarehousePlanPolicy(planId, expectedVersion ?? planningPolicy.version, {
				layerScheme: values.layerScheme,
				namingPolicy: values.namingPolicy || null,
				historyPolicy: values.historyPolicy || null,
				defaultTimeZone: values.defaultTimeZone?.trim() || null,
			});
			setPolicyDirty(false);
			setPolicyConflictVersion(null);
			toast.success("数仓分层策略已保存");
			await refreshBaselineWorkspace("policy");
		} catch (error: any) {
			if (error?.errorFields) return;
			const currentVersion = resolveWarehousePlanConflictVersion(error);
			if (currentVersion != null) {
				setPolicyConflictVersion(currentVersion);
			} else {
				toast.error(warehousePlanMutationErrorMessage(error, "数仓分层策略保存失败"));
			}
		} finally {
			setPolicySaving(false);
		}
	};

	const openSpecialist = (route: string) => navigate(withWarehousePlanContext(route, planId));
	const openSection = (section: DetailSection) => navigate(buildWarehousePlanRoute(planId, section));

	if (!planId) {
		return (
			<Result
				status="404"
				title="缺少计划标识"
				extra={<Button onClick={() => navigate("/modeling/workbench")}>返回工作台</Button>}
			/>
		);
	}
	if (loading) {
		return (
			<div className="mx-auto max-w-[1480px] p-6">
				<Skeleton active paragraph={{ rows: 10 }} />
			</div>
		);
	}
	if (failed || !plan) {
		return (
			<div className="mx-auto max-w-[1180px] p-6">
				<Result
					data-testid="warehouse-plan-load-recovery"
					status="warning"
					title="指定的建设计划不可用"
					subTitle="该计划可能不存在或当前账号无权访问。系统不会改为展示其他计划。"
					extra={[
						<Button key="retry" type="primary" onClick={() => void load()}>
							重新加载
						</Button>,
						<Button key="workbench" onClick={() => navigate("/modeling/workbench")}>
							返回工作台选择计划
						</Button>,
					]}
				/>
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
	const planEditable = plan.lifecycleStatus !== "PUBLISHED" && plan.lifecycleStatus !== "ARCHIVED";
	const nextAction = projection?.nextAction || null;
	const categorySelectOptions = buildWarehouseCategoryOptions(
		domainOptions.filter((item): item is DomainOptionNode & { id: string } => Boolean(item.id)),
		categoryScope?.value.domainBindings || [],
	);

	return (
		<div className="mx-auto w-full max-w-[1480px] space-y-4 p-4 md:p-6" data-testid="warehouse-plan-detail">
			<header className="rounded-[22px] border border-slate-200 bg-white px-5 pt-5 md:px-7 md:pt-6">
				<div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
					<div>
						<Button
							type="link"
							className="-ml-3 mb-1 px-2 text-slate-500"
							icon={<ArrowLeft size={15} />}
							onClick={() => navigate(`/modeling/workbench?planId=${encodeURIComponent(planId)}`)}
						>
							返回数据建设工作台
						</Button>
						<div className="flex flex-wrap items-center gap-3">
							<Title level={2} style={{ margin: 0 }}>
								{plan.name}
							</Title>
							<Tag color="blue">{lifecycleLabel[plan.lifecycleStatus]}</Tag>
						</div>
						<div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-sm text-slate-500">
							<span>计划负责人：{plan.ownerId}</span>
							<span>开始方式：{plan.onboardingMode === "ASSET_FIRST" ? "从现有数据开始" : "从业务目标开始"}</span>
							<span>
								当前阶段：
								{projection
									? projection.currentStage
										? warehouseStageLabel(projection.currentStage)
										: "全部阶段已完成"
									: "状态未知"}
							</span>
						</div>
					</div>
					{activeSection !== "baseline" && nextAction ? (
						<Button type="primary" size="large" onClick={() => openSpecialist(nextAction.path)}>
							{projection?.currentStage ? warehouseStageActionLabel(projection.currentStage) : nextAction.label}
							<ArrowRight size={16} />
						</Button>
					) : null}
				</div>
				<Tabs
					className="mt-5"
					activeKey={activeSection}
					items={tabItems}
					onChange={(key) => openSection(key as DetailSection)}
				/>
			</header>

			{evidenceFailed ? (
				<Alert
					data-testid="warehouse-plan-evidence-recovery"
					type="warning"
					showIcon
					message="部分规划证据暂时不可用"
					description="计划本身已恢复；基线或阶段状态保持未知，不会误报为完成。"
					action={<Button onClick={() => void loadEvidence()}>重新加载证据</Button>}
				/>
			) : null}

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
								<div className="text-lg font-semibold">
									{baseline ? (baseline.ready ? "已具备建模基线" : "仍有基线缺口") : "基线状态未知"}
								</div>
								<Text type="secondary">
									{baseline
										? baseline.ready
											? "业务分类、分层和来源均已确认"
											: `${baseline.missingCodes.length} 项待处理`
										: "请重新加载规划证据"}
								</Text>
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
							<Title level={4} style={{ margin: 0 }}>
								规划基线
							</Title>
							<Text type="secondary">按顺序确认管什么业务、数据放在哪一层、数据从哪里来。</Text>
						</div>
						<Tag color={baseline?.ready ? "green" : "gold"}>{baseline?.ready ? "基线已具备" : "基线待完善"}</Tag>
					</div>
					{baselineInputsFailed ? (
						<Alert
							className="mb-4"
							type="warning"
							showIcon
							message="部分规划输入暂时不可用"
							description="已加载的内容会继续显示；重新加载后以服务端最新版本为准。"
							action={<Button onClick={() => void loadBaselineInputs()}>重新加载</Button>}
						/>
					) : null}
					<Tabs
						activeKey={baselineTab}
						onChange={(value) => navigate(buildWarehousePlanRoute(planId, "baseline", { tab: value }))}
						items={[
							{
								key: "categories",
								label: "业务分类",
								children:
									baselineInputsLoading && !categoryScope ? (
										<Skeleton active paragraph={{ rows: 4 }} />
									) : (
										<div className="max-w-4xl space-y-4" data-testid="warehouse-plan-category-form">
											<div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-slate-50 px-4 py-3">
												<div>
													<div className="font-medium">管什么业务</div>
													<Text type="secondary">至少确认一个当前可访问的业务分类，才能进入模型设计。</Text>
												</div>
												<Tag color={categoryScope?.value.readiness === "READY" ? "green" : "gold"}>
													{categoryScope ? categoryReadinessLabel[categoryScope.value.readiness] : "状态未知"}
												</Tag>
											</div>
											{categoryConflictVersion != null ? (
												<Alert
													type="warning"
													showIcon
													message={`业务分类已更新到版本 ${categoryConflictVersion}`}
													description="当前表单已保留，系统没有覆盖你的修改。"
													action={
														<Space wrap>
															<Button
																loading={categorySaving}
																onClick={() => void saveCategories(categoryConflictVersion)}
															>
																保留当前输入并基于版本 {categoryConflictVersion} 重试
															</Button>
															<Button
																onClick={() => {
																	setCategoryDirty(false);
																	void loadBaselineInputs("categories");
																}}
															>
																放弃并加载最新版
															</Button>
														</Space>
													}
												/>
											) : null}
											{categoryScope?.value.issues.map((issue) => (
												<Alert
													key={`${issue.code}-${issue.field || ""}`}
													type="warning"
													showIcon
													message={warehousePlanIssueMessage(issue.code, issue.message)}
												/>
											))}
											<Form
												form={categoryForm}
												layout="vertical"
												disabled={categorySaving}
												onValuesChange={() => setCategoryDirty(true)}
												onFinish={() => void saveCategories()}
											>
												<Form.List name="domainBindings">
													{(fields, { add, remove }, { errors }) => (
														<div className="space-y-3">
															{fields.map((field) => (
																<div
																	key={field.key}
																	className="grid gap-3 rounded-xl border border-slate-200 p-4 md:grid-cols-[1fr_180px_40px]"
																>
																	<Form.Item
																		{...field}
																		name={[field.name, "domainId"]}
																		label="业务分类"
																		rules={[{ required: true, message: "请选择业务分类" }]}
																		className="mb-0"
																	>
																		<Select
																			showSearch
																			optionFilterProp="label"
																			placeholder="选择业务分类"
																			options={categorySelectOptions}
																		/>
																	</Form.Item>
																	<Form.Item
																		{...field}
																		name={[field.name, "confirmationStatus"]}
																		label="使用状态"
																		rules={[{ required: true, message: "请选择使用状态" }]}
																		className="mb-0"
																	>
																		<Select
																			options={[
																				{ value: "CANDIDATE", label: "待确认" },
																				{ value: "CONFIRMED", label: "已确认" },
																				{ value: "EXCLUDED", label: "不纳入" },
																			]}
																		/>
																	</Form.Item>
																	<Button
																		className="mt-7"
																		type="text"
																		danger
																		aria-label="移除业务分类"
																		icon={<Trash2 size={16} />}
																		onClick={() => {
																			remove(field.name);
																			setCategoryDirty(true);
																		}}
																	/>
																</div>
															))}
															<Button
																type="dashed"
																icon={<Plus size={16} />}
																onClick={() => {
																	add({ confirmationStatus: "CANDIDATE" });
																	setCategoryDirty(true);
																}}
															>
																添加业务分类
															</Button>
															<Form.ErrorList errors={errors} />
														</div>
													)}
												</Form.List>
												<div className="mt-5 flex flex-wrap items-center justify-between gap-3">
													<Button
														type="link"
														className="px-0"
														icon={<Waypoints size={16} />}
														onClick={() => navigate(buildBusinessCategoryManagementRoute(planId))}
													>
														管理业务分类
													</Button>
													<Button
														type="primary"
														htmlType="submit"
														loading={categorySaving}
														disabled={
															!categoryScope || !planEditable || !categoryDirty || categoryConflictVersion != null
														}
													>
														保存业务分类
													</Button>
												</div>
											</Form>
										</div>
									),
							},
							{
								key: "layers",
								label: "数仓分层",
								children:
									baselineInputsLoading && !planningPolicy ? (
										<Skeleton active paragraph={{ rows: 4 }} />
									) : (
										<div className="max-w-3xl space-y-4" data-testid="warehouse-plan-policy-form">
											<div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-slate-50 px-4 py-3">
												<div>
													<div className="font-medium">数据放在哪一层</div>
													<Text type="secondary">先确认经典数仓分层；命名与历史策略可在进入实现前补齐。</Text>
												</div>
												<Tag color={planningPolicy?.value.readiness === "IMPLEMENTATION_READY" ? "green" : "blue"}>
													{planningPolicy ? policyReadinessLabel[planningPolicy.value.readiness] : "状态未知"}
												</Tag>
											</div>
											{policyConflictVersion != null ? (
												<Alert
													type="warning"
													showIcon
													message={`分层策略已更新到版本 ${policyConflictVersion}`}
													description="当前表单已保留，系统没有覆盖你的修改。"
													action={
														<Space wrap>
															<Button loading={policySaving} onClick={() => void savePolicy(policyConflictVersion)}>
																保留当前输入并基于版本 {policyConflictVersion} 重试
															</Button>
															<Button
																onClick={() => {
																	setPolicyDirty(false);
																	void loadBaselineInputs("policy");
																}}
															>
																放弃并加载最新版
															</Button>
														</Space>
													}
												/>
											) : null}
											{planningPolicy?.value.issues
												.filter(
													(issue) =>
														issue.code !== "NAMING_POLICY_REQUIRED" && issue.code !== "HISTORY_POLICY_REQUIRED",
												)
												.map((issue) => (
													<Alert
														key={`${issue.code}-${issue.field || ""}`}
														type="warning"
														showIcon
														message={warehousePlanIssueMessage(issue.code, issue.message)}
													/>
												))}
											<Form
												form={policyForm}
												layout="vertical"
												disabled={policySaving}
												onValuesChange={() => setPolicyDirty(true)}
												onFinish={() => void savePolicy()}
											>
												<Form.Item
													name="layerScheme"
													label="分层方案"
													rules={[{ required: true, message: "请选择数仓分层方案" }]}
												>
													<Select
														options={[{ value: "CLASSIC_ODS_DWD_DWS_ADS", label: "经典数仓：ODS → DWD → DWS → ADS" }]}
													/>
												</Form.Item>
												<div className="grid gap-4 md:grid-cols-2">
													<Form.Item name="namingPolicy" label="命名规则（进入实现前补齐）">
														<Select
															allowClear
															options={[
																{ value: "CLASSIC_LOWER_SNAKE", label: "小写下划线" },
																{ value: "CLASSIC_UPPER_SNAKE", label: "大写下划线" },
															]}
														/>
													</Form.Item>
													<Form.Item name="historyPolicy" label="历史保留（进入实现前补齐）">
														<Select
															allowClear
															options={[
																{ value: "PRESERVE_BUSINESS_HISTORY", label: "保留业务历史" },
																{ value: "LATEST_STATE_ONLY", label: "仅保留最新状态" },
															]}
														/>
													</Form.Item>
												</div>
												<Form.Item
													name="defaultTimeZone"
													label="默认时区（可选）"
													extra="使用 IANA 时区名称，不填写则不设默认值。"
												>
													<Input placeholder="例如：Asia/Shanghai" />
												</Form.Item>
												<div className="flex justify-end">
													<Button
														type="primary"
														htmlType="submit"
														loading={policySaving}
														disabled={!planningPolicy || !planEditable || !policyDirty || policyConflictVersion != null}
													>
														保存分层策略
													</Button>
												</div>
											</Form>
										</div>
									),
							},
							{
								key: "sources",
								label: "来源盘点",
								children: (
									<div className="max-w-3xl rounded-xl border border-slate-200 p-5">
										<Title level={5}>数据从哪里来</Title>
										<Paragraph type="secondary">
											{plan.onboardingMode === "ASSET_FIRST"
												? "核对已选数据并确认是否纳入本计划。"
												: "业务目标可以先进入概念设计，在生成或实现模型前补齐数据来源。"}
										</Paragraph>
										<Button
											icon={<Database size={16} />}
											onClick={() => openSpecialist("/catalog/metadata-management")}
										>
											盘点现有数据
										</Button>
									</div>
								),
							},
						]}
					/>
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
				<Title level={4} style={{ margin: 0 }}>
					{title}
				</Title>
				<Paragraph className="mt-2 text-slate-500">{description}</Paragraph>
				<Space wrap>
					{actions.map((action) => (
						<Button key={action.route} icon={action.icon} onClick={() => onOpen(action.route)}>
							{action.label}
						</Button>
					))}
				</Space>
			</div>
		</Card>
	);
}
