import apiClient from "../apiClient";

/**
 * Sprint-15 F6/T01 — Thin HTTP client for the workbench client-event
 * audit sink (`POST /api/workbench/audit`). The backend records the
 * event via the same `AuditService.audit(...)` path used by every other
 * resource, so leader-overview UI events land in the single audit table.
 *
 * Failures never propagate: callers in `@/utils/audit` swallow errors
 * so audit issues cannot break the page.
 */
export interface WorkbenchClientAudit {
	event: string;
	payload?: Record<string, unknown> | undefined;
}

export function recordClientAudit(input: WorkbenchClientAudit): Promise<void> {
	return apiClient
		.post<unknown>({ url: "/workbench/audit", data: input })
		.then(() => undefined);
}

export default { recordClientAudit };
