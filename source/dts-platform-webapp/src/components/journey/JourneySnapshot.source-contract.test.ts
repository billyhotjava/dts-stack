import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const snapshotUrl = new URL("./journeySnapshot.ts", import.meta.url);
const indexUrl = new URL("./index.ts", import.meta.url);
const behaviorTestUrl = new URL("./journeySnapshot.test.ts", import.meta.url);

test("journey snapshot module persists, restores and clears journey instances", () => {
	assert.equal(existsSync(snapshotUrl), true, `${snapshotUrl.pathname} should exist`);
	const source = readFileSync(snapshotUrl, "utf8");

	assert.match(source, /JOURNEY_SNAPSHOT_STORAGE_KEY/);
	assert.match(source, /JOURNEY_SNAPSHOT_VERSION/);
	assert.match(source, /JourneySnapshot = \{/);
	for (const field of ["version", "journey", "stage", "params", "savedAt"]) {
		assert.match(source, new RegExp(field));
	}
	for (const fn of [
		"createJourneySnapshot",
		"saveJourneySnapshot",
		"loadJourneySnapshot",
		"clearJourneySnapshot",
		"buildSnapshotResumeUrl",
	]) {
		assert.match(source, new RegExp(`export const ${fn}`));
	}
});

test("journey snapshot storage is injectable and degrades silently", () => {
	const source = readFileSync(snapshotUrl, "utf8");

	assert.match(source, /Pick<Storage, "getItem" \| "setItem" \| "removeItem">/);
	assert.match(source, /typeof window === "undefined"/);
	assert.match(source, /catch/);
	assert.doesNotMatch(source, /console\.(log|error|warn)/);
});

test("journey snapshot marks the backend instance api gap explicitly", () => {
	const source = readFileSync(snapshotUrl, "utf8");
	assert.match(source, /api\/journey\/instances/);
});

test("journey snapshot is exported from the journey barrel and has behavior tests", () => {
	const indexSource = readFileSync(indexUrl, "utf8");
	assert.match(indexSource, /journeySnapshot/);
	for (const fn of ["saveJourneySnapshot", "loadJourneySnapshot", "clearJourneySnapshot", "buildSnapshotResumeUrl"]) {
		assert.match(indexSource, new RegExp(fn));
	}
	assert.equal(existsSync(behaviorTestUrl), true, "journeySnapshot.test.ts (vitest) should exist");
});
