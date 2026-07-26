import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const RULES_PAGE = readFileSync(new URL("./QualityRulesPage.tsx", import.meta.url), "utf8");
const CREATE_WIZARD = readFileSync(new URL("./components/RuleCreateWizard.tsx", import.meta.url), "utf8");

const DATASET_BINDING_PAYLOAD =
	/bindings:\s*values\.datasetId\s*\?\s*\[\{\s*datasetId:\s*values\.datasetId,\s*scopeType:\s*"DATASET"\s*\}\]\s*:\s*\[\]/;

test("quality rule creation persists the selected dataset as a version binding", () => {
	assert.match(CREATE_WIZARD, DATASET_BINDING_PAYLOAD);
});

test("quality rule editing persists the selected dataset as a version binding", () => {
	assert.match(RULES_PAGE, DATASET_BINDING_PAYLOAD);
});
