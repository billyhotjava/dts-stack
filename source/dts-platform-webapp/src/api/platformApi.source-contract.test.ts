import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const platformApiPath = new URL("./platformApi.ts", import.meta.url);

test("platformApi uses modeling request timeout for long-running dbt modeling actions", async () => {
	const source = await readFile(platformApiPath, "utf8");

	assert.equal(source.includes('url: "/session/status"'), true);
	assert.equal(source.includes("X-Portal-Access-Token"), false);
	assert.equal(source.includes("_skipAuth: true"), true);
	assert.equal(source.includes('export const submitDbtRelease = (data: any) =>\n\tapi.post(withModelingRequestTimeout({ url: "/etl/dbt/release/submit", data }));'), true);
	assert.equal(source.includes('export const checkDagReady = (params?: { selector?: string }) =>\n\tapi.get(withModelingRequestTimeout({ url: "/etl/dbt/dag/ready", params }));'), true);
	assert.equal(source.includes('export const triggerDbtRun = (data: any) => api.post(withModelingRequestTimeout({ url: "/etl/dbt/run", data }));'), true);
	assert.equal(source.includes(`export const getDbtModelDiagnostics = (model: string) =>\n\tapi.get(withModelingRequestTimeout({ url: \`/etl/dbt/models/\${encodeURIComponent(model)}/diagnostics\` }));`), true);
	assert.equal(source.includes('export const checkDbtQualityGate = (data?: any) =>\n\tapi.post(withModelingRequestTimeout({ url: "/etl/dbt/quality-gate/check", data }));'), true);
	assert.equal(source.includes('export const checkDbtReleaseGate = (data?: any) =>\n\tapi.post(withModelingRequestTimeout({ url: "/etl/dbt/release-gate/check", data }));'), true);
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
});
