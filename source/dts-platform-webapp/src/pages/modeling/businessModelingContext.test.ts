import { describe, expect, it } from "vitest";
import { buildBusinessModelingRoute, resolveBusinessModelingContext } from "./businessModelingContext";

describe("business modeling journey context", () => {
	it("keeps object, model and revision identifiers across ledger links", () => {
		const context = resolveBusinessModelingContext(new URLSearchParams("processId=pjm&objectId=obj-1&modelSpecId=model-1&revision=2"));

		expect(context.projectSpaceMode).toBe("implicit");
		expect(buildBusinessModelingRoute("/modeling/semantic/models", context)).toContain("objectId=obj-1");
		expect(buildBusinessModelingRoute("/modeling/semantic/models", context)).toContain("modelSpecId=model-1");
		expect(buildBusinessModelingRoute("/modeling/semantic/models", context)).toContain("revision=2");
	});

	it("does not require a project space for a valid process context", () => {
		const context = resolveBusinessModelingContext(new URLSearchParams("processId=pjm"));

		expect(context.projectSpaceId).toBeUndefined();
		expect(context.projectSpaceMode).toBe("implicit");
	});
});
