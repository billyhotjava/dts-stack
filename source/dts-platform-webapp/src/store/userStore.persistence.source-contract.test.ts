import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

describe("platform user store persistence", () => {
	it("uses a platform-specific localStorage namespace", () => {
		const source = fs.readFileSync(path.resolve(import.meta.dirname, "./userStore.ts"), "utf8");
		const e2eSetup = fs.readFileSync(path.resolve(import.meta.dirname, "../../e2e/auth.setup.ts"), "utf8");

		expect(source.includes('name: "dts.platform.userStore"')).toBe(true);
		expect(source.includes('name: "userStore"')).toBe(false);
		expect(e2eSetup.includes('{ name: "dts.platform.userStore", value: storeJson }')).toBe(true);
		expect(e2eSetup.includes('{ name: "userStore", value: storeJson }')).toBe(false);
	});
});
