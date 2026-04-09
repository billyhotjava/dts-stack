import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const platformApiPath = new URL("./platformApi.ts", import.meta.url);

test("platformApi uses modeling request timeout for long-running dbt modeling actions", async () => {
	const source = await readFile(platformApiPath, "utf8");

	assert.equal(source.includes('export const submitDbtRelease = (data: any) =>\n\tapi.post(withModelingRequestTimeout({ url: "/etl/dbt/release/submit", data }));'), true);
	assert.equal(source.includes('export const checkDagReady = (params?: { selector?: string }) =>\n\tapi.get(withModelingRequestTimeout({ url: "/etl/dbt/dag/ready", params }));'), true);
	assert.equal(source.includes('export const triggerDbtRun = (data: any) => api.post(withModelingRequestTimeout({ url: "/etl/dbt/run", data }));'), true);
	assert.equal(source.includes('export const getDbtModelDiagnostics = (model: string) =>\n\tapi.get(withModelingRequestTimeout({ url: `/etl/dbt/models/${encodeURIComponent(model)}/diagnostics` }));'), true);
	assert.equal(source.includes('export const checkDbtQualityGate = (data?: any) =>\n\tapi.post(withModelingRequestTimeout({ url: "/etl/dbt/quality-gate/check", data }));'), true);
	assert.equal(source.includes('export const checkDbtReleaseGate = (data?: any) =>\n\tapi.post(withModelingRequestTimeout({ url: "/etl/dbt/release-gate/check", data }));'), true);
});
