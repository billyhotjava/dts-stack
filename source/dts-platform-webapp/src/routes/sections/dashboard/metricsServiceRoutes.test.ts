import assert from "node:assert/strict";
import test from "node:test";
import { metricsServiceHrefFromPlatformLocation, metricsServicePathFromPlatformPath } from "./metricsServiceRoutes.ts";

test("maps legacy platform metrics routes to platform modeling routes", () => {
	const metrics = "/modeling/workbench?module=metrics&workspaceView=definitions";
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics"), metrics);
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/center"), metrics);
	assert.equal(metricsServicePathFromPlatformPath("/metrics"), metrics);
	assert.equal(metricsServicePathFromPlatformPath("/metrics/center"), metrics);
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/assets"), metrics);
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/dictionary"), metrics);
	assert.equal(metricsServicePathFromPlatformPath("/metrics/dictionary"), metrics);
	assert.equal(metricsServicePathFromPlatformPath("/metrics/operations"), "/ops/instances");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/f5-security-it"), "/ops/instances");
	assert.equal(metricsServicePathFromPlatformPath("/metrics/runs"), "/ops/instances");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/subjects"), "/governance/subjects");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/subjects"), "/governance/subjects");
	assert.equal(
		metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/objects"),
		"/modeling/workbench?module=models&workspaceView=dimensions",
	);
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/metrics"), metrics);
	assert.equal(
		metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/models"),
		"/modeling/workbench?module=models&workspaceView=model-specs",
	);
	assert.equal(
		metricsServicePathFromPlatformPath("/bi-apps/metrics/publish"),
		"/modeling/workbench?module=models&workspaceView=model-specs&view=release",
	);
	assert.equal(
		metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/publish"),
		"/modeling/workbench?module=models&workspaceView=model-specs&view=release",
	);
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/runs"), "/ops/instances");
});

test("maps legacy semantic center routes to platform semantic pages", () => {
	assert.equal(
		metricsServicePathFromPlatformPath("/modeling/semantic-center"),
		"/modeling/workbench?module=models&workspaceView=model-specs",
	);
	assert.equal(metricsServicePathFromPlatformPath("/modeling/semantic-center/subjects"), "/governance/subjects");
	assert.equal(
		metricsServicePathFromPlatformPath("/modeling/semantic-center/objects"),
		"/modeling/workbench?module=models&workspaceView=dimensions",
	);
	assert.equal(
		metricsServicePathFromPlatformPath("/bi/semantic-modeling"),
		"/modeling/workbench?module=models&workspaceView=model-specs",
	);
});

test("preserves query and hash while redirecting to platform modeling", () => {
	assert.equal(
		metricsServiceHrefFromPlatformLocation("/bi-apps/metrics/semantic/metrics", "?draft=1", "#formula"),
		"/modeling/workbench?module=metrics&workspaceView=definitions&draft=1#formula",
	);
	assert.equal(
		metricsServiceHrefFromPlatformLocation("/bi-apps/metrics/publish"),
		"/modeling/workbench?module=models&workspaceView=model-specs&view=release",
	);
});
