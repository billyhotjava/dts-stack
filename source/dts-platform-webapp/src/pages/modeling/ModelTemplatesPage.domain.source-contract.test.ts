import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./ModelTemplatesPage.tsx", import.meta.url), "utf8");

test("project spaces select governance domains by id instead of free text", () => {
	assert.match(SOURCE, /listDomains/);
	assert.match(SOURCE, /name="domainId"/);
	assert.match(SOURCE, /主题域管理/);
	assert.match(SOURCE, /\/governance\/subjects/);
	assert.doesNotMatch(SOURCE, /name="domain" label="业务域"/);
	assert.doesNotMatch(SOURCE, /domain:\s*normalizeText\(form\.getFieldValue\("domain"\)\)/);
});

test("legacy project-space domain text is treated as unclassified", () => {
	assert.match(SOURCE, /domainNameById/);
	assert.match(SOURCE, /未分类/);
	assert.doesNotMatch(SOURCE, /row\.domain\s*\|\|/);
});
