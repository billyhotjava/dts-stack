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
	assert.match(SOURCE, /disabled:\s*!confirmationReady \|\| confirmationConsumed/);
});

test("rollback failures use controlled messages instead of raw backend details", () => {
	assert.match(SOURCE, /回退影响分析失败，请稍后重试/);
	assert.match(SOURCE, /确认令牌已提交，禁止再次执行整个回退/);
	assert.doesNotMatch(SOURCE, /(?:error|response)\.(?:message|data)/i);
});

test("partial rollback preserves recovery details and never offers a whole-operation retry", () => {
	assert.match(SOURCE, /state === "PARTIAL_FAILED"/);
	assert.match(SOURCE, /executionResult\.succeeded/);
	assert.match(SOURCE, /executionResult\.failedStep/);
	assert.match(SOURCE, /executionResult\.manualRecoveryRequired/);
	assert.match(SOURCE, /禁止再次执行整个回退/);
	assert.doesNotMatch(SOURCE, /重试失败步骤|重新执行回退/);
});
