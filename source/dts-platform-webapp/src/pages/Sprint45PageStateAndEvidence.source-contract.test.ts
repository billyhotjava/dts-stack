import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const WORKBENCH_SOURCE = readFileSync(new URL("./workbench/DataManagementWorkbenchPage.tsx", import.meta.url), "utf8");
const DATASOURCES_SOURCE = readFileSync(new URL("./foundation/access/AccessWorkspace.tsx", import.meta.url), "utf8");
const ASSETS_SOURCE = readFileSync(new URL("./catalog/DatasetsPage.tsx", import.meta.url), "utf8");
const API_SOURCE = readFileSync(new URL("./services/ApiServicesPage.tsx", import.meta.url), "utf8");
const TOKENS_SOURCE = readFileSync(new URL("./services/TokensPage.tsx", import.meta.url), "utf8");
const OPS_SOURCE = readFileSync(new URL("./ops/OpsInstancesPage.tsx", import.meta.url), "utf8");
const IT_README_SOURCE = readFileSync(
	new URL("../../../../worklog/v2.2.3/sprint-45-202606/it/README.md", import.meta.url),
	"utf8",
);

const CORE_PAGE_SOURCES = [
	WORKBENCH_SOURCE,
	DATASOURCES_SOURCE,
	ASSETS_SOURCE,
	API_SOURCE,
	TOKENS_SOURCE,
	OPS_SOURCE,
];

test("Sprint-45 core pages use a shared page shell, real states and table/detail components", () => {
	for (const source of CORE_PAGE_SOURCES) {
		assert.match(source, /PageHeader|PlatformPageHero/);
		assert.match(source, /CompactTable|Card|RecordDetailDrawer|Drawer/);
		assert.match(source, /EmptyState|empty|暂无|Alert|ErrorNotice|Spin|loading/);
	}
});

test("Sprint-45 disabled or deferred actions explain the missing next step", () => {
	for (const source of [API_SOURCE, TOKENS_SOURCE, OPS_SOURCE]) {
		assert.match(source, /disabled/);
		assert.match(source, /title=|Alert|message=/);
	}
	assert.match(OPS_SOURCE, /后端/);
});

test("Sprint-45 verification plan records build, contract and Playwright screenshot evidence", () => {
	for (const label of ["source-contract", "pnpm build", "Playwright", "截图证据"]) {
		assert.match(IT_README_SOURCE, new RegExp(label));
	}
});

test("Sprint-45 screenshot evidence directories exist for every feature track", () => {
	for (const dir of [
		"F1-navigation",
		"F2-workbench",
		"F3-ingestion-studio",
		"F4-governance-catalog",
		"F5-bi-screens",
		"F6-service-ops",
		"F7-ui-contract",
	]) {
		assert.equal(
			existsSync(new URL(`../../../../worklog/v2.2.3/sprint-45-202606/it/evidence/${dir}`, import.meta.url)),
			true,
			`${dir} evidence directory is missing`,
		);
	}
});
