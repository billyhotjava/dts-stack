import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

describe("AdminGuard session resilience contract", () => {
	const source = () => fs.readFileSync(path.resolve(import.meta.dirname, "./guard.tsx"), "utf8");

	it("keeps the user on the current admin route during transient whoami failures", () => {
		const guardSource = source();

		expect(guardSource.includes("isRecoverableWhoamiError")).toBe(true);
		expect(guardSource.includes("refetchOnReconnect: true")).toBe(true);
		expect(guardSource.includes("refetchOnWindowFocus: true")).toBe(true);
		expect(guardSource.includes("invalidateQueries")).toBe(true);
		expect(guardSource.includes("管理服务连接中")).toBe(true);
		expect(guardSource.includes("retry: false")).toBe(false);
		expect(guardSource.includes("if (isError && isRecoverableWhoamiError(error))")).toBe(true);
	});
});
