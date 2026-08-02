import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), "utf8");

describe("Sprint-83 model representation integration", () => {
	it("uses the revision-pinned representation API as the model-detail source", () => {
		const api = read("../../api/modelRepresentationApi.ts");
		const workspace = read("./pages/DimensionalModelingWorkspace.tsx");

		expect(api).toContain("/representations");
		expect(api).toContain("modelRevision");
		expect(api).toContain("implementationRevision");
		expect(api).toContain("representationScope");
		expect(workspace).toContain("ModelDetailWorkbench");
		expect(workspace).toContain("listModelSpecs");
		expect(workspace).not.toContain("DOMAIN_CATALOG");
	});

	it("keeps SQL and dbt source text out of the business visualization component", () => {
		const businessView = read("./components/model-detail/BusinessModelVisualization.tsx");
		const advancedView = read("./components/model-detail/AdvancedDbtImplementationView.tsx");

		expect(businessView).not.toMatch(/technicalImplementation|compiledSql|rawSql|jinja|projectPath/i);
		expect(businessView).toContain("logicalModel");
		expect(businessView).toContain("dependencyProjection");
		expect(advancedView).toContain("technicalImplementation");
	});

	it("does not use legacy dbt files or preview endpoints", () => {
		const api = read("../../api/modelRepresentationApi.ts");
		const workbench = read("./components/model-detail/ModelDetailWorkbench.tsx");
		const source = `${api}\n${workbench}`;

		expect(source).not.toContain("/api/etl/dbt/files");
		expect(source).not.toContain("/api/etl/dbt/preview");
		expect(source).not.toContain("DISCOVERED_MODELS");
		expect(workbench).toContain("useCatalogMaintainerAccess");
	});
});
