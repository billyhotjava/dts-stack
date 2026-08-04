import assert from "node:assert/strict";
import test from "node:test";
import { normalizeClassification } from "./classification.ts";

test("normalizeClassification preserves an explicitly optional missing value", () => {
	assert.equal(normalizeClassification(undefined, undefined), undefined);
	assert.equal(normalizeClassification("UNKNOWN_LEVEL", undefined), undefined);
});

test("normalizeClassification keeps the default INTERNAL fallback when no fallback is supplied", () => {
	assert.equal(normalizeClassification(undefined), "INTERNAL");
});
