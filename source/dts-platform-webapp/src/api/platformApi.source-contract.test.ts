import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const platformApiPath = new URL("./platformApi.ts", import.meta.url);

test("platformApi keeps supported dbt reads and omits retired direct and selector execution routes", async () => {
	const source = await readFile(platformApiPath, "utf8");

	assert.equal(source.includes('url: "/session/status"'), true);
	assert.equal(source.includes("X-Portal-Access-Token"), false);
	assert.equal(source.includes("_skipAuth: true"), true);
	assert.equal(source.includes("checkDagReady"), false);
	assert.equal(source.includes('/etl/dbt/dag/ready'), false);
	assert.equal(source.includes("selector?: string"), false);
	assert.equal(
		source.includes(
			`export const getDbtModelDiagnostics = (model: string) =>\n\tapi.get(withModelingRequestTimeout({ url: \`/etl/dbt/models/\${encodeURIComponent(model)}/diagnostics\` }));`,
		),
		true,
	);
	for (const route of [
		'/etl/dbt/output',
		'/etl/dbt/run',
		'/etl/dbt/git/status',
		'/etl/dbt/git/commit',
		'/etl/dbt/git/log',
		'/etl/dbt/git/diff',
		'/etl/dbt/git/revert',
		'/etl/dbt/git/file-at-commit',
		'/etl/dbt/compile',
		'/etl/dbt/test',
		'/etl/dbt/docs',
		'/etl/dbt/quality-gate/check',
		'/etl/dbt/release-gate/check',
		'/etl/dbt/release/submit',
	]) {
		assert.equal(source.includes(`url: "${route}"`), false, `retired route is still exported: ${route}`);
	}
});

test("rollback requests suppress raw interceptor errors and execute with server confirmation fields", async () => {
	const source = await readFile(platformApiPath, "utf8");
	const rollbackSection = source.slice(
		source.indexOf("export const rollbackAnalyze"),
		source.indexOf("export const getRollbackAuditLog"),
	);

	assert.match(rollbackSection, /rollback\/analyze[\s\S]*_skipErrorToast:\s*true/);
	assert.match(rollbackSection, /confirmationType:\s*string/);
	assert.match(rollbackSection, /confirmationToken:\s*string/);
	assert.match(rollbackSection, /confirmationText\?:\s*string/);
	assert.match(rollbackSection, /rollback\/execute[\s\S]*_skipErrorToast:\s*true/);
	assert.match(rollbackSection, /_acceptedEnvelopeStatuses:\s*\[207\]/);
	assert.match(rollbackSection, /_returnEnvelope:\s*true/);
});
