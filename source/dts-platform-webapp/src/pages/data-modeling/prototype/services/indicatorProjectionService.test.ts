import { beforeEach, describe, expect, it, vi } from "vitest";
import {
	classifyIndicator,
	createIndicatorDraft,
	filterIndicators,
	type MetricSelection,
	saveAndValidateIndicatorDraft,
	supportsIndicatorCreation,
} from "./indicatorProjectionService";

const apiMocks = vi.hoisted(() => ({
	getIndicator: vi.fn(),
	listVersions: vi.fn(),
	runPreflight: vi.fn(),
	updateIndicator: vi.fn(),
}));

vi.mock("@/api/services/indicatorGovernanceService", () => ({
	archiveIndicator: vi.fn(),
	createIndicator: vi.fn(),
	getIndicator: apiMocks.getIndicator,
	getIndicatorPublishPreview: vi.fn(),
	listIndicators: vi.fn(),
	listIndicatorVersions: apiMocks.listVersions,
	publishIndicator: vi.fn(),
	publishIndicatorRevision: vi.fn(),
	updateIndicator: apiMocks.updateIndicator,
	validateIndicator: vi.fn(),
	validateIndicatorDerivation: vi.fn(),
}));

vi.mock("@/features/modeling/indicators/indicatorDefinitionWorkflow", () => ({
	publishIndicatorWithPreview: vi.fn(),
	runIndicatorPreflight: apiMocks.runPreflight,
}));

const baseline: MetricSelection = {
	id: "indicator-1",
	code: "BUDGET_AMOUNT",
	name: "预算金额",
	domain: "FIN",
	definition: "预算金额",
	status: "DRAFT",
	version: "v1",
	isDerived: false,
	aggregationType: "SUM",
	measureField: "amount",
	lastModifiedDate: "2026-08-03T10:00:00Z",
};

beforeEach(() => Object.values(apiMocks).forEach((mock) => mock.mockReset()));

describe("indicator draft validation", () => {
	it("persists the current form and validates the returned revision", async () => {
		const saved = { ...baseline, name: "预算总额", lastModifiedDate: "2026-08-03T10:01:00Z" };
		apiMocks.getIndicator.mockResolvedValue(baseline);
		apiMocks.listVersions.mockResolvedValue([{ version: "v1" }]);
		apiMocks.updateIndicator.mockResolvedValue(saved);
		apiMocks.runPreflight.mockResolvedValue({ valid: true, issues: [] });

		const result = await saveAndValidateIndicatorDraft(baseline, { name: "预算总额" });

		expect(apiMocks.updateIndicator).toHaveBeenCalledWith(
			baseline.id,
			expect.objectContaining({ name: "预算总额", expectedLastModifiedDate: baseline.lastModifiedDate }),
		);
		expect(apiMocks.runPreflight).toHaveBeenCalledWith(saved, expect.any(Object));
		expect(result.saved.name).toBe("预算总额");
	});

	it("offers stable-context draft creation for the three governed metric types", () => {
		expect(supportsIndicatorCreation("原子指标")).toBe(true);
		expect(supportsIndicatorCreation("派生指标")).toBe(true);
		expect(supportsIndicatorCreation("复合指标")).toBe(true);
		expect(createIndicatorDraft("原子指标").metricType).toBe("ATOMIC");
		expect(createIndicatorDraft("派生指标").metricType).toBe("DERIVED");
		expect(createIndicatorDraft("复合指标").metricType).toBe("COMPOSITE");
	});

	it("classifies and filters by stable metric and data-domain ids before legacy text", () => {
		const rows = [
			{ code: "ATOMIC_1", metricType: "ATOMIC", dataDomainId: "domain-a", domain: "legacy-a" },
			{ code: "DERIVED_1", metricType: "DERIVED", dataDomainId: "domain-b", domain: "legacy-b" },
		];

		expect(classifyIndicator(rows[1])).toBe("派生指标");
		expect(filterIndicators(rows, { type: "派生指标", domain: "domain-b", query: "" })).toEqual([rows[1]]);
	});
});
