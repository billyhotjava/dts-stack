import { Alert, Button, Card, Collapse, Empty, Form, Input, Modal, Radio, Select, Skeleton, Space, Tag, Typography } from "antd";
import { ArrowRight, CheckCircle2, Circle, Database, Layers3, RefreshCw, ShieldAlert } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import {
	createWarehousePlan,
	getWarehousePlanStageProjection,
	listWarehousePlans,
	type CreateWarehousePlanInput,
	type WarehousePlanHeader,
	type WarehousePlanOnboardingMode,
	type WarehousePlanStageProjection,
	type WarehousePlanStageStatus,
} from "@/api/warehousePlanApi";
import { useSearchParams } from "@/routes/hooks";
import {
	WAREHOUSE_STAGE_ORDER,
	buildWarehousePlanRoute,
	isWarehouseStageComplete,
	stageStatusLabel,
	warehouseBlockerMessage,
	warehouseStageActionLabel,
	warehouseStageLabel,
	withWarehousePlanContext,
} from "./warehousePlanViewModel";

const { Paragraph, Text, Title } = Typography;

type CreatePlanForm = Omit<CreateWarehousePlanInput, "code" | "onboardingMode">;

const lifecycleLabel: Record<WarehousePlanHeader["lifecycleStatus"], string> = {
	DRAFT: "规划中",
	BASELINE_READY: "基线已确认",
	MODELING: "建模中",
	IMPLEMENTING: "实现中",
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
	const requestedPlanId = searchParams.get("planId")?.trim() || "";
	const [plans, setPlans] = useState<WarehousePlanHeader[]>([]);
	const [selectedPlanId, setSelectedPlanId] = useState(requestedPlanId);
	const [projection, setProjection] = useState<WarehousePlanStageProjection | null>(null);
	const [loadingPlans, setLoadingPlans] = useState(true);
	const [loadingProjection, setLoadingProjection] = useState(false);
	const [plansFailed, setPlansFailed] = useState(false);
	const [projectionFailed, setProjectionFailed] = useState(false);
	const [createOpen, setCreateOpen] = useState(false);
	const [creating, setCreating] = useState(false);
	const [onboardingMode, setOnboardingMode] = useState<WarehousePlanOnboardingMode>("BUSINESS_FIRST");
	const [form] = Form.useForm<CreatePlanForm>();

	const selectedPlan = useMemo(
		() => plans.find((plan) => plan.id === selectedPlanId) ?? null,
		[plans, selectedPlanId],
	);

	const selectPlan = useCallback(
		(planId: string, replace = false) => {
			setSelectedPlanId(planId);
			navigate(`/modeling/workbench?planId=${encodeURIComponent(planId)}`, { replace });
		},
		[navigate],
	);

	const loadPlans = useCallback(async () => {
		setLoadingPlans(true);
		setPlansFailed(false);
		try {
			const result = await listWarehousePlans();
			const safePlans = Array.isArray(result) ? result : [];
			setPlans(safePlans);
			const nextPlanId = safePlans.some((plan) => plan.id === requestedPlanId)
				? requestedPlanId
				: safePlans.find((plan) => plan.lifecycleStatus !== "ARCHIVED")?.id || safePlans[0]?.id || "";
			if (nextPlanId && nextPlanId !== selectedPlanId) selectPlan(nextPlanId, true);
			if (!nextPlanId) {
				setSelectedPlanId("");
				setProjection(null);
			}
		} catch {
			setPlansFailed(true);
			setPlans([]);
		} finally {
			setLoadingPlans(false);
		}
	}, [requestedPlanId, selectPlan, selectedPlanId]);

	const loadProjection = useCallback(async () => {
		if (!selectedPlanId) return;
		setLoadingProjection(true);
		setProjectionFailed(false);
		try {
			setProjection(await getWarehousePlanStageProjection(selectedPlanId));
		} catch {
			setProjection(null);
			setProjectionFailed(true);
		} finally {
			setLoadingProjection(false);
		}
	}, [selectedPlanId]);

	useEffect(() => {
		void loadPlans();
	}, [loadPlans]);

	useEffect(() => {
		if (selectedPlanId) void loadProjection();
	}, [loadProjection, selectedPlanId]);

	const openCreate = () => {
		setOnboardingMode("BUSINESS_FIRST");
		form.resetFields();
		setCreateOpen(true);
	};

	const submitCreate = async (values: CreatePlanForm) => {
		setCreating(true);
		try {
			const created = await createWarehousePlan({
				...values,
				code: `warehouse-${Date.now().toString(36)}`,
				onboardingMode,
			});
			setPlans((current) => [created, ...current]);
			setCreateOpen(false);
			toast.success("规划已创建");
			const tab = onboardingMode === "ASSET_FIRST" ? "sources" : "business-scope";
			navigate(buildWarehousePlanRoute(created.id, "baseline", { tab }));
		} catch {
			// The shared HTTP interceptor presents the actionable server error.
		} finally {
			setCreating(false);
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
					{plans.length > 0 ? (
						<div className="flex flex-wrap items-center gap-2">
							<Select
								aria-label="当前建设计划"
								value={selectedPlanId || undefined}
								style={{ minWidth: 280 }}
								options={plans.map((plan) => ({ label: plan.name, value: plan.id }))}
								onChange={(value) => selectPlan(value)}
							/>
							<Button type="link" onClick={openCreate}>新建规划</Button>
						</div>
					) : null}
				</div>
			</header>

			{plans.length === 0 ? (
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
									</div>
								) : null}
							</div>
						</Card>

						<Card className="border-slate-200" title="规划摘要">
							<div className="space-y-4 text-sm">
								<div><div className="text-slate-500">建设目标</div><div className="mt-1 text-slate-900">{selectedPlan.objective || "待补充"}</div></div>
								<div><div className="text-slate-500">建设范围</div><div className="mt-1 text-slate-900">{selectedPlan.scope || "待补充"}</div></div>
								<div><div className="text-slate-500">开始方式</div><div className="mt-1 text-slate-900">{selectedPlan.onboardingMode === "ASSET_FIRST" ? "从现有数据开始" : "从业务目标开始"}</div></div>
								<Button block onClick={() => navigate(buildWarehousePlanRoute(selectedPlan.id))}>查看计划详情</Button>
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
				onCancel={() => setCreateOpen(false)}
				onOk={() => form.submit()}
				destroyOnClose
			>
				<div className="mb-5 grid grid-cols-2 gap-3">
					<label className={`cursor-pointer rounded-xl border p-4 ${onboardingMode === "BUSINESS_FIRST" ? "border-blue-500 bg-blue-50" : "border-slate-200"}`}>
						<Radio checked={onboardingMode === "BUSINESS_FIRST"} onChange={() => setOnboardingMode("BUSINESS_FIRST")} />
						<div className="mt-3 flex items-center gap-2 font-medium"><Layers3 size={17} />从业务目标开始</div>
						<div className="mt-1 text-xs leading-5 text-slate-500">先明确建设目标、范围和业务过程。</div>
					</label>
					<label className={`cursor-pointer rounded-xl border p-4 ${onboardingMode === "ASSET_FIRST" ? "border-blue-500 bg-blue-50" : "border-slate-200"}`}>
						<Radio checked={onboardingMode === "ASSET_FIRST"} onChange={() => setOnboardingMode("ASSET_FIRST")} />
						<div className="mt-3 flex items-center gap-2 font-medium"><Database size={17} />从现有数据开始</div>
						<div className="mt-1 text-xs leading-5 text-slate-500">先盘点现有表、文件和 dbt 产物。</div>
					</label>
				</div>
				<Form<CreatePlanForm> form={form} layout="vertical" onFinish={(values) => void submitCreate(values)}>
					<Form.Item name="name" label="规划名称" rules={[{ required: true, message: "请输入规划名称" }]}><Input placeholder="例如：经营分析主题数仓" /></Form.Item>
					<Form.Item name="objective" label="建设目标"><Input.TextArea rows={2} placeholder="这次建设要解决什么业务问题" /></Form.Item>
					<Form.Item name="scope" label="初始范围"><Input placeholder="涉及的业务范围、组织或数据边界" /></Form.Item>
					<div className="grid grid-cols-2 gap-3">
						<Form.Item name="ownerId" label="计划负责人" rules={[{ required: true, message: "请输入负责人" }]}><Input /></Form.Item>
						<Form.Item name="ownerDepartmentId" label="负责部门"><Input /></Form.Item>
					</div>
				</Form>
			</Modal>
		</div>
	);
}
