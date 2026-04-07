import assert from "node:assert/strict";
import test from "node:test";
import { resolveReleaseSubmitOutcome } from "./sqlModelReleaseSubmit.helpers.ts";

test("resolveReleaseSubmitOutcome returns blocked for BLOCKED status", () => {
	assert.equal(resolveReleaseSubmitOutcome({ status: "BLOCKED" }), "blocked");
});

test("resolveReleaseSubmitOutcome returns blocked when blocking flag is true", () => {
	assert.equal(resolveReleaseSubmitOutcome({ blocking: true }), "blocked");
});

test("resolveReleaseSubmitOutcome returns warning for WARNING status", () => {
	assert.equal(resolveReleaseSubmitOutcome({ status: "WARNING" }), "warning");
});

test("resolveReleaseSubmitOutcome returns warning when warning flag is true", () => {
	assert.equal(resolveReleaseSubmitOutcome({ warning: true }), "warning");
});

test("resolveReleaseSubmitOutcome returns submitted for SUBMITTED status", () => {
	assert.equal(resolveReleaseSubmitOutcome({ status: "SUBMITTED" }), "submitted");
});

test("resolveReleaseSubmitOutcome returns submitted for null input", () => {
	assert.equal(resolveReleaseSubmitOutcome(null), "submitted");
});

test("resolveReleaseSubmitOutcome returns submitted for undefined input", () => {
	assert.equal(resolveReleaseSubmitOutcome(undefined), "submitted");
});

test("resolveReleaseSubmitOutcome prefers blocked over warning when both flags set", () => {
	assert.equal(
		resolveReleaseSubmitOutcome({ status: "BLOCKED", warning: true, blocking: true }),
		"blocked",
	);
});
