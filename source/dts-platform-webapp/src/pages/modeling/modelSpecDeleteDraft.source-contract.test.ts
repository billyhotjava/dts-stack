import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const center = readFileSync(new URL("./ModelCenterPage.tsx", import.meta.url), "utf8");
const api = readFileSync(new URL("../../api/modelSpecApi.ts", import.meta.url), "utf8");

test("model center deletes only canonical drafts with explicit confirmation", () => {
	assert.match(center, /model\.status === "DRAFT"/);
	assert.match(center, /model\.compatibilityMode === "CANONICAL"/);
	assert.match(center, /删除草稿模型/);
	assert.match(center, /删除后模型将从模型中心移除/);
	assert.match(center, /deleteModelSpec/);
	assert.match(center, /setModels\(\(current\) => current\.filter\(\(item\) => item\.id !== model\.id\)\)/);
	assert.match(center, /model\.status === "ARCHIVED"/);
});

test("canonical drafts expose the logical design edit entry", () => {
	assert.match(
		center,
		/const editable = canEdit && model\.status === "DRAFT" && model\.compatibilityMode === "CANONICAL"/,
	);
	assert.match(center, /modelSpecDetailPath\(model\.id, "logical", model\.planId\)/);
	assert.match(center, />\s*编辑\s*</);
});

test("draft deletion uses the canonical model etag", () => {
	assert.match(api, /export const deleteModelSpec/);
	assert.match(api, /api\.delete/);
	assert.match(api, /"If-Match": toModelSpecEtag\(expected\)/);
});
