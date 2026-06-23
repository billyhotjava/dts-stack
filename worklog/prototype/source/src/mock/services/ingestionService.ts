import type { Result } from "@/types/api";
import type { AccessChange, ScheduleJob } from "@/types/ingestion";
import { ok } from "../client";
import { SEED_ACCESS_CHANGES, SEED_SCHEDULE_JOBS } from "../fixtures/ingestion";

/** 接入变更 + 采集调度服务（按部门，只读；审批/启停在前端态演示）。 */
export const ingestionService = {
	listAccessChanges(departmentId: string): Promise<Result<AccessChange[]>> {
		return ok(SEED_ACCESS_CHANGES.filter((c) => c.departmentId === departmentId));
	},
	listSchedules(departmentId: string): Promise<Result<ScheduleJob[]>> {
		return ok(SEED_SCHEDULE_JOBS.filter((s) => s.departmentId === departmentId));
	},
};
