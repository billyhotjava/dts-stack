import assert from "node:assert/strict";
import test from "node:test";

import { createPlatformServerProxy } from "./vite.config";

test("createPlatformServerProxy forwards analytics api requests to analytics backend", () => {
	const proxy = createPlatformServerProxy({
		apiProxyTarget: "http://platform.internal:8081",
		apiProxyPrefix: "",
		adminProxyTarget: "http://admin.internal:8081",
		analyticsApiProxyTarget: "http://analytics.internal:3000",
		analyticsUiProxyTarget: "http://analytics-ui.internal:3002",
	});

	assert.equal(proxy["/analytics/api"].target, "http://analytics.internal:3000");
	assert.equal(proxy["/analytics/api"].rewrite("/analytics/api/health"), "/api/health");
	assert.equal(proxy["/analytics"].target, "http://analytics-ui.internal:3002");
});

test("createPlatformServerProxy preserves platform and admin proxy rewrites", () => {
	const proxy = createPlatformServerProxy({
		apiProxyTarget: "https://gateway.internal",
		apiProxyPrefix: "/platform",
		adminProxyTarget: "http://admin.internal:8081",
		analyticsApiProxyTarget: "http://analytics.internal:3000",
		analyticsUiProxyTarget: "http://analytics-ui.internal:3002",
	});

	assert.equal(proxy["/api"].rewrite?.("/api/workbench/overview"), "/platform/api/workbench/overview");
	assert.equal(proxy["/admin/api"].rewrite("/admin/api/keycloak/auth/refresh"), "/api/keycloak/auth/refresh");
	assert.equal(proxy["/admin/api"].rewrite("/admin/api/users"), "/api/admin/users");
});
