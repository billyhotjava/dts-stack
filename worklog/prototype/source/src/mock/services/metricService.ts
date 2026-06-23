import type { Result } from "@/types/api";
import type { GlossaryTerm, Metric, ReferenceCode, SemanticSubject } from "@/types/metric";
import { fail, ok } from "../client";
import { db } from "../db";

let seq = 200;

/** 指标的底层 dbt 模型 SQL（自动生成，体现 dbt 隐藏）。 */
export function metricDbtSql(metric: Metric): string {
	const dataset = db.datasets.find((d) => d.id === metric.datasetId);
	const ref = dataset ? `{{ ref('${dataset.name}') }}` : "{{ ref('upstream') }}";
	return `-- models/metrics/${metric.code}.sql （自动生成，只读）\n{{ config(materialized='view') }}\nselect\n  ${metric.expression} as ${metric.code}\nfrom ${ref}`;
}

/**
 * 指标 / 语义 / 字典 服务 —— 指标归口部门。
 */
export const metricService = {
	listByDepartment(departmentId: string): Promise<Result<Metric[]>> {
		return ok(db.metrics.filter((m) => m.departmentId === departmentId));
	},
	get(id: string): Promise<Result<Metric | null>> {
		return ok(db.metrics.find((m) => m.id === id) ?? null);
	},
	create(departmentId: string, payload: Omit<Metric, "id" | "departmentId" | "status">): Promise<Result<Metric>> {
		const created: Metric = { id: `mt-${seq++}`, departmentId, status: "draft", ...payload };
		db.metrics = [created, ...db.metrics];
		return ok(created, "已创建");
	},
	publish(id: string): Promise<Result<Metric | null>> {
		const m = db.metrics.find((x) => x.id === id);
		if (!m) return fail("指标不存在", null);
		const updated = { ...m, status: "published" as const };
		db.metrics = db.metrics.map((x) => (x.id === id ? updated : x));
		return ok(updated, "已发布");
	},
	listSubjects(departmentId: string): Promise<Result<SemanticSubject[]>> {
		return ok(db.semanticSubjects.filter((s) => s.departmentId === departmentId));
	},
	listGlossary(departmentId: string): Promise<Result<GlossaryTerm[]>> {
		return ok(db.glossary.filter((g) => g.departmentId === departmentId));
	},
	listReferenceCodes(): Promise<Result<ReferenceCode[]>> {
		return ok(db.referenceCodes);
	},
};
