import { describe, expect, it } from "vitest";
import {
	dataArchitecturePath,
	legacyPlanningArchitectureTarget,
	legacySubjectAreasTarget,
	resolveDataArchitectureView,
} from "./navigation";

describe("data architecture navigation", () => {
	it("uses one canonical route and a stable default view", () => {
		expect(resolveDataArchitectureView("layers")).toBe("layers");
		expect(resolveDataArchitectureView("unknown")).toBe("business-domains");
		expect(dataArchitecturePath("subjects", "subject-id")).toBe("/data-architecture?view=subjects&active=subject-id");
	});

	it("redirects the retired modeling-space route to the canonical warehouse-planning owner", () => {
		expect(legacyPlanningArchitectureTarget("/data-modeling/planning/spaces", "?planId=plan-id", "#scope")).toBe(
			"/data-architecture?planId=plan-id&view=business-domains#scope",
		);
	});

	it("maps every legacy planning dictionary route without losing query or hash", () => {
		expect(
			legacyPlanningArchitectureTarget(
				"/data-modeling/planning/processes",
				"?domain=domain-id&returnPlanId=plan-id",
				"#processes",
			),
		).toBe("/data-architecture?domain=domain-id&returnPlanId=plan-id&view=processes&active=domain-id#processes");
		expect(legacyPlanningArchitectureTarget("/data-modeling/planning/system", "?keep=1", "#system")).toBeNull();
	});

	it("maps all subject workspace tabs to their unique owners", () => {
		expect(legacySubjectAreasTarget("?tab=data-marts&active=category-id&returnPlanId=plan-id", "#marts")).toBe(
			"/data-architecture?tab=data-marts&active=category-id&returnPlanId=plan-id&view=marts&businessCategoryId=category-id#marts",
		);
		expect(legacySubjectAreasTarget("?tab=details&active=category-id")).toContain(
			"/data-architecture?tab=details&active=category-id&view=subjects",
		);
		expect(legacySubjectAreasTarget("?tab=governance&active=domain-id", "#assets")).toBe(
			"/catalog/assets?tab=governance&active=domain-id&domain=domain-id#assets",
		);
		expect(legacySubjectAreasTarget("?tab=scope&active=domain-id&returnPlanId=plan-id", "#scope")).toBe(
			"/data-architecture?tab=scope&active=domain-id&returnPlanId=plan-id&view=business-domains#scope",
		);
	});
});
