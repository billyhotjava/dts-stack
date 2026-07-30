import { Alert, Button, Card, Collapse, Empty, Form, Skeleton, Space, Tag, Typography } from "antd";
import { isAxiosError } from "axios";
import { ArrowRight, CheckCircle2, Circle, PackageOpen, RefreshCw, ShieldAlert } from "lucide-react";
import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import {
	createWarehousePlan,
	getWarehousePlan,
	getWarehousePlanStageProjection,
	listWarehousePlans,
	type WarehousePlanHeader,
	type WarehousePlanOnboardingMode,
	type WarehousePlanStageProjection,
	type WarehousePlanStageStatus,
} from "@/api/warehousePlanApi";
import { useSearchParams } from "@/routes/hooks";
import { useUserInfo, useUserRoles } from "@/store/userStore";
import { ModelingWorkspacePanels } from "./ModelingWorkspacePanels";
import { ModelingWorkspaceShell } from "./ModelingWorkspaceShell";
import { ImportModelPackageWizard } from "./model-package-import/ImportModelPackageWizard";
import { buildModelPackageImportQuery } from "./model-package-import/modelPackageImportNavigation";
import {
	isModelingWorkspaceModelAssetOpen,
	type ModelingWorkspaceModelStage,
	type ModelingWorkspaceModule,
	type ModelingWorkspaceRoutePatch,
	type ModelingWorkspaceView,
	parseModelingWorkspaceRouteState,
	updateModelingWorkspaceSearch,
} from "./modelingWorkspaceRouteState";
import { type CreatePlanForm, WarehousePlanCreateModal } from "./WarehousePlanCreateModal";
import {
	createLatestRequestGuard,
	createWarehousePlanIdempotencyKey,
	EMPTY_WAREHOUSE_PLAN_CREATE_SESSION,
	hasWarehousePlanCreateAccess,
	loadRequestedWarehousePlan,
	mergeWarehousePlanLists,
	reduceWarehousePlanCreateSession,
} from "./warehousePlanCreateFlow";
import {
	buildWarehousePlanRoute,
	canEditWarehousePlanHeader,
	isWarehouseStageComplete,
	stageStatusLabel,
	WAREHOUSE_STAGE_ORDER,
	warehouseBlockerMessage,
	warehouseStageActionLabel,
	warehouseStageLabel,
	withWarehousePlanContext,
} from "./warehousePlanViewModel";

const { Text } = Typography;

const lifecycleLabel: Record<WarehousePlanHeader["lifecycleStatus"], string> = {
	DRAFT: "规划中",
	BASELINE_READY: "基线已确认",
	DESIGNING: "设计中",
	VALIDATING: "验证中",
	READY_TO_PUBLISH: "待发布",
	PUBLISHED: "已发布",
	ARCHIVED: "已归档",
};

const statusTone: Record<WarehousePlanStageStatus, { border: string; dot: string }> = {
	NOT_STARTED: { border: "#d9e1ea", dot: "#8b98a8" },
	IN_PROGRESS: { border: "#1677ff", dot: "#1677ff" },
	BLOCKED: { border: "#d97706", dot: "#d97706" },
	COMPLETE: { border: "#0f8a5f", dot: "#0f8a5f" },
	UNKNOWN: { border: "#7c6f64", dot: "#7c6f64" },
};

export default function ModelingWorkbenchPage() {
	const navigate = useNavigate();
	const searchParams = useSearchParams();
	const userRoles = useUserRoles();
	const canCreatePlan = hasWarehousePlanCreateAccess(userRoles);
	const userInfo = useUserInfo() as Record<string, unknown>;
	const workspaceRoute = useMemo(() => parseModelingWorkspaceRouteState(searchParams), [searchParams]);
	const requestedPlanId = workspaceRoute.planId || "";
	const activeModule = workspaceRoute.module;
	const modelAssetOpen = isModelingWorkspaceModelAssetOpen(workspaceRoute);
	const resolvingModelAsset = modelAssetOpen && !requestedPlanId;
	const requestedCreate = searchParams.get("create") === "1";
	const importOpen = searchParams.get("modelImport") === "open";
	const importRunId = searchParams.get("importRunId")?.trim() || "";
	const [plans, setPlans] = useState<WarehousePlanHeader[]>([]);
	const [selectedPlanId, setSelectedPlanId] = useState("");
	const [projection, setProjection] = useState<WarehousePlanStageProjection | null>(null);
	const [loadingPlans, setLoadingPlans] = useState(true);
	const [loadingProjection, setLoadingProjection] = useState(false);
	const [plansFailed, setPlansFailed] = useState(false);
	const [planListFailed, setPlanListFailed] = useState(false);
	const [retryingPlanList, setRetryingPlanList] = useState(false);
	const [requestedPlanFailed, setRequestedPlanFailed] = useState(false);
	const [projectionFailed, setProjectionFailed] = useState(false);
	const [createOpen, setCreateOpen] = useState(false);
	const [creating, setCreating] = useState(false);
	const [onboardingMode, setOnboardingMode] = useState<WarehousePlanOnboardingMode>("BUSINESS_FIRST");
	const [createSession, dispatchCreateSession] = useReducer(
		reduceWarehousePlanCreateSession,
		EMPTY_WAREHOUSE_PLAN_CREATE_SESSION,
	);
	const [form] = Form.useForm<CreatePlanForm>();
	const currentOwner = String(userInfo.username || userInfo.login || userInfo.id || "当前登录用户");
	const currentDepartment = String(userInfo.deptName || userInfo.deptCode || userInfo.department || "未配置部门");
	const planLoadGuard = useMemo(() => createLatestRequestGuard(), []);
	const planListRetryGuard = useMemo(() => createLatestRequestGuard(), []);
	const projectionLoadGuard = useMemo(() => createLatestRequestGuard(), []);
	const createRequestGuard = useMemo(() => createLatestRequestGuard(), []);
	const createRouteHandledRef = useRef(false);

	const selectedPlan = useMemo(() => plans.find((plan) => plan.id === selectedPlanId) ?? null, [plans, selectedPlanId]);
	const planOptions = useMemo(() => plans.map((plan) => ({ label: plan.name, value: plan.id })), [plans]);
	const canImportModels = Boolean(
		selectedPlan && canEditWarehousePlanHeader(canCreatePlan, selectedPlan.lifecycleStatus),
	);

	const navigateWorkspace = useCallback(
		(patch: ModelingWorkspaceRoutePatch, replace = false) => {
			const query = updateModelingWorkspaceSearch(searchParams, patch);
			navigate(`/modeling/workbench${query ? `?${query}` : ""}`, { replace });
		},
		[navigate, searchParams],
	);

	const changeModule = useCallback(
		(module: ModelingWorkspaceModule) => navigateWorkspace({ module }),
		[navigateWorkspace],
	);

	const changeWorkspaceView = useCallback(
		(view: ModelingWorkspaceView) => navigateWorkspace({ workspaceView: view }),
		[navigateWorkspace],
	);

	const openModel = useCallback(
		(modelSpecId: string, planId?: string) =>
			navigateWorkspace({
				module: "models",
				workspaceView: "model-specs",
				...(planId ? { planId } : {}),
				assetKind: "model",
				assetId: modelSpecId,
				activeStage: "logical",
			}),
		[navigateWorkspace],
	);

	const closeModel = useCallback(
		() => navigateWorkspace({ assetKind: null, assetId: null, activeStage: null }, true),
		[navigateWorkspace],
	);

	const openIndicator = useCallback(
		(indicatorId?: string) =>
			navigateWorkspace(
				{
					module: "metrics",
					workspaceView: "definitions",
					assetKind: indicatorId ? "indicator" : null,
					assetId: indicatorId || null,
					activeStage: null,
				},
				true,
			),
		[navigateWorkspace],
	);

	const changeModelStage = useCallback(
		(stage: ModelingWorkspaceModelStage) => navigateWorkspace({ activeStage: stage }, true),
		[navigateWorkspace],
	);

	const resolveModelContext = useCallback(
		(context: { modelSpecId: string; planId: string }) => {
			setSelectedPlanId(context.planId);
			if (
				workspaceRoute.planId === context.planId &&
				workspaceRoute.assetKind === "model" &&
				workspaceRoute.assetId === context.modelSpecId
			) {
				return;
			}
			navigateWorkspace(
				{
					planId: context.planId,
					assetKind: "model",
					assetId: context.modelSpecId,
					activeStage: workspaceRoute.activeStage || "logical",
				},
				true,
			);
		},
		[
			navigateWorkspace,
			workspaceRoute.activeStage,
			workspaceRoute.assetId,
			workspaceRoute.assetKind,
			workspaceRoute.planId,
		],
	);

	const setImportRoute = useCallback(
		(open: boolean, runId = importRunId) => {
			navigate(
				buildModelPackageImportQuery("/modeling/workbench", searchParams, {
					open,
					planId: selectedPlanId || undefined,
					runId: runId || undefined,
				}),
				{ replace: !open },
			);
		},
		[importRunId, navigate, searchParams, selectedPlanId],
	);

	const selectPlan = useCallback(
		(planId: string, replace = false) => {
			planListRetryGuard.invalidate();
			setRetryingPlanList(false);
			setPlanListFailed(false);
			setSelectedPlanId(planId);
			navigateWorkspace({ planId }, replace);
		},
		[navigateWorkspace, planListRetryGuard],
	);

	const loadPlans = useCallback(async () => {
		const isCurrent = planLoadGuard.begin();
		planListRetryGuard.invalidate();
		setRetryingPlanList(false);
		setLoadingPlans(true);
		setPlansFailed(false);
		setPlanListFailed(false);
		try {
			if (requestedPlanId) {
				setSelectedPlanId("");
				setProjection(null);
				const restored = await loadRequestedWarehousePlan(
					requestedPlanId,
					getWarehousePlan,
					listWarehousePlans,
					(requestedPlan) => {
						if (!isCurrent()) return;
						setPlans([requestedPlan]);
						setSelectedPlanId(requestedPlan.id);
						setRequestedPlanFailed(false);
						setLoadingPlans(false);
					},
					() => {
						if (!isCurrent()) return;
						setPlans([]);
						setSelectedPlanId("");
						setProjection(null);
						setRequestedPlanFailed(true);
						setLoadingPlans(false);
					},
				);
				if (!isCurrent()) return;
				setPlans(restored.plans);
				setPlanListFailed(restored.listFailed);
				setRequestedPlanFailed(restored.requestedPlanFailed);
				if (restored.requestedPlan) {
					setSelectedPlanId(restored.requestedPlan.id);
				} else {
					setSelectedPlanId("");
					setProjection(null);
				}
				return;
			}

			const result = await listWarehousePlans();
			if (!isCurrent()) return;
			const safePlans = Array.isArray(result) ? result : [];
			setPlans(safePlans);
			setPlanListFailed(false);
			setRequestedPlanFailed(false);
			const nextPlanId = safePlans.find((plan) => plan.lifecycleStatus !== "ARCHIVED")?.id || safePlans[0]?.id || "";
			if (resolvingModelAsset) {
				setSelectedPlanId("");
				setProjection(null);
			} else if (nextPlanId && !requestedCreate) {
				selectPlan(nextPlanId, true);
			} else if (nextPlanId) {
				setSelectedPlanId(nextPlanId);
			} else {
				setSelectedPlanId("");
				setProjection(null);
			}
		} catch {
			if (!isCurrent()) return;
			setPlansFailed(true);
			setPlans([]);
		} finally {
			if (isCurrent()) setLoadingPlans(false);
		}
	}, [planListRetryGuard, planLoadGuard, requestedCreate, requestedPlanId, resolvingModelAsset, selectPlan]);

	const retryPlanList = useCallback(async () => {
		const isCurrent = planListRetryGuard.begin();
		setRetryingPlanList(true);
		try {
			const result = await listWarehousePlans();
			if (!isCurrent()) return;
			const safePlans = Array.isArray(result) ? result : [];
			setPlans((current) => mergeWarehousePlanLists(current, safePlans, selectedPlanId));
			setPlanListFailed(false);
		} catch {
			if (!isCurrent()) return;
			setPlanListFailed(true);
		} finally {
			if (isCurrent()) setRetryingPlanList(false);
		}
	}, [planListRetryGuard, selectedPlanId]);

	const loadProjection = useCallback(async () => {
		if (!selectedPlanId) return;
		const isCurrent = projectionLoadGuard.begin();
		setLoadingProjection(true);
		setProjectionFailed(false);
		try {
			const result = await getWarehousePlanStageProjection(selectedPlanId);
			if (!isCurrent()) return;
			setProjection(result);
		} catch {
			if (!isCurrent()) return;
			setProjection(null);
			setProjectionFailed(true);
		} finally {
			if (isCurrent()) setLoadingProjection(false);
		}
	}, [projectionLoadGuard, selectedPlanId]);

	useEffect(() => {
		void loadPlans();
		return () => planLoadGuard.invalidate();
	}, [loadPlans, planLoadGuard]);

	useEffect(() => {
		if (!selectedPlanId) {
			projectionLoadGuard.invalidate();
			setLoadingProjection(false);
			return;
		}
		void loadProjection();
		return () => projectionLoadGuard.invalidate();
	}, [loadProjection, projectionLoadGuard, selectedPlanId]);

	useEffect(() => () => createRequestGuard.invalidate(), [createRequestGuard]);

	const openCreate = useCallback(() => {
		if (!canCreatePlan) return;
		createRequestGuard.invalidate();
		setCreating(false);
		setOnboardingMode("BUSINESS_FIRST");
		form.resetFields();
		dispatchCreateSession({ type: "OPEN", idempotencyKey: createWarehousePlanIdempotencyKey() });
		setCreateOpen(true);
	}, [canCreatePlan, createRequestGuard, form]);

	useEffect(() => {
		if (!requestedCreate) {
			createRouteHandledRef.current = false;
			return;
		}
		if (loadingPlans || !canCreatePlan || createRouteHandledRef.current) return;
		createRouteHandledRef.current = true;
		openCreate();
	}, [canCreatePlan, loadingPlans, openCreate, requestedCreate]);

	const cancelCreate = () => {
		if (creating) return;
		createRequestGuard.invalidate();
		setCreateOpen(false);
		dispatchCreateSession({ type: "CLEAR" });
		form.resetFields();
		if (requestedCreate) {
			const next = new URLSearchParams(updateModelingWorkspaceSearch(searchParams, { planId: selectedPlanId || null }));
			next.delete("create");
			const query = next.toString();
			navigate(`/modeling/workbench${query ? `?${query}` : ""}`, { replace: true });
		}
	};

	const changeOnboardingMode = (mode: WarehousePlanOnboardingMode) => {
		setOnboardingMode(mode);
		form.setFieldValue(
			"initialSourceRefs",
			mode === "ASSET_FIRST" ? [{ sourceType: "CATALOG_TABLE", sourceId: "" }] : [],
		);
	};

	const replaceConflictingIdempotencyKey = () => {
		createRequestGuard.invalidate();
		dispatchCreateSession({ type: "REPLACE_KEY", idempotencyKey: createWarehousePlanIdempotencyKey() });
	};

	const submitCreate = async (values: CreatePlanForm) => {
		if (!canCreatePlan) return;
		const idempotencyKey = createSession.idempotencyKey;
		if (!idempotencyKey) {
			toast.error("当前创建会话已失效，请关闭后重新打开窗口");
			return;
		}
		const isCurrentCreate = createRequestGuard.begin();
		setCreating(true);
		try {
			const created = await createWarehousePlan({
				...values,
				onboardingMode,
				idempotencyKey,
			});
			if (!isCurrentCreate()) return;
			setPlans((current) => [created.plan, ...current.filter((plan) => plan.id !== created.planId)]);
			setCreateOpen(false);
			dispatchCreateSession({ type: "CLEAR" });
			toast.success("规划已创建");
			navigate(created.nextAction);
		} catch (error) {
			if (!isCurrentCreate()) return;
			const code = isAxiosError(error)
				? String((error.response?.data as { code?: string } | undefined)?.code || "")
				: "";
			dispatchCreateSession({
				type: code === "WAREHOUSE_PLAN_IDEMPOTENCY_CONFLICT" ? "IDEMPOTENCY_CONFLICT" : "REQUEST_FAILED",
			});
			// The shared HTTP interceptor presents the actionable server error.
		} finally {
			if (isCurrentCreate()) setCreating(false);
		}
	};

	if (loadingPlans && !modelAssetOpen) {
		return (
			<div className="mx-auto w-full max-w-[1480px] p-6">
				<Skeleton active paragraph={{ rows: 8 }} />
			</div>
		);
	}

	if (plansFailed && !modelAssetOpen) {
		return (
			<div className="mx-auto w-full max-w-[1180px] p-6">
				<Alert
					type="error"
					showIcon
					message="规划工作台暂时不可用"
					description="计划读取失败，未把未知状态显示为已完成。"
					action={
						<Button icon={<RefreshCw size={15} />} onClick={() => void loadPlans()}>
							重新加载
						</Button>
					}
				/>
			</div>
		);
	}

	return (
		<div className="mx-auto w-full max-w-[1560px] p-3 md:p-4" data-testid="warehouse-plan-workbench">
			<ModelingWorkspaceShell
				activeModule={activeModule}
				planId={selectedPlanId || undefined}
				planOptions={planOptions}
				canCreatePlan={canCreatePlan}
				onModuleChange={changeModule}
				onPlanChange={selectPlan}
				onCreatePlan={openCreate}
				onOpenAllPlans={() => navigate("/modeling/plans")}
			>
				{activeModule === "home" ? (
					<div className="space-y-4 p-4 md:p-5">
						{requestedCreate && !canCreatePlan ? (
							<Alert
								type="info"
								showIcon
								message="当前账号没有规划维护权限"
								description="可以查看已有规划，但不能新建或修改规划。"
							/>
						) : null}

						{requestedPlanFailed ? (
							<Alert
								data-testid="warehouse-plan-requested-plan-recovery"
								type="warning"
								showIcon
								message="指定的建设计划不可用"
								description="该计划可能不存在、无权访问或暂时网络异常。系统不会自动替换成其他计划。"
								action={<Button onClick={() => void loadPlans()}>重新加载指定计划</Button>}
							/>
						) : null}

						{planListFailed && !requestedPlanFailed ? (
							<Alert
								data-testid="warehouse-plan-list-recovery"
								type="warning"
								showIcon
								message="计划列表未完整加载"
								description="当前计划可以继续使用；其他计划暂未显示。"
								action={
									<Button loading={retryingPlanList} onClick={() => void retryPlanList()}>
										重新加载列表
									</Button>
								}
							/>
						) : null}

						{plans.length === 0 && !requestedPlanFailed ? (
							<Card className="border-slate-200" styles={{ body: { padding: "72px 24px" } }}>
								<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无建设规划">
									<Button
										type="primary"
										size="large"
										data-testid="warehouse-plan-empty-primary-action"
										disabled={!canCreatePlan}
										title={canCreatePlan ? undefined : "当前账号没有规划维护权限"}
										onClick={openCreate}
									>
										新建规划
									</Button>
								</Empty>
							</Card>
						) : selectedPlan ? (
							<>
								<section className="grid gap-4 xl:grid-cols-[minmax(0,1.65fr)_minmax(300px,0.7fr)]">
									<Card className="overflow-hidden border-slate-200" styles={{ body: { padding: 0 } }}>
										<div className="border-b border-slate-200 px-5 py-4 md:px-6">
											<div className="flex flex-wrap items-center justify-between gap-3">
												<div>
													<Text type="secondary">当前计划</Text>
													<div className="mt-1 text-xl font-semibold text-slate-950">{selectedPlan.name}</div>
												</div>
												<Space wrap>
													<Tag color="blue">生命周期：{lifecycleLabel[selectedPlan.lifecycleStatus]}</Tag>
													<Tag>计划负责人：{selectedPlan.ownerId}</Tag>
												</Space>
											</div>
										</div>

										<div className="px-5 py-6 md:px-6 md:py-7">
											{loadingProjection ? (
												<Skeleton active paragraph={{ rows: 3 }} />
											) : projectionFailed ? (
												<Alert
													type="warning"
													showIcon
													message="阶段证据暂时不可用"
													description="当前状态保持未知，不会误报为完成。"
													action={<Button onClick={() => void loadProjection()}>重试</Button>}
												/>
											) : projection ? (
												<div className="grid gap-6 lg:grid-cols-[1fr_auto] lg:items-end">
													<div>
														<div className="mb-2 text-sm font-medium text-slate-500">当前阶段</div>
														<div className="text-3xl font-semibold tracking-tight text-slate-950">
															{projection.currentStage
																? warehouseStageLabel(projection.currentStage)
																: "全部阶段已有当前证据"}
														</div>
														{projection.primaryBlocker ? (
															<div className="mt-4 flex max-w-3xl items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-amber-950">
																<ShieldAlert className="mt-0.5 shrink-0" size={18} />
																<div>
																	<div className="font-medium">首要阻塞</div>
																	<div className="mt-1 text-sm">
																		{warehouseBlockerMessage(
																			projection.primaryBlocker.code,
																			projection.primaryBlocker.message,
																		)}
																	</div>
																</div>
															</div>
														) : null}
													</div>
													<Space wrap>
														<Button
															size="large"
															icon={<PackageOpen size={16} />}
															disabled={!canImportModels}
															title={
																canImportModels
																	? "将 dbt 模型包导入当前建设计划"
																	: "发布中、已发布、已归档或只读计划不能导入"
															}
															onClick={() => setImportRoute(true)}
														>
															导入已有模型
														</Button>
														{projection.nextAction ? (
															<Button
																type="primary"
																size="large"
																data-testid="warehouse-plan-next-action"
																onClick={() => {
																	if (!projection.nextAction) return;
																	navigate(withWarehousePlanContext(projection.nextAction.path, selectedPlan.id));
																}}
															>
																{projection.currentStage
																	? warehouseStageActionLabel(projection.currentStage)
																	: projection.nextAction.label}
																<ArrowRight size={16} />
															</Button>
														) : null}
													</Space>
												</div>
											) : null}
										</div>
									</Card>

									<Card className="border-slate-200" title="规划摘要">
										<div className="space-y-4 text-sm">
											<div>
												<div className="text-slate-500">建设目标</div>
												<div className="mt-1 text-slate-900">{selectedPlan.objective || "待补充"}</div>
											</div>
											<div>
												<div className="text-slate-500">建设范围</div>
												<div className="mt-1 text-slate-900">{selectedPlan.scope || "待补充"}</div>
											</div>
											<div>
												<div className="text-slate-500">开始方式</div>
												<div className="mt-1 text-slate-900">
													{selectedPlan.onboardingMode === "ASSET_FIRST" ? "从现有数据开始" : "从业务目标开始"}
												</div>
											</div>
											<Space direction="vertical" className="w-full" size={8}>
												<Button
													block
													onClick={() =>
														navigate(buildWarehousePlanRoute(selectedPlan.id, "overview", { mode: "view" }))
													}
												>
													查看计划详情
												</Button>
												{canEditWarehousePlanHeader(canCreatePlan, selectedPlan.lifecycleStatus) ? (
													<Button
														block
														onClick={() =>
															navigate(buildWarehousePlanRoute(selectedPlan.id, "overview", { mode: "edit" }))
														}
													>
														编辑规划
													</Button>
												) : null}
											</Space>
										</div>
									</Card>
								</section>

								<Card
									className="border-slate-200"
									title="建设轨迹"
									extra={<Text type="secondary">状态来自 StageProjection，只读</Text>}
								>
									<div
										className="grid gap-0 overflow-hidden rounded-xl border border-slate-200 md:grid-cols-3 xl:grid-cols-9"
										data-testid="warehouse-plan-nine-stage-track"
									>
										{WAREHOUSE_STAGE_ORDER.map((stageCode, index) => {
											const evidence = projection?.stages.find((stage) => stage.code === stageCode);
											const status = evidence?.status || "UNKNOWN";
											const freshness = evidence?.freshness || "UNAVAILABLE";
											const complete = isWarehouseStageComplete(status, freshness);
											const tone = statusTone[complete ? "COMPLETE" : status];
											return (
												<div
													key={stageCode}
													className="min-h-28 border-b border-r border-slate-200 p-3 last:border-r-0 md:border-b-0"
													style={{ borderTop: `3px solid ${tone.border}` }}
												>
													<div className="mb-3 flex items-center justify-between">
														<span className="text-xs font-semibold text-slate-400">
															{String(index + 1).padStart(2, "0")}
														</span>
														{complete ? (
															<CheckCircle2 size={17} color={tone.dot} />
														) : (
															<Circle size={15} color={tone.dot} />
														)}
													</div>
													<div className="text-sm font-medium leading-5 text-slate-900">
														{warehouseStageLabel(stageCode)}
													</div>
													<div className="mt-2 text-xs text-slate-500">{stageStatusLabel(status, freshness)}</div>
												</div>
											);
										})}
									</div>
									<Collapse
										ghost
										className="mt-3"
										items={[
											{
												key: "evidence",
												label: "查看次要缺口与证据说明",
												children: (
													<Text type="secondary">
														阶段完成度只由后台证据计算；刷新时间：
														{projection?.computedAt ? new Date(projection.computedAt).toLocaleString() : "暂无"}
													</Text>
												),
											},
										]}
									/>
								</Card>
							</>
						) : null}
					</div>
				) : (
					<>
						{plansFailed ? (
							<Alert
								className="m-3 mb-0"
								type="warning"
								showIcon
								message="计划列表暂不可用"
								description="模型仍按资产标识读取；顶部计划选择暂时不可用。"
								action={<Button onClick={() => void loadPlans()}>重新加载计划</Button>}
							/>
						) : null}
						<ModelingWorkspacePanels
							module={activeModule}
							planId={selectedPlanId || undefined}
							planOptions={planOptions}
							canImportModels={canImportModels}
							workspaceView={workspaceRoute.workspaceView}
							assetKind={workspaceRoute.assetKind}
							assetId={workspaceRoute.assetId}
							onViewChange={changeWorkspaceView}
							onNavigate={navigate}
							onOpenModelImport={() => setImportRoute(true)}
							onOpenModel={openModel}
							onOpenIndicator={openIndicator}
							onCloseModel={closeModel}
							onModelStageChange={changeModelStage}
							onModelResolvedContext={resolveModelContext}
						/>
					</>
				)}

				<WarehousePlanCreateModal
					open={createOpen}
					creating={creating}
					onboardingMode={onboardingMode}
					createSession={createSession}
					form={form}
					currentOwner={currentOwner}
					currentDepartment={currentDepartment}
					onCancel={cancelCreate}
					onSubmit={(values) => void submitCreate(values)}
					onOnboardingModeChange={changeOnboardingMode}
					onReplaceConflictingIdempotencyKey={replaceConflictingIdempotencyKey}
				/>

				<ImportModelPackageWizard
					open={importOpen}
					lockedPlanId={selectedPlanId || undefined}
					initialRunId={importRunId || undefined}
					plans={plans}
					canEdit={canImportModels}
					returnSurface="workbench"
					onClose={(runId) => setImportRoute(false, runId)}
					onRunIdChange={(runId) => setImportRoute(true, runId)}
					onApplied={() => void loadProjection()}
					onNavigate={navigate}
				/>
			</ModelingWorkspaceShell>
		</div>
	);
}
