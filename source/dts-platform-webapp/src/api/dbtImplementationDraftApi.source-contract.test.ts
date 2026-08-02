import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (path: string) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("advanced dbt implementation draft contract", () => {
	it("uses the isolated draft create/save/validate/commit API and never the legacy shared projectDir API", () => {
		const api = read("./dbtImplementationDraftApi.ts");

		expect(api).toContain("/dbt-drafts");
		expect(api).toContain("/files");
		expect(api).toContain("/validate");
		expect(api).toContain("/commit");
		expect(api).toContain("baseModelRevision");
		expect(api).toContain("baseImplementationRevision");
		expect(api).toContain("expectedEtag");
		expect(api).toContain("sourceBundle");
		expect(api).toContain("bundleChecksum");
		expect(api).toContain("projectChecksum");
		expect(api).toContain("byteSize");
		expect(api).toContain("checksum");
		expect(api).not.toContain("/api/etl/dbt/files");
		expect(api).not.toContain("/etl/dbt/files");
	});

	it("keeps maintainer-only editing in the existing workbench with dirty and conflict states", () => {
		const workbench = read("../pages/data-modeling/components/model-detail/ModelDetailWorkbench.tsx");
		const editor = read("../pages/data-modeling/components/model-detail/AdvancedDbtImplementationView.tsx");

		expect(workbench).toContain("useCatalogMaintainerAccess");
		expect(workbench).toContain('ownershipMode === "DBT_MANAGED"');
		expect(editor).toContain("dirty");
		expect(editor).toContain("conflict");
		expect(editor).toContain("validateDbtImplementationDraft");
		expect(editor).toContain("commitDbtImplementationDraft");
		expect(editor).toContain("created.sourceBundle.files");
		expect(editor).not.toContain("initialFiles");
		expect(editor).not.toContain('path: "dbt_project.yml"');
		expect(editor).not.toContain("model-paths: [models]");
		expect(`${workbench}\n${editor}`).not.toContain("/api/etl/dbt/files");
	});
});
