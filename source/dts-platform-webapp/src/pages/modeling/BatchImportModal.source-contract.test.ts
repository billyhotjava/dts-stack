import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const modal = readFileSync(new URL("./BatchImportModal.tsx", import.meta.url), "utf8");

test("advanced modeling accepts the same standard dbt project ZIP without client repacking", () => {
	assert.match(modal, /标准 dbt 项目 ZIP/);
	assert.match(modal, /dbt_project\.yml、models、YAML、macros/);
	assert.match(modal, /models\.tsv 和 manifest 为可选兼容文件/);
	assert.match(modal, /formData\.append\("archive", zipFileList\[0\]\.originFileObj\)/);
	assert.doesNotMatch(modal, /JSZip\.loadAsync\(file\)/);
});
