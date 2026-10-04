import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

describe("standard code editor", () => {
	it("uses catalog domains and offers the existing code lifecycle states", () => {
		const page = readFileSync(new URL("./StandardsPage.tsx", import.meta.url), "utf8");
		expect(page).toContain("catalogDomainService.list()");
		expect(page).toContain('view === "codes" && key === "dataType"');
		expect(page).toContain('view === "codes" && key === "domain"');
		expect(page).toContain('<option value="1">已发布</option>');
		expect(page).toContain('set("status", event.target.value)');
	});
});
