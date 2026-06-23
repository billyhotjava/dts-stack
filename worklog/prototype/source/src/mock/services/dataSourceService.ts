import type { Result } from "@/types/api";
import type { DataSource } from "@/types/datasource";
import { ok } from "../client";
import { db } from "../db";

export interface AvailableSources {
	/** 平台层共享源（全所级系统，跨部门） */
	platform: DataSource[];
	/** 本部门本地源 */
	department: DataSource[];
}

/**
 * 数据源服务 —— 混合制归属。
 * availableFor 返回某部门可用的源：平台共享 + 本部门本地。
 */
export const dataSourceService = {
	list(): Promise<Result<DataSource[]>> {
		return ok(db.dataSources);
	},
	availableFor(departmentId: string): Promise<Result<AvailableSources>> {
		const platform = db.dataSources.filter((d) => d.scope === "platform");
		const department = db.dataSources.filter((d) => d.scope === "department" && d.departmentId === departmentId);
		return ok({ platform, department });
	},
};
