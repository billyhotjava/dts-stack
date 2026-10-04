import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const readSource = (relativePath: string) => fs.readFileSync(path.resolve(import.meta.dirname, relativePath), "utf8");

describe("role approval pending guards", () => {
	it("keeps role pending checks scoped to pending role changes", () => {
		const detailSource = readSource("./role-detail.tsx");
		const managementSource = readSource("./role-management.tsx");

		expect(detailSource.includes("isPendingRoleChange(item)")).toBe(true);
		expect(managementSource.includes("isPendingRoleChange(change)")).toBe(true);
	});

	it("supports deep links from approval detail notifications", () => {
		const routesSource = readSource("../../routes/admin-routes.tsx");
		const approvalCenterSource = readSource("./approval-center.tsx");

		expect(routesSource.includes('path: "approval/:requestId"')).toBe(true);
		expect(approvalCenterSource.includes("useParams")).toBe(true);
		expect(approvalCenterSource.includes("requestId")).toBe(true);
	});
});
