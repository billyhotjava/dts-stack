import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const source = readFileSync(resolve(process.cwd(), "src/components/sql/SqlWorkbenchExperimental.tsx"), "utf8");

describe("即席查询交互契约", () => {
	it("uses the selected datasource tables for SQL autocomplete", () => {
		expect(source).toContain("<SqlTableAutocomplete");
		expect(source).toContain("tables={tables}");
		expect(source).toContain("onSelect={handleAutocompleteSelect}");
	});

	it("saves a loaded query by update and exposes run and stop controls", () => {
		expect(source).toContain("updateSavedQuery(savedQueryId, payload)");
		expect(source).toContain('{savedQueryId ? "保存修改" : "保存查询"}');
		expect(source).toContain("disabled={!showRunning}");
		expect(source).toMatch(/>\s*停止\s*</);
	});
});
