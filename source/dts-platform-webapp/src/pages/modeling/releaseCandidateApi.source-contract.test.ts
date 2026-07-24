import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const API = readFileSync(new URL("../../api/modelSpecApi.ts", import.meta.url), "utf8");

test("release candidate writes keep plan scope, strong etag and idempotency headers", () => {
	assert.match(API, /releaseCandidateResource\(planId\)/);
	assert.match(API, /`"release-candidate:\$\{expected\.id\}:\$\{expected\.version\}"`/);
	assert.match(API, /"Idempotency-Key": idempotencyKey/);
	assert.match(API, /"If-Match": toReleaseCandidateEtag\(expected\)/);
	assert.match(API, /releaseCandidateItemUrl\(planId, expected\.id, "\/scope"\)/);
	assert.match(API, /releaseCandidateItemUrl\(planId, expected\.id, "\/lock"\)/);
	assert.match(API, /releaseCandidateItemUrl\(planId, expected\.id, "\/retry"\)/);
	assert.match(API, /releaseCandidateItemUrl\(planId, expected\.id, "\/refresh"\)/);
	assert.match(API, /releaseCandidateItemUrl\(planId, expected\.id, "\/replacement"\)/);
});

test("release candidate workbench preserves transport states and server-owned drift recovery", () => {
	assert.match(API, /\| \{ kind: "loading" \}/);
	assert.match(API, /\| \{ kind: "empty"; data: ReleaseCandidateWorkbench \}/);
	assert.match(API, /\| \{ kind: "forbidden"; code: string; message: string \}/);
	assert.match(API, /\| \{ kind: "error"; code: string; message: string \}/);
	assert.match(API, /\| \{ kind: "ready"; data: ReleaseCandidateWorkbench \}/);
	assert.match(API, /\| "REFRESH_CANDIDATE"/);
	assert.match(API, /export const refreshReleaseCandidate/);

	const refresh = API.slice(
		API.indexOf("export const refreshReleaseCandidate"),
		API.indexOf("export const createReplacementReleaseCandidate"),
	);
	assert.match(refresh, /api\.post<ReleaseCandidateCommandResult>/);
	assert.match(refresh, /releaseCandidateItemUrl\(planId, expected\.id, "\/refresh"\)/);
	assert.match(refresh, /headers: releaseCandidateWriteHeaders\(idempotencyKey, expected\)/);
	assert.match(refresh, /data: \{ reason \}/);
});
