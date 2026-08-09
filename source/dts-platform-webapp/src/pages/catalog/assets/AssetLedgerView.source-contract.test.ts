import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./AssetLedgerView.tsx", import.meta.url), "utf8");

test("asset ledger exposes a row-level access-request entry with deep-link params", () => {
	assert.match(SOURCE, /title: "操作"/);
	assert.match(SOURCE, /dataset-access-approval\?action=new&assetId=/);
	assert.match(SOURCE, /assetType=dataset/);
	assert.match(SOURCE, /row-request-access/);
	assert.match(SOURCE, /申请权限/);
});
