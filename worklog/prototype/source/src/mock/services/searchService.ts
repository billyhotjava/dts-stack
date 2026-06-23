import type { Result } from "@/types/api";
import { ok } from "../client";
import { db } from "../db";
import { SEED_API_SERVICES } from "../fixtures/platform";

export type SearchHitType = "部门" | "数据集" | "指标" | "数据源" | "API";

export interface SearchHit {
	id: string;
	type: SearchHitType;
	label: string;
	sub?: string;
	/** 命中所属部门（选中后切换部门上下文） */
	departmentId?: string;
	/** 导航目标路由 */
	to: string;
}

/** 全局搜索 —— 跨部门聚合 部门/数据集/指标/数据源/API。 */
export const searchService = {
	search(keyword: string): Promise<Result<SearchHit[]>> {
		const kw = keyword.trim().toLowerCase();
		if (!kw) return ok([]);
		const hits: SearchHit[] = [];
		const match = (...vals: (string | undefined)[]) => vals.some((v) => (v ?? "").toLowerCase().includes(kw));

		for (const d of db.departments) if (match(d.name, d.deptCode, d.description)) hits.push({ id: d.id, type: "部门", label: d.name, sub: d.deptCode, departmentId: d.id, to: "/portal" });
		for (const d of db.datasets) if (match(d.name, d.description)) hits.push({ id: d.id, type: "数据集", label: d.name, sub: `${d.layer} · ${d.owner}`, departmentId: d.departmentId, to: "/assets" });
		for (const m of db.metrics) if (match(m.name, m.code, m.caliber)) hits.push({ id: m.id, type: "指标", label: m.name, sub: m.code, departmentId: m.departmentId, to: "/metrics" });
		for (const s of db.dataSources) if (match(s.name, s.type, s.connector)) hits.push({ id: s.id, type: "数据源", label: s.name, sub: `${s.type} · ${s.scope === "platform" ? "平台共享" : "部门"}`, departmentId: s.departmentId, to: "/connect" });
		for (const a of SEED_API_SERVICES) if (match(a.name, a.path)) hits.push({ id: a.id, type: "API", label: a.name, sub: a.path, departmentId: a.departmentId, to: "/platform/serve" });

		return ok(hits.slice(0, 30));
	},
};
