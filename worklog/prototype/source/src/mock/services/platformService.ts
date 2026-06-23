import type { Result } from "@/types/api";
import type { AccessRequest, AccessToken, AlertEvent, ApiService, AuditEvent, BiLink, OpsJob } from "@/types/platform";
import { ok } from "../client";
import {
	SEED_ACCESS_REQUESTS,
	SEED_ALERTS,
	SEED_API_SERVICES,
	SEED_AUDIT_EVENTS,
	SEED_BI_LINKS,
	SEED_OPS_JOBS,
	SEED_TOKENS,
} from "../fixtures/platform";

/** 平台级旁路服务（跨部门、只读）。 */
export const platformService = {
	apiServices: (): Promise<Result<ApiService[]>> => ok(SEED_API_SERVICES),
	tokens: (): Promise<Result<AccessToken[]>> => ok(SEED_TOKENS),
	biLinks: (): Promise<Result<BiLink[]>> => ok(SEED_BI_LINKS),
	auditEvents: (): Promise<Result<AuditEvent[]>> => ok(SEED_AUDIT_EVENTS),
	accessRequests: (): Promise<Result<AccessRequest[]>> => ok(SEED_ACCESS_REQUESTS),
	opsJobs: (): Promise<Result<OpsJob[]>> => ok(SEED_OPS_JOBS),
	alerts: (): Promise<Result<AlertEvent[]>> => ok(SEED_ALERTS),
};
