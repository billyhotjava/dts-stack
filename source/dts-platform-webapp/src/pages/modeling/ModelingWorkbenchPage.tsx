import { Alert, Button, Card, Collapse, Empty, Form, Input, Modal, Radio, Select, Skeleton, Space, Tag, Typography } from "antd";
import { isAxiosError } from "axios";
import { ArrowRight, CheckCircle2, Circle, Database, Layers3, PackageOpen, Plus, RefreshCw, ShieldAlert, Trash2 } from "lucide-react";
import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import {
	createWarehousePlan,
	getWarehousePlan,
	getWarehousePlanStageProjection,
	listWarehousePlans,
	type CreateWarehousePlanInput,
	type WarehousePlanHeader,
	type WarehousePlanOnboardingMode,
	type WarehousePlanStageProjection,
	type WarehousePlanStageStatus,
} from "@/api/warehousePlanApi";
import { useSearchParams } from "@/routes/hooks";
import { useUserInfo, useUserRoles } from "@/store/userStore";
import { WarehousePlanHeaderEditor } from "./components/WarehousePlanHeaderEditor";
import { ImportModelPackageWizard } from "./model-package-import/ImportModelPackageWizard";
import { buildModelPackageImportQuery } from "./model-package-import/modelPackageImportNavigation";
import {
	WAREHOUSE_STAGE_ORDER,
	buildWarehousePlanRoute,
	canEditWarehousePlanHeader,
	isWarehouseStageComplete,
	replaceWarehousePlanHeader,
	stageStatusLabel,
	warehouseBlockerMessage,
	warehouseStageActionLabel,
	warehouseStageLabel,
	withWarehousePlanContext,
} from "./warehousePlanViewModel";
import {
	createWarehousePlanIdempotencyKey,
	createLatestRequestGuard,
	EMPTY_WAREHOUSE_PLAN_CREATE_SESSION,
	hasWarehousePlanCreateAccess,
	loadRequestedWarehousePlan,
	mergeWarehousePlanLists,
	reduceWarehousePlanCreateSession,
	validateWarehousePlanInitialSources,
} from "./warehousePlanCreateFlow";

const { Paragraph, Text, Title } = Typography;

type CreatePlanForm = Omit<CreateWarehousePlanInput, "idempotencyKey" | "onboardingMode" | "ownerId" | "ownerDepartmentId">;

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
	const requestedPlanId = searchParams.get("planId")?.trim() || "";
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
	const [editorOpen, setEditorOpen] = useState(false);
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

	const selectedPlan = useMemo(
		() => plans.find((plan) => plan.id === selectedPlanId) ?? null,
		[plans, selectedPlanId],
	);
	const canImportModels = Boolean(
		selectedPlan && canEditWarehousePlanHeader(canCreatePlan, selectedPlan.lifecycleStatus),
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
			navigate(`/modeling/workbench?planId=${encodeURIComponent(planId)}`, { replace });
		},
		[navigate, planListRetryGuard],
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
			if (nextPlanId && !requestedCreate) {
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
	}, [planListRetryGuard, planLoadGuard, requestedCreate, requestedPlanId, selectPlan]);

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
			navigate(selectedPlanId ? `/modeling/workbench?planId=${encodeURIComponent(selectedPlanId)}` : "/modeling/workbench", {
				replace: true,
			});
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
			const code = isAxiosError(error) ? String((error.response?.data as { code?: string } | undefined)?.code || "") : "";
			dispatchCreateSession({ type: code === "WAREHOUSE_PLAN_IDEMPOTENCY_CONFLICT" ? "IDEMPOTENCY_CONFLICT" : "REQUEST_FAILED" });
			// The shared HTTP interceptor presents the actionable server error.
		} finally {
			if (isCurrentCreate()) setCreating(false);
		}
	};

	if (loadingPlans) {
		return (
			<div className="mx-auto w-full max-w-[1480px] p-6">
				<Skeleton active paragraph={{ rows: 8 }} />
			</div>
		);
	}

	if (plansFailed) {
		return (
			<div className="mx-auto w-full max-w-[1180px] p-6">
				<Alert
				type="error"
				showIcon
				message="规划工作台暂时不可用"
				description="计划读取失败，未把未知状态显示为已完成。"
				action={<Button icon={<RefreshCw size={15} />} onClick={() => void loadPlans()}>重新加载</Button>}
			/>
			</div>
		);
	}

	return (
		<div className="mx-auto w-full max-w-[1480px] space-y-4 p-4 md:p-6" data-testid="warehouse-plan-workbench">
			<header
				className="overflow-hidden rounded-[22px] border border-slate-200 px-5 py-5 md:px-7"
				style={{
					background:
						"linear-gradient(120deg, rgba(238,246,252,0.98) 0%, rgba(249,251,252,0.98) 58%, rgba(247,244,235,0.96) 100%)",
				}}
			>
				<div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
					<div className="max-w-3xl">
						<div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
							<Layers3 size={15} /> Data construction
						</div>
						<Title level={2} style={{ margin: 0 }}>数据建设工作台</Title>
						<Paragraph className="mb-0 mt-2 max-w-2xl text-slate-600">
							围绕一个建设计划查看真实证据、首要阻塞和下一步。专业配置仍在各自模块完成。
						</Paragraph>
					</div>
					<div className="flex flex-wrap items-center gap-2">
						{plans.length > 0 ? (
							<>
							<Select
								aria-label="当前建设计划"
								value={selectedPlanId || undefined}
								style={{ minWidth: 280 }}
								options={plans.map((plan) => ({ label: plan.name, value: plan.id }))}
								onChange={(value) => selectPlan(value)}
							/>
							<Button
								type="link"
								disabled={!canCreatePlan}
								title={canCreatePlan ? undefined : "当前账号没有规划维护权限"}
								onClick={openCreate}
							>
								新建规划
							</Button>
							</>
						) : null}
						<Button type="link" onClick={() => navigate("/modeling/plans")}>全部规划</Button>
					</div>
				</div>
			</header>

			{requestedCreate && !canCreatePlan ? (
				<Alert type="info" showIcon message="当前账号没有规划维护权限" description="可以查看已有规划，但不能新建或修改规划。" />
			) : null}

			{requestedPlanFailed ? (
				<Alert
					data-testid="warehouse-plan-requested-plan-recovery"
					type="warning"
					showIcon
					message="指定的建设计划不可用"
					description="该计划可能不存在、无权访问或暂时网络异常。系统不会自动替换成其他计划。"
					action={
						<Button onClick={() => void loadPlans()}>
							重新加载指定计划
						</Button>
					}
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
					<Empty
						image={Empty.PRESENTED_IMAGE_SIMPLE}
						description={
							<div className="mx-auto max-w-lg text-center">
								<div className="mb-2 text-lg font-semibold text-slate-900">先建立一个数据建设计划</div>
								<div className="text-sm leading-6 text-slate-500">无论从业务目标还是现有数据开始，都会进入同一套规划基线与九站证据链。</div>
							</div>
						}
					>
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
								{loadingProjection ? <Skeleton active paragraph={{ rows: 3 }} /> : projectionFailed ? (
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
												{projection.currentStage ? warehouseStageLabel(projection.currentStage) : "全部阶段已有当前证据"}
											</div>
											{projection.primaryBlocker ? (
												<div className="mt-4 flex max-w-3xl items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-amber-950">
													<ShieldAlert className="mt-0.5 shrink-0" size={18} />
													<div>
														<div className="font-medium">首要阻塞</div>
														<div className="mt-1 text-sm">{warehouseBlockerMessage(projection.primaryBlocker.code, projection.primaryBlocker.message)}</div>
													</div>
												</div>
											) : null}
										</div>
										<Space wrap>
											<Button
												size="large"
												icon={<PackageOpen size={16} />}
												disabled={!canImportModels}
												title={canImportModels ? "将 dbt 模型包导入当前建设计划" : "发布中、已发布、已归档或只读计划不能导入"}
												onClick={() => setImportRoute(true)}
											>
												导入已有模型
											</Button>
											{projection.nextAction ? (
												<Button
													type="primary"
													size="large"
													data-testid="warehouse-plan-next-action"
													onClick={() => navigate(withWarehousePlanContext(projection.nextAction!.path, selectedPlan.id))}
												>
													{projection.currentStage ? warehouseStageActionLabel(projection.currentStage) : projection.nextAction.label}<ArrowRight size={16} />
												</Button>
											) : null}
										</Space>
									</div>
								) : null}
							</div>
						</Card>

						<Card className="border-slate-200" title="规划摘要">
							<div className="space-y-4 text-sm">
								<div><div className="text-slate-500">建设目标</div><div className="mt-1 text-slate-900">{selectedPlan.objective || "待补充"}</div></div>
								<div><div className="text-slate-500">建设范围</div><div className="mt-1 text-slate-900">{selectedPlan.scope || "待补充"}</div></div>
								<div><div className="text-slate-500">开始方式</div><div className="mt-1 text-slate-900">{selectedPlan.onboardingMode === "ASSET_FIRST" ? "从现有数据开始" : "从业务目标开始"}</div></div>
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
										<Button block onClick={() => setEditorOpen(true)}>编辑基本信息</Button>
									) : null}
								</Space>
							</div>
						</Card>
					</section>

					<Card className="border-slate-200" title="建设轨迹" extra={<Text type="secondary">状态来自 StageProjection，只读</Text>}>
						<div className="grid gap-0 overflow-hidden rounded-xl border border-slate-200 md:grid-cols-3 xl:grid-cols-9" data-testid="warehouse-plan-nine-stage-track">
							{WAREHOUSE_STAGE_ORDER.map((stageCode, index) => {
								const evidence = projection?.stages.find((stage) => stage.code === stageCode);
								const status = evidence?.status || "UNKNOWN";
								const freshness = evidence?.freshness || "UNAVAILABLE";
								const complete = isWarehouseStageComplete(status, freshness);
								const tone = statusTone[complete ? "COMPLETE" : status];
								return (
									<div key={stageCode} className="min-h-28 border-b border-r border-slate-200 p-3 last:border-r-0 md:border-b-0" style={{ borderTop: `3px solid ${tone.border}` }}>
										<div className="mb-3 flex items-center justify-between">
											<span className="text-xs font-semibold text-slate-400">{String(index + 1).padStart(2, "0")}</span>
											{complete ? <CheckCircle2 size={17} color={tone.dot} /> : <Circle size={15} color={tone.dot} />}
										</div>
										<div className="text-sm font-medium leading-5 text-slate-900">{warehouseStageLabel(stageCode)}</div>
										<div className="mt-2 text-xs text-slate-500">{stageStatusLabel(status, freshness)}</div>
									</div>
								);
							})}
						</div>
						<Collapse
							ghost
							className="mt-3"
							items={[{
								key: "evidence",
								label: "查看次要缺口与证据说明",
								children: <Text type="secondary">阶段完成度只由后台证据计算；刷新时间：{projection?.computedAt ? new Date(projection.computedAt).toLocaleString() : "暂无"}</Text>,
							}]}
						/>
					</Card>
				</>
			) : null}

			<Modal
				title="新建数据建设规划"
				open={createOpen}
				confirmLoading={creating}
				okText="创建并继续"
				cancelText="取消"
				closable={!creating}
				maskClosable={!creating}
				keyboard={!creating}
				cancelButtonProps={{ disabled: creating }}
				onCancel={() => {
					if (!creating) cancelCreate();
				}}
				onOk={() => form.submit()}
				destroyOnClose
			>
				<div className="mb-5 grid grid-cols-2 gap-3">
					<label className={`cursor-pointer rounded-xl border p-4 ${onboardingMode === "BUSINESS_FIRST" ? "border-blue-500 bg-blue-50" : "border-slate-200"}`}>
						<Radio disabled={creating} checked={onboardingMode === "BUSINESS_FIRST"} onChange={() => changeOnboardingMode("BUSINESS_FIRST")} />
						<div className="mt-3 flex items-center gap-2 font-medium"><Layers3 size={17} />从业务目标开始</div>
						<div className="mt-1 text-xs leading-5 text-slate-500">先说明要解决的问题，再逐步确认业务分类和范围。</div>
					</label>
					<label className={`cursor-pointer rounded-xl border p-4 ${onboardingMode === "ASSET_FIRST" ? "border-blue-500 bg-blue-50" : "border-slate-200"}`}>
						<Radio disabled={creating} checked={onboardingMode === "ASSET_FIRST"} onChange={() => changeOnboardingMode("ASSET_FIRST")} />
						<div className="mt-3 flex items-center gap-2 font-medium"><Database size={17} />从现有数据开始</div>
						<div className="mt-1 text-xs leading-5 text-slate-500">先盘点现有表、文件和 dbt 产物。</div>
					</label>
				</div>
				{createSession.idempotencyConflict ? (
					<Alert
						className="mb-4"
						type="warning"
						showIcon
						message="这次创建内容与此前提交不一致"
						description="表单已保留。确认内容后，可作为新计划重新提交。"
						action={<Button onClick={replaceConflictingIdempotencyKey}>作为新计划重新提交</Button>}
					/>
				) : null}
				<Form<CreatePlanForm> form={form} layout="vertical" disabled={creating} onFinish={(values) => void submitCreate(values)}>
					<Form.Item name="name" label="规划名称" rules={[{ required: true, whitespace: true, max: 128, message: "请输入 1-128 个字符的规划名称" }]}><Input maxLength={128} placeholder="例如：经营分析主题数仓" /></Form.Item>
					<Form.Item
						name="objective"
						label="建设目标"
						rules={onboardingMode === "BUSINESS_FIRST" ? [{ required: true, whitespace: true, message: "请说明这次建设要解决的问题" }] : []}
					>
						<Input.TextArea rows={2} placeholder={onboardingMode === "BUSINESS_FIRST" ? "这次建设要解决什么业务问题" : "可稍后补充"} />
					</Form.Item>
					<Form.Item name="scope" label="初始范围"><Input placeholder="涉及的业务范围、组织或数据边界" /></Form.Item>
					{onboardingMode === "ASSET_FIRST" ? (
						<div className="mb-4" data-testid="warehouse-plan-initial-sources">
							<Form.List
								name="initialSourceRefs"
								rules={[{ validator: async (_, sources) => {
									const issue = validateWarehousePlanInitialSources(sources);
									if (issue) throw new Error(issue);
								} }]}
							>
								{(fields, { add, remove }, { errors }) => (
									<div className="space-y-2">
										<div className="flex items-center justify-between">
											<Text strong>现有数据来源</Text>
											<Button type="link" icon={<Plus size={14} />} onClick={() => add({ sourceType: "CATALOG_TABLE", sourceId: "" })}>添加来源</Button>
										</div>
										{fields.map((field) => (
											<div key={field.key} className="grid grid-cols-[150px_1fr_1fr_auto] gap-2">
												<Form.Item {...field} name={[field.name, "sourceType"]} rules={[{ required: true, message: "选择类型" }]} noStyle>
													<Select options={[
														{ value: "CATALOG_TABLE", label: "资产目录表" },
														{ value: "CONNECTION_TABLE", label: "连接中的表" },
														{ value: "EXCEL_FILE", label: "Excel 文件" },
														{ value: "DBT_NODE", label: "dbt 节点" },
													]} />
												</Form.Item>
												<Form.Item {...field} name={[field.name, "sourceId"]} rules={[{ required: true, whitespace: true, message: "填写稳定来源标识" }, { max: 256, message: "来源标识不能超过 256 个字符" }]} noStyle>
													<Input maxLength={256} placeholder="来源标识" />
												</Form.Item>
												<Form.Item {...field} name={[field.name, "sourceVersion"]} rules={[{ max: 128, message: "来源版本不能超过 128 个字符" }]} noStyle>
													<Input maxLength={128} placeholder="版本（可选）" />
												</Form.Item>
												<Button aria-label="删除来源" icon={<Trash2 size={14} />} onClick={() => remove(field.name)} />
											</div>
										))}
										<Form.ErrorList errors={errors} />
									</div>
								)}
							</Form.List>
						</div>
					) : null}
					<div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2" data-testid="warehouse-plan-current-owner">
						<div className="text-xs text-slate-500">当前负责人由登录身份确定；创建后可在“建设规划”中按权限转派</div>
						<div className="mt-1 text-sm font-medium text-slate-900">{currentOwner} · {currentDepartment}</div>
					</div>
				</Form>
			</Modal>

			<WarehousePlanHeaderEditor
				open={editorOpen}
				plan={selectedPlan}
				canMaintainPlan={canCreatePlan}
				onClose={() => setEditorOpen(false)}
				onPlanChange={(updated) => setPlans((current) => replaceWarehousePlanHeader(current, updated))}
				onUnavailable={() => navigate("/modeling/plans")}
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
		</div>
	);
}
