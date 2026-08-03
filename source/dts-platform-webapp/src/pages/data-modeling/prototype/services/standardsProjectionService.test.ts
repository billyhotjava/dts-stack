import { describe, expect, it, vi } from "vitest";
import { archiveStandardsRow, type StandardsRow, standardsCapability } from "./standardsProjectionService";

const apiMocks = vi.hoisted(() => ({
	archiveStandard: vi.fn(),
	updateReferenceCode: vi.fn(),
}));

vi.mock("@/api/modelingStandardsApi", () => ({
	archiveStandard: apiMocks.archiveStandard,
	createGlossaryTerm: vi.fn(),
	createReferenceCode: vi.fn(),
	createStandard: vi.fn(),
	listGlossaryTerms: vi.fn(),
	listMetadataStandards: vi.fn(),
	listReferenceCodes: vi.fn(),
	listStandards: vi.fn(),
	updateGlossaryTerm: vi.fn(),
	updateReferenceCode: apiMocks.updateReferenceCode,
	updateStandard: vi.fn(),
}));

const glossaryRow: StandardsRow = {
	id: "term-1",
	code: "BUDGET",
	name: "预算",
	dataType: "—",
	definition: "预算术语",
	domain: "FIN",
	scope: "—",
	version: "v1",
	state: "草稿",
	valueCount: "—",
	source: {},
};

describe("standards archive safety", () => {
	it("does not advertise archive when the glossary owner only exposes permanent delete", () => {
		const capability = standardsCapability("dictionary");
		expect(capability.archive).toBe(false);
		expect(capability.archiveDisabledReason).toContain("永久删除");
	});

	it("fails closed instead of deleting a glossary term", async () => {
		await expect(archiveStandardsRow("dictionary", glossaryRow)).rejects.toThrow("禁止执行永久删除");
		expect(apiMocks.archiveStandard).not.toHaveBeenCalled();
		expect(apiMocks.updateReferenceCode).not.toHaveBeenCalled();
	});
});
