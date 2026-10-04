import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PORTAL_MENU = readFileSync(
	new URL("../../../../../dts-admin/src/main/resources/config/data/portal-menu-seed.json", import.meta.url),
	"utf8",
);
const ROLE_DEFAULTS = readFileSync(
	new URL("../../../../../dts-admin/src/main/resources/config/data/role-menu-defaults.json", import.meta.url),
	"utf8",
);
const VISIBILITY_CHANGELOG = readFileSync(
	new URL(
		"../../../../../dts-admin/src/main/resources/config/liquibase/changelog/20260801-02_connection_profiles_and_access_defaults_visibility.xml",
		import.meta.url,
	),
	"utf8",
);

test("connection profiles are a visible runtime entry with a real route", () => {
	for (const source of [PORTAL_MENU, ROLE_DEFAULTS]) {
		assert.match(source, /sys\.nav\.portal\.resourceConnections/);
		assert.match(source, /\/foundation\/connections/);
	}
	assert.match(VISIBILITY_CHANGELOG, /resourceConnections/);
	assert.match(VISIBILITY_CHANGELOG, /portal_menu_visibility/);
});

test("default policies inherit access-overview visibility instead of remaining orphaned", () => {
	assert.match(VISIBILITY_CHANGELOG, /resourceAccessOverview/);
	assert.match(VISIBILITY_CHANGELOG, /resourceAccessDefaults/);
	assert.match(VISIBILITY_CHANGELOG, /WHERE visibility\.menu_id = overview_id/);
});
