import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const pagePath = new URL("./DataPortalPage.tsx", import.meta.url);
const previewPath = new URL("./ScreenPreviewPage.tsx", import.meta.url);
const apiPath = new URL("../../api/analyticsApi.ts", import.meta.url);
const routesPath = new URL("../../../routes/sections/dashboard/static-routes.tsx", import.meta.url);

test("data portal binds the published-only directory, domain tree, deep link, and four states", async () => {
	const [page, api, routes] = await Promise.all([
		readFile(pagePath, "utf8"),
		readFile(apiPath, "utf8"),
		readFile(routesPath, "utf8"),
	]);

	assert.match(api, /publishedOnly\?:\s*boolean/);
	assert.match(api, /qs\.set\(["']publishedOnly["'],\s*["']true["']\)/);
	assert.match(page, /listScreens\(\{\s*publishedOnly:\s*true\s*\}\)/);
	assert.match(page, /getDomainTree\(/);
	assert.match(page, /buildDataPortalTree/);
	assert.match(page, /data-testid=["']data-portal-page["']/);
	assert.match(page, /data-testid=["']data-portal-loading["']/);
	assert.match(page, /data-testid=["']data-portal-empty["']/);
	assert.match(page, /data-testid=["']data-portal-error["']/);
	assert.match(page, /data-testid=["']data-portal-runtime["']/);
	assert.match(page, /\/bi\/screens\/\$\{encodeURIComponent\(String\(selectedScreen\.id\)\)\}\/preview/);
	assert.match(page, /mode=published/);
	assert.match(page, /embed=1/);
	assert.match(page, /大屏管理/);
	assert.match(routes, /path:\s*["']bi\/portal["']/);
	assert.match(routes, /path:\s*["']bi\/portal\/:screenId["']/);
});

test("screen preview preserves draft default and supports fail-closed published embed mode", async () => {
	const preview = await readFile(previewPath, "utf8");

	assert.match(preview, /previewMode/);
	assert.match(preview, /mode:\s*previewMode/);
	assert.match(preview, /fallbackDraft:\s*previewMode\s*!==\s*["']published["']/);
	assert.match(preview, /isEmbedded/);
	assert.match(preview, /!isEmbedded\s*&&/);
});
