export const issueText = (
	issues: Array<{
		code: string;
		message: string;
		recoveryAction?: string | null;
		stage?: string | null;
		category?: string | null;
		retryable?: boolean;
		correlationId?: string | null;
	}>,
) =>
	issues.length
		? issues
				.map(
					(issue) =>
						`${issue.stage || "UNKNOWN"}/${issue.category || "GENERAL"} · ${issue.code}：${issue.message}${issue.recoveryAction ? `；处理建议：${issue.recoveryAction}` : ""}${issue.retryable == null ? "" : `；可重试：${issue.retryable ? "是" : "否"}`}${issue.correlationId ? `；关联号：${issue.correlationId}` : ""}`,
				)
				.join("\n")
		: "—";
