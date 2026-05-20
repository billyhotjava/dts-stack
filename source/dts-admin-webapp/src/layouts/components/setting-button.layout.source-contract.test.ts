import fs from "node:fs";
import { describe, expect, it } from "vitest";

const source = fs.readFileSync(new URL("./setting-button.tsx", import.meta.url), "utf8");

describe("admin setting panel layout contract", () => {
	it("does not expose the retired global stretch switch", () => {
		expect(source).not.toContain("themeStretch");
		expect(source).not.toContain("sys.settings.stretch");
	});
});
