import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

describe("platform account dropdown login IP contract", () => {
	it("surfaces the current login IP from user session state", () => {
		const accountDropdownSource = fs.readFileSync(path.resolve(import.meta.dirname, "./account-dropdown.tsx"), "utf8");
		const loginPageSource = fs.readFileSync(path.resolve(import.meta.dirname, "../../pages/sys/login/index.tsx"), "utf8");
		const platformApiSource = fs.readFileSync(path.resolve(import.meta.dirname, "../../api/platformApi.ts"), "utf8");
		const entitySource = fs.readFileSync(path.resolve(import.meta.dirname, "../../types/entity.ts"), "utf8");

		expect(accountDropdownSource.includes("loginIp")).toBe(true);
		expect(accountDropdownSource.includes("clientIp")).toBe(true);
		expect(accountDropdownSource.includes("登录IP")).toBe(true);
		expect(loginPageSource.includes("loginIp: status.loginIp ?? status.clientIp")).toBe(true);
		expect(platformApiSource.includes("loginIp?: string;")).toBe(true);
		expect(platformApiSource.includes("clientIp?: string;")).toBe(true);
		expect(entitySource.includes("loginIp?: string;")).toBe(true);
		expect(entitySource.includes("clientIp?: string;")).toBe(true);
	});
});
