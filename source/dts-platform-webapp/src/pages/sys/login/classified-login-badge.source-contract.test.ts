import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const repoRoot = fileURLToPath(new URL("../../../../../../", import.meta.url));
const read = (path: string) => readFileSync(join(repoRoot, path), "utf8");

test("classified login badge is controlled by one runtime env flag across admin and platform", () => {
	const adminLogin = read("source/dts-admin-webapp/src/pages/sys/login/index.tsx");
	const platformLogin = read("source/dts-platform-webapp/src/pages/sys/login/index.tsx");
	const adminConfig = read("source/dts-admin-webapp/src/global-config.ts");
	const platformConfig = read("source/dts-platform-webapp/src/global-config.ts");
	const adminEntrypoint = read("builds/dts-admin-webapp/docker-entrypoint.sh");
	const platformEntrypoint = read("builds/dts-platform-webapp/docker-entrypoint.sh");
	const appCompose = read("docker-compose-app.yml");
	const devCompose = read("docker-compose.dev.yml");
	const legacyCompose = read("docker-compose.legacy.yml");
	const initScript = read("init.sh");
	const envFile = read(".env");

	assert.match(adminLogin, /GLOBAL_CONFIG\.showClassifiedLoginBadge/);
	assert.match(adminLogin, /showClassifiedLoginBadge \?/);
	assert.match(adminLogin, /<Star className="h-8 w-8 text-red-600"/);
	assert.match(platformLogin, /GLOBAL_CONFIG\.showClassifiedLoginBadge/);
	assert.match(platformLogin, /showClassifiedLoginBadge \?/);
	assert.match(platformLogin, /<Star className="h-8 w-8 text-red-600"/);

	assert.match(adminConfig, /showClassifiedLoginBadge\?: string \| boolean/);
	assert.match(adminConfig, /showClassifiedLoginBadge: resolveShowClassifiedLoginBadge\(\)/);
	assert.match(platformConfig, /showClassifiedLoginBadge\?: string \| boolean/);
	assert.match(platformConfig, /showClassifiedLoginBadge: resolveShowClassifiedLoginBadge\(\)/);

	assert.match(adminEntrypoint, /WEBAPP_SHOW_CLASSIFIED_LOGIN_BADGE/);
	assert.match(adminEntrypoint, /showClassifiedLoginBadge='\$\{val\}'/);
	assert.match(platformEntrypoint, /WEBAPP_SHOW_CLASSIFIED_LOGIN_BADGE/);
	assert.match(platformEntrypoint, /showClassifiedLoginBadge='\$\{val\}'/);

	assert.equal((appCompose.match(/^\s+WEBAPP_SHOW_CLASSIFIED_LOGIN_BADGE:/gm) || []).length, 2);
	assert.equal((devCompose.match(/^\s+WEBAPP_SHOW_CLASSIFIED_LOGIN_BADGE:/gm) || []).length, 2);
	assert.equal((legacyCompose.match(/^\s+WEBAPP_SHOW_CLASSIFIED_LOGIN_BADGE:/gm) || []).length, 2);
	assert.match(initScript, /WEBAPP_SHOW_CLASSIFIED_LOGIN_BADGE:=true/);
	assert.match(initScript, /WEBAPP_SHOW_CLASSIFIED_LOGIN_BADGE=\$\{WEBAPP_SHOW_CLASSIFIED_LOGIN_BADGE\}/);
	assert.match(envFile, /WEBAPP_SHOW_CLASSIFIED_LOGIN_BADGE=true/);
});
