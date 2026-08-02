import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), "utf8");

describe("Sprint-84 real indicator workspace", () => {
	it("removes built-in indicator examples and consumes the governance adapter", () => {
		const workspace = read("./pages/MetricsWorkspace.tsx");
		const adapter = read("./indicatorWorkspaceAdapter.ts");
		const service = read("../../api/services/indicatorGovernanceService.ts");

		expect(workspace).not.toContain("METRIC_CATALOG");
		expect(workspace).not.toContain("TYPE_CONFIG");
		expect(workspace).not.toContain("演示数据");
		expect(workspace).toContain("loadIndicatorCatalog");
		expect(adapter).toContain('from "@/api/services/indicatorGovernanceService"');
		expect(adapter).not.toContain('from "@/api/platformApi"');
		expect(service).toContain('from "../platformApi"');
		expect(adapter).toContain("listIndicators");
	});

	it("uses real lifecycle controls instead of backend-pending buttons", () => {
		const editor = read("./components/MetricEditor.tsx");

		expect(editor).not.toContain("BackendPendingButton");
		expect(editor).toContain("saveIndicatorDraft");
		expect(editor).toContain("publishIndicatorDraft");
		expect(editor).toContain("archiveIndicatorDraft");
		expect(editor).toContain("loadIndicatorGovernanceContext");
	});

	it("renders loading, empty, permission, retry, disabled-reason and success states", () => {
		const workspace = read("./pages/MetricsWorkspace.tsx");
		const editor = read("./components/MetricEditor.tsx");
		const source = `${workspace}\n${editor}`;

		for (const marker of ["正在加载指标目录", "暂无指标", "无权访问指标", "重试", "无指标维护权限", "保存成功"]) {
			expect(source).toContain(marker);
		}
	});
});
