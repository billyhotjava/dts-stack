import type { Result } from "@/types/api";
import type { DataSource } from "@/types/datasource";
import { ok } from "../client";
import { db } from "../db";

export interface AvailableSources {
	/** 平台层共享源（全所级系统，跨部门） */
	platform: DataSource[];
	/** 本工作区（部门）本地源 */
	workspace: DataSource[];
}

/**
 * 数据源服务 —— 体现混合制归属。
 * availableFor 返回某工作区"可绑定到项目"的源：平台共享 + 本部门本地。
 */
export const dataSourceService = {
	list(): Promise<Result<DataSource[]>> {
		return ok(db.dataSources);
	},
	availableFor(workspaceId: string): Promise<Result<AvailableSources>> {
		const platform = db.dataSources.filter((d) => d.scope === "platform");
		const workspace = db.dataSources.filter((d) => d.scope === "workspace" && d.workspaceId === workspaceId);
		return ok({ platform, workspace });
	},
};
