/**
 * Sprint-15 F6/T01 — Audit helper for the leader workbench.
 *
 * Dispatches to the backend `POST /api/workbench/audit` sink via
 * `workbenchAuditService.recordClientAudit`; failures are swallowed so
 * audit hiccups never break the page. A DEV-only `console.debug`
 * mirror is kept for local debugging.
 *
 * F3-F5 call-sites use `auditLog(event, payload)` unchanged.
 */
import { recordClientAudit } from "@/api/services/workbenchAuditService";

type AuditPayload = Record<string, unknown>;

function isDevEnv(): boolean {
	try {
		// Vite exposes import.meta.env in both runtime and test environments.
		return Boolean(import.meta.env?.DEV);
	} catch {
		return false;
	}
}

export function auditLog(event: string, payload: AuditPayload): void {
	// Fire-and-forget: never block the UI on audit traffic.
	try {
		recordClientAudit({ event, payload }).catch((ex) => {
			if (isDevEnv()) {
				// eslint-disable-next-line no-console
				console.debug("[audit] failed", event, ex);
			}
		});
	} catch (ex) {
		// Covers the synchronous-throw case (e.g. stub fails before returning a Promise).
		if (isDevEnv()) {
			// eslint-disable-next-line no-console
			console.debug("[audit] threw", event, ex);
		}
	}
	if (isDevEnv()) {
		// eslint-disable-next-line no-console
		console.debug(`[audit] ${event}`, payload);
	}
}

export default { auditLog };
