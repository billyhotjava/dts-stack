import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const readSource = (relativePath: string) => fs.readFileSync(path.resolve(import.meta.dirname, relativePath), "utf8");

describe("role detail assignment table contract", () => {
	it("uses a role-scoped paged assignment-user api instead of loading all users", () => {
		const apiSource = readSource("../api/adminApi.ts");
		const typesSource = readSource("../types.ts");
		const detailSource = readSource("./role-detail.tsx");

		expect(apiSource.includes("getRoleAssignmentUsers")).toBe(true);
		expect(apiSource.includes("/assignment-users")).toBe(true);
		expect(typesSource.includes("inRole: boolean")).toBe(true);
		expect(detailSource.includes("getAllAdminUsers")).toBe(false);
	});

	it("renders assignment users through table rowSelection and keeps approval deltas", () => {
		const detailSource = readSource("./role-detail.tsx");

		expect(detailSource.includes("rowSelection")).toBe(true);
		expect(detailSource.includes("pendingAdds")).toBe(true);
		expect(detailSource.includes("pendingRemovals")).toBe(true);
		expect(detailSource.includes("memberAdds")).toBe(true);
		expect(detailSource.includes("memberRemoves")).toBe(true);
	});

	it("supports department full-name and username query fields", () => {
		const detailSource = readSource("./role-detail.tsx");

		expect(detailSource.includes("deptPath")).toBe(true);
		expect(detailSource.includes("fullName")).toBe(true);
		expect(detailSource.includes("username")).toBe(true);
	});
});
