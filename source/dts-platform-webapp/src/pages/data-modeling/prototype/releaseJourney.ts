import type { DeliveryStepState } from "@/api/modelDeliveryStatusApi";
import type {
	PlanExecutionBinding,
	ReleaseCandidate,
	ReleaseCandidateBlocker,
	ReleaseCandidateEntryEvidence,
	ReleaseCandidateGovernanceQuality,
	ReleaseCandidateLifecycleAction,
} from "@/api/modelSpecApi";

/**
 * F15-T01: one derivation of "where is this release, why is it stopped, what can the user do next".
 * The dialog renders this result only; actions come exclusively from server-provided allowances.
 */

export type ReleaseWorkflowAction = Extract<
	ReleaseCandidateLifecycleAction,
	"RUN_QUALITY" | "SUBMIT_REVIEW" | "APPROVE" | "REJECT" | "PUBLISH" | "RETRY_PUBLICATION" | "ROLLBACK"
>;

export type JourneyStageKey =
	| "precheck"
	| "build"
	| "registration"
	| "engineering"
	| "governance"
	| "review"
	| "publish"
	| "online";

export type JourneyStageState = "waiting" | "active" | "passed" | "warning" | "failed" | "rolled-back";

export type JourneyActionKey =
	| "BUILD"
	| "CANCEL_BLOCKING_CANDIDATE"
	| "RERUN_GOVERNANCE_QUALITY"
	| "CONFIGURE_QUALITY"
	| "RUN_NOW"
	| "REPAIR_DEPLOYMENT"
	| "RELOAD"
	| ReleaseWorkflowAction;

export interface JourneyAction {
	key: JourneyActionKey;
	label: string;
	tone: "primary" | "default" | "danger";
}

export interface JourneyStage {
	key: JourneyStageKey;
	label: string;
	state: JourneyStageState;
	summary: string;
	reason?: string;
	technical?: { code?: string | null; runId?: string | null; detail?: string | null };
}

export interface ReleaseJourney {
	stages: JourneyStage[];
	current: JourneyStageKey;
	headline: string;
	primaryAction?: JourneyAction;
	secondaryAction?: JourneyAction;
	policy?: "ADVISORY" | "BLOCKING";
}

export interface ReleaseJourneyInput {
	loadState: "loading" | "error" | "ready";
	candidate: ReleaseCandidate | null;
	entryEvidence: ReleaseCandidateEntryEvidence[];
	governanceQuality: ReleaseCandidateGovernanceQuality | null;
	primaryBlocker: ReleaseCandidateBlocker | null;
	releaseActions: ReleaseWorkflowAction[];
	plan: {
		state: "idle" | "loading" | "error" | "ready";
		blockers: { code: string; message: string }[];
		failure?: string;
	};
	precheckBlocker: string | null;
	canBuild: boolean;
	buildLabel: string;
	registration: { catalog: DeliveryStepState | null; analysis: DeliveryStepState | null } | null;
	binding: PlanExecutionBinding | null;
	canRerunGovernanceQuality: boolean;
	canConfigureQuality: boolean;
	canCancelBlockingCandidate: boolean;
}

export const RELEASE_ACTION_LABELS: Record<ReleaseWorkflowAction, string> = {
	RUN_QUALITY: "运行工程验证",
	SUBMIT_REVIEW: "提交发布评审",
	APPROVE: "审核通过",
	REJECT: "驳回",
	PUBLISH: "确认发布",
	RETRY_PUBLICATION: "重试发布",
	ROLLBACK: "回滚发布",
};

const STAGE_LABELS: Record<JourneyStageKey, string> = {
	precheck: "构建前检查",
	build: "构建",
	registration: "资产登记",
	engineering: "工程验证",
	governance: "治理数据质量",
	review: "发布评审",
	publish: "发布",
	online: "上线就绪",
};

const STATUS_ORDER = [
	"DRAFT",
	"BUILDING",
	"BUILD_FAILED",
	"BUILT",
	"QUALITY_RUNNING",
	"QUALITY_FAILED",
	"QUALITY_PASSED",
	"REVIEW_PENDING",
	"REJECTED",
	"APPROVED",
	"PUBLISHING",
	"PARTIAL",
	"PUBLISHED",
	"ROLLED_BACK",
] as const;
type TrackedStatus = (typeof STATUS_ORDER)[number];

const trackedStatus = (candidate: ReleaseCandidate | null): TrackedStatus | null => {
	const status = candidate?.status as TrackedStatus | undefined;
	return status && STATUS_ORDER.includes(status) ? status : null;
};

const reached = (status: TrackedStatus | null, target: TrackedStatus) =>
	status !== null && STATUS_ORDER.indexOf(status) >= STATUS_ORDER.indexOf(target);

const releaseAction = (action: ReleaseWorkflowAction, label = RELEASE_ACTION_LABELS[action]): JourneyAction => ({
	key: action,
	label,
	tone: action === "REJECT" || action === "ROLLBACK" ? "danger" : "primary",
});

function precheckStage(input: ReleaseJourneyInput, status: TrackedStatus | null): JourneyStage {
	const stage = { key: "precheck" as const, label: STAGE_LABELS.precheck };
	if (status) return { ...stage, state: "passed", summary: "构建范围与依赖计划已确认" };
	if (input.precheckBlocker) return { ...stage, state: "failed", summary: "暂不能构建", reason: input.precheckBlocker };
	const planBlocker = input.plan.blockers[0];
	if (planBlocker)
		return {
			...stage,
			state: "failed",
			summary: "依赖计划存在阻断",
			reason: planBlocker.message,
			technical: { code: planBlocker.code },
		};
	if (input.plan.state === "error")
		return {
			...stage,
			state: "failed",
			summary: "依赖计划未能计算",
			reason: input.plan.failure || "依赖计划未能计算，请重试",
		};
	if (input.plan.state === "loading") return { ...stage, state: "active", summary: "正在计算依赖构建计划…" };
	return { ...stage, state: "active", summary: "可以开始构建" };
}

function buildStage(input: ReleaseJourneyInput, status: TrackedStatus | null): JourneyStage {
	const stage = { key: "build" as const, label: STAGE_LABELS.build };
	const total = input.entryEvidence.length;
	const verified = input.entryEvidence.filter((entry) => entry.relationState === "VERIFIED").length;
	if (!status) return { ...stage, state: "waiting", summary: "等待构建" };
	if (status === "BUILD_FAILED") {
		const failed = input.entryEvidence.find((entry) => entry.failureMessage || entry.repairCode);
		return {
			...stage,
			state: "failed",
			summary: "构建未完成",
			reason: input.primaryBlocker?.message || "构建未完成，请查看技术详情后重新构建",
			technical: {
				code: failed?.repairCode || input.primaryBlocker?.code || null,
				runId: failed?.pipelineRunGroupId || null,
				detail: failed?.failureMessage || null,
			},
		};
	}
	if (status === "DRAFT" || status === "BUILDING")
		return { ...stage, state: "active", summary: total ? `${verified}/${total} 张表完成` : "构建中" };
	return { ...stage, state: "passed", summary: total ? `${total} 张表完成` : "构建完成" };
}

function registrationStage(input: ReleaseJourneyInput, status: TrackedStatus | null): JourneyStage {
	const stage = { key: "registration" as const, label: STAGE_LABELS.registration };
	const catalog = input.registration?.catalog ?? null;
	const analysis = input.registration?.analysis ?? null;
	if (!reached(status, "BUILT") || status === "BUILD_FAILED")
		return { ...stage, state: "waiting", summary: "构建完成后登记资产" };
	if (catalog === "FAILED" || analysis === "FAILED")
		return {
			...stage,
			state: "failed",
			summary: catalog === "FAILED" ? "目录登记失败" : "分析准备失败",
			reason:
				catalog === "FAILED"
					? "资产目录登记未完成，请刷新状态；仍失败时联系管理员处理"
					: "分析准备未完成，请刷新状态；仍失败时联系管理员处理",
		};
	if (catalog === "SUCCEEDED" && (analysis === "SUCCEEDED" || analysis === "NOT_APPLICABLE"))
		return { ...stage, state: "passed", summary: "目录已登记 · 分析已准备" };
	if (catalog === "RUNNING" || catalog === "WAITING_INPUT" || analysis === "RUNNING" || analysis === "WAITING_INPUT")
		return { ...stage, state: "active", summary: "登记中" };
	if (catalog === "SUCCEEDED") return { ...stage, state: "passed", summary: "目录已登记" };
	return { ...stage, state: "waiting", summary: "暂无当前登记记录" };
}

function engineeringStage(input: ReleaseJourneyInput, status: TrackedStatus | null): JourneyStage {
	const stage = { key: "engineering" as const, label: STAGE_LABELS.engineering };
	if (status === "QUALITY_FAILED")
		return {
			...stage,
			state: "failed",
			summary: "未通过",
			reason: input.primaryBlocker?.message || "工程验证未通过",
			technical: { code: input.primaryBlocker?.code || null },
		};
	if (status === "QUALITY_RUNNING") return { ...stage, state: "active", summary: "验证中" };
	if (reached(status, "QUALITY_PASSED")) return { ...stage, state: "passed", summary: "已通过" };
	if (status === "BUILT") return { ...stage, state: "waiting", summary: "待运行" };
	return { ...stage, state: "waiting", summary: "等待构建完成" };
}

function governanceStage(input: ReleaseJourneyInput, status: TrackedStatus | null): JourneyStage {
	const stage = { key: "governance" as const, label: STAGE_LABELS.governance };
	const quality = input.governanceQuality;
	if (!status || !quality) return { ...stage, state: "waiting", summary: "等待工程验证" };
	if (quality.state === "PASSED") return { ...stage, state: "passed", summary: "质量检查通过" };
	if (quality.state === "RUNNING") return { ...stage, state: "active", summary: "质量检查运行中" };
	const unresolved = quality.state === "FAILED" || quality.state === "STALE" || reached(status, "QUALITY_PASSED");
	if (!unresolved) return { ...stage, state: "waiting", summary: "等待工程验证" };
	const technical = { code: quality.code || null };
	const reason = quality.message || "治理数据质量记录尚未就绪";
	return quality.required
		? { ...stage, state: "failed", summary: "质量未通过，需处理", reason, technical }
		: { ...stage, state: "warning", summary: "质量未通过（提示，不阻断发布）", reason, technical };
}

function reviewRequired(input: ReleaseJourneyInput, status: TrackedStatus | null) {
	return (
		Boolean(input.candidate?.audit?.approvedBy) ||
		status === "APPROVED" ||
		status === "REJECTED" ||
		(!input.releaseActions.includes("PUBLISH") &&
			(status === "REVIEW_PENDING" ||
				input.releaseActions.some(
					(action) => action === "SUBMIT_REVIEW" || action === "APPROVE" || action === "REJECT",
				)))
	);
}

function reviewStage(status: TrackedStatus | null): JourneyStage {
	const stage = { key: "review" as const, label: STAGE_LABELS.review };
	if (status === "REJECTED") return { ...stage, state: "failed", summary: "已驳回", reason: "发布评审已驳回" };
	if (status === "REVIEW_PENDING") return { ...stage, state: "active", summary: "评审中" };
	if (reached(status, "APPROVED")) return { ...stage, state: "passed", summary: "评审通过" };
	return { ...stage, state: "waiting", summary: "等待提交评审" };
}

function publishStage(input: ReleaseJourneyInput, status: TrackedStatus | null): JourneyStage {
	const stage = { key: "publish" as const, label: STAGE_LABELS.publish };
	if (status === "ROLLED_BACK") return { ...stage, state: "rolled-back", summary: "已回滚" };
	if (status === "PARTIAL")
		return {
			...stage,
			state: "failed",
			summary: "发布未完整完成",
			reason: input.primaryBlocker?.message || "发布未完整提交，请重试发布或回滚",
			technical: { code: input.primaryBlocker?.code || null },
		};
	if (status === "PUBLISHING") return { ...stage, state: "active", summary: "发布中" };
	if (status === "PUBLISHED") return { ...stage, state: "passed", summary: "已发布" };
	return { ...stage, state: "waiting", summary: "等待发布" };
}

function onlineStage(input: ReleaseJourneyInput, status: TrackedStatus | null): JourneyStage {
	const stage = { key: "online" as const, label: STAGE_LABELS.online };
	const binding = input.binding;
	if (status !== "PUBLISHED") return { ...stage, state: "waiting", summary: "发布完成后检查运行计划" };
	if (
		binding?.state === "ONLINE" &&
		binding.latestRelation?.verified === true &&
		binding.latestRelation.exists === true
	)
		return { ...stage, state: "passed", summary: "运行计划已生效，关系健康" };
	if (binding?.state === "DEGRADED")
		return {
			...stage,
			state: "failed",
			summary: "运行计划或物理关系异常",
			reason: binding.primaryBlocker?.message || "运行计划或物理关系异常，请修复部署",
		};
	if (!binding) return { ...stage, state: "active", summary: "运行计划尚未生成" };
	if (binding.state === "DEPLOYING") return { ...stage, state: "active", summary: "运行计划部署中" };
	if (binding.state === "DISABLED") return { ...stage, state: "active", summary: "运行计划当前停用" };
	return { ...stage, state: "active", summary: "运行状态待平台核验" };
}

function pickCurrent(stages: JourneyStage[]): number {
	for (let index = 0; index < stages.length; index++) {
		const state = stages[index].state;
		if (state === "failed" || state === "active") return index;
		// A warning holds the flow only while nothing after it has moved on.
		if (state === "warning" && stages.slice(index + 1).every((stage) => stage.state === "waiting")) return index;
	}
	let lastSettled = -1;
	stages.forEach((stage, index) => {
		if (stage.state !== "waiting") lastSettled = index;
	});
	return Math.min(lastSettled + 1, stages.length - 1);
}

function stageActions(stage: JourneyStage, input: ReleaseJourneyInput): [JourneyAction?, JourneyAction?] {
	const has = (action: ReleaseWorkflowAction) => input.releaseActions.includes(action);
	const build: JourneyAction | undefined = input.canBuild
		? { key: "BUILD", label: input.buildLabel, tone: "primary" }
		: undefined;
	const configure: JourneyAction | undefined = input.canConfigureQuality
		? { key: "CONFIGURE_QUALITY", label: "配置质量规则", tone: "default" }
		: undefined;
	const rollback = has("ROLLBACK") ? releaseAction("ROLLBACK") : undefined;
	switch (stage.key) {
		case "precheck":
			return input.canCancelBlockingCandidate
				? [{ key: "CANCEL_BLOCKING_CANDIDATE", label: "关闭占用发布单", tone: "primary" }]
				: [build];
		case "build":
			return stage.state === "failed" ? [build] : [];
		case "registration":
			// No registration retry command exists in this dialog yet (F13-T02); never steer users to a rebuild.
			return [];
		case "engineering":
			return has("RUN_QUALITY") ? [releaseAction("RUN_QUALITY")] : [];
		case "governance": {
			if (input.canRerunGovernanceQuality)
				return [{ key: "RERUN_GOVERNANCE_QUALITY", label: "重新运行质量检查", tone: "primary" }, configure];
			if (stage.state === "warning") {
				const next = has("PUBLISH")
					? releaseAction("PUBLISH", "继续发布")
					: has("SUBMIT_REVIEW")
						? releaseAction("SUBMIT_REVIEW")
						: undefined;
				return [next, configure];
			}
			return [configure];
		}
		case "review":
			if (has("APPROVE")) return [releaseAction("APPROVE"), has("REJECT") ? releaseAction("REJECT") : undefined];
			return has("SUBMIT_REVIEW") ? [releaseAction("SUBMIT_REVIEW")] : [];
		case "publish":
			if (has("RETRY_PUBLICATION")) return [releaseAction("RETRY_PUBLICATION"), rollback];
			return has("PUBLISH") ? [releaseAction("PUBLISH")] : [];
		case "online": {
			const allowed = input.binding?.allowedActions || [];
			const primary: JourneyAction | undefined = allowed.includes("REPAIR_DEPLOYMENT")
				? { key: "REPAIR_DEPLOYMENT", label: "修复部署", tone: "primary" }
				: allowed.includes("RUN_NOW")
					? { key: "RUN_NOW", label: "立即运行并核验", tone: "primary" }
					: undefined;
			return [primary, rollback];
		}
	}
}

export function deriveReleaseJourney(input: ReleaseJourneyInput): ReleaseJourney {
	const status = trackedStatus(input.candidate);
	const stages: JourneyStage[] = [
		precheckStage(input, status),
		buildStage(input, status),
		registrationStage(input, status),
		engineeringStage(input, status),
		governanceStage(input, status),
		...(reviewRequired(input, status) ? [reviewStage(status)] : []),
		publishStage(input, status),
		onlineStage(input, status),
	];
	const index = pickCurrent(stages);
	const current = stages[index];
	const policy = input.governanceQuality ? (input.governanceQuality.required ? "BLOCKING" : "ADVISORY") : undefined;
	if (input.loadState === "loading") return { stages, current: current.key, headline: "正在读取发布状态…", policy };
	if (input.loadState === "error")
		return {
			stages,
			current: current.key,
			headline: "发布状态读取失败",
			primaryAction: { key: "RELOAD", label: "重新读取", tone: "primary" },
			policy,
		};
	const [primary, secondary] = stageActions(current, input);
	return {
		stages,
		current: current.key,
		headline: `第 ${index + 1} 步 ${current.label}：${current.reason || current.summary}`,
		primaryAction: primary,
		secondaryAction: primary ? secondary : undefined,
		policy,
	};
}
