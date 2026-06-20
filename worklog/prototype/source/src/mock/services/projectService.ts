import type { Result } from "@/types/api";
import type { Project } from "@/types/project";
import { ok } from "../client";
import { db } from "../db";

/**
 * 项目服务 —— 契约形状对齐现网 *Service.ts（返回 Promise<Result<T>>）。
 * 项目隶属工作区，故按工作区过滤。
 */
export const projectService = {
	listByWorkspace(workspaceId: string): Promise<Result<Project[]>> {
		return ok(db.projects.filter((p) => p.workspaceId === workspaceId));
	},
	get(id: string): Promise<Result<Project | null>> {
		return ok(db.projects.find((p) => p.id === id) ?? null);
	},
};
