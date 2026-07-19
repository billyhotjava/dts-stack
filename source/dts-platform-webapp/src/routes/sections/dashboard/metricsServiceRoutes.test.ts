import assert from "node:assert/strict";
import test from "node:test";
import {
	metricsServiceHrefFromPlatformLocation,
	metricsServicePathFromPlatformPath,
} from "./metricsServiceRoutes.ts";

test("maps legacy platform metrics routes to platform modeling routes", () => {
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics"), "/modeling/metric-workbench");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/center"), "/modeling/metric-workbench");
	assert.equal(metricsServicePathFromPlatformPath("/metrics"), "/modeling/metric-workbench");
	assert.equal(metricsServicePathFromPlatformPath("/metrics/center"), "/modeling/metric-workbench");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/assets"), "/modeling/metric-workbench");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/dictionary"), "/modeling/metric-workbench");
	assert.equal(metricsServicePathFromPlatformPath("/metrics/dictionary"), "/modeling/metric-workbench");
	assert.equal(metricsServicePathFromPlatformPath("/metrics/operations"), "/ops/instances");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/f5-security-it"), "/ops/instances");
	assert.equal(metricsServicePathFromPlatformPath("/metrics/runs"), "/ops/instances");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/subjects"), "/governance/subjects");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/subjects"), "/governance/subjects");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/objects"), "/modeling/dimensions");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/metrics"), "/modeling/metric-workbench");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/models"), "/modeling/models");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/publish"), "/modeling/models?view=release");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/publish"), "/modeling/models?view=release");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/runs"), "/ops/instances");
});

test("maps legacy semantic center routes to platform semantic pages", () => {
	assert.equal(metricsServicePathFromPlatformPath("/modeling/semantic-center"), "/modeling/models");
	assert.equal(metricsServicePathFromPlatformPath("/modeling/semantic-center/subjects"), "/governance/subjects");
	assert.equal(metricsServicePathFromPlatformPath("/modeling/semantic-center/objects"), "/modeling/dimensions");
	assert.equal(metricsServicePathFromPlatformPath("/bi/semantic-modeling"), "/modeling/models");
});

test("preserves query and hash while redirecting to platform modeling", () => {
	assert.equal(
		metricsServiceHrefFromPlatformLocation("/bi-apps/metrics/semantic/metrics", "?draft=1", "#formula"),
		"/modeling/metric-workbench?draft=1#formula",
	);
	assert.equal(
		metricsServiceHrefFromPlatformLocation("/bi-apps/metrics/publish"),
		"/modeling/models?view=release",
	);
});
