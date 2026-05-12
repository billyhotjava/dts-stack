import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const nginxTemplatePath = new URL("../../../../../../builds/dts-platform-webapp/nginx.conf.template", import.meta.url);
const nginxDefaultPath = new URL("../../../../nginx/default.conf", import.meta.url);
const appComposePath = new URL("../../../../../../docker-compose-app.yml", import.meta.url);

test("platform webapp nginx allows screen asset upload bodies above backend asset limits", async () => {
	const [templateSource, defaultSource] = await Promise.all([
		readFile(nginxTemplatePath, "utf8"),
		readFile(nginxDefaultPath, "utf8"),
	]);

	for (const source of [templateSource, defaultSource]) {
		assert.match(source, /client_max_body_size\s+25m;/);
	}
});

test("app-mode platform CORS accepts canonical and LAN origins for screen image uploads", async () => {
	const source = await readFile(appComposePath, "utf8");

	assert.match(source, /JHIPSTER_CORS_ALLOWED_ORIGIN_PATTERNS/);
	assert.match(source, /DTS_CORS_ALLOWED_ORIGIN_PATTERNS/);
	assert.match(source, /http:\/\/\$\{HOST_PLATFORM_UI\}/);
	assert.match(source, /http:\/\/192\.168\.\*/);
	assert.match(source, /https:\/\/192\.168\.\*/);
	assert.match(source, /http:\/\/10\.\*/);
});
