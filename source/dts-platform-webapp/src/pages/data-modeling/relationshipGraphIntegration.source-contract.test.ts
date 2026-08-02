import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), "utf8");

describe("relationship graph workspace integration", () => {
	it("uses the canonical warehouse-plan graph through a service boundary", () => {
		const page = read("./pages/RelationshipGraphWorkspace.tsx");
		const service = read("../../api/services/modelingRelationshipGraphService.ts");

		expect(page).toContain('from "@/api/services/modelingRelationshipGraphService"');
		expect(page).not.toContain('from "@/api/platformApi"');
		expect(service).toContain("listWarehousePlans");
		expect(service).toContain("getWarehousePlanRelationshipGraph");
		expect(service).not.toContain("GRAPH_DATA");
	});

	it("removes demo nodes and unsupported export controls", () => {
		const page = read("./pages/RelationshipGraphWorkspace.tsx");

		expect(page).not.toContain("GRAPH_DATA");
		expect(page).not.toContain("示例数据域");
		expect(page).not.toContain("界面示例");
		expect(page).not.toContain("BackendPendingButton");
		expect(page).not.toContain("UiStageNotice");
		expect(page).not.toContain("导出关系");
	});

	it("exposes real plan, filtering, layout, retry and bounded graph states", () => {
		const page = read("./pages/RelationshipGraphWorkspace.tsx");

		for (const token of [
			"当前建设计划",
			"节点类型",
			"重新布局",
			"重新加载",
			"无权访问关系图",
			"暂无关系数据",
			"图规模已达安全上限",
			"nextCursor",
		]) {
			expect(page).toContain(token);
		}
	});
});
