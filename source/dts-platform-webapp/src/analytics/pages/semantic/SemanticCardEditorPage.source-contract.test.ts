import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

describe("SemanticCardEditorPage source contract", () => {
	it("searches base models by the customer-visible label", () => {
		const source = readFileSync(new URL("./SemanticCardEditorPage.tsx", import.meta.url), "utf8");

		expect(source).toContain('optionFilterProp="label"');
	});
});
