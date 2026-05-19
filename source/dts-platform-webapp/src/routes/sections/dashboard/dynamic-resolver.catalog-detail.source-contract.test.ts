import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./dynamic-resolver.tsx", import.meta.url), "utf8");

test("catalog asset detail override renders before menu-match fallback redirects to workbench", () => {
	assert.match(SOURCE, /"\/catalog\/datasets\/:id": "\/pages\/catalog\/DatasetDetailPage"/);
	assert.match(SOURCE, /findMenuByPath/);
	assert.match(SOURCE, /pathname\.startsWith\("\/catalog\/datasets\/"\)/);
	assert.match(SOURCE, /return "\/catalog\/assets"/);
	assert.match(SOURCE, /if \(!menusLoaded\)[\s\S]*directOverridePath[\s\S]*Component\(directOverridePath\)/);
	assert.match(SOURCE, /menu-backed routes may have deeper non-menu operational detail pages/);
	assert.match(SOURCE, /overrideParentPath && !directOverrideParent/);
	assert.equal((SOURCE.match(/if \(directOverridePath/g) || []).length, 2);
	assert.ok(
		SOURCE.lastIndexOf("if (directOverridePath && !match)") < SOURCE.indexOf("if (!match)"),
		"deep direct override routes such as /catalog/datasets/:id must render before no-menu-match fallback",
	);
});
