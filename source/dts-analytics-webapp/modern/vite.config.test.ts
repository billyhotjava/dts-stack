import assert from "node:assert/strict";
import test from "node:test";

import {
	createAnalyticsServerProxy,
	isRuntimeConfigRequestPath,
	resolveBrowserPlatformBaseUrl,
} from "./vite.config";

test("createAnalyticsServerProxy wires analytics and platform APIs in container mode", () => {
	const proxy = createAnalyticsServerProxy({}, true);

	assert.equal(proxy["/api"].target, "http://dts-platform:8081");
	assert.equal(proxy["/analytics/api"].target, "http://dts-analytics:3000");
	assert.equal(proxy["/analytics/api"].rewrite("/analytics/api/health"), "/api/health");
});

test("createAnalyticsServerProxy honors explicit proxy targets in host mode", () => {
	const proxy = createAnalyticsServerProxy(
		{
			VITE_API_PROXY_TARGET: "http://platform-proxy.internal:8081",
			VITE_ANALYTICS_API_PROXY_TARGET: "http://analytics-proxy.internal:3000",
		},
		false,
	);

	assert.equal(proxy["/api"].target, "http://platform-proxy.internal:8081");
	assert.equal(proxy["/analytics/api"].target, "http://analytics-proxy.internal:3000");
	assert.equal(proxy["/analytics/api"].rewrite("/analytics/api/dashboard"), "/api/dashboard");
});

test("createAnalyticsServerProxy falls back to localhost targets outside containers", () => {
	const proxy = createAnalyticsServerProxy({}, false);

	assert.equal(proxy["/api"].target, "http://127.0.0.1:18082");
	assert.equal(proxy["/analytics/api"].target, "http://127.0.0.1:3000");
});

test("resolveBrowserPlatformBaseUrl prefers explicit runtime configuration", () => {
	assert.equal(
		resolveBrowserPlatformBaseUrl("https://dts.local", "127.0.0.1:3002", "http"),
		"https://dts.local",
	);
});

test("resolveBrowserPlatformBaseUrl derives host-accessible platform origin for standalone analytics dev", () => {
	assert.equal(
		resolveBrowserPlatformBaseUrl("", "127.0.0.1:3002", "http"),
		"http://127.0.0.1:18012",
	);
	assert.equal(
		resolveBrowserPlatformBaseUrl("", "dts.local", "https"),
		"https://dts.local",
	);
});

test("isRuntimeConfigRequestPath matches both root and analytics-base runtime config urls", () => {
	assert.equal(isRuntimeConfigRequestPath("/runtime-config.js"), true);
	assert.equal(isRuntimeConfigRequestPath("/analytics/runtime-config.js"), true);
	assert.equal(isRuntimeConfigRequestPath("/analytics/src/main.tsx"), false);
});
