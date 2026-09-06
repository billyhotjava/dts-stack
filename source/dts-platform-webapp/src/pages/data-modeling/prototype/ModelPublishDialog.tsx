import { useCallback, useEffect, useMemo, useState } from "react";
import {
	approveReleaseCandidateReview,
	cancelReleaseCandidate,
	compileModelLifecycle,
	createReleaseCandidate,
	createReplacementReleaseCandidate,
	getModelLifecycle,
	getModelMaterializationStatuses,
	getModelSpec,
	getPlanExecutionWorkspace,
	getReleaseCandidateWorkbench,
	lockReleaseCandidate,
	type MaterializationPlanEntry,
	type MaterializationPlanPreview,
	type MaterializationPlanStrategy,
	type ModelMaterializationStatus,
	type PlanExecutionWorkspace,
	previewMaterializationPlan,
	publishReleaseCandidate,
	type ReleaseCandidateEntryEvidence,
	type ReleaseCandidateLifecycleAction,
	type ReleaseCandidateWorkbench,
	refreshReleaseCandidate,
	rejectReleaseCandidateReview,
	rematerializeReleaseCandidate,
	repairPlanExecutionBinding,
	rerunReleaseCandidateGovernanceQuality,
	retryReleaseCandidate,
	retryReleaseCandidateRegistration,
	rollbackReleaseCandidate,
	runPlanExecutionNow,
	runReleaseCandidateQuality,
	startModelBuildIntent,
	startModelPublicationIntent,
	submitReleaseCandidateReview,
} from "@/api/modelSpecApi";
import { type CompactColumns, CompactTable } from "@/components/table";
import type { CanonicalModelSpecView, ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { ModelAssetDeliveryResult } from "./ModelAssetDeliveryResult";
import { ModelReleaseWorkflowPanel } from "./ModelReleaseWorkflowPanel";
import { Button, Modal, RequestState, Status } from "./PrototypePrimitives";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";

const MAX_MATERIALIZATION_MODELS = 64;
const COMPILE_CONCURRENCY = 5;

type MaterializationBuildAction =
	| "CREATE_CANDIDATE"
	| "REFRESH_AND_CREATE"
	| "CREATE_AFTER_TERMINAL"
	| "CANCEL_AND_CREATE"
	| "REFRESH_AND_REPLACE"
	| "CREATE_REPLACEMENT"
	| "REMATERIALIZE"
	| "RETRY_BUILD"
	| "START_BUILD";

type ReleaseWorkflowAction = Extract<
	ReleaseCandidateLifecycleAction,
	"RUN_QUALITY" | "SUBMIT_REVIEW" | "APPROVE" | "REJECT" | "PUBLISH" | "RETRY_PUBLICATION" | "ROLLBACK"
>;

const RELEASE_WORKFLOW_ACTIONS: ReleaseWorkflowAction[] = [
	"RUN_QUALITY",
	"SUBMIT_REVIEW",
	"APPROVE",
	"REJECT",
	"PUBLISH",
	"RETRY_PUBLICATION",
	"ROLLBACK",
];

const RELEASE_ACTION_LABELS: Record<ReleaseWorkflowAction, string> = {
	RUN_QUALITY: "运行工程验证",
	SUBMIT_REVIEW: "提交发布评审",
	APPROVE: "审核通过",
	REJECT: "驳回",
	PUBLISH: "发布上线",
	RETRY_PUBLICATION: "重试发布",
	ROLLBACK: "回滚发布",
};

const PLAN_BOUND_BUILD_ACTIONS = new Set<MaterializationBuildAction>([
	"CREATE_CANDIDATE",
	"REFRESH_AND_CREATE",
	"CREATE_AFTER_TERMINAL",
	"CANCEL_AND_CREATE",
	"REFRESH_AND_REPLACE",
	"CREATE_REPLACEMENT",
	"REMATERIALIZE",
]);

const canonical = (model: ModelSpecView): model is CanonicalModelSpecView =>
	model.compatibilityMode === "CANONICAL" && model.contractVersion === 2;

const formatTime = (value?: string | null) => {
	if (!value) return "—";
	const parsed = new Date(value);
	return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString("zh-CN", { hour12: false });
};

async function compileSelectedModels(models: CanonicalModelSpecView[], modelSpecIds = models.map((model) => model.id)) {
	const known = new Map(models.map((model) => [model.id, model]));
	const plannedIds = Array.from(new Set(modelSpecIds));
	for (let offset = 0; offset < plannedIds.length; offset += COMPILE_CONCURRENCY) {
		const batch = await Promise.all(
			plannedIds.slice(offset, offset + COMPILE_CONCURRENCY).map(async (modelSpecId) => {
				const resolved = known.get(modelSpecId) || (await getModelSpec(modelSpecId));
				if (!canonical(resolved)) throw new Error("物化计划包含不可编译的历史模型，请刷新计划后重试。");
				return resolved;
			}),
		);
		await Promise.all(
			batch.map(async (model) => {
				const lifecycle = await getModelLifecycle(model.id);
				if (!lifecycle.implementation)
					throw new Error(
						plannedIds.length === 1
							? "当前模型尚未保存可编译的数据实现，请先保存数据实现后重试。"
							: `${model.name} 尚未保存可编译的数据实现，请先保存数据实现后重试。`,
					);
				await compileModelLifecycle(model, lifecycle.implementation, crypto.randomUUID());
			}),
		);
	}
}

export function ModelPublishDialog({
	models,
	onClose,
	canMaintain,
}: {
	models: ModelSpecView[];
	onClose: () => void;
	canMaintain: boolean;
}) {
	const [tab, setTab] = useState<"materialize" | "publish">("materialize");
	const [environment, setEnvironment] = useState("dev");
	const [strategy, setStrategy] = useState<MaterializationPlanStrategy>("WITH_MISSING_UPSTREAMS");
	const [reason, setReason] = useState("从模型工作台发布");
	const [workspace, setWorkspace] = useState<ReleaseCandidateWorkbench | null>(null);
	const [materializations, setMaterializations] = useState<ModelMaterializationStatus[]>([]);
	const [materializationPlan, setMaterializationPlan] = useState<MaterializationPlanPreview | null>(null);
	const [planState, setPlanState] = useState<"idle" | "loading" | "ready" | "blocked" | "error">("idle");
	const [planFailure, setPlanFailure] = useState("");
	const [executionWorkspace, setExecutionWorkspace] = useState<PlanExecutionWorkspace | null>(null);
	const [busy, setBusy] = useState<"load" | "build" | "release" | "governance-quality" | "run" | "">("load");
	const [activeReleaseAction, setActiveReleaseAction] = useState<ReleaseWorkflowAction | null>(null);
	const [failure, setFailure] = useState<string>("");
	const selection = useMemo(() => Array.from(new Map(models.map((model) => [model.id, model])).values()), [models]);
	const selectedIds = useMemo(() => new Set(selection.map((model) => model.id)), [selection]);
	const primary = selection[0] || null;
	const batch = selection.length > 1;
	const selectionProblem = !selection.length
		? "请至少选择一个模型。"
		: selection.length > MAX_MATERIALIZATION_MODELS
			? `一次最多物化 ${MAX_MATERIALIZATION_MODELS} 个模型。`
			: selection.some((model) => !canonical(model))
				? "历史只读模型不能进入发布与物化链路。"
				: selection.some((model) => !model.planId)
					? "所选模型尚未归属建模规划，不能启动物化。"
					: new Set(selection.map((model) => model.planId)).size !== 1
						? "批量物化只能选择同一规划下的模型。"
						: "";
	const planId = selectionProblem ? "" : primary?.planId || "";
	const selectionIsPublished = Boolean(
		selection.length && selection.every((model) => canonical(model) && model.status === "PUBLISHED"),
	);
	const load = useCallback(async () => {
		if (!planId) {
			setBusy("");
			return;
		}
		setBusy("load");
		setFailure("");
		try {
			const [releaseWorkspace, selectedMaterializations] = await Promise.all([
				getReleaseCandidateWorkbench(planId),
				getModelMaterializationStatuses(planId, Array.from(selectedIds)),
			]);
			setWorkspace(releaseWorkspace);
			setMaterializations(selectedMaterializations);
			if (
				selectionIsPublished ||
				releaseWorkspace.candidate?.status === "PUBLISHED" ||
				selectedMaterializations.some((item) => item.candidateStatus === "PUBLISHED")
			) {
				try {
					setExecutionWorkspace(await getPlanExecutionWorkspace(planId));
				} catch {
					setExecutionWorkspace(null);
				}
			} else {
				setExecutionWorkspace(null);
			}
		} catch (error) {
			setMaterializations([]);
			setFailure(normalizeModelingRequestFailure(error, "发布候选读取失败。").message);
		} finally {
			setBusy("");
		}
	}, [planId, selectedIds, selectionIsPublished]);
	useEffect(() => {
		void load();
	}, [load]);
	const candidate = workspace?.candidate || null;
	const explicitRootEntries =
		candidate?.entries.filter((entry) => entry.selectedReason === "MATERIALIZATION_ROOT") || [];
	const candidateRootEntries = explicitRootEntries.length
		? explicitRootEntries
		: candidate?.entries.filter((entry) => entry.selectedReason !== "AUTO_DEPENDENCY") || [];
	const candidateScopeMatches = Boolean(
		candidate &&
			candidateRootEntries.length === selectedIds.size &&
			candidateRootEntries.every((entry) => selectedIds.has(entry.modelSpecId)),
	);
	const scopedCandidate = candidateScopeMatches ? candidate : null;
	const selectedEvidence = candidateScopeMatches
		? workspace?.entryEvidence || []
		: materializations.map((item) => item.evidence);
	const selectedCandidateSummary = candidateScopeMatches
		? `${candidate?.status} · v${candidate?.version}`
		: materializations.length === 1
			? `${materializations[0].candidateStatus} · v${materializations[0].candidateVersion}`
			: materializations.length > 1
				? `${materializations.length} 个模型有历史物化`
				: "所选模型尚无";
	const evidenceIsHistorical =
		!candidateScopeMatches ||
		candidate?.status === "CANCELLED" ||
		selectedEvidence.some((entry) =>
			selection.some((model) => model.id === entry.modelSpecId && model.revision !== entry.modelRevision),
		);
	const buildAction: MaterializationBuildAction | null = workspace?.allowedActions.includes("CREATE_CANDIDATE")
		? "CREATE_CANDIDATE"
		: candidate && !candidateScopeMatches
			? workspace?.allowedActions.includes("REFRESH_CANDIDATE")
				? "REFRESH_AND_CREATE"
				: workspace?.allowedActions.includes("CREATE_REPLACEMENT_CANDIDATE")
					? "CREATE_AFTER_TERMINAL"
					: workspace?.allowedActions.includes("CANCEL_CANDIDATE")
						? "CANCEL_AND_CREATE"
						: null
			: candidate && workspace?.allowedActions.includes("REFRESH_CANDIDATE")
				? "REFRESH_AND_REPLACE"
				: candidate && workspace?.allowedActions.includes("CREATE_REPLACEMENT_CANDIDATE")
					? "CREATE_REPLACEMENT"
					: candidateScopeMatches && candidate && workspace?.allowedActions.includes("REMATERIALIZE")
						? "REMATERIALIZE"
						: candidateScopeMatches && workspace?.allowedActions.includes("RETRY_BUILD")
							? "RETRY_BUILD"
							: candidateScopeMatches && workspace?.allowedActions.includes("START_BUILD")
								? "START_BUILD"
								: null;
	const releaseActions = RELEASE_WORKFLOW_ACTIONS.filter(
		(action) => candidateScopeMatches && workspace?.allowedActions.includes(action),
	);
	const publishedMaterialization = materializations.find((item) => item.candidateStatus === "PUBLISHED") || null;
	const executionEnvironment =
		(scopedCandidate?.status === "PUBLISHED" ? scopedCandidate.environment : publishedMaterialization?.environment) ||
		environment;
	const executionBinding = executionWorkspace
		? executionWorkspace.bindings.find((binding) => binding.environment === executionEnvironment) ||
			executionWorkspace.bindings[0] ||
			null
		: null;
	const publishedSelection = Boolean(selectionIsPublished && executionBinding);
	const operationalAction = publishedSelection
		? executionBinding?.allowedActions.includes("REPAIR_DEPLOYMENT")
			? "REPAIR_DEPLOYMENT"
			: executionBinding?.allowedActions.includes("RUN_NOW")
				? "RUN_NOW"
				: null
		: null;
	const entries = selection.map((model, sortOrder) => ({
		modelSpecId: model.id,
		sortOrder,
		selectedReason: "从模型工作台选择",
	}));
	const materializationRequestEntries = entries;
	const materializationRequestedIds = useMemo(
		() => materializationRequestEntries.map((entry) => entry.modelSpecId),
		[materializationRequestEntries.map((entry) => entry.modelSpecId).join(",")],
	);
	const materializationRequestedKey = materializationRequestedIds.join(",");
	const refreshMaterializationPlan = useCallback(async () => {
		if (!canMaintain || !planId || !materializationRequestedIds.length) {
			setMaterializationPlan(null);
			setPlanState("idle");
			setPlanFailure("");
			return null;
		}
		setPlanState("loading");
		setPlanFailure("");
		try {
			const preview = await previewMaterializationPlan(planId, {
				environment,
				requestedModelSpecIds: materializationRequestedIds,
				strategy,
			});
			setMaterializationPlan(preview);
			setPlanState(preview.canStart ? "ready" : "blocked");
			return preview;
		} catch (error) {
			const normalized = normalizeModelingRequestFailure(error, "物化计划预览失败。");
			setMaterializationPlan(null);
			setPlanState("error");
			setPlanFailure(normalized.message);
			return null;
		}
	}, [canMaintain, environment, materializationRequestedKey, planId, strategy]);
	useEffect(() => {
		void refreshMaterializationPlan();
	}, [refreshMaterializationPlan]);
	const requiresPlan = Boolean(buildAction && PLAN_BOUND_BUILD_ACTIONS.has(buildAction));
	const currentOnlyAvailable = Boolean(
		materializationPlan?.canStart &&
			materializationPlan.orderedEntries.every((entry) => entry.dependencyRole === "ROOT" || entry.action === "REUSE"),
	);
	const canBuild = Boolean(
		!selectionProblem &&
			(operationalAction ||
				(buildAction && (!requiresPlan || (planState === "ready" && materializationPlan?.canStart)))),
	);
	const build = async () => {
		if (!canMaintain || !canBuild || !planId || !primary || !selection.every(canonical)) return;
		if (operationalAction === "REPAIR_DEPLOYMENT") {
			await repairOperationalBinding();
			return;
		}
		if (operationalAction === "RUN_NOW") {
			await runOperationalBinding();
			return;
		}
		setBusy("build");
		setFailure("");
		try {
			const requireCurrentMaterializationPlan = async (fallback: string) => {
				const preview = await previewMaterializationPlan(planId, {
					environment,
					requestedModelSpecIds: materializationRequestedIds,
					strategy,
				});
				setMaterializationPlan(preview);
				setPlanState(preview.canStart ? "ready" : "blocked");
				if (!preview.canStart) {
					throw new Error(
						preview.blockers.map((blocker) => `${blocker.code}：${blocker.message}`).join("；") || fallback,
					);
				}
				return preview;
			};
			let checkedPlan: MaterializationPlanPreview | null = null;
			if (requiresPlan) {
				checkedPlan = await requireCurrentMaterializationPlan("当前依赖计划存在阻断。");
				await compileSelectedModels(
					selection,
					checkedPlan.orderedEntries.filter((entry) => entry.action === "BUILD").map((entry) => entry.modelSpecId),
				);
				checkedPlan = await requireCurrentMaterializationPlan("编译后依赖计划发生变化，请确认阻断后重试。");
			} else if (buildAction === "START_BUILD" && candidate) {
				await compileSelectedModels(
					selection,
					candidate.entries.map((entry) => entry.modelSpecId),
				);
			} else if (buildAction !== "RETRY_BUILD") {
				await compileSelectedModels(selection);
			}
			let planFence = checkedPlan
				? { materializationPlanChecksum: checkedPlan.planChecksum, strategy: checkedPlan.strategy }
				: {};
			if (buildAction === "CREATE_CANDIDATE") {
				const created = await createReleaseCandidate(planId, crypto.randomUUID(), {
					environment,
					entries,
					reason: batch ? "从模型列表创建批量物化候选" : "从模型工作台创建单模型候选",
					...planFence,
				});
				await lockReleaseCandidate(planId, created.candidate, crypto.randomUUID(), "从模型工作台启动构建");
			} else if (buildAction === "REMATERIALIZE" && candidate) {
				await rematerializeReleaseCandidate(planId, candidate, crypto.randomUUID(), {
					environment,
					entries: materializationRequestEntries,
					reason: batch ? "从模型列表重新物化所选模型" : "从模型工作台重新物化",
					...planFence,
				});
			} else if (
				(buildAction === "REFRESH_AND_CREATE" ||
					buildAction === "CREATE_AFTER_TERMINAL" ||
					buildAction === "CANCEL_AND_CREATE") &&
				candidate
			) {
				if (buildAction === "REFRESH_AND_CREATE")
					await refreshReleaseCandidate(planId, candidate, crypto.randomUUID(), "模型已发生新修订，废弃旧候选");
				else if (buildAction === "CANCEL_AND_CREATE")
					await cancelReleaseCandidate(planId, candidate, crypto.randomUUID(), "所选模型范围已变化，关闭旧候选");
				if (buildAction !== "CREATE_AFTER_TERMINAL") {
					checkedPlan = await requireCurrentMaterializationPlan("候选更新后依赖计划存在阻断。");
					planFence = {
						materializationPlanChecksum: checkedPlan.planChecksum,
						strategy: checkedPlan.strategy,
					};
				}
				const created = await createReleaseCandidate(planId, crypto.randomUUID(), {
					environment,
					entries,
					reason: batch ? "从模型列表按新范围创建候选" : "从模型工作台按新范围创建候选",
					...planFence,
				});
				await lockReleaseCandidate(planId, created.candidate, crypto.randomUUID(), "从模型工作台启动新范围构建");
			} else if ((buildAction === "REFRESH_AND_REPLACE" || buildAction === "CREATE_REPLACEMENT") && candidate) {
				let source = candidate;
				if (buildAction === "REFRESH_AND_REPLACE") {
					source = (
						await refreshReleaseCandidate(planId, candidate, crypto.randomUUID(), "模型已发生新修订，废弃旧候选")
					).candidate;
					checkedPlan = await requireCurrentMaterializationPlan("候选刷新后依赖计划存在阻断。");
					planFence = {
						materializationPlanChecksum: checkedPlan.planChecksum,
						strategy: checkedPlan.strategy,
					};
				}
				const replacement = await createReplacementReleaseCandidate(planId, source, crypto.randomUUID(), {
					environment,
					entries,
					reason: batch ? "从模型列表按新修订创建替代候选" : "从模型工作台按新修订创建替代候选",
					...planFence,
				});
				await lockReleaseCandidate(planId, replacement.candidate, crypto.randomUUID(), "从模型工作台启动新修订构建");
			} else if (buildAction === "RETRY_BUILD" && candidate) {
				await retryReleaseCandidate(planId, candidate, crypto.randomUUID(), "从模型工作台重试构建");
			} else if (candidate?.origin === "BATCH_WORKBENCH") {
				await lockReleaseCandidate(planId, candidate, crypto.randomUUID(), "从模型工作台启动构建");
			} else {
				await startModelBuildIntent(primary, crypto.randomUUID(), { planId, environment });
			}
			await load();
			if (!batch) setTab("publish");
		} catch (error) {
			const normalized = normalizeModelingRequestFailure(error, "物化构建未能启动。");
			await Promise.all([load(), refreshMaterializationPlan()]);
			setFailure(
				normalized.code === "MODEL_MATERIALIZATION_PLAN_STALE"
					? "依赖计划已自动刷新，请确认后重试。"
					: normalized.code === "MODEL_RELEASE_CANDIDATE_VERSION_CONFLICT"
						? "候选状态已发生变化，页面已自动刷新，请确认后重试。"
						: normalized.message,
			);
		} finally {
			setBusy("");
		}
	};
	const runReleaseAction = async (action: ReleaseWorkflowAction) => {
		if (!primary || !canonical(primary) || !candidate || !releaseActions.includes(action)) return;
		setBusy("release");
		setActiveReleaseAction(action);
		setFailure("");
		try {
			const publishReason = reason.trim() || "从模型工作台发布";
			const idempotencyKey = crypto.randomUUID();
			if (action === "RUN_QUALITY" && candidate.origin === "SINGLE_MODEL_INTENT")
				await startModelPublicationIntent(primary.id, candidate, idempotencyKey, publishReason);
			else if (action === "RUN_QUALITY")
				await runReleaseCandidateQuality(planId, candidate, idempotencyKey, publishReason);
			else if (action === "SUBMIT_REVIEW")
				await submitReleaseCandidateReview(planId, candidate, idempotencyKey, publishReason);
			else if (action === "APPROVE")
				await approveReleaseCandidateReview(planId, candidate, idempotencyKey, publishReason);
			else if (action === "REJECT")
				await rejectReleaseCandidateReview(planId, candidate, idempotencyKey, publishReason);
			else if (action === "PUBLISH") await publishReleaseCandidate(planId, candidate, idempotencyKey, publishReason);
			else if (action === "RETRY_PUBLICATION")
				await retryReleaseCandidateRegistration(planId, candidate, idempotencyKey, publishReason);
			else await rollbackReleaseCandidate(planId, candidate, idempotencyKey, publishReason);
			await load();
		} catch (error) {
			const message = normalizeModelingRequestFailure(error, `${RELEASE_ACTION_LABELS[action]}未能完成。`).message;
			if (action === "RUN_QUALITY") await load();
			setFailure(message);
		} finally {
			setBusy("");
			setActiveReleaseAction(null);
		}
	};
	const rerunGovernanceQuality = async () => {
		if (!canMaintain || !planId || !scopedCandidate) return;
		setBusy("governance-quality");
		setFailure("");
		try {
			await rerunReleaseCandidateGovernanceQuality(planId, scopedCandidate, crypto.randomUUID());
			await load();
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "治理数据质量未能重新运行。").message);
		} finally {
			setBusy("");
		}
	};
	const runOperationalBinding = async () => {
		if (!planId || !executionBinding?.allowedActions.includes("RUN_NOW")) return;
		setBusy("run");
		setFailure("");
		try {
			await runPlanExecutionNow(planId, executionBinding.id, crypto.randomUUID());
			await load();
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "运行计划未能启动。").message);
		} finally {
			setBusy("");
		}
	};
	const repairOperationalBinding = async () => {
		if (!planId || !executionBinding?.allowedActions.includes("REPAIR_DEPLOYMENT")) return;
		setBusy("run");
		setFailure("");
		try {
			await repairPlanExecutionBinding(planId, executionBinding.id, executionBinding.version);
			await load();
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "运行计划部署修复未能启动。").message);
		} finally {
			setBusy("");
		}
	};
	const materializationPlanColumns = useMemo<CompactColumns<MaterializationPlanEntry>>(
		() => [
			{ title: "顺序", dataIndex: "topologyLevel", render: (value: number) => value + 1 },
			{ title: "模型", dataIndex: "modelName" },
			{ title: "分层", dataIndex: "layer" },
			{ title: "依赖角色", dataIndex: "dependencyRole" },
			{
				title: "计划动作",
				dataIndex: "action",
				render: (value: MaterializationPlanEntry["action"]) => (
					<Status tone={value === "REUSE" ? "success" : "info"}>{value}</Status>
				),
			},
			{
				title: "目标关系",
				dataIndex: "targetRelation",
				render: (value?: string | null) => value || "构建后生成",
			},
		],
		[],
	);
	const evidenceColumns = useMemo<CompactColumns<ReleaseCandidateEntryEvidence>>(
		() => [
			{
				title: "模型",
				dataIndex: "modelName",
				render: (value: string, row: ReleaseCandidateEntryEvidence) => `${value} · r${row.modelRevision}`,
			},
			{ title: "目标关系", dataIndex: "targetRelation", render: (value?: string | null) => value || "—" },
			{ title: "运行", dataIndex: "runStatus", render: (value?: string | null) => value || "未启动" },
			{
				title: "关系核验",
				dataIndex: "relationState",
				render: (value: ReleaseCandidateEntryEvidence["relationState"], row: ReleaseCandidateEntryEvidence) => (
					<Status tone={value === "VERIFIED" ? "success" : "warning"}>
						{value === "VERIFIED"
							? "关系已核验"
							: !row.observedAt && row.runStatus === "BLOCKED"
								? "未完成核验"
								: value}
					</Status>
				),
			},
			{
				title: "原因",
				dataIndex: "repairCode",
				render: (value?: string | null) =>
					value === "MODEL_AIRFLOW_DAG_NOT_REGISTERED" ? "执行任务尚未就绪，构建未启动" : value || "—",
			},
			{ title: "尝试", dataIndex: "attempt", render: (value?: number | null) => value ?? "—" },
			{ title: "完成时间", dataIndex: "finishedAt", render: (value?: string | null) => formatTime(value) },
		],
		[],
	);
	return (
		<Modal onClose={onClose} title={batch ? "批量物化" : "发布与物化"} wide>
			{!canMaintain ? (
				<RequestState
					description="当前账号不能维护所选数据范围；所级数据管理员可维护全局模型，部门数据管理员仅可维护所属部门模型。"
					kind="permission"
					title="模型维护受限"
				/>
			) : null}
			<div className="dmx-publish-grid">
				<nav>
					<button className={tab === "materialize" ? "active" : ""} onClick={() => setTab("materialize")} type="button">
						{batch ? "批量物化" : "生成物化任务"}
					</button>
					<button className={tab === "publish" ? "active" : ""} onClick={() => setTab("publish")} type="button">
						{batch ? "批量发布流程" : "发布模型"}
					</button>
				</nav>
				<section>
					{failure ? (
						<div className="dmx-inline-error" role="alert">
							{failure}
						</div>
					) : null}
					{!selectionProblem ? (
						<ModelAssetDeliveryResult key={workspace?.candidate?.status || "none"} models={selection} />
					) : null}
					{selectionProblem ? (
						<RequestState description={selectionProblem} kind="empty" title="当前选择不可物化" />
					) : tab === "materialize" ? (
						<>
							<h3>使用 dbt 生成物化任务</h3>
							<p className="dmx-capability-note">
								服务端先生成或校验当前实现制品，再由候选控制面生成 dbt 选择器与目标关系；前端不拼接 SQL 或表名。
							</p>
							<label>
								<span>执行环境</span>
								<select onChange={(event) => setEnvironment(event.target.value)} value={environment}>
									<option value="dev">开发环境</option>
									<option value="test">测试环境</option>
									<option value="prod">生产环境</option>
								</select>
							</label>
							<label>
								<span>依赖策略</span>
								<select
									onChange={(event) => setStrategy(event.target.value as MaterializationPlanStrategy)}
									value={strategy}
								>
									<option value="WITH_MISSING_UPSTREAMS">缺失上游一并构建（推荐）</option>
									<option disabled={!currentOnlyAvailable} value="CURRENT_ONLY">
										仅使用当前已验证上游
									</option>
								</select>
							</label>
							<div className="dmx-materialization-plan-toolbar">
								<strong>依赖物化计划</strong>
								<Button
									disabled={planState === "loading" || Boolean(busy)}
									onClick={() => void refreshMaterializationPlan()}
								>
									{planState === "loading" ? "预览中…" : "刷新计划"}
								</Button>
							</div>
							{planState === "error" ? (
								<div className="dmx-inline-error" role="alert">
									{planFailure}
								</div>
							) : null}
							{materializationPlan ? (
								<>
									<div className="dmx-table-scroll dmx-materialization-plan-table">
										<CompactTable<MaterializationPlanEntry>
											columns={materializationPlanColumns}
											dataSource={materializationPlan.orderedEntries}
											pagination={false}
											rowKey="modelSpecId"
										/>
									</div>
									{materializationPlan.blockers.length ? (
										<ul className="dmx-materialization-plan-blockers">
											{materializationPlan.blockers.map((blocker) => (
												<li key={`${blocker.code}:${blocker.modelSpecId || "plan"}`}>
													<strong>{blocker.code}</strong>：{blocker.message}
												</li>
											))}
										</ul>
									) : null}
									<p className="dmx-materialization-plan-checksum">
										计划校验和：{materializationPlan.planChecksum.slice(0, 12)}…
									</p>
								</>
							) : planState === "loading" ? (
								<p className="dmx-capability-note">正在计算 BUILD、REUSE 与阻断项…</p>
							) : null}
							<dl className="dmx-summary-list">
								<dt>模型范围</dt>
								<dd>{selection.map((model) => `${model.name} · r${model.revision}`).join("；")}</dd>
								<dt>物化方式</dt>
								<dd>{batch ? "按各模型实现策略" : primary?.materialization || "由实现策略决定"}</dd>
								<dt>{evidenceIsHistorical ? "历史候选" : "当前候选"}</dt>
								<dd>{selectedCandidateSummary}</dd>
								<dt>主要阻断</dt>
								<dd>
									{candidateScopeMatches && workspace?.primaryBlocker
										? `${workspace.primaryBlocker.code}：${workspace.primaryBlocker.message}`
										: "无"}
								</dd>
							</dl>
							{selectedEvidence.length && evidenceIsHistorical ? (
								<p className="dmx-capability-note">以下为历史运行结果，不代表当前模型修订的核验结果。</p>
							) : null}
							{selectedEvidence.length ? (
								<div className="dmx-table-scroll dmx-materialization-evidence">
									<CompactTable<ReleaseCandidateEntryEvidence>
										columns={evidenceColumns}
										dataSource={selectedEvidence}
										pagination={false}
										rowKey="candidateEntryId"
									/>
								</div>
							) : (
								<p className="dmx-capability-note">当前候选尚无逐表执行证据。</p>
							)}
							<div className="dmx-dialog-actions">
								<Button onClick={onClose}>取消</Button>
								<Button
									disabled={!canMaintain || !canBuild || Boolean(busy)}
									onClick={() => void build()}
									primary
									title={
										canBuild
											? undefined
											: materializationPlan?.blockers[0]?.message ||
												planFailure ||
												workspace?.primaryBlocker?.message ||
												"当前候选不允许启动构建"
									}
								>
									{busy === "build" || busy === "run"
										? "处理中…"
										: operationalAction === "REPAIR_DEPLOYMENT"
											? "修复部署"
											: operationalAction === "RUN_NOW"
												? "再次运行并核验"
												: buildAction === "REFRESH_AND_REPLACE" || buildAction === "CREATE_REPLACEMENT"
													? "按新修订重新物化"
													: buildAction === "REFRESH_AND_CREATE" || buildAction === "CREATE_AFTER_TERMINAL"
														? "按新范围重新物化"
														: buildAction === "CANCEL_AND_CREATE"
															? "替换候选并物化"
															: buildAction === "REMATERIALIZE"
																? "重新物化"
																: buildAction === "RETRY_BUILD"
																	? "重试构建"
																	: buildAction === "START_BUILD"
																		? "开始构建"
																		: batch
																			? `创建并运行 ${selection.length} 个模型`
																			: "创建并运行"}
								</Button>
							</div>
						</>
					) : (
						<>
							<h3>{batch ? "批量发布流程" : "发布模型"}</h3>
							<p className="dmx-capability-note">
								构建、工程验证和治理数据质量通过后，数据管理员可在职责范围内直接发布；旧候选仍兼容评审流程。发布登记完成不代表运行计划已经上线。
							</p>
							<ModelReleaseWorkflowPanel
								binding={executionBinding}
								candidate={scopedCandidate}
								evidence={candidateScopeMatches ? workspace?.evidence || [] : []}
								governanceQuality={candidateScopeMatches ? workspace?.governanceQuality || null : null}
								governanceQualityRerunning={busy === "governance-quality"}
								onRerunGovernanceQuality={
									canMaintain && scopedCandidate ? () => void rerunGovernanceQuality() : undefined
								}
								releaseActions={releaseActions}
							/>
							<label>
								<span>操作说明</span>
								<input onChange={(event) => setReason(event.target.value)} value={reason} />
							</label>
							<dl className="dmx-summary-list dmx-summary-list--compact">
								<dt>候选状态</dt>
								<dd>{scopedCandidate?.status || "当前计划候选不包含所选模型"}</dd>
								<dt>允许动作</dt>
								<dd>
									{releaseActions.map((action) => RELEASE_ACTION_LABELS[action]).join("、") ||
										"等待服务端推进或当前职责无可执行动作"}
								</dd>
								<dt>主要阻断</dt>
								<dd>
									{candidateScopeMatches && workspace?.primaryBlocker
										? `${workspace.primaryBlocker.code}：${workspace.primaryBlocker.message}`
										: "无"}
								</dd>
							</dl>
							<div className="dmx-dialog-actions">
								<Button disabled={Boolean(busy)} onClick={() => void load()}>
									刷新状态
								</Button>
								<Button onClick={onClose}>关闭</Button>
								{executionBinding?.allowedActions.includes("RUN_NOW") ? (
									<Button disabled={Boolean(busy)} onClick={() => void runOperationalBinding()} primary>
										{busy === "run" ? "运行中…" : "立即运行并核验"}
									</Button>
								) : null}
								{executionBinding?.allowedActions.includes("REPAIR_DEPLOYMENT") ? (
									<Button disabled={Boolean(busy)} onClick={() => void repairOperationalBinding()} primary>
										{busy === "run" ? "处理中…" : "修复部署"}
									</Button>
								) : null}
								{releaseActions.map((action, index) => (
									<Button
										danger={action === "REJECT" || action === "ROLLBACK"}
										disabled={Boolean(busy)}
										key={action}
										onClick={() => void runReleaseAction(action)}
										primary={index === 0 && action !== "REJECT" && action !== "ROLLBACK"}
									>
										{busy === "release" && activeReleaseAction === action ? "处理中…" : RELEASE_ACTION_LABELS[action]}
									</Button>
								))}
							</div>
						</>
					)}
				</section>
			</div>
		</Modal>
	);
}
