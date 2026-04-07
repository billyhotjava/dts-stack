import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const deptServicePath = new URL("./services/deptService.ts", import.meta.url);
const roleServicePath = new URL("./services/roleService.ts", import.meta.url);
const pkiServicePath = new URL("./services/pkiService.ts", import.meta.url);
const adminServicePath = new URL("./services/adminService.ts", import.meta.url);
const globalConfigPath = new URL("../global-config.ts", import.meta.url);

test("platform webapp admin-related services use platform APIs instead of adminApiBaseUrl", async () => {
	const [deptSource, roleSource, pkiSource, adminSource, globalConfigSource] = await Promise.all([
		readFile(deptServicePath, "utf8"),
		readFile(roleServicePath, "utf8"),
		readFile(pkiServicePath, "utf8"),
		readFile(adminServicePath, "utf8"),
		readFile(globalConfigPath, "utf8"),
	]);

	assert.equal(deptSource.includes("adminApiBaseUrl"), false);
	assert.equal(roleSource.includes("adminApiBaseUrl"), false);
	assert.equal(pkiSource.includes("adminApiBaseUrl"), false);
	assert.equal(adminSource.includes("adminApiBaseUrl"), false);
	assert.equal(globalConfigSource.includes("adminApiBaseUrl"), false);

	assert.equal(roleSource.includes('url: "/directory/roles"'), true);
	assert.equal(pkiSource.includes('url: "/keycloak/auth/pki-challenge"'), true);
	assert.equal(pkiSource.includes('url: "/keycloak/auth/pki-login"'), true);
	assert.equal(adminSource.includes('url: "/admin/whoami"'), true);
	assert.equal(adminSource.includes('url: "/directory/orgs"'), true);
});
