import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const DIRECTORY = readFileSync(new URL("./DataSearchPage.tsx", import.meta.url), "utf8");
const LEDGER = readFileSync(new URL("./assets/AssetLedgerView.tsx", import.meta.url), "utf8");
const QUERY = readFileSync(new URL("./assets/assetV2Query.ts", import.meta.url), "utf8");
const DETAIL = readFileSync(new URL("./DatasetDetailPage.tsx", import.meta.url), "utf8");
const DELIVERY_STATUS = readFileSync(new URL("./assets/AssetDeliveryStatusPanel.tsx", import.meta.url), "utf8");
const MODEL_SYNC = readFileSync(
	new URL("../data-modeling/prototype/ModelServingSyncStatus.tsx", import.meta.url),
	"utf8",
);

test("asset directory persists eligibility serving and quality filters in the URL", () => {
	for (const field of ["eligibility", "servingStatus", "qualityStatus"]) {
		assert.match(DIRECTORY, new RegExp(`searchParams\\.get\\("${field}"\\)`));
		assert.match(DIRECTORY, new RegExp(`setFilterParam\\(next, "${field}"`));
		assert.match(QUERY, new RegExp(`${field}: filters\\.${field}`));
	}
});

test("asset ledger exposes the three delivery facts without introducing another workbench", () => {
	assert.match(LEDGER, /title: "消费资格"/);
	assert.match(LEDGER, /title: "服务状态"/);
	assert.match(LEDGER, /title: "质量状态"/);
	assert.doesNotMatch(LEDGER, /AssetStatusWorkbench|资产状态工作台/);
});

test("asset detail links model evidence back to the existing modeling workbench", () => {
	const source = `${DETAIL}\n${DELIVERY_STATUS}`;
	assert.match(source, /modelRefs/);
	assert.match(source, /\/modeling\/models\//);
	assert.match(source, /消费资格/);
	assert.match(source, /服务同步/);
	assert.match(MODEL_SYNC, /查看资产/);
	assert.match(MODEL_SYNC, /\/catalog\/datasets\//);
});
