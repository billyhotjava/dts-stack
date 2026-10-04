import { beforeEach, describe, expect, it, vi } from "vitest";
import {
	classifyIndicator,
	createIndicatorDraft,
	filterIndicators,
	type MetricSelection,
	publishIndicatorDraft,
	saveAndValidateIndicatorDraft,
	saveIndicatorDraft,
	supportsIndicatorCreation,
} from "./indicatorProjectionService";

const apiMocks = vi.hoisted(() => ({
	getIndicator: vi.fn(),
	createIndicator: vi.fn(),
	createModelFieldIndicatorDraft: vi.fn(),
	rebindModelFieldIndicatorDraft: vi.fn(),
	getModelSpecRevision: vi.fn(),
	listModelSpecs: vi.fn(),
	listVersions: vi.fn(),
	runPreflight: vi.fn(),
	publishWithPreview: vi.fn(),
	updateIndicator: vi.fn(),
}));

vi.mock("@/api/services/indicatorGovernanceService", () => ({
	archiveIndicator: vi.fn(),
	calculateIndicators: vi.fn(),
	createIndicator: apiMocks.createIndicator,
	createModelFieldIndicatorDraft: apiMocks.createModelFieldIndicatorDraft,
	rebindModelFieldIndicatorDraft: apiMocks.rebindModelFieldIndicatorDraft,
	getIndicator: apiMocks.getIndicator,
	getIndicatorDetail: vi.fn(),
	getIndicatorPublishPreview: vi.fn(),
	listIndicators: vi.fn(),
	listIndicatorVersions: apiMocks.listVersions,
	publishIndicator: vi.fn(),
	publishIndicatorRevision: vi.fn(),
	updateIndicator: apiMocks.updateIndicator,
	validateIndicator: vi.fn(),
	validateIndicatorDerivation: vi.fn(),
}));

vi.mock("@/api/modelSpecApi", () => ({
	getModelSpecRevision: apiMocks.getModelSpecRevision,
	listModelSpecs: apiMocks.listModelSpecs,
}));

vi.mock("@/features/modeling/indicators/indicatorDefinitionWorkflow", () => ({
	publishIndicatorWithPreview: apiMocks.publishWithPreview,
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
		expect(createIndicatorDraft("复合指标").category).toBeNull();
	});

	it("creates a modifier as a reusable scope definition instead of an atomic metric", async () => {
		expect(supportsIndicatorCreation("修饰词")).toBe(true);
		const draft = createIndicatorDraft("修饰词", "budget");
		expect(draft).toMatchObject({
			category: "MODIFIER",
			metricType: null,
			aggregationType: null,
			measureField: null,
			isDerived: false,
		});
		apiMocks.createIndicator.mockResolvedValue({
			...draft,
			id: "modifier-1",
			code: "CUMULATIVE",
			name: "累计",
			definition: "限定指标值为统计期内累计结果",
		});

		const saved = await saveIndicatorDraft(draft, {
			code: "CUMULATIVE",
			name: "累计",
			definition: "限定指标值为统计期内累计结果",
			businessCategoryId: "category-1",
			dataDomainId: "domain-1",
			category: "MODIFIER",
		});

		expect(apiMocks.createIndicator).toHaveBeenCalledWith(
			expect.objectContaining({
				category: "MODIFIER",
				metricType: null,
				aggregationType: null,
				measureField: null,
			}),
		);
		expect(apiMocks.createModelFieldIndicatorDraft).not.toHaveBeenCalled();
		expect(saved.id).toBe("modifier-1");
	});

	it("classifies and filters by stable metric and data-domain ids before legacy text", () => {
		const rows = [
			{ code: "ATOMIC_1", metricType: "ATOMIC", dataDomainId: "domain-a", domain: "legacy-a" },
			{ code: "DERIVED_1", metricType: "DERIVED", dataDomainId: "domain-b", domain: "legacy-b" },
		];

		expect(classifyIndicator(rows[1])).toBe("派生指标");
		expect(filterIndicators(rows, { type: "派生指标", domain: "domain-b", query: "" })).toEqual([rows[1]]);
	});

	it("creates a new atomic metric through the exact model-field boundary", async () => {
		const draft: MetricSelection = {
			code: "TASK_TOTAL",
			name: "任务总数",
			domain: "PJM",
			category: "研究所业务",
			businessCategoryId: "category-1",
			dataDomainId: "domain-1",
			businessProcessId: "process-1",
			metricType: "ATOMIC",
			aggregationType: "SUM",
			measureField: "task_total",
			sourceRefs: [{ sourceType: "SEMANTIC_MODEL_REVISION", sourceId: "model-1", sourceVersion: "r3" }],
			status: "DRAFT",
			version: "v1",
			isNew: true,
		};
		apiMocks.getModelSpecRevision.mockResolvedValue({
			id: "model-1",
			revision: 3,
			fields: [{ name: "task_total", role: "MEASURE" }],
			standardBindings: [{ fieldName: "task_total", measurementUnitId: "unit-1", measurementUnitVersion: 2 }],
		});
		apiMocks.createModelFieldIndicatorDraft.mockResolvedValue({ ...draft, id: "metric-1" });

		const saved = await saveIndicatorDraft(draft, draft);

		expect(apiMocks.createModelFieldIndicatorDraft).toHaveBeenCalledWith(
			expect.objectContaining({
				modelSpecId: "model-1",
				modelRevision: 3,
				fieldName: "task_total",
				measurementUnitId: "unit-1",
				measurementUnitVersion: 2,
			}),
		);
		expect(apiMocks.createIndicator).not.toHaveBeenCalled();
		expect(saved.id).toBe("metric-1");
	});

	it("creates a derived metric with semantic dependencies and a fixed implementation field", async () => {
		const draft: MetricSelection = {
			code: "TASK_RATE",
			name: "任务完成率",
			domain: "PJM",
			category: "研究所业务",
			businessCategoryId: "category-1",
			dataDomainId: "domain-1",
			metricType: "DERIVED",
			executionMode: "PRECOMPUTED",
			implementationRef: { modelSpecId: "model-2", modelRevision: 4, fieldName: "task_rate" },
			aggregationType: "DERIVED",
			measureField: "task_rate",
			targetModelName: "biz_ads_task_rate",
			expressionSql: "{{metric:TASK_DONE}} / nullif({{metric:TASK_TOTAL}}, 0)",
			dependencyIndicators: '["TASK_DONE","TASK_TOTAL"]',
			sourceRefs: [
				{ sourceType: "INDICATOR_VERSION", sourceId: "metric-1", sourceVersion: "v1" },
				{ sourceType: "INDICATOR_VERSION", sourceId: "metric-2", sourceVersion: "v1" },
			],
			status: "DRAFT",
			version: "v1",
			isNew: true,
		};
		apiMocks.getModelSpecRevision.mockResolvedValue(
			{
				id: "model-2",
				name: "biz_ads_task_rate",
				status: "PUBLISHED",
				revision: 4,
				fields: [{ name: "task_rate", role: "MEASURE" }],
				standardBindings: [],
			}
		);
		apiMocks.createModelFieldIndicatorDraft.mockResolvedValue({ ...draft, id: "metric-3" });

		await saveIndicatorDraft(draft, {
			...draft,
			dependencyCodes: ["TASK_DONE", "TASK_TOTAL"],
		});

		expect(apiMocks.createModelFieldIndicatorDraft).toHaveBeenCalledWith(
			expect.objectContaining({
				modelSpecId: "model-2",
				modelRevision: 4,
				fieldName: "task_rate",
				indicator: expect.objectContaining({
					metricType: "DERIVED",
					sourceRefs: draft.sourceRefs,
				}),
			}),
		);
	});

	it("rebinds an existing atomic draft through the transactional model-field boundary", async () => {
		const draft = {
			...baseline,
			businessCategoryId: "category-1",
			dataDomainId: "domain-1",
			businessProcessId: "process-1",
			metricType: "ATOMIC" as const,
			sourceRefs: [{ sourceType: "SEMANTIC_MODEL_REVISION" as const, sourceId: "model-1", sourceVersion: "r3" }],
		};
		apiMocks.getIndicator.mockResolvedValue(draft);
		apiMocks.listVersions.mockResolvedValue([{ version: "v1" }]);
		apiMocks.getModelSpecRevision.mockResolvedValue({
			id: "model-1",
			revision: 3,
			fields: [{ name: "amount", role: "MEASURE" }],
			standardBindings: [],
		});
		apiMocks.rebindModelFieldIndicatorDraft.mockResolvedValue(draft);

		await saveIndicatorDraft(draft, draft);

		expect(apiMocks.rebindModelFieldIndicatorDraft).toHaveBeenCalledWith(
			draft.id,
			expect.objectContaining({ modelSpecId: "model-1", modelRevision: 3, fieldName: "amount" }),
		);
		expect(apiMocks.updateIndicator).not.toHaveBeenCalled();
	});

	it("stages a model-field rebind before publishing an atomic revision", async () => {
		const published = {
			...baseline,
			status: "PUBLISHED",
			businessCategoryId: "category-1",
			dataDomainId: "domain-1",
			businessProcessId: "process-1",
			metricType: "ATOMIC" as const,
			sourceRefs: [{ sourceType: "SEMANTIC_MODEL_REVISION" as const, sourceId: "model-1", sourceVersion: "r3" }],
		};
		const staged = { ...published, status: "DRAFT", version: "v2" };
		apiMocks.getIndicator.mockResolvedValue(published);
		apiMocks.listVersions.mockResolvedValue([{ version: "v1" }]);
		apiMocks.getModelSpecRevision.mockResolvedValue({
			id: "model-1",
			revision: 3,
			fields: [{ name: "amount", role: "MEASURE" }],
			standardBindings: [],
		});
		apiMocks.rebindModelFieldIndicatorDraft.mockResolvedValue(staged);
		apiMocks.publishWithPreview.mockResolvedValue({ ...staged, status: "PUBLISHED" });

		const result = await publishIndicatorDraft(published, published);

		expect(apiMocks.rebindModelFieldIndicatorDraft).toHaveBeenCalledWith(published.id, expect.any(Object));
		expect(apiMocks.publishWithPreview).toHaveBeenCalledWith(published.id, expect.any(Object));
		expect(result.status).toBe("PUBLISHED");
	});
});
