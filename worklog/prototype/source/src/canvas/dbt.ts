import type { Edge } from "@xyflow/react";
import type { TransformNode } from "./transformGraphStore";

/** 由画布转换图自动生成的 dbt 模型（只读，用户全程不手写 dbt）。 */
export interface DbtModel {
	name: string;
	materialization: "view" | "table";
	dependsOn: string[];
	sql: string;
}

/** 从中文/混合标签提取 ascii 片段作为 dbt 名，回落到 kind。 */
function asciiSlug(label: string, fallback: string): string {
	const m = label.toLowerCase().match(/[a-z0-9]+/g);
	return m && m.length ? m.join("_") : fallback;
}

const PREFIX: Record<string, string> = { clean: "stg", join: "int", aggregate: "int", output: "mart" };

/**
 * 把画布图映射为 dbt 模型集合：
 * - 源表节点 → dbt source（{{ source(...) }}）
 * - 清洗/连接/聚合 → view 模型；输出 → table 模型
 * 上游引用用 {{ ref(...) }} / {{ source(...) }}，体现 dbt 沿袭。
 */
export function generateDbtModels(nodes: TransformNode[], edges: Edge[]): DbtModel[] {
	const refName = new Map<string, string>(); // nodeId -> ref/source 表达式
	const modelName = new Map<string, string>(); // nodeId -> 模型名（非源）
	let seq = 0;

	for (const n of nodes) {
		if (n.data.kind === "source") {
			const src = asciiSlug(n.data.label, `src_${seq}`);
			refName.set(n.id, `{{ source('raw', '${src}') }}`);
		} else {
			seq += 1;
			const name = `${PREFIX[n.data.kind] ?? "int"}_${asciiSlug(n.data.label, String(seq))}`;
			modelName.set(n.id, name);
			refName.set(n.id, `{{ ref('${name}') }}`);
		}
	}

	const upstream = (id: string) => edges.filter((e) => e.target === id).map((e) => e.source);

	const models: DbtModel[] = [];
	for (const n of nodes) {
		if (n.data.kind === "source") continue;
		const ups = upstream(n.id);
		const upRefs = ups.map((u) => refName.get(u) ?? "{{ ref('unknown') }}");
		const name = modelName.get(n.id) as string;
		const mat = n.data.kind === "output" ? "table" : "view";
		let sql: string;
		switch (n.data.kind) {
			case "clean":
				sql = `select distinct *\nfrom ${upRefs[0] ?? "{{ ref('upstream') }}"}`;
				break;
			case "join":
				sql = `select a.*, b.*\nfrom ${upRefs[0] ?? "a"} a\njoin ${upRefs[1] ?? "b"} b on a.cust_id = b.id`;
				break;
			case "aggregate":
				sql = `select grp, count(*) as cnt\nfrom ${upRefs[0] ?? "{{ ref('upstream') }}"}\ngroup by grp`;
				break;
			default: // output
				sql = `{{ config(materialized='table') }}\nselect *\nfrom ${upRefs[0] ?? "{{ ref('upstream') }}"}`;
		}
		models.push({ name, materialization: mat, dependsOn: upRefs, sql });
	}
	return models;
}
