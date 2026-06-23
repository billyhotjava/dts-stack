import type { Result } from "@/types/api";
import type { Connector } from "@/types/datasource";
import { ok } from "../client";
import { db } from "../db";

/** 连接器服务 —— 平台层登记的连接器类型（跨部门共享）。 */
export const connectorService = {
	list(): Promise<Result<Connector[]>> {
		return ok(db.connectors);
	},
};
