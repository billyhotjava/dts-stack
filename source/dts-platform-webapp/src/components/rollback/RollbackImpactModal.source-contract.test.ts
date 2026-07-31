import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./RollbackImpactModal.tsx", import.meta.url), "utf8");

test("rollback analysis fails closed without a server-issued confirmation contract", () => {
	assert.match(SOURCE, /confirmationType:\s*string/);
	assert.match(SOURCE, /confirmationToken:\s*string/);
	assert.match(SOURCE, /confirmationText\?:\s*string/);
	assert.match(SOURCE, /SUPPORTED_CONFIRMATION_TYPES/);
	assert.match(SOURCE, /回退确认凭据无效/);
});

test("rollback confirmation follows MODAL and TYPE_TEXT policies", () => {
	assert.match(SOURCE, /confirmationType === "MODAL"/);
	assert.match(SOURCE, /confirmationType === "TYPE_TEXT"/);
	assert.match(SOURCE, /<Checkbox/);
	assert.match(SOURCE, /<Input/);
	assert.match(SOURCE, /confirmationInput === impact\.confirmationText/);
	assert.match(SOURCE, /暂不支持当前回退确认方式/);
});

test("rollback execution echoes the analyzed confirmation contract", () => {
	assert.match(SOURCE, /confirmationType:\s*impact\.confirmationType/);
	assert.match(SOURCE, /confirmationToken:\s*impact\.confirmationToken/);
	assert.match(SOURCE, /confirmationText:\s*confirmationInput/);
	assert.match(SOURCE, /disabled:\s*!confirmationReady/);
});

test("rollback failures use controlled messages instead of raw backend details", () => {
	assert.match(SOURCE, /回退影响分析失败，请稍后重试/);
	assert.match(SOURCE, /回退执行失败，请稍后重试/);
	assert.doesNotMatch(SOURCE, /(?:error|response)\.(?:message|data)/i);
});
