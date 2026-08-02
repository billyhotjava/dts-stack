import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), "utf8");

describe("Sprint-83 artifact-rich dbt reverse modeling", () => {
	it("uses the real inspect proof preview apply and retry control plane", () => {
		const source = read("./components/ReverseModelingWizard.tsx");

		for (const operation of [
			"inspectDbtModelArchive",
			"previewModelSpecImport",
			"applyModelSpecImport",
			"retryModelSpecImport",
			"getModelSpecImportPreviewRun",
			"getModelSpecImportApplyResult",
			"forwardUndoModelSpecImport",
		]) {
			expect(source).toContain(operation);
		}
		expect(source).toContain("inspection.inspectionProof");
		expect(source).toContain("semanticOverrides");
		expect(source).toContain("renameMappings");
		expect(source).toContain("conflictResolutions");
		expect(source).toContain("listWarehousePlans");
	});

	it("removes the hardcoded database-table prototype and backend-pending action", () => {
		const source = read("./components/ReverseModelingWizard.tsx");

		expect(source).not.toContain("DISCOVERED_MODELS");
		expect(source).not.toContain("BackendPendingButton");
		expect(source).not.toContain("ods_budget_v2");
		expect(source).toContain('accept=".zip,application/zip"');
	});

	it("renders canonical apply counts without legacy status aliases", () => {
		const source = read("./components/ReverseModelingWizard.tsx");

		expect(source).toContain("applyResult.summary.selected");
		expect(source).toContain("applyResult.summary.pending");
		expect(source).toContain("applyResult.summary.succeeded");
		expect(source).not.toMatch(/SUCCEEDED|REPLAYED|summary\.replayed/);
		expect(source).toContain("最新尝试状态");
		expect(source).toContain("整体导入状态");
		expect(source).toContain("applyResult.overallRun.items");
	});

	it("uses persisted failure facts for diagnostics and retry eligibility", () => {
		const source = read("./components/ReverseModelingWizard.tsx");

		for (const persistedFailureFact of [
			"issue.retryable",
			"issue.stage",
			"issue.category",
			"issue.correlationId",
			"issue.dependencyUniqueId",
		]) {
			expect(source).toContain(persistedFailureFact);
		}
		expect(source).not.toContain("applyResult.summary.failed + applyResult.summary.blocked > 0");
	});

	it("lets operators recover a persisted run without inventing a local history ledger", () => {
		const source = read("./components/ReverseModelingWizard.tsx");

		expect(source).toContain("resumeRunId");
		expect(source).toContain('aria-label="导入运行 ID"');
		expect(source).toContain("preview.runId");
		expect(source).not.toContain("IMPORT_HISTORY");
	});
});
