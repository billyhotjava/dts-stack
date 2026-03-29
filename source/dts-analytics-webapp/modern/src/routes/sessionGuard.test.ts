import assert from "node:assert/strict";
import test from "node:test";
import {
	buildPlatformLoginHref,
	isPublicAnalyticsPath,
	resolvePlatformBaseOrigin,
	requiresPlatformSession,
} from "./sessionGuard";

test("isPublicAnalyticsPath only treats public share routes as anonymous", () => {
	assert.equal(isPublicAnalyticsPath("/public/card/abc"), true);
	assert.equal(isPublicAnalyticsPath("/public/dashboard/abc"), true);
	assert.equal(isPublicAnalyticsPath("/public/screen/abc"), true);
	assert.equal(isPublicAnalyticsPath("/screens"), false);
	assert.equal(isPublicAnalyticsPath("/screens/123/preview"), false);
});

test("requiresPlatformSession protects non-public analytics routes", () => {
	assert.equal(requiresPlatformSession("/"), true);
	assert.equal(requiresPlatformSession("/screens"), true);
	assert.equal(requiresPlatformSession("/screens/new"), true);
	assert.equal(requiresPlatformSession("/screens/123/edit"), true);
	assert.equal(requiresPlatformSession("/screens/123/preview"), true);
	assert.equal(requiresPlatformSession("/screens/123/export"), true);
	assert.equal(requiresPlatformSession("/public/card/abc"), false);
	assert.equal(requiresPlatformSession("/public/dashboard/abc"), false);
	assert.equal(requiresPlatformSession("/public/screen/abc"), false);
});

test("resolvePlatformBaseOrigin prefers configured platform base and normalizes it", () => {
	assert.equal(resolvePlatformBaseOrigin("http://127.0.0.1:18012", "http://127.0.0.1:3002"), "http://127.0.0.1:18012");
	assert.equal(resolvePlatformBaseOrigin("https://dts.local/platform/", "http://127.0.0.1:3002"), "https://dts.local");
	assert.equal(resolvePlatformBaseOrigin("", "http://127.0.0.1:3002"), "http://127.0.0.1:3002");
});

test("buildPlatformLoginHref preserves the analytics return url", () => {
	assert.equal(
		buildPlatformLoginHref("/screens/123/edit", "?tab=design", "http://127.0.0.1:18012", "http://127.0.0.1:3002"),
		"http://127.0.0.1:18012/#/auth/login?redirect=%2Fscreens%2F123%2Fedit%3Ftab%3Ddesign",
	);
	assert.equal(
		buildPlatformLoginHref("/", "", "", "http://127.0.0.1:3002"),
		"http://127.0.0.1:3002/#/auth/login?redirect=%2F",
	);
});
