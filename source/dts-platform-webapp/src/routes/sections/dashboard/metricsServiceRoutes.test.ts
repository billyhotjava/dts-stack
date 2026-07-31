import assert from "node:assert/strict";
import test from "node:test";
import { metricsServiceHrefFromPlatformLocation, metricsServicePathFromPlatformPath } from "./metricsServiceRoutes.ts";

test("maps legacy platform metrics routes to prototype-owned modeling routes", () => {
	const metrics = "/data-modeling/metrics/atomic";
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
	assert.equal(
		metricsServicePathFromPlatformPath("/bi-apps/metrics/subjects"),
		"/data-modeling/planning/business-categories",
	);
	assert.equal(
		metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/subjects"),
		"/data-modeling/planning/business-categories",
	);
	assert.equal(
		metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/objects"),
		"/data-modeling/dimensions/workbench",
	);
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/metrics"), metrics);
	assert.equal(
		metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/models"),
		"/data-modeling/dimensions/workbench",
	);
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/publish"), "/data-modeling/home/workspace");
	assert.equal(
		metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/publish"),
		"/data-modeling/home/workspace",
	);
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/runs"), "/ops/instances");
});

test("maps legacy semantic center routes to the new dimension and planning surfaces", () => {
	assert.equal(metricsServicePathFromPlatformPath("/modeling/semantic-center"), "/data-modeling/dimensions/workbench");
	assert.equal(
		metricsServicePathFromPlatformPath("/modeling/semantic-center/subjects"),
		"/data-modeling/planning/business-categories",
	);
	assert.equal(
		metricsServicePathFromPlatformPath("/modeling/semantic-center/objects"),
		"/data-modeling/dimensions/workbench",
	);
	assert.equal(metricsServicePathFromPlatformPath("/bi/semantic-modeling"), "/data-modeling/dimensions/workbench");
});

test("preserves query and hash while redirecting to platform modeling", () => {
	assert.equal(
		metricsServiceHrefFromPlatformLocation("/bi-apps/metrics/semantic/metrics", "?draft=1", "#formula"),
		"/data-modeling/metrics/atomic?draft=1#formula",
	);
	assert.equal(metricsServiceHrefFromPlatformLocation("/bi-apps/metrics/publish"), "/data-modeling/home/workspace");
});
