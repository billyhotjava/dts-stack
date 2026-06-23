import type { Result } from "@/types/api";
import type { ProjectSpace } from "@/types/projectSpace";
import { ok } from "../client";
import { db } from "../db";

/** 项目空间服务 —— dev 辅助分组，按部门过滤（用于集成/建模阶段）。 */
export const projectSpaceService = {
	listByDepartment(departmentId: string): Promise<Result<ProjectSpace[]>> {
		return ok(db.projectSpaces.filter((p) => p.departmentId === departmentId));
	},
	get(id: string): Promise<Result<ProjectSpace | null>> {
		return ok(db.projectSpaces.find((p) => p.id === id) ?? null);
	},
};
