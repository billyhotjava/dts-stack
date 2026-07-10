import { describe, expect, it } from "vitest";
import { createWarehousePlanningContext } from "../governance/warehousePlanningContext";
import { resolveDimensionCandidateGate } from "./dimensionCandidateGate";

const planningContext = createWarehousePlanningContext({
	planningId: "plan-1",
	domainId: "domain-1",
	domainName: "客户域",
	warehouseLayer: "DWD",
	modelingMode: "dimension",
	standardDraftId: "draft-1",
});

describe("dimension candidate gate", () => {
	it("is ready only for a session planning draft with standard fields", () => {
		expect(
			resolveDimensionCandidateGate({
				planningContext,
				planningSource: "session",
				standardDraftId: "draft-1",
				standardFieldCount: 3,
			}),
		).toMatchObject({ status: "ready" });
	});

	it("marks a missing planning draft and points back to warehouse planning", () => {
		const result = resolveDimensionCandidateGate({
			planningContext: null,
			planningSource: "missing",
			standardDraftId: "",
			standardFieldCount: 0,
		});

		expect(result.status).toBe("missing");
		expect(result.reason).toContain("数仓规划");
		expect(result.repairRoute).toBe("/governance/subjects");
	});

	it("blocks URL-only or mismatched planning context", () => {
		const result = resolveDimensionCandidateGate({
			planningContext,
			planningSource: "url-fallback",
			standardDraftId: "draft-1",
			standardFieldCount: 3,
		});

		expect(result.status).toBe("blocked");
		expect(result.reason).toContain("session");
		expect(result.repairRoute).toContain("/governance/subjects");
	});

	it("marks a missing standard draft and points to data elements", () => {
		const result = resolveDimensionCandidateGate({
			planningContext: { ...planningContext, standardDraftId: undefined },
			planningSource: "session",
			standardDraftId: "",
			standardFieldCount: 0,
		});

		expect(result.status).toBe("missing");
		expect(result.reason).toContain("标准草稿");
		expect(result.repairRoute).toContain("/governance/standards/elements");
	});

	it("blocks an empty standard draft", () => {
		const result = resolveDimensionCandidateGate({
			planningContext,
			planningSource: "session",
			standardDraftId: "draft-1",
			standardFieldCount: 0,
		});

		expect(result.status).toBe("blocked");
		expect(result.reason).toContain("字段");
	});
});
