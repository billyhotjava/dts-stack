import assert from "node:assert/strict";
import test from "node:test";
import {
	resolveReleaseSubmitOutcome,
	type ReleaseSubmitOutcome,
} from "./sqlModelReleaseSubmit.helpers";

test("resolveReleaseSubmitOutcome returns blocked when backend blocks release", () => {
	const result = resolveReleaseSubmitOutcome({ status: "BLOCKED", blocking: true });
	assert.equal(result, "blocked" satisfies ReleaseSubmitOutcome);
});

test("resolveReleaseSubmitOutcome returns warning when backend requests confirmation", () => {
	const result = resolveReleaseSubmitOutcome({ status: "WARNING", warning: true });
	assert.equal(result, "warning" satisfies ReleaseSubmitOutcome);
});

test("resolveReleaseSubmitOutcome returns submitted for successful submission", () => {
	const result = resolveReleaseSubmitOutcome({ status: "SUBMITTED", dagRunId: "run-1" });
	assert.equal(result, "submitted" satisfies ReleaseSubmitOutcome);
});
