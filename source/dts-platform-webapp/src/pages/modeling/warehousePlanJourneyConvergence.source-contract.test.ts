import assert from "node:assert/strict";
import { globSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import test from "node:test";
import { buildJourneyUrl, JOURNEY_CONTEXT_PARAM_KEYS } from "../../components/journey/journeyContext.ts";
import { resolveBusinessModelingContext } from "./businessModelingContext.ts";

const SRC_ROOT = fileURLToPath(new URL("../../", import.meta.url));
const journeyContextSource = readFileSync(new URL("../../components/journey/journeyContext.ts", import.meta.url), "utf8");
const businessContextSource = readFileSync(new URL("./businessModelingContext.ts", import.meta.url), "utf8");
const modelingContextSource = readFileSync(new URL("./modelingJourneyContext.ts", import.meta.url), "utf8");
const modelingWorkbenchSource = readFileSync(new URL("./ModelingWorkbenchPage.tsx", import.meta.url), "utf8");
const personalWorkbenchSource = readFileSync(new URL("../workbench/index.tsx", import.meta.url), "utf8");

const productionConsumers = (moduleName: string) =>
	globSync("**/*.{ts,tsx}", { cwd: SRC_ROOT })
		.filter((file) => !file.includes(".test.") && !file.includes(".source-contract."))
		.filter((file) => {
			const source = readFileSync(new URL(`../../${file}`, import.meta.url), "utf8");
			return new RegExp(`from\\s+["'][^"']*${moduleName}["']`).test(source);
		})
		.sort();

test("canonical planId is preserved by the cross-module journey context", () => {
	assert.ok(JOURNEY_CONTEXT_PARAM_KEYS.includes("planId"));
	const route = buildJourneyUrl("/ops/instances", new URLSearchParams({ planId: "plan-65" }));
	assert.equal(new URL(route, "http://dts.local").searchParams.get("planId"), "plan-65");
	assert.match(journeyContextSource, /planningId.*@deprecated|@deprecated.*planningId/s);
});

test("legacy planning parameters only map into canonical planId", () => {
	const context = resolveBusinessModelingContext(new URLSearchParams({ planningId: "legacy-plan" }));
	assert.equal(context.planId, "legacy-plan");
	assert.match(businessContextSource, /@deprecated/);
	assert.match(modelingContextSource, /planId/);
});

test("the canonical modeling workbench does not consume old frontend completion state", () => {
	assert.doesNotMatch(modelingWorkbenchSource, /resolveDataProductJourneyStageStates|gateEvidence|journeySnapshot/);
	assert.match(modelingWorkbenchSource, /getWarehousePlanStageProjection/);
});

test("the personal workbench data-management entry converges on the canonical plan workbench", () => {
	assert.match(personalWorkbenchSource, /import ModelingWorkbenchPage/);
	assert.match(personalWorkbenchSource, /activeSection === "data-management"[\s\S]*return <ModelingWorkbenchPage/);
	assert.doesNotMatch(
		personalWorkbenchSource,
		/activeSection === "data-management"[\s\S]{0,240}<DataManagementWorkbenchPage/,
	);
});

test("legacy context consumer sets are frozen during controlled retirement", () => {
	assert.deepEqual(productionConsumers("warehousePlanningContext"), [
		"pages/governance/ElementsPage.tsx",
		"pages/governance/SubjectAreasPage.tsx",
		"pages/modeling/LowCodeDevelopmentPage.tsx",
		"pages/modeling/SemanticModelsPage.tsx",
		"pages/modeling/SemanticObjectsPage.tsx",
		"pages/modeling/SqlModelingPage.tsx",
		"pages/modeling/businessModelingContext.ts",
		"pages/modeling/dimensionCandidateGate.ts",
	]);
	assert.deepEqual(productionConsumers("businessModelingContext"), [
		"pages/governance/SubjectAreasPage.tsx",
		"pages/modeling/SemanticModelsPage.tsx",
		"pages/modeling/SemanticObjectsPage.tsx",
		"pages/modeling/modelingJourneyContext.ts",
		"pages/modeling/semantic-workspace/BusinessModelingContextBar.tsx",
		"pages/modeling/semantic-workspace/ConformedDimensionRecommendations.tsx",
		"pages/modeling/semantic-workspace/ModelingConceptCards.tsx",
		"pages/modeling/semantic-workspace/SemanticWorkspaceFrame.tsx",
	]);
	assert.deepEqual(productionConsumers("modelingJourneyContext"), [
		"pages/governance/SubjectAreasPage.tsx",
		"pages/modeling/semantic-workspace/SemanticWorkspaceFrame.tsx",
	]);
});
