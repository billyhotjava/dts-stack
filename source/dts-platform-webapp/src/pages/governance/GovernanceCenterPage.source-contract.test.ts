import { readFileSync } from "node:fs";
import { expect, it } from "vitest";

it("links governance cards to the current standard, quality, asset and subject pages", () => {
	const source = readFileSync(new URL("./GovernanceCenterPage.tsx", import.meta.url), "utf8");
	for (const path of [
		"/data-modeling/standards/dictionary",
		"/data-modeling/standards/fields",
		"/data-modeling/standards/codes",
		"/foundation/standard-package",
		"/governance/rules/catalog",
		"/catalog/search",
		"/data-architecture?view=subjects",
	]) {
		expect(source).toContain(`path: "${path}"`);
	}
	for (const path of ["/governance/templates", "/catalog/quality", "/governance/subjects"]) {
		expect(source).not.toContain(`path: "${path}"`);
	}
});
