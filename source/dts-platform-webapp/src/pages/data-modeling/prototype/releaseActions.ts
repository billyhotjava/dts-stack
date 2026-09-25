import type { ReleaseCandidateLifecycleAction } from "@/api/modelSpecApi";

/** Lifecycle commands the version-publication dialog may offer; each is still authorized by the server. */
export type ReleaseWorkflowAction = Extract<
	ReleaseCandidateLifecycleAction,
	"RUN_QUALITY" | "SUBMIT_REVIEW" | "APPROVE" | "REJECT" | "PUBLISH" | "RETRY_PUBLICATION" | "ROLLBACK"
>;

export const RELEASE_ACTION_LABELS: Record<ReleaseWorkflowAction, string> = {
	RUN_QUALITY: "运行工程验证",
	SUBMIT_REVIEW: "提交发布评审",
	APPROVE: "审核通过",
	REJECT: "驳回",
	PUBLISH: "确认发布",
	RETRY_PUBLICATION: "重试发布",
	ROLLBACK: "回滚发布",
};
