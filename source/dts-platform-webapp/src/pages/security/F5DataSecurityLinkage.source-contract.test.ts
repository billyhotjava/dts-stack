import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SECURITY_SOURCE = readFileSync(new URL("./data-security.tsx", import.meta.url), "utf8");
const ASSET_LEDGER_SOURCE = readFileSync(new URL("../catalog/assets/AssetLedgerView.tsx", import.meta.url), "utf8");

test("F5-T04 asset ledger links directly into dataset security binding", () => {
	assert.match(ASSET_LEDGER_SOURCE, /\/security\/data-security\?tab=datasetSecurity&datasetId=\$\{row\.id\}/);
	assert.match(ASSET_LEDGER_SOURCE, /分级分类|密级/);
});

test("F5-T04 data-security page is deep-linkable to dataset security binding tab", () => {
	assert.match(SECURITY_SOURCE, /useSearchParams/);
	assert.match(SECURITY_SOURCE, /searchParams\.get\("tab"\)\s*\|\|\s*"classification"/);
	assert.match(SECURITY_SOURCE, /searchParams\.get\("datasetId"\)/);
	assert.match(SECURITY_SOURCE, /activeKey=\{activeTab\}/);
	assert.match(SECURITY_SOURCE, /onChange=\{handleTabChange\}/);
});

test("F5-T04 binding asset command is an active navigation command, not a disabled placeholder", () => {
	assert.match(SECURITY_SOURCE, /绑定资产/);
	assert.match(SECURITY_SOURCE, /setActiveDatasetSecurityTab/);
	assert.doesNotMatch(SECURITY_SOURCE, /<Button disabled title="请在数据集安全字段页选择数据集后保存密级字段和部门字段">\s*绑定资产\s*<\/Button>/);
});

test("F5-T04 classification mapping exposes batch import and export commands", () => {
	assert.match(SECURITY_SOURCE, /import Papa from "papaparse"/);
	assert.match(SECURITY_SOURCE, /importClassificationMapping/);
	assert.match(SECURITY_SOURCE, /exportClassificationMapping/);
	assert.match(SECURITY_SOURCE, /批量导入/);
	assert.match(SECURITY_SOURCE, /导出映射/);
	assert.match(SECURITY_SOURCE, /Papa\.parse/);
	assert.match(SECURITY_SOURCE, /handleImportMappingBatch/);
	assert.match(SECURITY_SOURCE, /downloadClassificationMappingCsv/);
});
