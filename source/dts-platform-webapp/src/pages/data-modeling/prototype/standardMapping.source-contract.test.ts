import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), "utf8");

describe("standard-mapping page contract", () => {
	it("creates revision-pinned model-field bindings through the ModelSpec owner", () => {
		const page = read("./StandardsPage.tsx");
		const service = read("./services/standardsProjectionService.ts");

		expect(page).toMatch(/所属模型 \*|模型字段 \*|数据元标准 \*|保存映射/);
		expect(page).toMatch(/智能补全标准|StandardMappingSuggestionModal/);
		expect(page).toContain('row.state !== "草稿"');
		expect(service).toMatch(/listModelSpecs|updateModelSpec|standardElementId|standardElementVersion/);
		expect(service).not.toContain("本页只读展示引用证据");
	});
});
