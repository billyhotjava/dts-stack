import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const validationUrl = new URL("./journeyArtifactValidation.ts", import.meta.url);
const indexUrl = new URL("./index.ts", import.meta.url);
const behaviorTestUrl = new URL("./journeyArtifactValidation.test.ts", import.meta.url);

test("artifact validation defines the three-state trust contract", () => {
	assert.equal(existsSync(validationUrl), true, `${validationUrl.pathname} should exist`);
	const source = readFileSync(validationUrl, "utf8");

	assert.match(source, /"valid" \| "invalid" \| "unknown"/);
	assert.match(source, /ArtifactValidationResult/);
	assert.match(source, /ArtifactValidator/);
	for (const fn of ["createDataProductArtifactValidator", "resolveArtifactValidations", "toArtifactValidationMap"]) {
		assert.match(source, new RegExp(`export const ${fn}`));
	}
});

test("artifact validation marks backend api gaps for every journey param", () => {
	const source = readFileSync(validationUrl, "utf8");

	assert.match(source, /ARTIFACT_VALIDATION_API_NAMES/);
	for (const key of ["sourceId", "standardDraftId", "modelId", "metricId", "serviceId", "runId", "auditId"]) {
		assert.match(source, new RegExp(`${key}: "GET /api/`));
	}
});

test("artifact validation supports an injectable standard draft lookup and never throws", () => {
	const source = readFileSync(validationUrl, "utf8");

	assert.match(source, /StandardDraftLookup/);
	assert.match(source, /findDraft/);
	assert.match(source, /isBackendDraftId/);
	assert.match(source, /catch/);
	assert.doesNotMatch(source, /console\.(log|error|warn)/);
});

test("artifact validation is exported from the barrel and has behavior tests", () => {
	const indexSource = readFileSync(indexUrl, "utf8");
	assert.match(indexSource, /journeyArtifactValidation/);
	for (const fn of ["createDataProductArtifactValidator", "resolveArtifactValidations", "toArtifactValidationMap"]) {
		assert.match(indexSource, new RegExp(fn));
	}
	assert.equal(existsSync(behaviorTestUrl), true, "journeyArtifactValidation.test.ts (vitest) should exist");
});

test("workbench and low-code page wire the real standard draft lookup into validations", () => {
	const workbenchSource = readFileSync(
		new URL("../../pages/workbench/DataManagementWorkbenchPage.tsx", import.meta.url),
		"utf8",
	);
	assert.match(workbenchSource, /createDataProductArtifactValidator/);
	assert.match(workbenchSource, /resolveArtifactValidations/);
	assert.match(workbenchSource, /getStandardBindingDraft/);
	assert.match(workbenchSource, /isBackendStandardBindingDraftId/);
	assert.match(workbenchSource, /-unverified/);
	assert.match(workbenchSource, /清除无效参数/);

	const lowCodeSource = readFileSync(
		new URL("../../pages/modeling/LowCodeDevelopmentPage.tsx", import.meta.url),
		"utf8",
	);
	assert.match(lowCodeSource, /validations=\{resolveArtifactValidations/);
});
