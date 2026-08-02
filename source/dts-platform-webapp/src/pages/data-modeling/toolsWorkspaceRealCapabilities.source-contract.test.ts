import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), "utf8");

describe("Sprint-84 real data-modeling tool workflows", () => {
	it("routes every retained tool to an existing canonical owner", () => {
		const page = read("./pages/ToolsWorkspace.tsx");
		const registry = read("../../features/modeling/navigation/dataModelingToolWorkflows.ts");

		expect(page).toContain("useNavigate");
		expect(page).toContain("getDataModelingToolWorkflows");
		expect(registry).toContain('path: "/data-modeling/dimensions/reverse"');
		expect(registry).toContain('path: "/data-modeling/standards/fields"');
		expect(registry).toContain('path: "/data-modeling/standards/codes"');
		expect(registry).toContain('path: "/catalog/lineage/import"');
		expect(registry).toContain('path: "/ops/audit-evidence"');
	});

	it("does not present local demo jobs, ownerless exports, or pending controls", () => {
		const page = read("./pages/ToolsWorkspace.tsx");

		expect(page).not.toMatch(/TOOL_CARDS|IMPORT_ROWS|EXPORT_ROWS|DemoRow|DataTable/);
		expect(page).not.toMatch(/BackendPendingButton|UiStageNotice|后台待接入|界面示例|示例用户/);
		expect(page).not.toMatch(/模型批量导出|DDL 结构解析|交付清单生成/);
		expect(page).toContain("没有统一的工具运行台账");
		expect(page).toContain("当前没有归属明确的建模导出流程");
	});

	it("keeps APIs and results at their owning workflow instead of building a parallel ledger", () => {
		const page = read("./pages/ToolsWorkspace.tsx");
		const registry = read("../../features/modeling/navigation/dataModelingToolWorkflows.ts");

		expect(page).not.toMatch(/from ["']@\/api\/platformApi/);
		expect(registry).not.toMatch(/status:|operator:|startedAt:|completedAt:|runId:/);
		expect(registry).toContain('resultOwner: "目标流程"');
	});
});
