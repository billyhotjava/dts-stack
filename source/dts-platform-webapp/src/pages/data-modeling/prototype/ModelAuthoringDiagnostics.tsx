import type { ModelAuthoringValidation } from "@/api/modelAuthoringApi";

export function ModelAuthoringDiagnostics({ validation }: { validation: ModelAuthoringValidation | null }) {
	const issues = [
		...(validation?.projectionIssues || []),
		...(validation?.implementationValidation?.diagnostics || []),
	];
	if (!issues.length) return null;
	return (
		<section className="dmx-dbt-diagnostics" aria-label="实现校验结果">
			{issues.map((issue, index) => (
				<div
					key={`${issue.code}:${issue.path || ""}:${index}`}
					role={/^(ERROR|FATAL)$/i.test(issue.severity) ? "alert" : "status"}
				>
					<b>{issue.path || issue.code}</b>
					<p>{issue.message}</p>
				</div>
			))}
		</section>
	);
}
