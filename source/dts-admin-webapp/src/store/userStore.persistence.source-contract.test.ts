import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

describe("admin user store persistence", () => {
	it("uses an admin-specific localStorage namespace", () => {
		const source = fs.readFileSync(path.resolve(import.meta.dirname, "./userStore.ts"), "utf8");

		expect(source.includes('name: "dts.admin.userStore"')).toBe(true);
		expect(source.includes('name: "userStore"')).toBe(false);
	});
});
