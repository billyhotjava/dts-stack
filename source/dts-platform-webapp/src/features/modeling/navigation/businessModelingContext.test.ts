import { describe, expect, it } from "vitest";
import { buildBusinessModelingRoute, resolveBusinessModelingContext } from "./businessModelingContext";

describe("business modeling journey context", () => {
	it("reads legacy identifiers but only writes canonical route context", () => {
		const context = resolveBusinessModelingContext(
			new URLSearchParams("processId=pjm&objectId=obj-1&modelSpecId=model-1&revision=2"),
		);
		const route = new URL(
			buildBusinessModelingRoute("/data-modeling/home/workspace?view=release", {
				...context,
				planId: "plan-1",
				domainId: "domain-1",
				modelType: "FACT",
				returnTo: "/data-modeling/planning/spaces?planId=plan-1&view=baseline",
			}),
			"http://dts.local",
		);

		expect(context.projectSpaceMode).toBe("implicit");
		expect(route.searchParams.get("planId")).toBe("plan-1");
		expect(route.searchParams.get("domainId")).toBe("domain-1");
		expect(route.searchParams.get("modelSpecId")).toBe("model-1");
		expect(route.searchParams.get("revision")).toBe("2");
		expect(route.searchParams.get("modelType")).toBe("FACT");
		expect(route.searchParams.get("returnTo")).toBe("/data-modeling/planning/spaces?planId=plan-1&view=baseline");
		expect(route.searchParams.get("view")).toBe("release");
		for (const retired of ["objectId", "processId", "planningId", "warehouseLayer", "modelingMode", "projectSpaceId"]) {
			expect(route.searchParams.has(retired)).toBe(false);
		}
	});

	it("does not require a project space for a valid process context", () => {
		const context = resolveBusinessModelingContext(new URLSearchParams("processId=pjm"));

		expect(context.projectSpaceId).toBeUndefined();
		expect(context.projectSpaceMode).toBe("implicit");
	});
});
