import type { Result } from "@/types/api";
import type { Workspace } from "@/types/workspace";
import { ok } from "../client";
import { db } from "../db";

/** 工作区（部门）服务 —— 契约对齐现网 *Service.ts。 */
export const workspaceService = {
	list(): Promise<Result<Workspace[]>> {
		return ok(db.workspaces);
	},
	get(id: string): Promise<Result<Workspace | null>> {
		return ok(db.workspaces.find((w) => w.id === id) ?? null);
	},
};
