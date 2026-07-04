import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const ELEMENTS_PAGE = readFileSync(new URL("./ElementsPage.tsx", import.meta.url), "utf8");
const GLOSSARY_PAGE = readFileSync(new URL("./GlossaryPage.tsx", import.meta.url), "utf8");
const REFERENCE_CODES_PAGE = readFileSync(new URL("./ReferenceCodesPage.tsx", import.meta.url), "utf8");
const SPRINT_ROOT = new URL("../../../../../worklog/v2.2.3/sprint-57-202607/", import.meta.url);
const AUDIT_DOC = new URL("assets/foundation-pages-audit.md", SPRINT_ROOT);

const PAGES = [
	["数据元", ELEMENTS_PAGE],
	["业务术语", GLOSSARY_PAGE],
	["公共码表", REFERENCE_CODES_PAGE],
] as const;

test("foundation pages share the CompactTable pagination convention", () => {
	for (const [name, source] of PAGES) {
		assert.match(source, /CompactTable/, `${name} should use CompactTable`);
		assert.match(source, /pageSizeOptions:\s*\[10,\s*20,\s*50,\s*100\]/, `${name} should expose the same page sizes`);
		assert.match(source, /showTotal:\s*\(total\) => `共 \$\{total\} 条`/, `${name} should show total count`);
		assert.match(source, /setPageNum\(size !== pageSize \? 0 : page - 1\)/, `${name} should reset to page 1 when size changes`);
	}
});

test("foundation pages keep empty, permission, and detail affordances aligned", () => {
	for (const [name, source] of PAGES) {
		assert.match(source, /EmptyState title=/, `${name} should render EmptyState`);
		assert.match(source, /useGovernanceManageAccess/, `${name} should use the shared manage-access hook`);
		assert.match(source, /disabled=\{!canManage/, `${name} should disable management actions consistently`);
	}
	assert.match(ELEMENTS_PAGE, /<Drawer/);
	assert.match(GLOSSARY_PAGE, /<Drawer/);
	assert.match(REFERENCE_CODES_PAGE, /referenceOpen/);
});

test("foundation pages audit evidence is checked in with screenshots", () => {
	assert.ok(existsSync(AUDIT_DOC), "foundation-pages-audit.md should be checked in");
	const audit = readFileSync(AUDIT_DOC, "utf8");
	assert.match(audit, /数据元/);
	assert.match(audit, /业务术语/);
	assert.match(audit, /公共码表/);
	assert.match(audit, /分页/);
	assert.match(audit, /空态/);
	assert.match(audit, /权限/);
	assert.match(audit, /详情/);
	assert.match(audit, /豁免/);

	for (const filename of [
		"assets/it-8-f3-elements-page.png",
		"assets/it-8-f3-glossary-page.png",
		"assets/it-8-f3-reference-codes-page.png",
	]) {
		assert.ok(existsSync(new URL(filename, SPRINT_ROOT)), `${filename} should be checked in`);
	}
});
