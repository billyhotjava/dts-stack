import type { Result } from "@/types/api";
import type { JdbcDriver } from "@/types/datasource";
import { ok } from "../client";
import { db } from "../db";

/** JDBC 驱动服务 —— 平台层管理。 */
export const jdbcDriverService = {
	list(): Promise<Result<JdbcDriver[]>> {
		return ok(db.jdbcDrivers);
	},
};
