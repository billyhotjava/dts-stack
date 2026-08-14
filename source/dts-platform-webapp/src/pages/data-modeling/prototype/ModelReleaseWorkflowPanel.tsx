import { AlertTriangle, Check, Circle, Clock3, RotateCcw, ShieldCheck } from "lucide-react";
import type {
	PlanExecutionBinding,
	ReleaseCandidate,
	ReleaseCandidateEvidenceSummary,
	ReleaseCandidateLifecycleAction,
} from "@/api/modelSpecApi";
import { Status } from "./PrototypePrimitives";

type ReleaseWorkflowAction = Extract<
	ReleaseCandidateLifecycleAction,
	"RUN_QUALITY" | "SUBMIT_REVIEW" | "APPROVE" | "REJECT" | "PUBLISH" | "RETRY_REGISTRATION" | "ROLLBACK"
>;

type WorkflowStepState = "waiting" | "active" | "passed" | "failed" | "rolled-back";

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

const statusIndex = (status?: ReleaseCandidate["status"] | null) =>
	STATUS_ORDER.indexOf(status as (typeof STATUS_ORDER)[number]);
const reached = (candidate: ReleaseCandidate | null, status: (typeof STATUS_ORDER)[number]) =>
	statusIndex(candidate?.status) >= STATUS_ORDER.indexOf(status);

const stepState = (
	candidate: ReleaseCandidate | null,
	step: "build" | "quality" | "review" | "publication" | "online",
	binding: PlanExecutionBinding | null,
): WorkflowStepState => {
	const status = candidate?.status;
	if (!status) return "waiting";
	if (status === "ROLLED_BACK" && step === "publication") return "rolled-back";
	if (step === "build") {
		if (status === "BUILD_FAILED") return "failed";
		if (status === "BUILDING" || status === "DRAFT") return "active";
		return reached(candidate, "BUILT") ? "passed" : "waiting";
	}
	if (step === "quality") {
		if (status === "QUALITY_FAILED") return "failed";
		if (status === "QUALITY_RUNNING") return "active";
		return reached(candidate, "QUALITY_PASSED") ? "passed" : "waiting";
	}
	if (step === "review") {
		if (status === "REJECTED") return "failed";
		if (status === "REVIEW_PENDING") return "active";
		return reached(candidate, "APPROVED") ? "passed" : "waiting";
	}
	if (step === "publication") {
		if (status === "PARTIAL") return "failed";
		if (status === "PUBLISHING") return "active";
		return status === "PUBLISHED" ? "passed" : "waiting";
	}
	if (
		binding?.state === "ONLINE" &&
		binding.latestRelation?.verified === true &&
		binding.latestRelation.exists === true
	)
		return "passed";
	if (status === "PUBLISHED" && binding?.state === "DEGRADED") return "failed";
	if (status === "PUBLISHED") return "active";
	return "waiting";
};

const stepText = (state: WorkflowStepState) => {
	if (state === "passed") return "已完成";
	if (state === "active") return "处理中";
	if (state === "failed") return "需处理";
	if (state === "rolled-back") return "已回滚";
	return "等待";
};

const stepIcon = (state: WorkflowStepState) => {
	if (state === "passed") return <Check size={16} />;
	if (state === "failed") return <AlertTriangle size={16} />;
	if (state === "rolled-back") return <RotateCcw size={16} />;
	if (state === "active") return <Clock3 size={16} />;
	return <Circle size={12} />;
};

const handoffText = (candidate: ReleaseCandidate | null, actions: ReleaseWorkflowAction[]) => {
	if (!candidate) return "先完成物化构建，系统才会开放质量检查。";
	if (actions.includes("RUN_QUALITY")) return "当前由模型维护者运行质量检查。";
	if (actions.includes("PUBLISH") && !candidate.audit?.approvedBy)
		return "质量检查已通过，数据管理员可在职责范围内直接发布，无需另行审批。";
	if (actions.includes("SUBMIT_REVIEW")) return "质量已通过，请提交发布评审。";
	if (candidate.status === "REVIEW_PENDING" && !actions.some((action) => action === "APPROVE" || action === "REJECT"))
		return "等待独立发布审核人处理；提交人不能审核自己的候选。";
	if (actions.includes("APPROVE") || actions.includes("REJECT")) return "当前由独立发布审核人通过或驳回。";
	if (candidate.status === "APPROVED" && !actions.includes("PUBLISH"))
		return "评审已通过，等待独立发布操作员登记上线。";
	if (actions.includes("PUBLISH")) return "评审已通过，当前由发布操作员完成发布登记。";
	if (actions.includes("RETRY_REGISTRATION")) return "发布登记未完整提交，请由发布操作员重试登记或回滚。";
	if (candidate.status === "PUBLISHED") return "发布登记已完成；上线状态继续以执行绑定和物理关系为准。";
	if (candidate.status === "ROLLED_BACK") return "本次发布已回滚，可按新修订创建替代候选。";
	return "服务端正在推进当前阶段，刷新后查看下一步。";
};

const onlineText = (candidate: ReleaseCandidate | null, binding: PlanExecutionBinding | null) => {
	if (candidate?.status !== "PUBLISHED") return "发布登记完成后检查运行计划。";
	if (!binding) return "发布登记已完成，运行计划尚未生成。";
	if (binding.state === "ONLINE" && binding.latestRelation?.verified === true && binding.latestRelation.exists === true)
		return "上线完成：运行计划已生效，关系健康。";
	if (binding.state === "DEPLOYING") return "发布登记已完成，运行计划部署中。";
	if (binding.state === "DEGRADED") return "已发布，但运行计划或物理关系异常。";
	if (binding.state === "DISABLED") return "已发布，运行计划当前停用。";
	return "已发布，运行状态待平台核验。";
};

const evidenceText = (evidence: ReleaseCandidateEvidenceSummary[]) => {
	const failed = evidence.filter((item) => item.state === "FAILED" || item.state === "STALE");
	if (failed.length) return failed.map((item) => `${item.type}：${item.message || item.code || item.state}`).join("；");
	const passed = evidence.filter((item) => item.state === "PASSED").length;
	return evidence.length ? `${passed}/${evidence.length} 项证据已通过` : "尚无发布证据";
};

export function ModelReleaseWorkflowPanel({
	candidate,
	evidence,
	releaseActions,
	binding,
}: {
	candidate: ReleaseCandidate | null;
	evidence: ReleaseCandidateEvidenceSummary[];
	releaseActions: ReleaseWorkflowAction[];
	binding: PlanExecutionBinding | null;
}) {
	const reviewRequired =
		Boolean(candidate?.audit?.approvedBy) ||
		candidate?.status === "APPROVED" ||
		candidate?.status === "REJECTED" ||
		(!releaseActions.includes("PUBLISH") &&
			(candidate?.status === "REVIEW_PENDING" ||
				releaseActions.some((action) => action === "SUBMIT_REVIEW" || action === "APPROVE" || action === "REJECT")));
	const steps = [
		{ key: "build" as const, label: "构建", state: stepState(candidate, "build", binding) },
		{ key: "quality" as const, label: "质量检查", state: stepState(candidate, "quality", binding) },
		...(reviewRequired
			? [{ key: "review" as const, label: "发布评审", state: stepState(candidate, "review", binding) }]
			: []),
		{ key: "publication" as const, label: "发布登记", state: stepState(candidate, "publication", binding) },
		{ key: "online" as const, label: "上线就绪", state: stepState(candidate, "online", binding) },
	];
	const online = onlineText(candidate, binding);
	return (
		<div className="dmx-release-workflow">
			<div className="dmx-release-workflow__heading">
				<div>
					<strong>候选发布流程</strong>
					<span>数据管理员按职责范围自助发布；报表和数据服务访问仍单独授权。</span>
				</div>
				<Status
					tone={candidate?.status === "PUBLISHED" ? "success" : candidate?.status === "PARTIAL" ? "danger" : "info"}
				>
					{candidate?.status || "尚无候选"}
				</Status>
			</div>
			<ol className="dmx-release-steps">
				{steps.map((step) => (
					<li className={`dmx-release-step dmx-release-step--${step.state}`} key={step.key}>
						<span className="dmx-release-step__icon">{stepIcon(step.state)}</span>
						<span>
							<strong>{step.label}</strong>
							<small>{stepText(step.state)}</small>
						</span>
					</li>
				))}
			</ol>
			<div className="dmx-release-handoff">
				<ShieldCheck size={18} />
				<div>
					<strong>当前责任</strong>
					<p>{handoffText(candidate, releaseActions)}</p>
					{candidate?.audit?.submittedBy ? <small>提交人：{candidate.audit.submittedBy}</small> : null}
					{candidate?.audit?.approvedBy ? <small>审核人：{candidate.audit.approvedBy}</small> : null}
					{candidate?.audit?.publishedBy ? <small>发布人：{candidate.audit.publishedBy}</small> : null}
				</div>
			</div>
			<div className="dmx-release-readiness">
				<div>
					<span>证据摘要</span>
					<strong>{evidenceText(evidence)}</strong>
				</div>
				<div>
					<span>运行状态</span>
					<strong>{online}</strong>
					{binding ? (
						<small>
							{binding.environment} · {binding.scheduleMode} · {binding.airflowDagId || "DAG 待生成"}
						</small>
					) : null}
				</div>
			</div>
		</div>
	);
}
