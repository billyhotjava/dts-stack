import { AlertTriangle, Check, Circle, Clock3, RotateCcw, ShieldCheck } from "lucide-react";
import type {
	PlanExecutionBinding,
	ReleaseCandidate,
	ReleaseCandidateEvidenceSummary,
	ReleaseCandidateGovernanceQuality,
	ReleaseCandidateLifecycleAction,
} from "@/api/modelSpecApi";
import { statusLabel } from "@/utils/customerDisplayLabels";
import { Button, Status } from "./PrototypePrimitives";

type ReleaseWorkflowAction = Extract<
	ReleaseCandidateLifecycleAction,
	"RUN_QUALITY" | "SUBMIT_REVIEW" | "APPROVE" | "REJECT" | "PUBLISH" | "RETRY_PUBLICATION" | "ROLLBACK"
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

const governanceStepState = (
	candidate: ReleaseCandidate | null,
	governanceQuality: ReleaseCandidateGovernanceQuality | null,
): WorkflowStepState => {
	if (!candidate || !governanceQuality) return "waiting";
	if (governanceQuality.state === "PASSED") return "passed";
	if (governanceQuality.state === "RUNNING") return "active";
	if (governanceQuality.state === "FAILED" || governanceQuality.state === "STALE") return "failed";
	if (governanceQuality.required && reached(candidate, "QUALITY_PASSED")) return "failed";
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

const handoffText = (
	candidate: ReleaseCandidate | null,
	actions: ReleaseWorkflowAction[],
	governanceQuality: ReleaseCandidateGovernanceQuality | null,
) => {
	if (!candidate) return "先完成数据构建，系统才会开放工程验证。";
	if (actions.includes("RUN_QUALITY")) return "当前由模型维护者运行工程验证。";
	if (governanceQuality?.required && governanceQuality.state !== "PASSED" && reached(candidate, "QUALITY_PASSED"))
		return governanceQuality.message || "工程验证已通过，请先补齐治理数据质量记录。";
	if (actions.includes("PUBLISH") && !candidate.audit?.approvedBy)
		return "工程验证与治理数据质量均已通过，数据管理员可在职责范围内直接发布，无需另行审批。";
	if (actions.includes("SUBMIT_REVIEW")) return "工程验证与治理数据质量均已通过，请提交发布评审。";
	if (candidate.status === "REVIEW_PENDING" && !actions.some((action) => action === "APPROVE" || action === "REJECT"))
		return "等待独立发布审核人处理；提交人不能审核自己的发布单。";
	if (actions.includes("APPROVE") || actions.includes("REJECT")) return "当前由独立发布审核人通过或驳回。";
	if (candidate.status === "APPROVED" && !actions.includes("PUBLISH"))
		return "评审已通过，等待独立发布操作员登记上线。";
	if (actions.includes("PUBLISH")) return "评审已通过，当前由发布操作员完成发布登记。";
	if (actions.includes("RETRY_PUBLICATION")) return "发布未完整提交，请重试发布或回滚。";
	if (candidate.status === "PUBLISHED") return "发布登记已完成；上线状态继续以执行关联和物理关系为准。";
	if (candidate.status === "ROLLED_BACK") return "本次发布已回滚，可按新修订创建替代发布单。";
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

const EVIDENCE_TYPE_LABEL: Record<ReleaseCandidateEvidenceSummary["type"], string> = {
	ARTIFACT: "发布产物",
	BUILD_RUN: "数据构建",
	QUALITY_RUN: "工程验证",
	REVIEW: "发布审核",
	PUBLICATION: "发布登记",
	REGISTRATION: "上线登记",
	ROLLBACK: "发布回退",
};

const failedEvidenceText = (item: ReleaseCandidateEvidenceSummary) => {
	const state = item.state === "STALE" ? "状态待确认" : "未通过";
	if (item.code) return `${EVIDENCE_TYPE_LABEL[item.type]}${state}（错误码 ${item.code}）`;
	return `${EVIDENCE_TYPE_LABEL[item.type]}${state}${item.message ? `：${item.message}` : ""}`;
};

const evidenceText = (evidence: ReleaseCandidateEvidenceSummary[]) => {
	const failed = evidence.filter((item) => item.state === "FAILED" || item.state === "STALE");
	if (failed.length) return failed.map(failedEvidenceText).join("；");
	const passed = evidence.filter((item) => item.state === "PASSED").length;
	return evidence.length ? `${passed}/${evidence.length} 项记录已通过` : "尚无发布记录";
};

const governanceQualityText = (governanceQuality: ReleaseCandidateGovernanceQuality | null) => {
	if (!governanceQuality) return "工程验证通过后读取规则版本、关联关系和运行记录。";
	if (governanceQuality.state === "PASSED") return "规则版本、资产关联和有效运行记录均已通过。";
	return governanceQuality.message || governanceQuality.code || "治理数据质量记录尚未就绪。";
};

const canRerunGovernanceQuality = (governanceQuality: ReleaseCandidateGovernanceQuality | null) =>
	Boolean(
		governanceQuality &&
			(governanceQuality.state === "FAILED" || governanceQuality.state === "STALE") &&
			governanceQuality.evidence.some(
				(item) =>
					item.ruleId &&
					item.ruleVersionId &&
					item.bindingId &&
					item.violations.some((violation) => ["MISSING", "FAILED", "ERROR", "EXPIRED"].includes(violation)) &&
					!item.violations.some((violation) =>
						["ASSET_MISMATCH", "VERSION_MISMATCH", "BINDING_MISMATCH"].includes(violation),
					),
			),
	);

const formatTime = (value?: string | null) => {
	if (!value) return "—";
	const parsed = new Date(value);
	return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString("zh-CN", { hour12: false });
};

export function ModelReleaseWorkflowPanel({
	candidate,
	evidence,
	governanceQuality,
	governanceQualityRerunning = false,
	onRerunGovernanceQuality,
	releaseActions,
	binding,
}: {
	candidate: ReleaseCandidate | null;
	evidence: ReleaseCandidateEvidenceSummary[];
	governanceQuality: ReleaseCandidateGovernanceQuality | null;
	governanceQualityRerunning?: boolean;
	onRerunGovernanceQuality?: () => void;
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
		{ key: "quality" as const, label: "工程验证", state: stepState(candidate, "quality", binding) },
		{ key: "governance-quality" as const, label: "治理数据质量", state: governanceStepState(candidate, governanceQuality) },
		...(reviewRequired
			? [{ key: "review" as const, label: "发布评审", state: stepState(candidate, "review", binding) }]
			: []),
		{ key: "publication" as const, label: "发布登记", state: stepState(candidate, "publication", binding) },
		{ key: "online" as const, label: "上线就绪", state: stepState(candidate, "online", binding) },
	];
	const online = onlineText(candidate, binding);
	const governanceRerunnable = Boolean(onRerunGovernanceQuality && canRerunGovernanceQuality(governanceQuality));
	return (
		<div className="dmx-release-workflow">
			<div className="dmx-release-workflow__heading">
				<div>
					<strong>发布流程</strong>
					<span>数据管理员按职责范围自助发布；报表和数据服务访问仍单独授权。</span>
				</div>
				<Status
					tone={candidate?.status === "PUBLISHED" ? "success" : candidate?.status === "PARTIAL" ? "danger" : "info"}
				>
					{candidate?.status ? statusLabel(candidate.status) : "暂无发布单"}
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
					<p>{handoffText(candidate, releaseActions, governanceQuality)}</p>
					{candidate?.audit?.submittedBy ? <small>提交人：{candidate.audit.submittedBy}</small> : null}
					{candidate?.audit?.approvedBy ? <small>审核人：{candidate.audit.approvedBy}</small> : null}
					{candidate?.audit?.publishedBy ? <small>发布人：{candidate.audit.publishedBy}</small> : null}
				</div>
			</div>
			<div className="dmx-governance-quality">
				<div className="dmx-governance-quality__heading">
					<div>
						<span>治理数据质量记录</span>
						<strong>{governanceQuality?.required ? "发布检查" : "提示项"}</strong>
					</div>
					<div className="dmx-governance-quality__actions">
						{governanceQuality?.evidence[0]?.runId ? (
							<a href={`#/governance/rules/runs/${encodeURIComponent(governanceQuality.evidence[0].runId)}`}>
								查看质量运行
							</a>
						) : governanceQuality?.evidence[0]?.assetKey ? (
							<a href="#/governance/rules/catalog" title={`物理资产：${governanceQuality.evidence[0].assetKey}`}>
								配置质量规则
							</a>
						) : null}
						{governanceRerunnable ? (
							<Button disabled={governanceQualityRerunning} onClick={onRerunGovernanceQuality} type="link">
								{governanceQualityRerunning ? "正在创建新运行…" : "重新运行治理质量"}
							</Button>
						) : null}
					</div>
				</div>
				<p>{governanceQualityText(governanceQuality)}</p>
				{governanceQuality ? (
					<small>有效期：{governanceQuality.maxAgeSeconds} 秒 · 状态：{statusLabel(governanceQuality.state)}</small>
				) : null}
				{governanceQuality?.evidence.length ? (
					<ul>
						{governanceQuality.evidence.map((item, index) => (
							<li key={`${item.assetKey}-${item.ruleVersionId || index}`}>
								<strong>{statusLabel(item.status)}</strong>
								<span>资产 {item.assetKey}</span>
								<span>规则版本 {item.ruleVersionId || "—"}</span>
								<span>关联 {item.bindingId || "—"}</span>
								<span>运行 {item.runId || "—"}</span>
								<span>完成 {formatTime(item.finishedAt)}</span>
								<span>校验码 {item.evidenceChecksum || "—"}</span>
								{item.violations.length ? <em>{item.violations.join("、")}</em> : null}
							</li>
						))}
					</ul>
				) : null}
			</div>
			<div className="dmx-release-readiness">
				<div>
					<span>记录摘要</span>
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
