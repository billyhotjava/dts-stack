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

	it("keeps generic and legacy identifiers across stage links", () => {
		const context = resolveModelingJourneyContext(
			new URLSearchParams("domainId=domain-1&modelSpecId=model-1&scopeKind=model&stage=implementation"),
		);
		const route = buildModelingJourneyRoute(modelingStagePath("RELEASE"), {
			...context,
			stage: "RELEASE",
		});

		expect(context.scopeKind).toBe("MODEL");
		expect(route).toContain("domainId=domain-1");
		expect(route).toContain("modelSpecId=model-1");
		expect(route).toContain("stage=release");
	});
});
