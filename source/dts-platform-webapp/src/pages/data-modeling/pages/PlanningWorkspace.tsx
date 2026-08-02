import { RefreshCw, Search } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router";
import {
	archiveWarehousePlan,
	createWarehousePlan,
	saveWarehousePlanPolicy,
	updateWarehousePlan,
	type WarehousePlanHeader,
	type WarehousePlanOnboardingMode,
	type WarehousePlanPolicyInput,
} from "@/api/warehousePlanApi";
import { warehousePlanMutationErrorMessage } from "@/features/modeling/navigation/warehousePlanViewModel";
import {
	classifyModelingLoadFailure,
	loadPlanningCatalog,
	loadWarehousePlanningHeaders,
	type ModelingLoadFailure,
	type PlanningCatalog,
} from "../adapters/planningHomeAdapter";
import { ActionButton, DataTable, EmptyState, Panel, StatusTag, WorkspacePage } from "../components/WorkspacePage";
import type { DemoRow, WorkspacePageProps } from "../types";

type PlanEditorMode = "create" | "edit";

type PlanDraft = {
	name: string;
	objective: string;
	scope: string;
	ownerId: string;
	ownerDepartmentId: string;
	onboardingMode: WarehousePlanOnboardingMode;
};

const emptyPlanDraft = (): PlanDraft => ({
	name: "",
	objective: "",
	scope: "",
	ownerId: "",
	ownerDepartmentId: "",
	onboardingMode: "BUSINESS_FIRST",
});

const emptyPolicyDraft = (): WarehousePlanPolicyInput => ({
	layerScheme: null,
	namingPolicy: null,
	historyPolicy: null,
	defaultTimeZone: "Asia/Shanghai",
	conceptualDesignAllowed: true,
	standardCoverage: "KEY_AND_MEASURE",
	qualityGate: "ADVISORY",
});

const filterRows = (rows: DemoRow[], query: string): DemoRow[] => {
	const normalized = query.trim().toLocaleLowerCase();
	if (!normalized) return rows;
	return rows.filter((row) =>
		Object.values(row).some((value) => String(value).toLocaleLowerCase().includes(normalized)),
	);
};

const catalogHandoff = (view: string): { label: string; path: string } | null => {
	if (view === "domains" || view === "business-categories" || view === "processes") {
		return { label: "在治理工作台维护", path: "/governance/subjects" };
	}
	if (view === "marts") {
		return { label: "在集市台账维护", path: "/governance/subjects?tab=data-marts" };
	}
	return null;
};

export function PlanningWorkspace({ route }: WorkspacePageProps) {
	const [searchParams, setSearchParams] = useSearchParams();
	const [plans, setPlans] = useState<WarehousePlanHeader[]>([]);
	const [activePlanId, setActivePlanId] = useState("");
	const [activeDomainId, setActiveDomainId] = useState("");
	const activePlanRef = useRef("");
	const activeDomainRef = useRef("");
	const refreshEpochRef = useRef(0);
	const createPlanIdempotencyKeyRef = useRef(crypto.randomUUID());
	const requestedPlanIdRef = useRef(searchParams.get("planId")?.trim() || "");
	const [catalog, setCatalog] = useState<PlanningCatalog | null>(null);
	const [query, setQuery] = useState("");
	const [loading, setLoading] = useState(true);
	const [failure, setFailure] = useState<ModelingLoadFailure | null>(null);
	const [success, setSuccess] = useState("");
	const [targetNotice, setTargetNotice] = useState<{ message: string; found: boolean } | null>(null);
	const [busy, setBusy] = useState(false);
	const [writeDenied, setWriteDenied] = useState(false);
	const [editorMode, setEditorMode] = useState<PlanEditorMode | null>(null);
	const [planDraft, setPlanDraft] = useState<PlanDraft>(emptyPlanDraft);
	const [policyDraft, setPolicyDraft] = useState<WarehousePlanPolicyInput>(emptyPolicyDraft);

	const consumeRequestedPlan = useCallback(() => {
		setSearchParams(
			(current) => {
				const next = new URLSearchParams(current);
				next.delete("planId");
				return next;
			},
			{ replace: true },
		);
	}, [setSearchParams]);

	const refresh = useCallback(
		async (requestedPlanId?: string, requestedDomainId?: string) => {
			const requestEpoch = ++refreshEpochRef.current;
			setLoading(true);
			setFailure(null);
			try {
				const nextPlans = await loadWarehousePlanningHeaders();
				if (requestEpoch !== refreshEpochRef.current) return;
				const requestedPlanFound = Boolean(requestedPlanId && nextPlans.some((plan) => plan.id === requestedPlanId));
				const currentPlanId = requestedPlanId || activePlanRef.current;
				const nextPlanId =
					currentPlanId && nextPlans.some((plan) => plan.id === currentPlanId) ? currentPlanId : nextPlans[0]?.id || "";
				const nextCatalog = await loadPlanningCatalog(route.view, {
					plans: nextPlans,
					planId: nextPlanId,
					domainId: requestedDomainId || activeDomainRef.current,
				});
				if (requestEpoch !== refreshEpochRef.current) return;
				const nextDomainId =
					(requestedDomainId || activeDomainRef.current) &&
					nextCatalog.domains.some((domain) => domain.code === (requestedDomainId || activeDomainRef.current))
						? requestedDomainId || activeDomainRef.current
						: nextCatalog.domains[0]?.code || "";
				setPlans(nextPlans);
				setActivePlanId(nextPlanId);
				activePlanRef.current = nextPlanId;
				setActiveDomainId(nextDomainId);
				activeDomainRef.current = nextDomainId;
				setCatalog(nextCatalog);
				if (nextCatalog.policy) setPolicyDraft(nextCatalog.policy.value);
				if (requestedPlanId && requestedPlanId === requestedPlanIdRef.current) {
					const requestedPlan = nextPlans.find((plan) => plan.id === requestedPlanId);
					setTargetNotice({
						found: requestedPlanFound,
						message: requestedPlan
							? `已定位建设计划“${requestedPlan.name}”。`
							: `目标建设计划 ${requestedPlanId} 不在当前真实计划目录中，已保留当前可用计划上下文。`,
					});
					requestedPlanIdRef.current = "";
					consumeRequestedPlan();
				}
			} catch (error) {
				if (requestEpoch !== refreshEpochRef.current) return;
				setCatalog(null);
				setPlans([]);
				setFailure(classifyModelingLoadFailure(error, "规划目录加载失败，请稍后重新加载。"));
			} finally {
				if (requestEpoch === refreshEpochRef.current) setLoading(false);
			}
		},
		[consumeRequestedPlan, route.view],
	);

	useEffect(() => {
		setQuery("");
		void refresh(requestedPlanIdRef.current || undefined);
		return () => {
			refreshEpochRef.current += 1;
		};
	}, [refresh]);

	const selectedPlan = plans.find((plan) => plan.id === activePlanId) || null;
	const rows = useMemo(() => filterRows(catalog?.rows || [], query), [catalog?.rows, query]);
	const handoff = catalogHandoff(route.view);

	const openPlanEditor = (mode: PlanEditorMode) => {
		setSuccess("");
		if (mode === "create") createPlanIdempotencyKeyRef.current = crypto.randomUUID();
		setEditorMode(mode);
		setPlanDraft(
			mode === "edit" && selectedPlan
				? {
						name: selectedPlan.name,
						objective: selectedPlan.objective || "",
						scope: selectedPlan.scope || "",
						ownerId: selectedPlan.ownerId,
						ownerDepartmentId: selectedPlan.ownerDepartmentId || "",
						onboardingMode: selectedPlan.onboardingMode,
					}
				: emptyPlanDraft(),
		);
	};

	const handleMutationFailure = (error: unknown, fallback: string) => {
		const classified = classifyModelingLoadFailure(error, warehousePlanMutationErrorMessage(error, fallback));
		setFailure(classified);
		if (classified.kind === "permission") setWriteDenied(true);
	};

	const savePlan = async () => {
		if (!planDraft.name.trim()) {
			setFailure({ kind: "error", message: "请输入建设计划名称。" });
			return;
		}
		if (editorMode === "edit" && !planDraft.ownerId.trim()) {
			setFailure({ kind: "error", message: "负责人不能为空。" });
			return;
		}
		setBusy(true);
		setFailure(null);
		try {
			if (editorMode === "edit" && selectedPlan) {
				await updateWarehousePlan(selectedPlan.id, selectedPlan.version, {
					name: planDraft.name.trim(),
					objective: planDraft.objective.trim() || null,
					scope: planDraft.scope.trim() || null,
					ownerId: planDraft.ownerId.trim(),
					ownerDepartmentId: planDraft.ownerDepartmentId.trim() || null,
				});
				setSuccess("建设计划已保存。服务端已记录本次变更审计。 ");
				await refresh(selectedPlan.id);
			} else {
				const result = await createWarehousePlan({
					name: planDraft.name.trim(),
					objective: planDraft.objective.trim() || undefined,
					scope: planDraft.scope.trim() || undefined,
					ownerId: planDraft.ownerId.trim() || undefined,
					ownerDepartmentId: planDraft.ownerDepartmentId.trim() || undefined,
					onboardingMode: planDraft.onboardingMode,
					idempotencyKey: createPlanIdempotencyKeyRef.current,
				});
				createPlanIdempotencyKeyRef.current = crypto.randomUUID();
				setSuccess("建设计划已创建。服务端已记录本次变更审计。 ");
				await refresh(result.planId);
			}
			setEditorMode(null);
		} catch (error) {
			handleMutationFailure(error, "建设计划保存失败，请保留输入后重试。");
		} finally {
			setBusy(false);
		}
	};

	const archivePlan = async () => {
		if (!selectedPlan || !window.confirm(`确认归档建设计划“${selectedPlan.name}”？`)) return;
		setBusy(true);
		setFailure(null);
		try {
			await archiveWarehousePlan(selectedPlan.id, selectedPlan.version);
			setSuccess("建设计划已归档。服务端已记录本次变更审计。 ");
			await refresh("");
		} catch (error) {
			handleMutationFailure(error, "建设计划归档失败，请重新加载后重试。");
		} finally {
			setBusy(false);
		}
	};

	const savePolicy = async () => {
		if (loading || !activePlanId || !catalog?.policy) return;
		setBusy(true);
		setFailure(null);
		try {
			await saveWarehousePlanPolicy(activePlanId, catalog.policy.version, policyDraft);
			setSuccess("规划参数已保存。服务端已记录本次变更审计。 ");
			await refresh(activePlanId);
		} catch (error) {
			handleMutationFailure(error, "规划参数保存失败，请重新加载后重试。");
		} finally {
			setBusy(false);
		}
	};

	const planActionDisabled = busy || writeDenied;
	const planActionReason = writeDenied ? "当前账号无权维护建设计划" : busy ? "正在提交，请稍候" : undefined;

	return (
		<WorkspacePage
			actions={
				<>
					<ActionButton
						disabled={loading || busy}
						onClick={() => void refresh(requestedPlanIdRef.current || undefined)}
						title="重新读取权威目录"
					>
						<RefreshCw aria-hidden="true" size={15} />
						刷新
					</ActionButton>
					<ActionButton
						disabled={planActionDisabled}
						kind="primary"
						onClick={() => openPlanEditor("create")}
						title={planActionReason}
					>
						新建建设计划
					</ActionButton>
				</>
			}
			description={route.description}
			eyebrow="数据建模 / 数仓规划"
			title={route.title}
		>
			{failure ? (
				<div className="dm-stage-notice" role="alert">
					<strong>{failure.kind === "permission" ? "无权访问" : "加载或保存失败"}</strong>
					<span>{failure.message}</span>
					<ActionButton disabled={loading} onClick={() => void refresh(requestedPlanIdRef.current || undefined)}>
						重新加载
					</ActionButton>
				</div>
			) : null}
			{success ? (
				<output className="dm-context-strip">
					<StatusTag tone="success">成功</StatusTag>
					{success}
				</output>
			) : null}
			{targetNotice ? (
				<output className="dm-context-strip">
					<StatusTag tone={targetNotice.found ? "success" : "warning"}>
						{targetNotice.found ? "已定位" : "目标不可见"}
					</StatusTag>
					{targetNotice.message}
				</output>
			) : null}

			<Panel subtitle="所有规划页共享同一建设计划上下文；空列表显示服务端真实空态。" title="建设计划上下文">
				<div className="dm-toolbar">
					<label className="dm-form-field">
						<span>当前建设计划</span>
						<select
							aria-label="当前建设计划"
							className="dm-select"
							disabled={loading || plans.length === 0}
							onChange={(event) => {
								activePlanRef.current = event.target.value;
								setActivePlanId(event.target.value);
								void refresh(event.target.value);
							}}
							value={activePlanId}
						>
							{plans.length === 0 ? <option value="">暂无建设计划</option> : null}
							{plans.map((plan) => (
								<option key={plan.id} value={plan.id}>
									{plan.name} · {plan.code}
								</option>
							))}
						</select>
					</label>
					<ActionButton
						disabled={!selectedPlan || planActionDisabled || selectedPlan.lifecycleStatus === "ARCHIVED"}
						onClick={() => openPlanEditor("edit")}
						title={selectedPlan?.lifecycleStatus === "ARCHIVED" ? "已归档计划不可编辑" : planActionReason}
					>
						编辑计划
					</ActionButton>
					<ActionButton
						disabled={!selectedPlan || planActionDisabled || selectedPlan.lifecycleStatus === "ARCHIVED"}
						kind="danger"
						onClick={() => void archivePlan()}
						title={selectedPlan?.lifecycleStatus === "ARCHIVED" ? "该计划已经归档" : planActionReason}
					>
						归档计划
					</ActionButton>
				</div>
				{loading ? <StatusTag tone="info">正在加载权威规划数据…</StatusTag> : null}
				{!loading && plans.length === 0 ? (
					<EmptyState title="暂无建设计划" description="创建首个建设计划后，才能维护计划基线和模型上下文。" />
				) : null}
			</Panel>

			{editorMode ? (
				<Panel title={editorMode === "create" ? "新建建设计划" : "编辑建设计划"}>
					<div className="dm-form-grid">
						<label className="dm-form-field">
							<span>计划名称 *</span>
							<input
								className="dm-input"
								onChange={(event) => setPlanDraft((current) => ({ ...current, name: event.target.value }))}
								value={planDraft.name}
							/>
						</label>
						<label className="dm-form-field">
							<span>负责人{editorMode === "edit" ? " *" : ""}</span>
							<input
								className="dm-input"
								onChange={(event) => setPlanDraft((current) => ({ ...current, ownerId: event.target.value }))}
								placeholder="留空时由服务端使用当前负责人"
								value={planDraft.ownerId}
							/>
						</label>
						<label className="dm-form-field dm-form-field--wide">
							<span>建设目标</span>
							<input
								className="dm-input"
								onChange={(event) => setPlanDraft((current) => ({ ...current, objective: event.target.value }))}
								value={planDraft.objective}
							/>
						</label>
						<label className="dm-form-field dm-form-field--wide">
							<span>建设范围</span>
							<input
								className="dm-input"
								onChange={(event) => setPlanDraft((current) => ({ ...current, scope: event.target.value }))}
								value={planDraft.scope}
							/>
						</label>
						{editorMode === "create" ? (
							<label className="dm-form-field">
								<span>启动方式</span>
								<select
									className="dm-select"
									onChange={(event) =>
										setPlanDraft((current) => ({
											...current,
											onboardingMode: event.target.value as WarehousePlanOnboardingMode,
										}))
									}
									value={planDraft.onboardingMode}
								>
									<option value="BUSINESS_FIRST">业务驱动</option>
									<option value="ASSET_FIRST">资产驱动</option>
								</select>
							</label>
						) : null}
					</div>
					<div className="dm-toolbar">
						<ActionButton disabled={busy} kind="primary" onClick={() => void savePlan()}>
							{busy ? "正在保存…" : "保存计划"}
						</ActionButton>
						<ActionButton disabled={busy} onClick={() => setEditorMode(null)}>
							取消
						</ActionButton>
					</div>
				</Panel>
			) : null}

			{route.view === "system" ? (
				<Panel subtitle="参数按建设计划版本保存，并由服务端执行权限、并发和审计控制。" title="规划策略">
					{!activePlanId ? (
						<EmptyState title="暂无可配置计划" description="请先创建或选择建设计划。" />
					) : catalog?.policy ? (
						<>
							<div className="dm-form-grid">
								<label className="dm-form-field">
									<span>分层方案</span>
									<select
										className="dm-select"
										onChange={(event) =>
											setPolicyDraft((current) => ({
												...current,
												layerScheme: event.target.value ? "CLASSIC_ODS_DWD_DWS_ADS" : null,
											}))
										}
										value={policyDraft.layerScheme || ""}
									>
										<option value="">请选择</option>
										<option value="CLASSIC_ODS_DWD_DWS_ADS">经典 ODS/DWD/DWS/ADS</option>
									</select>
								</label>
								<label className="dm-form-field">
									<span>命名规则</span>
									<select
										className="dm-select"
										onChange={(event) =>
											setPolicyDraft((current) => ({
												...current,
												namingPolicy: (event.target.value || null) as WarehousePlanPolicyInput["namingPolicy"],
											}))
										}
										value={policyDraft.namingPolicy || ""}
									>
										<option value="">请选择</option>
										<option value="CLASSIC_LOWER_SNAKE">小写下划线</option>
										<option value="CLASSIC_UPPER_SNAKE">大写下划线</option>
									</select>
								</label>
								<label className="dm-form-field">
									<span>历史策略</span>
									<select
										className="dm-select"
										onChange={(event) =>
											setPolicyDraft((current) => ({
												...current,
												historyPolicy: (event.target.value || null) as WarehousePlanPolicyInput["historyPolicy"],
											}))
										}
										value={policyDraft.historyPolicy || ""}
									>
										<option value="">请选择</option>
										<option value="PRESERVE_BUSINESS_HISTORY">保留业务历史</option>
										<option value="LATEST_STATE_ONLY">仅保留最新状态</option>
									</select>
								</label>
								<label className="dm-form-field">
									<span>默认时区</span>
									<input
										className="dm-input"
										onChange={(event) =>
											setPolicyDraft((current) => ({ ...current, defaultTimeZone: event.target.value }))
										}
										value={policyDraft.defaultTimeZone || ""}
									/>
								</label>
							</div>
							<ActionButton
								disabled={loading || busy || writeDenied}
								kind="primary"
								onClick={() => void savePolicy()}
								title={writeDenied ? "当前账号无权维护规划参数" : undefined}
							>
								{busy ? "正在保存…" : "保存规划参数"}
							</ActionButton>
						</>
					) : loading ? (
						<StatusTag tone="info">正在加载规划参数…</StatusTag>
					) : (
						<EmptyState title="暂无规划参数" description="服务端未返回当前计划的参数基线。" />
					)}
				</Panel>
			) : (
				<Panel
					actions={
						<>
							{catalog?.readOnlyReason ? <StatusTag tone="info">只读投影</StatusTag> : null}
							{handoff ? (
								<Link className="dm-button dm-button--primary" to={handoff.path}>
									{handoff.label}
								</Link>
							) : null}
						</>
					}
					subtitle={catalog?.readOnlyReason || "数据来自当前权威 owner，不在浏览器保存副本。"}
					title={`${route.title}目录`}
				>
					{route.view === "processes" && (catalog?.domains.length || 0) > 0 ? (
						<label className="dm-form-field">
							<span>数据域</span>
							<select
								aria-label="业务过程所属数据域"
								className="dm-select"
								onChange={(event) => {
									activeDomainRef.current = event.target.value;
									setActiveDomainId(event.target.value);
									void refresh(activePlanId, event.target.value);
								}}
								value={activeDomainId}
							>
								{catalog?.domains.map((domain) => (
									<option key={domain.code} value={domain.code}>
										{domain.name}
									</option>
								))}
							</select>
						</label>
					) : null}
					<div className="dm-toolbar">
						<div className="dm-search-control">
							<Search aria-hidden="true" size={15} />
							<input
								aria-label={`搜索${route.title}`}
								className="dm-input"
								onChange={(event) => setQuery(event.target.value)}
								placeholder="搜索名称、编码或说明"
								type="search"
								value={query}
							/>
						</div>
						<span>{loading ? "正在加载…" : `共 ${rows.length} 条`}</span>
					</div>
					{loading && !catalog ? (
						<StatusTag tone="info">正在加载真实目录…</StatusTag>
					) : !loading && !failure && (catalog?.columns.length || 0) === 0 ? (
						<EmptyState title="暂无权威目录" description={catalog?.readOnlyReason || "当前暂无数据。"} />
					) : (
						<DataTable
							columns={catalog?.columns || []}
							emptyText={loading ? "正在加载真实目录…" : "暂无符合条件的数据"}
							rowKey="code"
							rows={rows}
						/>
					)}
				</Panel>
			)}
		</WorkspacePage>
	);
}
