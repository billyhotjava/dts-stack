import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (url: URL) => readFileSync(url, "utf8");
const DETAIL = read(new URL("./ModelSpecDetailPage.tsx", import.meta.url));
const SQL = read(new URL("./SqlModelingPage.tsx", import.meta.url));
const OPS = read(new URL("../ops/OpsInstancesPage.tsx", import.meta.url));
const API = read(new URL("../../api/modelSpecApi.ts", import.meta.url));

test("implementation handoff preserves canonical model identity and revision", () => {
	assert.match(DETAIL, /implementationPath/);
	assert.match(DETAIL, /modelSpecId=.*revision=.*implementationMode=/);
	assert.match(DETAIL, /implementationRevision=\$\{currentImplementationRevision\}/);
	assert.match(DETAIL, /model-spec-advanced-implementation-recovery/);
	assert.match(DETAIL, /advancedEntryDisabled=\{!advancedImplementationReady\}/);
	assert.match(DETAIL, /返回数据实现/);
	assert.match(SQL, /canonical-model-implementation-context/);
	assert.match(SQL, /requestedModelSpecId/);
	assert.match(SQL, /requestedImplementationRevision/);
	assert.match(SQL, /getModelLifecycle\(requestedModelSpecId\)/);
	assert.match(SQL, /implementation\.revision !== model\.revision/);
	assert.match(SQL, /implementation\.implementationRevision/);
	assert.match(SQL, /window\.location\.hash\.indexOf\("\?"\)/);
	assert.match(API, /lifecycleUrl\(expected\.id, "\/compile"\)/);
	assert.match(API, /lifecycleUrl\(expected\.id, "\/tests"\)/);
	assert.match(API, /lifecycleUrl\(expected\.id, "\/publish"\)/);
});

test("failed dbt ops returns to the exact implementation revision recovery route", () => {
	assert.match(OPS, /searchParams\.get\("modelSpecId"\)/);
	assert.match(OPS, /searchParams\.get\("implementationRevision"\)/);
	assert.match(OPS, /ops-return-model-repair/);
	assert.match(OPS, /activeStage: "implementation"/);
	assert.match(OPS, /params\.set\("revision", revision\)/);
	assert.match(OPS, /params\.set\("implementationRevision", implementationRevision\)/);
	assert.doesNotMatch(OPS, /\?tab=design/);
});

test("ordinary editor cannot overwrite a pinned DBT-managed artifact and exposes explicit STG ephemeral", () => {
	const DRAWER = read(new URL("./components/ModelEditDrawer.tsx", import.meta.url));
	assert.match(DRAWER, /dbt-managed-artifact-non-overwrite/);
	assert.match(DRAWER, /DBT 托管用户产物不能由普通编辑模式覆盖/);
	assert.match(DRAWER, /implementation revision/);
	assert.match(DRAWER, /value: "ephemeral"/);
	assert.match(DRAWER, /STG 临时节点/);
	assert.match(SQL, /普通编辑模式不能覆盖/);
	assert.match(SQL, /普通 SQL 保存不能覆盖/);
	const PHYSICAL = read(new URL("./components/ModelSpecPhysicalAssetStage.tsx", import.meta.url));
	assert.match(PHYSICAL, /advancedEntryDisabled/);
	assert.match(PHYSICAL, /model-spec-physical-advanced-entry-locked/);
	assert.match(PHYSICAL, /disabled=\{advancedEntryDisabled\}/);
	assert.match(PHYSICAL, /onReturnToImplementation/);
});
