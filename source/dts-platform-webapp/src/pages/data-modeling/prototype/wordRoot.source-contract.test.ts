import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), "utf8");

describe("word-root standards contract", () => {
	it("uses the independent word-root owner and enables the dedicated editor", () => {
		const api = read("../../../api/platformApi.ts");
		const service = read("./services/standardsProjectionService.ts");
		const page = read("./StandardsPage.tsx");

		expect(api).toMatch(/listWordRoots|createWordRoot|updateWordRoot/);
		expect(service).toMatch(/listWordRoots|createWordRoot|updateWordRoot/);
		expect(service).not.toContain("当前服务端没有独立词根契约");
		expect(page).toMatch(/词根编码 \*|中文词根 \*|英文全称 \*|英文缩写 \*/);
	});
});
