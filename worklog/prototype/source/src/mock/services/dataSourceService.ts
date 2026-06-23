import type { Result } from "@/types/api";
import type { ConnectionTestResult, DataSource, DataSourceUpsertPayload } from "@/types/datasource";
import { fail, ok } from "../client";
import { db } from "../db";

let seq = 100;

/**
 * 数据源服务 —— 混合制归属 + 部门维度。
 * 平台共享源对部门只读；本部门本地源可增删改。
 */
export const dataSourceService = {
	/** 某部门视角下可见的数据源：平台共享 + 本部门本地。 */
	listForDepartment(departmentId: string): Promise<Result<DataSource[]>> {
		const list = db.dataSources.filter((d) => d.scope === "platform" || d.departmentId === departmentId);
		return ok(list);
	},

	get(id: string): Promise<Result<DataSource | null>> {
		return ok(db.dataSources.find((d) => d.id === id) ?? null);
	},

	/** 新建本部门本地源。 */
	create(departmentId: string, payload: DataSourceUpsertPayload): Promise<Result<DataSource>> {
		const created: DataSource = {
			id: `ds-${seq++}`,
			scope: "department",
			departmentId,
			status: "untested",
			owner: "本部门",
			createdAt: new Date().toISOString().slice(0, 10),
			...payload,
		};
		db.dataSources = [created, ...db.dataSources];
		return ok(created, "已创建");
	},

	update(id: string, payload: DataSourceUpsertPayload): Promise<Result<DataSource | null>> {
		const idx = db.dataSources.findIndex((d) => d.id === id);
		if (idx < 0) return fail("数据源不存在", null);
		if (db.dataSources[idx].scope === "platform") return fail("平台共享源不可编辑", null);
		const updated = { ...db.dataSources[idx], ...payload };
		db.dataSources = db.dataSources.map((d) => (d.id === id ? updated : d));
		return ok(updated, "已更新");
	},

	remove(id: string): Promise<Result<boolean>> {
		const target = db.dataSources.find((d) => d.id === id);
		if (!target) return fail("数据源不存在", false);
		if (target.scope === "platform") return fail("平台共享源不可删除", false);
		db.dataSources = db.dataSources.filter((d) => d.id !== id);
		return ok(true, "已删除");
	},

	/** 连通测试 —— 模拟结果并回写状态。 */
	testConnection(id: string): Promise<Result<ConnectionTestResult>> {
		const target = db.dataSources.find((d) => d.id === id);
		if (!target) return fail("数据源不存在", { success: false, message: "数据源不存在" });
		// 未配置 jdbcUrl 的本地文件源模拟失败，其余成功
		const success = target.connector !== "File" || Boolean(target.jdbcUrl);
		const now = new Date().toISOString().slice(0, 16).replace("T", " ");
		const elapsed = 120 + Math.floor(Math.random() * 380);
		db.dataSources = db.dataSources.map((d) =>
			d.id === id ? { ...d, status: success ? "connected" : "error", lastTestedAt: now } : d,
		);
		const result: ConnectionTestResult = success
			? { success: true, message: "连接成功", elapsedMillis: elapsed, engineVersion: target.engineVersion, driverVersion: target.driverVersion }
			: { success: false, message: "连接失败：未配置连接信息或驱动缺失", elapsedMillis: elapsed, warnings: ["请检查 JDBC URL 与凭据"] };
		return ok(result);
	},
};
