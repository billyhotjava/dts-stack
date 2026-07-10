import { describe, expect, it } from "vitest";
import { resolveArtifactValidations, type ArtifactValidator } from "./journeyArtifactValidation";
import {
	ACCEPTANCE_SIGN_COLUMNS,
	buildAcceptancePackageJson,
	buildAcceptancePackageMarkdown,
	buildAcceptancePrintMeta,
	buildDataProductAcceptancePackage,
} from "./dataProductAcceptancePackage";

describe("acceptance package with structured gate evidence", () => {
	it("prepends a gate group whose status follows the verdict", () => {
		const pkg = buildDataProductAcceptancePackage({ standardDraftId: "std-1", modelId: "m-1" });
		const gateGroup = pkg.groups[0];

		expect(gateGroup.key).toBe("gate");
		expect(gateGroup.status).toBe("missing");
		expect(gateGroup.missingReason).toContain("门禁未齐");
		expect(pkg.gateEvidence.verdict).toBe("warn");
		expect(pkg.summary).toContain("发布门禁 warn");
	});

	it("turns the gate group blocked when an artifact is confirmed invalid", () => {
		const invalidModel: ArtifactValidator = (key) => (key === "modelId" ? "invalid" : "unknown");
		const params = { standardDraftId: "std-1", modelId: "bad" };
		const validations = resolveArtifactValidations(params, invalidModel);

		const pkg = buildDataProductAcceptancePackage(params, { validations });

		expect(pkg.gateEvidence.verdict).toBe("fail");
		expect(pkg.groups[0].status).toBe("blocked");
	});

	it("exports gate details in markdown and json", () => {
		const pkg = buildDataProductAcceptancePackage({ standardDraftId: "std-1", modelId: "m-1", runId: "r-1" });
		const markdown = buildAcceptancePackageMarkdown(pkg);

		expect(markdown).toContain("## 发布门禁明细");
		expect(markdown).toContain("| 落标覆盖 | ready |");
		expect(markdown).toContain("GET /api/dbt/test-results");

		const json = JSON.parse(buildAcceptancePackageJson(pkg)) as { gateEvidence?: { checks: unknown[] } };
		expect(json.gateEvidence?.checks).toHaveLength(4);
	});

	it("keeps the original nine evidence groups after the gate group", () => {
		const pkg = buildDataProductAcceptancePackage({});
		expect(pkg.groups).toHaveLength(10);
		expect(pkg.groups.map((group) => group.key)).toContain("audit");
	});
});

describe("acceptance print meta", () => {
	it("builds a printable header with context, timestamp and three sign columns", () => {
		const pkg = buildDataProductAcceptancePackage({ modelId: "m-1", serviceId: "svc-9" });
		const meta = buildAcceptancePrintMeta(pkg, new Date("2026-07-10T10:30:00"));

		expect(meta.title).toBe("客户验收包");
		expect(meta.contextSummary).toContain("m-1");
		expect(meta.contextSummary).toContain("svc-9");
		expect(meta.generatedAt).toContain("2026");
		expect(meta.signColumns).toEqual(ACCEPTANCE_SIGN_COLUMNS);
		expect(meta.signColumns).toHaveLength(3);
	});

	it("falls back to a friendly context summary when empty", () => {
		const meta = buildAcceptancePrintMeta(buildDataProductAcceptancePackage({}));
		expect(meta.contextSummary).toBe("尚未积累上下文对象");
	});
});
