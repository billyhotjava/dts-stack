import type { Result } from "@/types/api";
import type { Project } from "@/types/project";
import { ok } from "../client";
import { db } from "../db";

/**
 * 项目服务 —— 契约形状对齐现网 *Service.ts（返回 Promise<Result<T>>）。
 * 切真实后端时，仅需把 ok(...) 换成 apiClient.get(...)，调用方零改动。
 */
export const projectService = {
	list(): Promise<Result<Project[]>> {
		return ok(db.projects);
	},
	get(id: string): Promise<Result<Project | null>> {
		return ok(db.projects.find((p) => p.id === id) ?? null);
	},
};
