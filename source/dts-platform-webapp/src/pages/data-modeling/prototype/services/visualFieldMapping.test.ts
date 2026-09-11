import { describe, expect, it } from "vitest";
import type { ModelInputFieldSource } from "@/api/modelInputInspectionApi";
import { defaultNameMappings, uniqueSourceField } from "./visualFieldMapping";

const source = (index: number, names: string[]): ModelInputFieldSource => ({
	index,
	input: null,
	alias: `src_${index}`,
	schemaState: "RESOLVED",
	fields: names.map((name) => ({ name, dataType: "text", nullable: true })),
});

describe("visual field name mapping", () => {
	it("fills only unmapped fields that have exactly one same-name source field", () => {
		const sources = [source(0, ["project_code", "task_code"]), source(1, ["task_code", "project_name"])];

		const next = defaultNameMappings(["project_code", "task_code", "project_name", "task_total"], sources, [
			{ targetField: "project_code", sourceField: "src_1.project_name" },
		]);

		expect(next).toEqual([
			{ targetField: "project_code", sourceField: "src_1.project_name" },
			{ targetField: "project_name", sourceField: "src_1.project_name" },
		]);
		expect(uniqueSourceField("task_code", sources)).toBeNull();
	});

	it("reports no change when every resolvable field is already mapped", () => {
		const sources = [source(0, ["project_code"])];
		expect(
			defaultNameMappings(["project_code"], sources, [
				{ targetField: "project_code", sourceField: "src_0.project_code" },
			]),
		).toBeNull();
	});
});
