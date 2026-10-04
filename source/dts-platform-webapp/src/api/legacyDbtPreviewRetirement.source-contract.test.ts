import { describe, expect, it } from "vitest";
import platformApiSource from "./platformApi.ts?raw";

describe("legacy dbt preview retirement", () => {
	it("does not expose the raw manifest-relation preview API", () => {
		expect(platformApiSource).not.toContain('url: "/etl/dbt/preview"');
		expect(platformApiSource).not.toContain("previewDbtModel");
	});
});
