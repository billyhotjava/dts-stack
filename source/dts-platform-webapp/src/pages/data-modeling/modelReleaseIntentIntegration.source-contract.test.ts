import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), "utf8");

describe("data-modeling release and materialization intents", () => {
	it("uses canonical build and publication intents and never calls legacy lifecycle or dbt run routes", () => {
		const dialogs = read("./components/ModelingDialogs.tsx");
		expect(dialogs).toContain("startModelBuildIntent");
		expect(dialogs).toContain("startModelPublicationIntent");
		expect(dialogs).toContain("getReleaseCandidateWorkbench");
		expect(dialogs).not.toContain("BackendPendingButton");
		expect(dialogs).not.toMatch(/submitModelReview|publishModelLifecycle|runModelLifecycle/);
		expect(dialogs).not.toMatch(/\/etl\/dbt\/run|\/api\/etl\/dbt\/preview/);
	});

	it("does not render a simulated target, task, or success state", () => {
		const dialogs = read("./components/ModelingDialogs.tsx");
		expect(dialogs).not.toMatch(/dts_demo|materialize_\$\{selectionCode\}|后台重构完成后/);
		expect(dialogs).toContain("candidate.status");
		expect(dialogs).toContain("nextHumanAction");
	});
});
