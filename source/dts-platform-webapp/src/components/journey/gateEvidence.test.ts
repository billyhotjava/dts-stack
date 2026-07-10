import { describe, expect, it } from "vitest";
import { resolveArtifactValidations, type ArtifactValidator } from "./journeyArtifactValidation";
import { buildGateEvidence, resolveGateVerdict, type GateCheck } from "./gateEvidence";

const check = (partial: Partial<GateCheck> & Pick<GateCheck, "key" | "status">): GateCheck => ({
	label: partial.key,
	detail: "",
	...partial,
});

describe("gate evidence", () => {
	it("aggregates verdict as blocked>missing>ready", () => {
		expect(
			resolveGateVerdict([check({ key: "compile", status: "ready" }), check({ key: "run", status: "ready" })]),
		).toBe("pass");
		expect(
			resolveGateVerdict([check({ key: "compile", status: "ready" }), check({ key: "run", status: "missing" })]),
		).toBe("warn");
		expect(
			resolveGateVerdict([check({ key: "compile", status: "blocked" }), check({ key: "run", status: "missing" })]),
		).toBe("fail");
	});

	it("derives check states from journey params", () => {
		const evidence = buildGateEvidence({ standardDraftId: "std-1", modelId: "m-1" });
		const byKey = Object.fromEntries(evidence.checks.map((item) => [item.key, item]));

		expect(byKey.standardsCoverage.status).toBe("ready");
		expect(byKey.standardsCoverage.detail).toContain("std-1");
		expect(byKey.compile.status).toBe("ready");
		expect(byKey.test.status).toBe("missing");
		expect(byKey.test.apiName).toBe("GET /api/dbt/test-results");
		expect(byKey.run.status).toBe("missing");
		expect(byKey.run.apiName).toBe("GET /api/ops/run-evidence");
		expect(evidence.verdict).toBe("warn");
	});

	it("keeps evidence urls inside the journey context", () => {
		const evidence = buildGateEvidence({ modelId: "m-1" });
		for (const item of evidence.checks) {
			expect(item.evidenceUrl).toContain("journey=e2e-data-product");
			expect(item.evidenceUrl).toContain("modelId=m-1");
		}
	});

	it("marks checks blocked when the backing artifact is confirmed invalid", () => {
		const invalidModel: ArtifactValidator = (key) => (key === "modelId" ? "invalid" : "unknown");
		const params = { standardDraftId: "std-1", modelId: "bad-model", runId: "r-1" };
		const validations = resolveArtifactValidations(params, invalidModel);

		const evidence = buildGateEvidence(params, { validations });
		const byKey = Object.fromEntries(evidence.checks.map((item) => [item.key, item]));

		expect(byKey.compile.status).toBe("blocked");
		expect(byKey.compile.detail).toContain("不存在或已失效");
		expect(byKey.run.status).toBe("ready");
		expect(evidence.verdict).toBe("fail");
	});

	it("requires api names on every non-ready check", () => {
		const evidence = buildGateEvidence({});
		for (const item of evidence.checks) {
			if (item.status !== "ready" && item.key !== "standardsCoverage" && item.key !== "compile") {
				expect(item.apiName, item.key).toBeTruthy();
			}
		}
	});
});
