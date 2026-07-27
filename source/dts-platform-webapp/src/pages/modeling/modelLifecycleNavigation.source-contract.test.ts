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
	assert.match(DETAIL, /advancedEntryDisabled=\{!advancedImplementationReady\}/);
	assert.match(DETAIL, /model-spec-delivery-intents/);
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

test("advanced SQL workspace binds ModelSpec identity and freezes only after lifecycle evidence", () => {
	const DRAWER = read(new URL("./components/ModelEditDrawer.tsx", import.meta.url));
	assert.match(DRAWER, /dbt-managed-artifact-non-overwrite/);
	assert.match(DRAWER, /当前实现版本已有证据，不能原地覆盖/);
	assert.match(DRAWER, /implementationRevision/);
	assert.match(DRAWER, /value: "ephemeral"/);
	assert.match(DRAWER, /STG 临时节点/);
	assert.match(SQL, /model\.modelSpecId.*requestedModelSpecId/);
	assert.match(SQL, /lifecycleRevisionFrozen/);
	assert.match(SQL, /await syncDbtModels\(\)/);
	assert.match(SQL, /await reloadLifecycleContext\(\)/);
	assert.match(SQL, /ModelSpec lifecycle timeline 未返回本次构建证据/);
	assert.match(SQL, /modelSpecId: editingModel\?\.modelSpecId \|\| requestedModelSpecId/);
	const IMPLEMENTATION = read(new URL("./components/ModelSpecImplementationStage.tsx", import.meta.url));
	const PHYSICAL = read(new URL("./components/ModelSpecPhysicalAssetStage.tsx", import.meta.url));
	assert.match(IMPLEMENTATION, /advancedEntryDisabled/);
	assert.match(IMPLEMENTATION, /disabled=\{advancedEntryDisabled\}/);
	assert.match(IMPLEMENTATION, /ModelDeliveryIntentActions/);
	assert.doesNotMatch(PHYSICAL, /advancedEntryDisabled|onReturnToImplementation/);
});

test("bound SQL runs wait for one exact model and fail closed before lifecycle evidence", () => {
	assert.match(SQL, /const ensureLifecycleRunSelection = async/);
	assert.match(SQL, /已绑定 ModelSpec 的 SQL 模型必须从数据实现阶段进入后再运行/);
	assert.match(SQL, /selectedModels\.length !== 1/);
	assert.match(SQL, /selectedModel\?\.modelSpecId \|\| ""\) !== requestedModelSpecId/);
	assert.match(SQL, /await ensureLifecycleRunSelection\(selectedModels\)/);
	assert.match(SQL, /await ensureLifecycleRunSelection\(operationModels\)/);
	assert.match(SQL, /await waitForBuildResult\(/);
	assert.match(SQL, /settled\.timedOut/);
	assert.match(SQL, /normalizeUpper\(completed\.status\) !== "SUCCESS"/);
	assert.match(SQL, /await syncLifecycleBuildEvidence\(operation, completed\)/);
	assert.match(
		SQL,
		/if \(settled\.timedOut\) \{\s*toast\.warning\(`dbt \$\{operation\} 仍在运行，请稍后刷新结果`\);\s*return;\s*\}\s*const finalStatus/,
	);
});

test("canonical build and publication use shared intents without automatic human actions", () => {
	const ACTIONS = read(new URL("./components/ModelDeliveryIntentActions.tsx", import.meta.url));
	assert.match(SQL, /ModelDeliveryIntentActions/);
	assert.match(SQL, /requestedModelSpecId && lifecycleModel/);
	assert.doesNotMatch(SQL, /openRun\("release"\)/);
	assert.doesNotMatch(SQL, /submitModelReview|approveModelReview|publishModelLifecycle/);
	assert.match(ACTIONS, /startModelBuildIntent/);
	assert.match(ACTIONS, /startModelPublicationIntent/);
	assert.match(ACTIONS, /candidate\.origin !== "SINGLE_MODEL_INTENT"/);
	assert.match(ACTIONS, /candidate\.entries\.length !== 1/);
	assert.match(ACTIONS, /REPLACEABLE_TERMINAL_STATUSES/);
	assert.match(ACTIONS, /"CANCELLED"/);
	assert.match(ACTIONS, /发布不等于上线完成/);
	assert.match(API, /\/build-intents/);
	assert.match(API, /\/publish-intents/);
	assert.match(API, /"If-Match": toModelSpecEtag\(expected\)/);
	assert.match(API, /headers: releaseCandidateWriteHeaders\(idempotencyKey, expected\)/);
});

test("bound SQL drafts require a loaded matching unfrozen lifecycle context", () => {
	assert.match(SQL, /const lifecycleSaveBlocked =/);
	assert.match(SQL, /lifecycleLoading/);
	assert.match(SQL, /lifecycleModel\?\.id !== activeModelSpecId/);
	assert.match(SQL, /lifecycleImplementation\?\.modelSpecId !== activeModelSpecId/);
	assert.match(SQL, /lifecycleRevisionFrozen/);
	assert.match(SQL, /disabled=\{!activeModel \|\| !sqlDirty \|\| !workspaceOk \|\| lifecycleSaveBlocked\}/);
	assert.match(SQL, /toast\.error\(normalizeText\(err\?\.message\) \|\| "SQL 保存失败"\)/);
});

test("ops navigation never substitutes a SQL workspace id for ModelSpec identity", () => {
	assert.match(SQL, /const contextModelSpecId = requestedModelSpecId \|\| activeModelSpecId/);
	assert.match(SQL, /if \(contextModelSpecId\) \{/);
	assert.match(SQL, /params\.set\("modelSpecId", contextModelSpecId\)/);
	assert.doesNotMatch(SQL, /requestedModelSpecId \|\| String\(activeModel\?\.id/);
	assert.match(SQL, /router\.push\(buildOpsInstanceRoute\(dagRunId\)\)/);
});
