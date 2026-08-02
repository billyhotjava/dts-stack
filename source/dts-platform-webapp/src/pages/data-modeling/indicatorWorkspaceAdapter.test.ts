// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
	archiveIndicator: vi.fn(),
	createIndicator: vi.fn(),
	getIndicator: vi.fn(),
	getIndicatorPublishPreview: vi.fn(),
	listIndicatorReferences: vi.fn(),
	listIndicators: vi.fn(),
	listIndicatorVersions: vi.fn(),
	publishIndicator: vi.fn(),
	publishIndicatorRevision: vi.fn(),
	updateIndicator: vi.fn(),
	validateIndicator: vi.fn(),
	validateIndicatorDerivation: vi.fn(),
}));

vi.mock("@/api/services/indicatorGovernanceService", () => api);

import {
	archiveIndicatorDraft,
	classifyIndicator,
	createIndicatorDraft,
	filterIndicatorCatalog,
	loadAllIndicators,
	normalizeIndicatorError,
	publishIndicatorDraft,
	saveIndicatorDraft,
} from "./indicatorWorkspaceAdapter";

describe("indicator workspace adapter", () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it("loads every real indicator page without inventing catalog entries", async () => {
		const fetchPage = vi
			.fn()
			.mockResolvedValueOnce({
				content: [{ id: "a", code: "AMOUNT", name: "金额", isDerived: false }],
				totalPages: 2,
			})
			.mockResolvedValueOnce({
				content: [{ id: "b", code: "RATE", name: "比率", isDerived: true }],
				totalPages: 2,
			});

		await expect(loadAllIndicators(fetchPage, { pageSize: 1 })).resolves.toEqual([
			{ id: "a", code: "AMOUNT", name: "金额", isDerived: false },
			{ id: "b", code: "RATE", name: "比率", isDerived: true },
		]);
		expect(fetchPage).toHaveBeenNthCalledWith(1, { page: 0, size: 1 });
		expect(fetchPage).toHaveBeenNthCalledWith(2, { page: 1, size: 1 });
	});

	it("classifies only persisted governance indicators into the prototype views", () => {
		expect(classifyIndicator({ isDerived: false })).toBe("原子指标");
		expect(classifyIndicator({ isDerived: true })).toBe("派生指标");
		expect(classifyIndicator({ isDerived: true, category: "COMPOSITE" })).toBe("复合指标");
		expect(classifyIndicator({ category: "MODIFIER" })).toBe("修饰词");
		expect(classifyIndicator({ category: "TIME_PERIOD" })).toBe("时间周期");
	});

	it("filters by view, domain and keyword while keeping server records intact", () => {
		const rows = [
			{ id: "a", code: "AMOUNT", name: "预算金额", domain: "FIN", isDerived: false },
			{ id: "b", code: "RATE", name: "预算执行率", domain: "FIN", isDerived: true },
			{ id: "c", code: "COUNT", name: "项目数", domain: "PROJECT", isDerived: false },
		];

		expect(filterIndicatorCatalog(rows, { type: "原子指标", domain: "FIN", query: "预算" })).toEqual([rows[0]]);
	});

	it("creates blank canonical drafts and never embeds a customer example", () => {
		expect(createIndicatorDraft("原子指标", "FIN")).toMatchObject({
			code: "",
			name: "",
			domain: "FIN",
			status: "DRAFT",
			isDerived: false,
		});
		expect(createIndicatorDraft("复合指标", null)).toMatchObject({
			category: "COMPOSITE",
			isDerived: true,
		});
	});

	it("distinguishes permission failures from retryable request failures", () => {
		expect(normalizeIndicatorError({ response: { status: 403 }, message: "Forbidden" })).toEqual({
			kind: "permission",
			message: "当前账号无权访问或维护指标，请联系管理员授权",
		});
		expect(normalizeIndicatorError(new Error("network down"))).toEqual({
			kind: "request",
			message: "network down",
		});
	});

	it("creates a canonical draft and trusts the service response", async () => {
		api.createIndicator.mockResolvedValue({
			id: "metric-1",
			code: "AMOUNT",
			name: "金额",
			status: "DRAFT",
			version: "v1",
			isDerived: false,
			aggregationType: "SUM",
			measureField: "amount",
		});
		const baseline = createIndicatorDraft("原子指标", null);

		const saved = await saveIndicatorDraft(baseline, {
			code: "AMOUNT",
			name: "金额",
			isDerived: false,
			aggregationType: "SUM",
			measureField: "amount",
		});

		expect(saved).toMatchObject({ id: "metric-1", status: "DRAFT", version: "v1" });
		expect(api.createIndicator).toHaveBeenCalledOnce();
	});

	it("updates and publishes a draft through CAS, preview and canonical publish", async () => {
		const baseline = {
			id: "metric-1",
			code: "AMOUNT",
			name: "金额",
			domain: "",
			status: "DRAFT",
			version: "v1",
			lastModifiedDate: "2026-08-02T00:00:00Z",
			isDerived: false,
			aggregationType: "SUM",
			measureField: "amount",
		};
		api.getIndicator.mockResolvedValue(baseline);
		api.listIndicatorVersions.mockResolvedValue([{ version: "v1" }]);
		api.updateIndicator.mockResolvedValue({ ...baseline, name: "含税金额" });
		api.getIndicatorPublishPreview.mockResolvedValue({ readyToPublish: true, blockingIssues: [] });
		api.publishIndicator.mockResolvedValue({ ...baseline, name: "含税金额", status: "PUBLISHED" });

		const published = await publishIndicatorDraft(baseline, {
			name: "含税金额",
			isDerived: false,
			aggregationType: "SUM",
			measureField: "amount",
		});

		expect(api.updateIndicator).toHaveBeenCalledWith(
			"metric-1",
			expect.objectContaining({ expectedLastModifiedDate: "2026-08-02T00:00:00Z", name: "含税金额" }),
		);
		expect(api.getIndicatorPublishPreview).toHaveBeenCalledWith("metric-1");
		expect(api.publishIndicator).toHaveBeenCalledWith("metric-1");
		expect(published.status).toBe("PUBLISHED");
	});

	it("publishes revisions and archives through the existing lifecycle endpoints", async () => {
		const baseline = {
			id: "metric-1",
			code: "RATE",
			name: "比率",
			domain: "",
			status: "PUBLISHED",
			version: "v2",
			lastModifiedDate: "2026-08-02T00:00:00Z",
			isDerived: true,
			dependencyIndicators: '["AMOUNT"]',
			expressionSql: "{{metric:AMOUNT}}",
		};
		api.getIndicator.mockResolvedValue(baseline);
		api.listIndicatorVersions.mockResolvedValue([{ version: "v2" }]);
		api.publishIndicatorRevision.mockResolvedValue({ ...baseline, status: "PUBLISHED", version: "v3" });
		api.archiveIndicator.mockResolvedValue({ ...baseline, status: "ARCHIVED", version: "v3" });

		await expect(
			publishIndicatorDraft(baseline, {
				name: "新比率",
				isDerived: true,
				dependencyCodes: ["AMOUNT"],
				expressionSql: "{{metric:AMOUNT}}",
			}),
		).resolves.toMatchObject({ status: "PUBLISHED", version: "v3" });
		expect(api.publishIndicatorRevision).toHaveBeenCalledOnce();
		await expect(archiveIndicatorDraft(baseline)).resolves.toMatchObject({ status: "ARCHIVED" });
		expect(api.archiveIndicator).toHaveBeenCalledWith("metric-1");
	});
});
