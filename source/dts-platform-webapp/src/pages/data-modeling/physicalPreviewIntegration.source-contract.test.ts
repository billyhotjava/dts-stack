import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), "utf8");

describe("Sprint-83 controlled physical preview integration", () => {
	it("keeps sample loading explicit and inside the existing model detail", () => {
		const workbench = read("./components/model-detail/ModelDetailWorkbench.tsx");
		const physical = read("./components/model-detail/PhysicalModelPreview.tsx");
		const source = `${workbench}\n${physical}`;

		expect(workbench).toContain("PhysicalModelPreview");
		expect(physical).toContain("加载样例");
		expect(physical).toContain("getModelPhysicalPreview");
		expect(physical).toContain("getModelPhysicalStructure(reference)");
		expect(physical).toContain("onClick={() => void loadSamples()}");
		expect(source).not.toMatch(/useEffect\([\s\S]{0,240}getModelPhysicalPreview/);
		expect(source).not.toMatch(/导出|下载样例|复制全部/);
	});

	it("does not expose SQL, Jinja or the legacy dbt preview surface", () => {
		const api = read("../../api/modelPhysicalPreviewApi.ts");
		const physical = read("./components/model-detail/PhysicalModelPreview.tsx");
		const source = `${api}\n${physical}`;

		expect(source).not.toContain("/api/etl/dbt/preview");
		expect(source).not.toMatch(/compiledSql|rawSql|jinja|projectPath/i);
		expect(api).toContain('"Cache-Control": "no-store"');
		expect(api).toContain("20 | 50 | 100 | 500");
	});
});
