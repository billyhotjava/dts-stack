import fs from "node:fs";
import { describe, expect, it } from "vitest";

const mainSource = fs.readFileSync(new URL("./main.tsx", import.meta.url), "utf8");

describe("admin dashboard workspace layout contract", () => {
	it("keeps the dashboard shell fluid and left aligned", () => {
		expect(mainSource).toContain("max-w-none");
		expect(mainSource).toContain("min-w-0");
		expect(mainSource).toContain("overflow-x-hidden");
		expect(mainSource).not.toContain("mx-auto");
		expect(mainSource).not.toContain("max-w-screen");
		expect(mainSource).not.toContain("themeStretch");
	});
});
