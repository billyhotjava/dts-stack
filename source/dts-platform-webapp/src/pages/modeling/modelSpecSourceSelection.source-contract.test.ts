import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const drawer = readFileSync(new URL("./components/ModelSpecCreateDrawer.tsx", import.meta.url), "utf8");
const editor = readFileSync(new URL("./components/ModelSpecEditorFields.tsx", import.meta.url), "utf8");
const contract = readFileSync(new URL("./modelSpecV2Contract.ts", import.meta.url), "utf8");
const detail = readFileSync(new URL("./ModelSpecDetailPage.tsx", import.meta.url), "utf8");
const sourceInventory = readFileSync(new URL("./WarehousePlanSourcesTab.tsx", import.meta.url), "utf8");
const sourceRegistration = readFileSync(new URL("./warehousePlanSourceRegistration.ts", import.meta.url), "utf8");
const sourceManagerUrl = new URL("./components/ModelSpecSourceInventoryModal.tsx", import.meta.url);
const sourceManager = existsSync(sourceManagerUrl) ? readFileSync(sourceManagerUrl, "utf8") : "";

test("create and detail forms query the selected plan's canonical source inventory", () => {
	assert.match(drawer, /getWarehousePlanSources/);
	assert.match(detail, /getWarehousePlanSources/);
	assert.match(drawer, /selectableModelSpecSources/);
	assert.match(detail, /withPinnedExistingSources/);
});

test("switching plans invalidates prior source requests and clears selected bindings first", () => {
	assert.match(drawer, /sourceRequestRef/);
	assert.match(drawer, /form\.setFieldValue\("sources",\s*\[\]\)/);
	assert.match(drawer, /loadSources\(planId\)/);
});

test("closing or reopening the drawer invalidates stale plan-list requests", () => {
	assert.match(drawer, /planRequestRef/);
	assert.match(drawer, /const requestId\s*=\s*\+\+planRequestRef\.current/);
	assert.match(drawer, /requestId\s*!==\s*planRequestRef\.current/);
	assert.match(drawer, /planRequestRef\.current\s*\+=\s*1/);
});

test("model source identity is selected by business label and cannot be typed or forged", () => {
	assert.match(editor, /label="规划来源"/);
	assert.match(editor, /modelSpecSourceDraftFromChoice/);
	assert.doesNotMatch(editor, /label="来源登记 ID"/);
	assert.doesNotMatch(editor, /placeholder="来源盘点中的 UUID"/);
	assert.doesNotMatch(editor, /label="来源标识"[\s\S]{0,180}<Input/);
	assert.doesNotMatch(editor, /label="已确认版本"[\s\S]{0,180}<Input/);
});

test("the editor explains the ingestion boundary and links empty states to the canonical source inventory", () => {
	assert.match(editor, /连接.*元数据同步.*来源确认.*建模.*实现\/测试.*发布运行/);
	assert.match(editor, /不需要先完成\s*ETL\/ELT/);
	assert.match(editor, /这里选择的是上游输入，不是正在创建的目标表/);
	assert.match(editor, /草稿阶段可暂不选择/);
	assert.match(editor, /进入实现前.*已确认的物理来源.*锁定版本的上游模型/s);
	assert.match(editor, /buildWarehousePlanRoute/);
	assert.match(editor, /完善来源盘点/);
});

test("an empty plan source can be registered in the current model form and refreshes choices after save", () => {
	assert.match(editor, /onManageSources/);
	assert.match(editor, /在当前表单登记来源/);
	assert.match(drawer, /ModelSpecSourceInventoryModal/);
	assert.match(drawer, /sourceInventoryOpen/);
	assert.match(drawer, /return loadSources\(selectedPlanId\)/);
});

test("source management stays reachable after the first source is ready", () => {
	assert.match(editor, /<Text strong>上游输入来源<\/Text>[\s\S]{0,800}管理规划来源/);
});

test("FACT separates its target layer from optional and composable upstream input mappings", () => {
	assert.match(editor, /label="本模型产物分层"/);
	assert.match(editor, /modelType === "FACT" \|\| modelType === "SUMMARY" \|\| modelType === "APPLICATION"/);
	assert.match(editor, /上游模型（可补充或替代物理来源）/);
	assert.match(editor, /实现前至少一种，也可组合使用/);
	assert.doesNotMatch(editor, /明细表至少需要一个已确认来源/);
	assert.doesNotMatch(editor, /明细表保存前必须选择/);
});

test("the editor fixes each of the four model categories to its product layer and explains every stage gate", () => {
	assert.match(editor, /label="模型类别（四类表）"/);
	assert.match(editor, /label="本模型产物分层"/);
	assert.match(contract, /DIMENSION:\s*"DWD"/);
	assert.match(contract, /FACT:\s*"DWD"/);
	assert.match(contract, /SUMMARY:\s*"DWS"/);
	assert.match(contract, /APPLICATION:\s*"ADS"/);
	assert.match(editor, /MODEL_SPEC_TARGET_LAYER_BY_TYPE\[modelType\]/);
	assert.match(editor, /options=\{\[\{ value: targetLayer, label: `\$\{targetLayer\}（系统固定）` \}\]\}\s+disabled/);
	for (const stage of ["草稿：", "实现：", "发布："]) {
		assert.match(editor, new RegExp(stage));
	}
	assert.match(editor, /MODEL_TYPE_LIFECYCLE_DEPENDENCIES\[modelType\]/);
	assert.match(editor, /ODS\/STG 属于数据接入层/);
	assert.match(editor, /不是四类表之外的第五类模型/);
});

test("upstream choices enforce the type-layer matrix before a create or update request", () => {
	assert.match(drawer, /isModelSpecReferenceTargetAllowed/);
	assert.match(drawer, /modelType:\s*selectedModelType,\s*planId:\s*selectedPlanId/);
	assert.match(drawer, /"DEPENDENCY"/);
	assert.match(drawer, /disabled:\s*!allowed/);
	assert.match(detail, /isModelSpecReferenceTargetAllowed/);
	assert.match(detail, /isModelSpecReferenceTargetAllowed\(canonicalModel,\s*candidate,\s*"DEPENDENCY"\)/);
	assert.match(detail, /disabled:\s*!allowed/);
	assert.match(editor, /存在不符合当前模型类别依赖规则的上游模型/);
	assert.match(editor, /optionById\.get\(id\)\?\.disabled !== false/);
});

test("physical input layers exclude DWS and ADS from the source mapping selector", () => {
	assert.match(editor, /const layerOptions[^=]*=\s*\["ODS",\s*"STG",\s*"DWD"\]/);
	assert.match(contract, /MODEL_SPEC_UPSTREAM_LAYER_NOT_ALLOWED/);
});

test("historical canonical models outside the fixed target-layer matrix are explicit read-only records", () => {
	assert.match(detail, /targetLayerMismatch/);
	assert.match(detail, /MODEL_SPEC_TARGET_LAYER_BY_TYPE\[canonicalModel\.modelType\]\s*!==\s*canonicalModel\.layer/);
	assert.match(detail, /typeBoundaryMismatch/);
	assert.match(detail, /hasModelSpecTypeBoundaryMismatch\(canonicalModel\)/);
	assert.match(detail, /dependencyContractMismatch/);
	assert.match(detail, /!dependencyContractMismatch/);
	assert.match(detail, /类型专属字段或输入依赖不符合当前四类表规则，仅支持查看/);
});

test("switching away from DIMENSION clears generation-only input and maps validation back to its visible field", () => {
	assert.match(drawer, /generationStrategyType:\s*""/);
	assert.match(drawer, /generationStrategyReference:\s*""/);
	assert.match(drawer, /field === "generationStrategy"\) return "generationStrategyType"/);
	assert.match(detail, /field === "generationStrategy"\) return "generationStrategyType"/);
	assert.match(contract, /MODEL_SPEC_INPUT_KIND_NOT_ALLOWED/);
});

test("dependency metadata outages defer client-side dependency classification to the backend", () => {
	assert.match(detail, /dependencyMetadataLoaded/);
	assert.match(detail, /setDependencyMetadataLoaded\(false\)/);
	assert.match(detail, /setDependencyMetadataLoaded\(true\)/);
	assert.match(detail, /upstreamValidationAvailable=\{dependencyMetadataLoaded\}/);
	assert.match(editor, /upstreamValidationAvailable = true/);
	assert.match(editor, /if \(!upstreamValidationAvailable\) return/);
	assert.match(detail, /referenceMetadataLoaded\s*&&[\s\S]{0,180}canonicalModel\?\.dependsOn\.some/);
});

test("revision-pinned upstreams require an explicit action before adopting a newer revision", () => {
	assert.match(drawer, /revision:\s*model\.revision/);
	assert.match(detail, /revision:\s*candidate\.revision/);
	assert.match(editor, /adoptCurrentUpstreamRevisions/);
	assert.match(editor, /采用所选上游当前版本/);
	assert.match(editor, /form\.setFieldValue\("existingUpstreamPins"/);
});

test("analysis-dimension candidates are canonical DWD dimensions and historical bad refs are read-only", () => {
	assert.match(contract, /isModelSpecDimensionRefAllowed/);
	assert.match(contract, /isCanonicalModelSpecReferenceTarget/);
	assert.match(contract, /!hasModelSpecTypeBoundaryMismatch\(candidate\)/);
	assert.match(contract, /candidate\.sourceRefs\.every\(.*isModelSpecDirectInputLayerAllowed/);
	assert.match(drawer, /isModelSpecReferenceTargetAllowed/);
	assert.match(detail, /isModelSpecReferenceTargetAllowed/);
	assert.match(drawer, /isModelSpecReferenceTargetAllowed\([\s\S]{0,180}"DIMENSION"/);
	assert.match(detail, /isModelSpecReferenceTargetAllowed\([\s\S]{0,180}"DIMENSION"/);
	assert.match(drawer, /revision:\s*model\.revision/);
	assert.match(detail, /revision:\s*candidate\.revision/);
	assert.match(detail, /dimensionReferenceMismatch/);
	assert.match(detail, /canonicalModel\?\.dimensionRefs\.some/);
});

test("detail validates existing references against their exact pinned revisions and fails closed", () => {
	assert.match(detail, /getModelSpecRevision/);
	assert.match(detail, /modelSpecRevisionRefKey/);
	assert.match(detail, /const resolveModelSpecReferenceTargets/);
	assert.match(detail, /resolveModelSpecReferenceTargets\(detail\)/);
	assert.match(detail, /resolveModelSpecReferenceTargets\(updated\)/);
	assert.equal(
		detail.match(/refreshReferenceTargetsAfterSave\(updated,\s*pageRequestId\)/g)?.length,
		2,
		"both draft and standard-binding saves must refresh exact reference pins",
	);
	assert.equal(
		detail.match(
			/const updated = await updateModelSpec\([\s\S]*?\);\s*if \(pageRequestId !== loadRequestRef\.current\) return(?: false)?;\s*setModel\(updated\);/g,
		)?.length,
		2,
		"both save responses must be rejected before any stale model state is written",
	);
	assert.equal(
		detail.match(/if \(pageRequestId !== loadRequestRef\.current\) return(?: false)?;/g)?.length,
		4,
		"both success and error responses must be rejected after navigation",
	);
	assert.equal(
		detail.match(/if \(pageRequestId === loadRequestRef\.current\) setSaving\(false\);/g)?.length,
		2,
		"stale saves must not clear a newer page's saving state",
	);
	assert.match(
		detail,
		/const load = useCallback\(async \(\) => \{[\s\S]{0,220}setSaving\(false\)/,
		"a newly loaded model must reset saving state after invalidating the prior request",
	);
	assert.match(detail, /referenceTargets/);
	assert.match(detail, /referenceMetadataLoaded/);
	assert.match(detail, /referenceRequestRef/);
	assert.match(detail, /target\.id !== reference\.modelSpecId \|\| target\.revision !== reference\.revision/);
	assert.match(detail, /setReferenceTargets/);
	assert.match(detail, /setReferenceMetadataLoaded\(false\)/);
	assert.match(detail, /setReferenceMetadataLoaded\(true\)/);
	assert.match(detail, /isModelSpecReferenceTargetAllowed\(canonicalModel,\s*target,\s*"DEPENDENCY"\)/);
	assert.match(detail, /isModelSpecReferenceTargetAllowed\(canonicalModel,\s*target,\s*"DIMENSION"\)/);
	assert.doesNotMatch(detail, /availableModels\.find\(\(item\) => item\.id === reference\.modelSpecId\)/);
});

test("exact-reference transport failures stay read-only with a direct retry and are not mislabeled as migrations", () => {
	assert.match(detail, /referenceResolutionFailed/);
	assert.match(detail, /!referenceResolutionFailed[\s\S]{0,120}canonicalModel\?\.dependsOn\.some/);
	assert.match(detail, /暂时无法核验已锁定上游版本，页面已只读，请重试/);
	assert.match(detail, /onClick=\{\(\) => void load\(\)\}/);
	assert.match(detail, /referenceMetadataLoaded[\s\S]{0,120}!referenceResolutionFailed/);
});

test("persisted type-specific hidden fields force read-only instead of silent normalization", () => {
	assert.match(contract, /hasModelSpecTypeBoundaryMismatch/);
	assert.match(contract, /model\.modelType !== "DIMENSION" && model\.dimensionProfile != null/);
	assert.match(contract, /model\.modelType !== "FACT" && model\.factShape != null/);
	assert.match(contract, /model\.modelType !== "FACT" && model\.timeSemantics != null/);
	assert.match(contract, /model\.modelType !== "FACT" && isNonBlankString\(model\.businessActivityRef\)/);
	assert.match(contract, /model\.modelType !== "APPLICATION" && isNonBlankString\(model\.consumptionScenario\)/);
	assert.match(contract, /model\.modelType !== "FACT" && model\.dimensionRefs\.length > 0/);
	assert.match(detail, /typeBoundaryMismatch/);
	assert.match(detail, /类型专属字段或输入依赖不符合当前四类表规则，仅支持查看/);
});

test("revision-pinned dimension refs require a per-item explicit adoption action", () => {
	assert.match(editor, /adoptCurrentDimensionRevisions/);
	assert.match(editor, /采用所选维度当前版本/);
	assert.match(editor, /form\.setFieldValue\("existingDimensionPins"/);
});

test("the embedded source manager uses authoritative plan lifecycle and policy with an unknown-plan deny default", () => {
	assert.match(sourceManager, /getWarehousePlan/);
	assert.match(sourceManager, /getWarehousePlanPolicy/);
	assert.match(sourceManager, /plan\.lifecycleStatus !== "PUBLISHED"/);
	assert.match(sourceManager, /plan\.lifecycleStatus !== "ARCHIVED"/);
	assert.match(sourceManager, /Boolean\(plan\)/);
	assert.match(sourceManager, /policy\?\.value\.conceptualDesignAllowed === true/);
});

test("candidate assets stay in the embedded manager until their inventory decision is saved", () => {
	assert.match(sourceInventory, /requiresFurtherConfirmation/);
	assert.match(sourceInventory, /sourceInventoryRequiresFurtherConfirmation/);
	assert.match(sourceRegistration, /confirmationStatus === "CANDIDATE"/);
	assert.match(sourceManager, /const refreshedInventory\s*=\s*await onSaved\(\)/);
	assert.match(sourceManager, /if \(!refreshedInventory\)/);
	assert.match(sourceManager, /refreshParentSources\(result\.requiresFurtherConfirmation\)/);
	assert.match(sourceManager, /!writeRequiresFurtherConfirmation/);
	assert.match(sourceManager, /sourceInventoryRequiresFurtherConfirmation\(refreshedInventory\.bindings\)/);
	assert.match(drawer, /return inventory/);
	assert.match(detail, /return inventory/);
	assert.match(drawer, /onSaved=\{async \(\) => \{[\s\S]{0,180}return loadSources\(selectedPlanId\)/);
	assert.match(detail, /onSaved=\{async \(\) => \{[\s\S]{0,300}return loadSources\(canonicalModel\.planId/);
});

test("the embedded manager cannot be dismissed while a source mutation is running", () => {
	assert.match(sourceManager, /onSavingChange=\{setSourceSaving\}/);
	assert.match(sourceManager, /const sourceBusy\s*=\s*sourceSaving \|\| refreshingSources/);
	assert.match(sourceManager, /closable=\{!sourceBusy\}/);
	assert.match(sourceManager, /maskClosable=\{!sourceBusy\}/);
	assert.match(sourceManager, /keyboard=\{!sourceBusy\}/);
});

test("parent refresh retries are latest-only and keep the manager busy until authoritative state returns", () => {
	assert.match(sourceManager, /const refreshGuard\s*=\s*useMemo\(\(\) => createLatestRequestGuard\(\)/);
	assert.match(sourceManager, /const isCurrent\s*=\s*refreshGuard\.begin\(\)/);
	assert.match(sourceManager, /refreshGuard\.invalidate\(\)/);
	assert.match(sourceManager, /refreshingSources/);
	assert.match(sourceManager, /loading=\{refreshingSources\}/);
});

test("unique system codes are generated and rendered read-only", () => {
	assert.match(drawer, /createDimensionSystemCode/);
	assert.match(editor, /nextDimensionHierarchyCode/);
	assert.match(editor, /label="维度系统编码"/);
	assert.match(editor, /系统生成，保存后不可修改/);
	assert.doesNotMatch(editor, /请输入稳定的维度编码/);
});

test("source inventory keeps the binding UUID internal now that model forms use association lookup", () => {
	assert.doesNotMatch(sourceInventory, /绑定标识：\{binding\.bindingId\}/);
	assert.match(sourceInventory, /系统自动关联/);
});

test("source drift requires explicit adoption and never silently upgrades another save path", () => {
	assert.match(editor, /modelSpecSourceMatchesChoice/);
	assert.match(editor, /采用当前版本/);
	assert.match(detail, /modelSpecSourcesAreCurrent/);
	assert.doesNotMatch(detail, /reconcileModelSpecSources/);
	assert.doesNotMatch(drawer, /reconcileModelSpecSources/);
	assert.match(detail, /const persistedSourceVerificationPending =/);
	assert.match(detail, /canEdit=\{canEdit && !persistedSourceVerificationPending && persistedSourcesCurrent\}/);
});

test("source verification blocks saves only when the draft actually selected physical sources", () => {
	assert.match(drawer, /const selectedSources = Form\.useWatch\("sources", form\) \|\| \[\];/);
	assert.match(drawer, /loadingSources && selectedSources\.length > 0/);
	assert.match(detail, /const sourceVerificationPending = sourceLoading && selectedSources\.length > 0;/);
	assert.match(detail, /if \(!canonicalModel \|\| !canEdit \|\| sourceVerificationPending\) return;/);
	assert.match(detail, /disabled=\{sourceVerificationPending\}/);
});

test("source loading distinguishes empty, forbidden and recoverable provider failures", () => {
	assert.match(drawer, /modelSpecSourceInventoryState/);
	assert.match(detail, /modelSpecSourceInventoryState/);
	assert.match(drawer, /modelSpecSourcePermissionDenied/);
	assert.match(detail, /modelSpecSourcePermissionDenied/);
	assert.match(drawer, /当前账号无权读取计划来源/);
	assert.match(detail, /当前账号无权核验规划来源/);
	assert.match(drawer, /当前计划尚未登记具体来源.*在当前表单登记来源.*已验证连接/s);
	assert.match(detail, /当前计划尚未登记具体来源.*在当前表单登记来源.*已验证连接/s);
});
