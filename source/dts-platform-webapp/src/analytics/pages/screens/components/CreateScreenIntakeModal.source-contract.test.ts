import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const modalPath = new URL("./CreateScreenIntakeModal.tsx", import.meta.url);

test("CreateScreenIntakeModal collects data-domain metadata for create and import flows", async () => {
	const source = await readFile(modalPath, "utf8");

	assert.match(source, /mode\?:\s*['"]create['"]\s*\|\s*['"]import['"]/);
	assert.match(source, /domainOptions/);
	assert.match(source, /domainId\?:\s*string/);
	assert.match(source, /defaultDomainId/);
	assert.match(source, /数据域/);
	assert.match(source, /未归类/);
});

test("CreateScreenIntakeModal owns the JSON file picker in import mode", async () => {
	const source = await readFile(modalPath, "utf8");

	assert.match(source, /file\?:\s*File/);
	assert.match(source, /setFile/);
	assert.match(source, /accept="application\/json,\s*\.json"/);
	assert.match(source, /mode\s*===\s*['"]import['"]/);
	assert.match(source, /请选择 JSON 文件/);
});
