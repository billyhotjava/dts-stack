import { describe, expect, it } from "vitest";
import { resolveArtifactValidations, type ArtifactValidator } from "./journeyArtifactValidation";
import { resolveDataProductJourneyStageState, resolveDataProductJourneyStageStates } from "./journeyStageState";

const fullParams = { sourceId: "ds-1", standardDraftId: "std-1", modelSpecId: "m-1" };

describe("journey stage state with artifact validations", () => {
	it("keeps the legacy behavior when no validations are provided", () => {
		const stage = resolveDataProductJourneyStageState("modeling", fullParams);

		expect(stage.status).toBe("done");
		expect(stage.verification).toBe("unverified");
		expect(stage.blocker).toBeUndefined();
	});

	it("blocks a stage whose artifact is confirmed invalid and offers recovery", () => {
		const invalidModel: ArtifactValidator = (key) => (key === "modelSpecId" ? "invalid" : "unknown");
		const validations = resolveArtifactValidations(fullParams, invalidModel);

		const stage = resolveDataProductJourneyStageState("modeling", fullParams, validations);

		expect(stage.status).toBe("blocked");
		expect(stage.verification).toBe("invalid");
		expect(stage.blocker?.reason).toContain("上下文对象无效");
		expect(stage.blocker?.recoveryAction).toContain("重新选择");
		expect(stage.gap).toContain("不存在或已失效");
	});

	it("marks verified artifacts as done + verified", () => {
		const allValid: ArtifactValidator = () => "valid";
		const validations = resolveArtifactValidations(fullParams, allValid);

		const stage = resolveDataProductJourneyStageState("modeling", fullParams, validations);

		expect(stage.status).toBe("done");
		expect(stage.verification).toBe("verified");
	});

	it("keeps unknown validations as done + unverified instead of pure green", () => {
		const allUnknown: ArtifactValidator = () => "unknown";
		const validations = resolveArtifactValidations(fullParams, allUnknown);

		const stage = resolveDataProductJourneyStageState("modeling", fullParams, validations);

		expect(stage.status).toBe("done");
		expect(stage.verification).toBe("unverified");
	});

	it("propagates validations through the plural resolver", () => {
		const invalidModel: ArtifactValidator = (key) => (key === "modelSpecId" ? "invalid" : "unknown");
		const validations = resolveArtifactValidations(fullParams, invalidModel);

		const stages = resolveDataProductJourneyStageStates(fullParams, validations);
		const modeling = stages.find((stage) => stage.stageKey === "modeling");
		const standards = stages.find((stage) => stage.stageKey === "standards");

		expect(modeling?.status).toBe("blocked");
		expect(standards?.status).toBe("done");
		expect(standards?.verification).toBe("unverified");
	});
});

describe("journey stage order follows the implementation sequence", () => {
	it("lists planning and standards before integration", async () => {
		const { DATA_PRODUCT_JOURNEY_STAGE_DEFINITIONS } = await import("./journeyStageState");

		expect(DATA_PRODUCT_JOURNEY_STAGE_DEFINITIONS.map((definition) => definition.stageKey)).toEqual([
			"planning",
			"standards",
			"integration",
			"modeling",
			"metrics",
			"development",
			"service",
			"evidence",
		]);
	});

	it("lets planning start without a data source and completes it only with a subject domain", () => {
		const idle = resolveDataProductJourneyStageState("planning", {});
		const confirmed = resolveDataProductJourneyStageState("planning", { domainId: "d-1" });

		expect(idle.status).toBe("ready");
		expect(idle.blocker).toBeUndefined();
		expect(idle.gap).toContain("尚未确认主题域");
		expect(confirmed.status).toBe("done");
	});

	it("does not block standards or integration on a missing upstream data source", () => {
		expect(resolveDataProductJourneyStageState("standards", {}).blocker).toBeUndefined();
		expect(resolveDataProductJourneyStageState("integration", {}).blocker).toBeUndefined();
		expect(resolveDataProductJourneyStageState("modeling", {}).blocker?.reason).toContain("缺少");
	});
});
