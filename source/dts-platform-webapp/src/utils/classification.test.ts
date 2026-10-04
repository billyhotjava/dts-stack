import assert from "node:assert/strict";
import test from "node:test";
import { DATA_SECURITY_LEVEL_OPTIONS } from "../constants/governance.ts";
import { classificationRank, normalizeClassification } from "./classification.ts";

test("normalizeClassification preserves an explicitly optional missing value", () => {
	assert.equal(normalizeClassification(undefined, undefined), undefined);
	assert.equal(normalizeClassification("UNKNOWN_LEVEL", undefined), undefined);
});

test("normalizeClassification keeps the default INTERNAL fallback when no fallback is supplied", () => {
	assert.equal(normalizeClassification(undefined), "INTERNAL");
});

test("normalizeClassification collapses the legacy SENSITIVE token onto SECRET", () => {
	assert.equal(normalizeClassification("SENSITIVE"), "SECRET");
	assert.equal(normalizeClassification("DATA_SENSITIVE"), "SECRET");
	assert.equal(normalizeClassification("敏感"), "SECRET");
});

test("classificationRank keeps CONFIDENTIAL above SECRET", () => {
	assert.equal(classificationRank("PUBLIC"), 0);
	assert.equal(classificationRank("INTERNAL"), 1);
	assert.equal(classificationRank("SECRET"), 2);
	assert.equal(classificationRank("CONFIDENTIAL"), 3);
	assert.ok((classificationRank("CONFIDENTIAL") ?? -1) > (classificationRank("SECRET") ?? -1));
	assert.equal(classificationRank("DATA_SENSITIVE"), 2);
});

test("the publish dropdown offers exactly the four canonical levels", () => {
	assert.deepEqual(DATA_SECURITY_LEVEL_OPTIONS, [
		{ value: "DATA_PUBLIC", label: "公开" },
		{ value: "DATA_INTERNAL", label: "内部" },
		{ value: "DATA_SECRET", label: "秘密" },
		{ value: "DATA_CONFIDENTIAL", label: "机密" },
	]);
});

test("retired ad-hoc levels are absent from the authoritative options", () => {
	const values = DATA_SECURITY_LEVEL_OPTIONS.map((option) => option.value);
	assert.ok(!values.includes("DATA_SENSITIVE"));
	assert.ok(!values.includes("DATA_RESTRICTED"));
});
