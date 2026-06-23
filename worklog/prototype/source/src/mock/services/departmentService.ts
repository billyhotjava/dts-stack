import type { Result } from "@/types/api";
import type { Department } from "@/types/department";
import { ok } from "../client";
import { db } from "../db";

/** 部门服务 —— 契约对齐现网 *Service.ts。部门是主组织边界。 */
export const departmentService = {
	list(): Promise<Result<Department[]>> {
		return ok(db.departments);
	},
	get(id: string): Promise<Result<Department | null>> {
		return ok(db.departments.find((d) => d.id === id) ?? null);
	},
};
