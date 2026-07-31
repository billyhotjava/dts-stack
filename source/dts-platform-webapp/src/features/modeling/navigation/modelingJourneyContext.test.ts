import { describe, expect, it } from "vitest";
import { buildModelingJourneyRoute, modelingStagePath, resolveModelingJourneyContext } from "./modelingJourneyContext";

describe("generic modeling journey context", () => {
	it("maps legacy subject parameters into the scope stage", () => {
		const context = resolveModelingJourneyContext(
			new URLSearchParams("active=domain-1&focus=business-processes&processId=process-1"),
		);

		expect(context.domainId).toBe("domain-1");
		expect(context.scopeId).toBe("process-1");
		expect(context.scopeKind).toBe("PROCESS");
		expect(context.stage).toBe("SCOPE");
		expect(context.method).toBe("DIMENSIONAL");
	});

	it("maps stage links to canonical pages without writing legacy journey keys", () => {
		const context = resolveModelingJourneyContext(
			new URLSearchParams("domainId=domain-1&modelSpecId=model-1&scopeKind=model&stage=implementation"),
		);
		const route = buildModelingJourneyRoute(modelingStagePath("RELEASE"), {
			...context,
			stage: "RELEASE",
		});

		expect(context.scopeKind).toBe("MODEL");
		const url = new URL(route, "http://dts.local");
		expect(url.pathname).toBe("/data-modeling/home/workspace");
		expect(url.searchParams.get("view")).toBe("release");
		expect(url.searchParams.get("domainId")).toBe("domain-1");
		expect(url.searchParams.get("modelSpecId")).toBe("model-1");
		for (const retired of ["scopeId", "scopeKind", "modelId", "method", "stage", "objectId", "processId"]) {
			expect(url.searchParams.has(retired)).toBe(false);
		}
	});

	it("uses only canonical stage destinations", () => {
		expect(modelingStagePath("SCOPE")).toBe("/data-modeling/planning/spaces");
		expect(modelingStagePath("LOGICAL")).toBe("/data-modeling/dimensions/workbench");
		expect(modelingStagePath("IMPLEMENTATION")).toBe("/data-modeling/dimensions/workbench?mode=implementation");
		expect(modelingStagePath("RELEASE")).toBe("/data-modeling/home/workspace?view=release");
	});
});
