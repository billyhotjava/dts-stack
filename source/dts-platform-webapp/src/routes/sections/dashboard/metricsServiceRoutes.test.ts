import assert from "node:assert/strict";
import test from "node:test";
import {
	metricsServiceEmbeddedHrefFromPlatformLocation,
	metricsServiceHrefFromPlatformLocation,
	metricsServicePathFromPlatformPath,
} from "./metricsServiceRoutes.ts";

test("maps legacy platform metrics routes to dts-metrics service routes", () => {
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics"), "/metrics/center");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/center"), "/metrics/center");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/assets"), "/metrics/dictionary");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/dictionary"), "/metrics/dictionary");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/semantic/models"), "/metrics/semantic/models");
	assert.equal(metricsServicePathFromPlatformPath("/bi-apps/metrics/publish"), "/metrics/semantic/publish");
});

test("maps legacy semantic center routes to dts-metrics semantic pages", () => {
	assert.equal(metricsServicePathFromPlatformPath("/modeling/semantic-center"), "/metrics/semantic");
	assert.equal(metricsServicePathFromPlatformPath("/modeling/semantic-center/objects"), "/metrics/semantic/objects");
	assert.equal(metricsServicePathFromPlatformPath("/bi/semantic-modeling"), "/metrics/semantic");
});

test("preserves query and hash while redirecting to dts-metrics", () => {
	assert.equal(
		metricsServiceHrefFromPlatformLocation("/bi-apps/metrics/semantic/metrics", "?draft=1", "#formula"),
		"/metrics/semantic/metrics?draft=1#formula",
	);
});

test("builds embedded dts-metrics iframe links for platform bridge routes", () => {
	assert.equal(
		metricsServiceEmbeddedHrefFromPlatformLocation("/bi-apps/metrics/semantic/metrics", "?draft=1", "#formula"),
		"/metrics/semantic/metrics?draft=1&embedded=1#formula",
	);
	assert.equal(
		metricsServiceEmbeddedHrefFromPlatformLocation("/bi-apps/metrics/publish"),
		"/metrics/semantic/publish?embedded=1",
	);
});
