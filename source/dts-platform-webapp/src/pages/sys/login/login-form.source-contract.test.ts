import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const repoRoot = fileURLToPath(new URL("../../../../../../", import.meta.url));

test("password login is visible when no login visibility flag is configured", () => {
	const loginForm = readFileSync(
		join(repoRoot, "source/dts-platform-webapp/src/pages/sys/login/login-form.tsx"),
		"utf8",
	);

	assert.match(loginForm, /WEBAPP_PASSWORD_LOGIN_ENABLED/);
	assert.match(loginForm, /VITE_HIDE_PASSWORD_LOGIN/);
	assert.match(loginForm, /return false; \/\/ 默认显示账号\/密码/);
});
