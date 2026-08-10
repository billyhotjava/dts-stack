import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { describe, it } from "vitest";

const migration = readFileSync(
	new URL(
		"../../../../../dts-admin/src/main/resources/config/liquibase/changelog/20260810-01_data_architecture_navigation_expand.xml",
		import.meta.url,
	),
	"utf8",
);
const master = readFileSync(
	new URL("../../../../../dts-admin/src/main/resources/config/liquibase/master.xml", import.meta.url),
	"utf8",
);
const planningGroupRepairUrl = new URL(
	"../../../../../dts-admin/src/main/resources/config/liquibase/changelog/20260810-02_retire_legacy_modeling_layer_groups.xml",
	import.meta.url,
);

describe("data architecture menu migration", () => {
	it("installs the canonical root and all five views", () => {
		assert.match(migration, /sys\.nav\.portal\.dataArchitecture/);
		for (const view of ["business-domains", "processes", "layers", "marts", "subjects"]) {
			assert.match(migration, new RegExp(`/data-architecture\\?view=${view}`));
		}
	});

	it("inherits visibility and retires legacy planning dictionaries without deleting menu rows", () => {
		assert.match(migration, /portal_menu_visibility/);
		assert.match(migration, /actor \|\| '-retired'/);
		assert.doesNotMatch(migration, /DELETE FROM portal_menu(?:\s|$)/);
		assert.match(migration, /last_modified_by = actor \|\| '-retired'/);
	});

	it("is registered in the admin Liquibase master", () => {
		assert.match(master, /20260810-01_data_architecture_navigation_expand\.xml/);
	});

	it("retires the orphaned public and application planning groups without deleting compatibility rows", () => {
		assert.match(master, /20260810-02_retire_legacy_modeling_layer_groups\.xml/);
		const repair = readFileSync(planningGroupRepairUrl, "utf8");
		assert.match(repair, /sys\.nav\.portal\.planningPublicLayer/);
		assert.match(repair, /sys\.nav\.portal\.planningApplicationLayer/);
		assert.match(repair, /deleted = TRUE/);
		assert.match(repair, /last_modified_by = actor \|\| '-retired'/);
		assert.match(repair, /last_modified_by = actor \|\| '-rollback'/);
		assert.doesNotMatch(repair, /DELETE FROM portal_menu(?:\s|$)/);
	});
});
