import { ShieldCheck } from "lucide-react";
import type {
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

const reached = (candidate: ReleaseCandidate | null, status: (typeof STATUS_ORDER)[number]) =>
	STATUS_ORDER.indexOf(candidate?.status as (typeof STATUS_ORDER)[number]) >= STATUS_ORDER.indexOf(status);

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
	if (candidate.status === "PUBLISHED") return "正式版本已发布。运行版本的部署与启用请到运维中心办理。";
	if (candidate.status === "ROLLED_BACK") return "本次发布已回滚，可按新修订创建替代发布单。";
	return "服务端正在推进当前阶段，刷新后查看下一步。";
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
}: {
	candidate: ReleaseCandidate | null;
	evidence: ReleaseCandidateEvidenceSummary[];
	governanceQuality: ReleaseCandidateGovernanceQuality | null;
	governanceQualityRerunning?: boolean;
	onRerunGovernanceQuality?: () => void;
	releaseActions: ReleaseWorkflowAction[];
}) {
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

			</div>
		</div>
	);
}
