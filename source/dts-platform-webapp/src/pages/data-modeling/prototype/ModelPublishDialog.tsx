import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router";
import { getModelDeliveryStatus, type ModelDeliveryStatus } from "@/api/modelDeliveryStatusApi";
import {
	abandonReleaseCandidateBuild,
	approveReleaseCandidateReview,
	cancelReleaseCandidate,
	createReleaseCandidate,
	createReplacementReleaseCandidate,
	getModelMaterializationStatuses,
	getModelLifecycle,
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
	type ReleaseCandidateWorkbench,
	refreshReleaseCandidate,
	rejectReleaseCandidateReview,
	rematerializeReleaseCandidate,
	rerunReleaseCandidateGovernanceQuality,
	retryReleaseCandidate,
	retryReleaseCandidateRegistration,
	rollbackReleaseCandidate,
	runReleaseCandidateQuality,
	startModelBuildIntent,
	startModelPublicationIntent,
	submitReleaseCandidateReview,
} from "@/api/modelSpecApi";
import { CompactTable } from "@/components/table";
import type { CanonicalModelSpecView, ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { ModelAssetDeliveryResult } from "./ModelAssetDeliveryResult";
import { IN_FLIGHT_REFRESH_MS, isCandidateInFlight, ModelBuildProgressNotice } from "./ModelBuildProgressNotice";
import { type MaterializationBuildAction, ModelMaterializationActions } from "./ModelMaterializationActions";
import { ModelReleaseWorkflowPanel } from "./ModelReleaseWorkflowPanel";
import { ModelReleaseScopeNotice, ModelReleaseScopeSummary } from "./ModelReleaseScopeSummary";
import { materializationScopeEntries, resolveMaterializationBuildAction, resolveReleaseCandidateScope } from "./modelReleaseCandidateScope";
import { Button, Modal, RequestState } from "./PrototypePrimitives";
import { RELEASE_ACTION_LABELS, type ReleaseWorkflowAction } from "./releaseActions";
import { compileSelectedModels } from "./services/compileSelectedModels";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";
import { useModelMaterializationColumns } from "./useModelMaterializationColumns";

const MAX_MATERIALIZATION_MODELS = 64;

const RELEASE_WORKFLOW_ACTIONS: ReleaseWorkflowAction[] = [
	"RUN_QUALITY",
	"SUBMIT_REVIEW",
	"APPROVE",
	"REJECT",
	"PUBLISH",
	"RETRY_PUBLICATION",
	"ROLLBACK",
];

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

export function ModelPublishDialog({
	models,
	onClose,
	canMaintain,
	canConfigureQuality = canMaintain,
	onConfigureQuality,
	step,
	deliveryStatus,
	initialEnvironment = "dev",
	onEnvironmentChange,
	onChanged,
	onNext,
	mode = "build",
}: {
	models: ModelSpecView[];
	/** F15: the standalone dialog handles either building or version publication, never both. */
	mode?: "build" | "release";
	onClose: () => void;
	canMaintain: boolean;
	canConfigureQuality?: boolean;
	onConfigureQuality?: () => void;
	step?: "verification" | "delivery";
	deliveryStatus?: ModelDeliveryStatus | null;
	initialEnvironment?: string;
	onEnvironmentChange?: (environment: string) => void;
	onChanged?: () => void;
	onNext?: () => void;
}) {
	// Only the embedded wizard still splits build and release; the standalone dialog shows one flow (F15-T02).
	const tab = step === "delivery" ? "publish" : "materialize";
	const [environment, setEnvironment] = useState(initialEnvironment);
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
	const [blockingWorkspace, setBlockingWorkspace] = useState<ReleaseCandidateWorkbench | null>(null);
	const [confirmedCandidateScope, setConfirmedCandidateScope] = useState("");
	const loadSequence = useRef(0);
	const [pollTick, setPollTick] = useState(0);
	const selection = useMemo(() => Array.from(new Map(models.map((model) => [model.id, model])).values()), [models]);
	const selectedIds = useMemo(() => new Set(selection.map((model) => model.id)), [selection]);
	const primary = selection[0] || null;
	const batch = selection.length > 1;
	const selectionProblem = !selection.length
		? "请至少选择一个模型。"
		: selection.length > MAX_MATERIALIZATION_MODELS
			? `一次最多构建 ${MAX_MATERIALIZATION_MODELS} 个模型。`
			: selection.some((model) => !canonical(model))
				? "历史只读模型不能进入发布与构建链路。"
				: selection.some((model) => !model.planId)
					? "所选模型尚未归属建模规划，不能启动构建。"
					: new Set(selection.map((model) => model.planId)).size !== 1
						? "批量构建只能选择同一规划下的模型。"
						: "";
	const planId = selectionProblem ? "" : primary?.planId || "";
	const selectionIdentity = selection.map((model) => `${model.id}:${model.revision}:${model.checksum}`).join(",");
	useEffect(() => {
		setBlockingWorkspace(null);
	}, [planId, selectionIdentity, environment]);
	const selectionIsPublished = Boolean(
		selection.length && selection.every((model) => canonical(model) && model.status === "PUBLISHED"),
	);
	// A silent reload keeps the current projection on screen and the buttons enabled; it is the
	// background refresh while the server advances an in-flight candidate.
	const load = useCallback(async (options?: { silent?: boolean }) => {
		const silent = Boolean(options?.silent);
		const sequence = ++loadSequence.current;
		if (!silent) {
			setWorkspace(null);
			setExecutionWorkspace(null);
			setMaterializations([]);
		}
		if (!planId) {
			setBusy("");
			return;
		}
		if (!silent) setBusy("load");
		try {
			const [releaseWorkspace, selectedMaterializations, status] = await Promise.all([
				step
					? Promise.resolve(deliveryStatus?.workspace || null)
					: batch
						? getReleaseCandidateWorkbench(planId, { environment, modelSpecIds: Array.from(selectedIds) })
						: Promise.resolve(null),
				getModelMaterializationStatuses(planId, Array.from(selectedIds)),
				!step && !batch && primary ? getModelDeliveryStatus(primary.id, environment) : Promise.resolve(null),
			]);
			if (sequence !== loadSequence.current) return;
			const identityMatches =
				status &&
				primary &&
				status.modelSpecId === primary.id &&
				status.planId === planId &&
				status.modelRevision === primary.revision &&
				status.modelChecksum === primary.checksum;
			const aggregateMatches =
				identityMatches &&
				(status.candidate
					? status.environment === environment &&
						status.candidate.matchesCurrentModel &&
						status.workspace?.candidate?.id === status.candidate.id &&
						status.workspace.candidate.version === status.candidate.version
					: !status.workspace?.candidate && (!status.environment || status.environment === environment));
			const scopedWorkspace = !step && !batch ? (aggregateMatches ? status.workspace : null) : releaseWorkspace;
			let nextExecutionWorkspace: PlanExecutionWorkspace | null = null;
			if (
				selectionIsPublished ||
				scopedWorkspace?.candidate?.status === "PUBLISHED" ||
				selectedMaterializations.some(
					(item) => item.candidateStatus === "PUBLISHED" && item.environment === environment,
				)
			) {
				try {
					nextExecutionWorkspace = await getPlanExecutionWorkspace(planId);
				} catch {
					/* The candidate remains readable while execution state is unavailable. */
				}
			}
			if (sequence !== loadSequence.current) return;
			setWorkspace(scopedWorkspace);
			setMaterializations(selectedMaterializations);
			setExecutionWorkspace(nextExecutionWorkspace);
		} catch (error) {
			if (sequence !== loadSequence.current || silent) return;
			setFailure(normalizeModelingRequestFailure(error, "发布单读取失败。").message);
		} finally {
			if (sequence === loadSequence.current) {
				if (silent) setPollTick((tick) => tick + 1);
				else setBusy("");
			}
		}
	}, [planId, selectedIds, selectionIsPublished, step, deliveryStatus, batch, primary, environment]);
	useEffect(() => {
		void load();
		return () => {
			++loadSequence.current;
		};
	}, [load]);
	const candidate = workspace?.candidate || null;
	const pageAction = deliveryStatus?.wizard.find((page) => page.key === step)?.primaryAction;
	const embedded = Boolean(step);
	const { containsSelection: candidateContainsSelection, exactScope: candidateScopeMatches } =
		resolveReleaseCandidateScope(candidate, selection, planId, environment);
	const candidateScopeKey = `${candidate?.id}:${candidate?.version}:${environment}:${selectionIdentity}`;
	const candidateCommandScopeAllowed = candidateScopeMatches ||
		(candidateContainsSelection && confirmedCandidateScope === candidateScopeKey);
	const scopedCandidate = candidateContainsSelection ? candidate : null;
	const selectedEvidence = candidateContainsSelection
		? (workspace?.entryEvidence || []).filter((entry) => selectedIds.has(entry.modelSpecId))
		: materializations.map((item) => item.evidence);
	const selectedCandidateSummary = candidateContainsSelection
		? `${candidate?.status} · v${candidate?.version}`
		: materializations.length === 1
			? `${materializations[0].candidateStatus} · v${materializations[0].candidateVersion}`
			: materializations.length > 1
				? `${materializations.length} 个模型有历史构建`
				: "所选模型尚无";
	const evidenceIsHistorical =
		!candidateContainsSelection ||
		candidate?.status === "CANCELLED" ||
		selectedEvidence.some((entry) =>
			selection.some((model) => model.id === entry.modelSpecId && model.revision !== entry.modelRevision),
		);
	const buildAction = resolveMaterializationBuildAction(workspace, candidateContainsSelection, candidateScopeMatches);
	const candidateInFlight = isCandidateInFlight(scopedCandidate?.status);
	const canAbandonBuild = canMaintain && candidateCommandScopeAllowed &&
		Boolean(workspace?.allowedActions.includes("ABANDON_BUILD"));
	const unconfirmedDispatch = candidateContainsSelection
		? selectedEvidence.find((entry) => entry.runStatus === "UNKNOWN" || entry.relationState === "UNKNOWN")
		: undefined;
	// The embedded wizard is refreshed by its parent's delivery-status polling.
	// biome-ignore lint/correctness/useExhaustiveDependencies: pollTick re-arms the timer after every silent reload.
	useEffect(() => {
		if (embedded || !candidateInFlight || busy) return;
		const timer = setTimeout(() => void load({ silent: true }), IN_FLIGHT_REFRESH_MS);
		return () => clearTimeout(timer);
	}, [embedded, candidateInFlight, busy, load, pollTick]);
	const releaseActions = RELEASE_WORKFLOW_ACTIONS.filter(
		(action) => candidateCommandScopeAllowed && workspace?.allowedActions.includes(action),
	);
	const executionBinding = executionWorkspace?.bindings.find((binding) => binding.environment === environment) || null;
	// F15 (option 2): running a published version is handled in 运维中心 › 调度计划, not in these dialogs.
	// An unchanged published version with a runnable binding is run from 调度计划; this dialog never rebuilds it instead.
	const runsFromSchedule = Boolean(
		selectionIsPublished &&
			executionBinding?.allowedActions.some((action) => action === "RUN_NOW" || action === "REPAIR_DEPLOYMENT"),
	);
	const schedulePath = planId ? `/ops/instances?tab=schedule&planId=${encodeURIComponent(planId)}` : "/ops/instances?tab=schedule";
	const materializationRequestEntries = useMemo(
		() => materializationScopeEntries(candidate, selection, buildAction), [candidate, selection, buildAction],
	);
	const materializationRequestedIds = useMemo(
		() => materializationRequestEntries.map((entry) => entry.modelSpecId), [materializationRequestEntries],
	);
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
			const normalized = normalizeModelingRequestFailure(error, "构建计划预览失败。");
			setMaterializationPlan(null);
			setPlanState("error");
			setPlanFailure(normalized.message);
			return null;
		}
	}, [canMaintain, environment, materializationRequestedIds, planId, strategy]);
	useEffect(() => {
		void refreshMaterializationPlan();
	}, [refreshMaterializationPlan]);
	const requiresPlan = Boolean(buildAction && PLAN_BOUND_BUILD_ACTIONS.has(buildAction));
	const materializationBlocker = planState === "loading"
		? "正在核对构建计划"
		: planState === "error"
			? planFailure || "构建计划加载失败，请刷新计划后重试"
			: materializationPlan?.blockers[0]
				? `${materializationPlan.blockers[0].code}：${materializationPlan.blockers[0].message}`
				: planState === "blocked"
					? "当前构建计划不允许开始，请刷新计划并核对加工配置"
					: candidateScopeMatches && workspace?.primaryBlocker
						? `${workspace.primaryBlocker.code}：${workspace.primaryBlocker.message}`
						: planState === "idle" ? "构建计划尚未核对" : "无";
	const currentOnlyAvailable = Boolean(
		materializationPlan?.canStart &&
			materializationPlan.orderedEntries.every((entry) => entry.dependencyRole === "ROOT" || entry.action === "REUSE"),
	);
	const canBuild = Boolean(
		!selectionProblem && !blockingWorkspace?.candidate && !runsFromSchedule &&
			(buildAction !== "REMATERIALIZE" || candidateCommandScopeAllowed) &&
			buildAction && (!requiresPlan || (planState === "ready" && materializationPlan?.canStart)),
	);
	const build = async () => {
		if (!canMaintain || !canBuild || !planId || !primary || !selection.every(canonical)) return;
		setBusy("build");
		setFailure("");
		try {
			if (!batch && buildAction === "CREATE_CANDIDATE") {
				const lifecycle = await getModelLifecycle(primary.id);
				const implementation = lifecycle.implementation;
				const schemaOnly = implementation?.inputMode === "GENERATED" && implementation.inputs.length === 1 &&
					"generatorType" in implementation.inputs[0] && (implementation.inputs[0].generatorType === "SCHEMA_ONLY" ||
						(implementation.ownership === "DBT_MANAGED" && implementation.inputs[0].generatorType === "DBT" && implementation.inputs[0].config?.buildMode === "SCHEMA_ONLY"));
				if (schemaOnly) {
					await compileSelectedModels(selection);
					await startModelBuildIntent(primary, crypto.randomUUID(), { planId, environment, buildMode: "SCHEMA_ONLY" });
					await load();
					return true;
				}
			}
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
				if (buildAction !== "REMATERIALIZE") {
					await compileSelectedModels(
						selection,
						checkedPlan.orderedEntries.filter((entry) => entry.action === "BUILD").map((entry) => entry.modelSpecId),
					);
					checkedPlan = await requireCurrentMaterializationPlan("编译后依赖计划发生变化，请确认阻断后重试。");
				}
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
					entries: materializationRequestEntries,
					reason: batch ? "从模型列表创建批量构建发布单" : "从模型工作台创建单模型发布单",
					...planFence,
				});
				await lockReleaseCandidate(planId, created.candidate, crypto.randomUUID(), "从模型工作台启动构建");
			} else if (buildAction === "REMATERIALIZE" && candidate) {
				await rematerializeReleaseCandidate(planId, candidate, crypto.randomUUID(), {
					environment,
					entries: materializationRequestEntries,
					reason: batch ? "从模型列表重新构建所选模型" : "从模型工作台重新构建",
					...planFence,
				});
			} else if (
				(buildAction === "REFRESH_AND_CREATE" ||
					buildAction === "CREATE_AFTER_TERMINAL" ||
					buildAction === "CANCEL_AND_CREATE") &&
				candidate
			) {
				if (buildAction === "REFRESH_AND_CREATE")
					await refreshReleaseCandidate(planId, candidate, crypto.randomUUID(), "模型已发生新修订，废弃旧发布单");
				else if (buildAction === "CANCEL_AND_CREATE")
					await cancelReleaseCandidate(planId, candidate, crypto.randomUUID(), "所选模型范围已变化，关闭旧发布单");
				if (buildAction !== "CREATE_AFTER_TERMINAL") {
					checkedPlan = await requireCurrentMaterializationPlan("发布单更新后依赖计划存在阻断。");
					planFence = {
						materializationPlanChecksum: checkedPlan.planChecksum,
						strategy: checkedPlan.strategy,
					};
				}
				const created = await createReleaseCandidate(planId, crypto.randomUUID(), {
					environment,
					entries: materializationRequestEntries,
					reason: batch ? "从模型列表按新范围创建发布单" : "从模型工作台按新范围创建发布单",
					...planFence,
				});
				await lockReleaseCandidate(planId, created.candidate, crypto.randomUUID(), "从模型工作台启动新范围构建");
			} else if ((buildAction === "REFRESH_AND_REPLACE" || buildAction === "CREATE_REPLACEMENT") && candidate) {
				let source = candidate;
				if (buildAction === "REFRESH_AND_REPLACE") {
					source = (
						await refreshReleaseCandidate(planId, candidate, crypto.randomUUID(), "模型已发生新修订，废弃旧发布单")
					).candidate;
					checkedPlan = await requireCurrentMaterializationPlan("发布单刷新后依赖计划存在阻断。");
					planFence = {
						materializationPlanChecksum: checkedPlan.planChecksum,
						strategy: checkedPlan.strategy,
					};
				}
				const replacement = await createReplacementReleaseCandidate(planId, source, crypto.randomUUID(), {
					environment,
					entries: materializationRequestEntries,
					reason: batch ? "从模型列表按新修订创建替代发布单" : "从模型工作台按新修订创建替代发布单",
					...planFence,
				});
				await lockReleaseCandidate(planId, replacement.candidate, crypto.randomUUID(), "从模型工作台启动新修订构建");
			} else if (buildAction === "RETRY_BUILD" && candidate) {
				await retryReleaseCandidate(planId, candidate, crypto.randomUUID(), "从模型工作台重试构建");
			} else if (candidate?.origin === "BATCH_WORKBENCH") {
				await lockReleaseCandidate(planId, candidate, crypto.randomUUID(), "从模型工作台启动构建");
			} else {
				const lifecycle = await getModelLifecycle(primary.id);
				const implementation = lifecycle.implementation;
				const schemaOnly = implementation?.inputMode === "GENERATED" && implementation.inputs.length === 1 &&
					"generatorType" in implementation.inputs[0] && (implementation.inputs[0].generatorType === "SCHEMA_ONLY" ||
						(implementation.ownership === "DBT_MANAGED" && implementation.inputs[0].generatorType === "DBT" && implementation.inputs[0].config?.buildMode === "SCHEMA_ONLY"));
				await startModelBuildIntent(primary, crypto.randomUUID(), { planId, environment, buildMode: schemaOnly ? "SCHEMA_ONLY" : "DATA_BUILD" });
			}
			await load();
			return true;
		} catch (error) {
			const normalized = normalizeModelingRequestFailure(error, "数据构建未能启动。");
			await Promise.all([load(), refreshMaterializationPlan()]);
			if (normalized.code === "MODEL_RELEASE_CANDIDATE_ACTIVE_EXISTS") {
				const sequence = loadSequence.current;
				try {
					const buildIds = materializationPlan?.orderedEntries.filter((entry) => entry.action === "BUILD").map((entry) => entry.modelSpecId);
					const modelSpecIds = buildIds?.length ? buildIds : Array.from(selectedIds);
					const occupied = await getReleaseCandidateWorkbench(planId, { environment, modelSpecIds });
					if (sequence !== loadSequence.current) return false;
					if (occupied.candidate?.planId === planId && occupied.candidate.environment === environment &&
						occupied.candidate.entries.some((entry) => modelSpecIds.includes(entry.modelSpecId)) &&
						!["PUBLISHED", "REJECTED", "ROLLED_BACK", "CANCELLED", "STALE"].includes(occupied.candidate.status)) {
						setBlockingWorkspace(occupied);
					}
				} catch {
					// Keep the original conflict visible if the recovery projection cannot be read.
				}
				setFailure("所选模型在当前环境已有未结束的构建发布单。请先处理占用发布单，再开始当前模型的构建。（错误码 MODEL_RELEASE_CANDIDATE_ACTIVE_EXISTS）");
				return false;
			}
			setFailure(
				normalized.code === "MODEL_MATERIALIZATION_PLAN_STALE"
					? "依赖计划已自动刷新，请确认后重试。"
					: normalized.code === "MODEL_RELEASE_CANDIDATE_VERSION_CONFLICT"
						? "发布单状态已发生变化，页面已自动刷新，请确认后重试。"
						: normalized.message,
			);
			return false;
		} finally {
			setBusy("");
		}
	};
	const abandonBuild = async () => {
		if (busy || !candidate || !canAbandonBuild) return false;
		setBusy("build");
		setFailure("");
		try {
			await abandonReleaseCandidateBuild(planId, candidate, crypto.randomUUID(), "用户放弃长时间无进展的构建");
			await Promise.all([load(), refreshMaterializationPlan()]);
			onChanged?.();
			return true;
		} catch (error) {
			const normalized = normalizeModelingRequestFailure(error, "放弃本次构建未能完成，请刷新后核对状态。");
			setFailure(
				normalized.code === "MODEL_MATERIALIZATION_BUILD_IN_PROGRESS"
					? "构建派发正在处理中，暂时不能放弃；请稍后刷新状态再试。（错误码 MODEL_MATERIALIZATION_BUILD_IN_PROGRESS）"
					: normalized.code === "MODEL_RELEASE_CANDIDATE_VERSION_CONFLICT"
						? "发布单状态已发生变化，页面已自动刷新，请确认后重试。"
						: normalized.message,
			);
			await load();
			return false;
		} finally {
			setBusy("");
		}
	};
	const closeBlockingCandidate = async () => {
		const occupied = blockingWorkspace?.candidate;
		if (busy || !canMaintain || !occupied || occupied.planId !== planId || !blockingWorkspace?.allowedActions.includes("CANCEL_CANDIDATE")) return;
		setBusy("build");
		try {
			await cancelReleaseCandidate(planId, occupied, crypto.randomUUID(), "用户确认关闭占用规划的旧发布单，保留现有构建结果");
			setBlockingWorkspace(null);
			await Promise.all([load(), refreshMaterializationPlan()]);
			setFailure("旧发布单已关闭。请点击开始构建，为当前模型创建新的发布单。");
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "关闭占用发布单失败，请刷新页面后核对状态。").message);
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
		if (!canMaintain || !planId || !scopedCandidate || !candidateCommandScopeAllowed) return;
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
	const { materializationPlanColumns, evidenceColumns } = useModelMaterializationColumns();
	const scopedGovernanceQuality = candidateContainsSelection ? workspace?.governanceQuality || null : null;
	const materializeContent = (
		<>
			{embedded ? <h3>构建与检查</h3> : null}
			{candidateContainsSelection && selectedEvidence.filter((entry) => entry.failureMessage).map((entry) => (
				<div key={entry.candidateEntryId} role="alert" className="dmx-request-state">
					<strong>{entry.modelName}：构建未完成</strong>
					<p style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{entry.failureMessage}</p>
					<p>错误码：{entry.repairCode || "未提供"}；执行编号：{entry.pipelineRunGroupId}</p>
				</div>
			))}
			<label>
				<span>执行环境</span>
				<select
					onChange={(event) => {
						setEnvironment(event.target.value);
						onEnvironmentChange?.(event.target.value);
					}}
					value={environment}
				>
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
				<strong>依赖构建计划</strong>
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
						计划校验码：{materializationPlan.planChecksum.slice(0, 12)}…
					</p>
				</>
			) : planState === "loading" ? (
				<p className="dmx-capability-note">正在计算 BUILD、REUSE 与阻断项…</p>
			) : null}
			<dl className="dmx-summary-list">
				<dt>模型范围</dt>
				<dd>{selection.map((model) => `${model.name} · r${model.revision}`).join("；")}</dd>
				<dt>存储方式</dt>
				<dd>{batch ? "按各模型代码策略" : primary?.materialization || "由加工策略决定"}</dd>
				<dt>{evidenceIsHistorical ? "历史发布单" : "当前发布单"}</dt>
				<dd>{selectedCandidateSummary}</dd>
				<dt>主要阻断</dt>
				<dd>
					{materializationBlocker}
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
				<p className="dmx-capability-note">当前发布单尚无逐表执行记录。</p>
			)}
			{(
				<ModelMaterializationActions
					embedded={embedded}
					pageAction={pageAction && ["RUN_QUALITY", "RERUN_GOVERNANCE_QUALITY"].includes(pageAction.code) && !candidateCommandScopeAllowed
						? { ...pageAction, enabled: false } : pageAction}
					canMaintain={canMaintain}
					canConfigureQuality={canConfigureQuality}
					busy={busy}
					canBuild={canBuild}
					buildAction={buildAction}
					modelCount={materializationRequestedIds.length}
					buildBlocker={
						materializationBlocker === "无" ? undefined : materializationBlocker
					}
					onBack={onClose}
					onConfigureQuality={onConfigureQuality}
					onPrimaryAction={() => {
						if (embedded && pageAction?.code === "RUN_QUALITY")
							void runReleaseAction("RUN_QUALITY").then(onChanged);
						else if (embedded && pageAction?.code === "NEXT") onNext?.();
						else if (embedded && pageAction?.code === "RERUN_GOVERNANCE_QUALITY")
							void rerunGovernanceQuality().then(onChanged);
						else void build().then((completed) => { if (completed) onChanged?.(); });
					}}
				/>
			)}
			{!embedded && runsFromSchedule ? (
				<p className="dmx-capability-note">
					当前版本已发布且没有改动，无需重新构建。运行已发布版本请到 <Link to={schedulePath}>运维中心 › 调度计划</Link>。
				</p>
			) : null}
			{!embedded && !selectionProblem ? (
				<ModelAssetDeliveryResult key={workspace?.candidate?.status || "none"} models={selection} />
			) : null}
		</>
	);
	const publishContent = (
		<>
			{embedded ? <h3>{batch ? "批量发布流程" : "发布模型"}</h3> : null}
			{!embedded ? (
				<ModelReleaseWorkflowPanel
					binding={executionBinding}
					candidate={scopedCandidate}
					evidence={candidateContainsSelection ? workspace?.evidence || [] : []}
					governanceQuality={scopedGovernanceQuality}
					governanceQualityRerunning={busy === "governance-quality"}
					onRerunGovernanceQuality={
						canMaintain && scopedCandidate && candidateCommandScopeAllowed
							? () => void rerunGovernanceQuality()
							: undefined
					}
					releaseActions={releaseActions}
				/>
			) : null}
			{embedded || releaseActions.length ? (
				<label>
					<span>操作说明</span>
					<input onChange={(event) => setReason(event.target.value)} value={reason} />
				</label>
			) : null}
			{!embedded && executionBinding ? (
				<p className="dmx-capability-note">
					发布只登记正式版本。已发布版本的运行、部署修复在 <Link to={schedulePath}>运维中心 › 调度计划</Link> 办理，运行失败不会改变发布结果。
				</p>
			) : null}
			<ModelReleaseScopeSummary
				status={busy === "load" ? "正在读取发布单状态…" : failure ? "发布单状态读取失败" :
					scopedCandidate?.status || (candidate ? "当前发布单不包含所选模型的当前版本" : "当前模型暂无发布单")}
				actions={releaseActions.map((action) => RELEASE_ACTION_LABELS[action])}
				blocker={candidateContainsSelection ? workspace?.primaryBlocker?.message || workspace?.governanceQuality?.message : null}
			/>
			<div className="dmx-dialog-actions">
				<Button
					disabled={Boolean(busy)}
					onClick={() => {
						void load();
						onChanged?.();
					}}
				>
					刷新状态
				</Button>
				<Button onClick={onClose}>关闭</Button>
				{releaseActions
					.filter((action) => !embedded || action === pageAction?.code)
					.map((action, index) => (
						<Button
							danger={action === "REJECT" || action === "ROLLBACK"}
							disabled={Boolean(busy) || (embedded && !pageAction?.enabled)}
							key={action}
							onClick={() => void runReleaseAction(action).then(onChanged)}
							primary={index === 0 && action !== "REJECT" && action !== "ROLLBACK"}
						>
							{busy === "release" && activeReleaseAction === action ? "处理中…" : RELEASE_ACTION_LABELS[action]}
						</Button>
					))}
			</div>
		</>
	);
	const occupiedNotice = blockingWorkspace?.candidate ? (
		<div role="alert" className="dmx-request-state">
			<p>所选模型范围被发布单 {blockingWorkspace.candidate.id} 占用（{blockingWorkspace.candidate.status}）。涉及模型：{blockingWorkspace.candidate.entries.map((entry) => blockingWorkspace.entryEvidence?.find((evidence) => evidence.modelSpecId === entry.modelSpecId)?.modelName || entry.modelSpecId).join("、")}。</p>
			<p>关闭会结束该发布单的后续发布流程，保留已有构建结果。确认不再继续该发布单后，再关闭并重新开始构建。</p>
			{!blockingWorkspace.allowedActions.includes("CANCEL_CANDIDATE") ? (
				<p>该发布单当前不能关闭，请先在原模型中处理正在进行的构建或发布流程。</p>
			) : (
				<Button disabled={!canMaintain || Boolean(busy)} onClick={() => void closeBlockingCandidate()}>确认关闭占用发布单</Button>
			)}
		</div>
	) : null;
	const sharedNotices = (
		<>
			{failure ? (
				<div className="dmx-inline-error" role="alert">
					{failure}
				</div>
			) : null}
			{occupiedNotice}
			<ModelReleaseScopeNotice candidate={scopedCandidate} evidence={workspace?.entryEvidence || []}
				selectedCount={selection.length} exactScope={candidateScopeMatches}
				confirmed={confirmedCandidateScope === candidateScopeKey} disabled={Boolean(busy)}
				onConfirm={(confirmed) => setConfirmedCandidateScope(confirmed ? candidateScopeKey : "")} />
			{!selectionProblem && scopedCandidate?.status === "BUILDING" ? (
				<ModelBuildProgressNotice
					busy={Boolean(busy)}
					canAbandon={canAbandonBuild}
					onAbandon={abandonBuild}
					unconfirmedCode={unconfirmedDispatch
						? unconfirmedDispatch.repairCode || "MODEL_MATERIALIZATION_DISPATCH_UNKNOWN"
						: null}
				/>
			) : null}
		</>
	);
	return (
		<DeliveryContainer embedded={embedded} onClose={onClose} title={mode === "release" ? (batch ? "批量版本发布" : "版本发布") : batch ? "批量构建" : "构建"}>
			{!canMaintain && !embedded ? (
				<RequestState
					description="当前账号不能维护所选数据范围；所级数据管理员可维护全局模型，部门数据管理员仅可维护所属部门模型。"
					kind="permission"
					title="模型维护受限"
				/>
			) : null}
			{embedded ? (
				<div className="dmx-delivery-step">
					<section>
						{sharedNotices}
						{selectionProblem ? (
							<RequestState description={selectionProblem} kind="empty" title="当前选择不可构建" />
						) : tab === "materialize" ? materializeContent : publishContent}
					</section>
				</div>
			) : (
				<section className="dmx-delivery-single">
					{sharedNotices}
					{selectionProblem ? (
						<RequestState description={selectionProblem} kind="empty" title={mode === "release" ? "当前选择不可发布" : "当前选择不可构建"} />
					) : mode === "release" ? publishContent : materializeContent}
				</section>
			)}
		</DeliveryContainer>
	);
}

function DeliveryContainer({
	embedded,
	children,
	onClose,
	title,
}: {
	embedded: boolean;
	children: ReactNode;
	onClose: () => void;
	title: string;
}) {
	return embedded ? (
		<section aria-label={title}>{children}</section>
	) : (
		<Modal onClose={onClose} title={title} wide>
			{children}
		</Modal>
	);
}
