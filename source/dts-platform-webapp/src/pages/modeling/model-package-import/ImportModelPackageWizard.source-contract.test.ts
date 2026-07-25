import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const wizard = readFileSync(new URL("./ImportModelPackageWizard.tsx", import.meta.url), "utf8");
const state = readFileSync(new URL("./modelPackageImportState.ts", import.meta.url), "utf8");
const workbench = readFileSync(new URL("../ModelingWorkbenchPage.tsx", import.meta.url), "utf8");
const modelCenter = readFileSync(new URL("../ModelCenterPage.tsx", import.meta.url), "utf8");
const api = readFileSync(new URL("../../../api/modelSpecImportApi.ts", import.meta.url), "utf8");

test("workbench and model center reuse one controlled model-package import wizard", () => {
	assert.match(workbench, /<ImportModelPackageWizard/);
	assert.match(workbench, /lockedPlanId=\{selectedPlanId/);
	assert.match(modelCenter, /<ImportModelPackageWizard/);
	assert.match(modelCenter, /导入模型包/);
	assert.doesNotMatch(workbench, /previewModelSpecImport|applyModelSpecImport/);
	assert.doesNotMatch(modelCenter, /previewModelSpecImport|applyModelSpecImport/);
});

test("wizard implements the four frozen steps, disabled blockers and durable result actions", () => {
	for (const label of ["上传 dbt ZIP", "建设上下文", "预检确认", "导入结果"]) assert.match(wizard, new RegExp(label));
	assert.match(wizard, /isSelectablePreviewItem/);
	assert.match(wizard, /isPreviewApplicable/);
	assert.match(wizard, /仅重试失败项/);
	assert.match(wizard, /查看模型/);
	assert.match(wizard, /getModelSpecImportPreviewRun/);
	assert.match(wizard, /getModelSpecImportApplyResult/);
	assert.match(wizard, /preview\.planId !== lockedPlanId/);
	assert.match(wizard, /resolveModelPackageImportIdempotencySlot/);
});

test("wizard accepts a dbt ZIP, inspects it into the internal package, then previews that contract", () => {
	assert.match(wizard, /accept="\.zip,application\/zip"/);
	assert.match(wizard, /inspectDbtModelArchive/);
	assert.match(wizard, /createConvertedModelPackage/);
	assert.match(wizard, /validateDbtModelPackageArchive/);
	assert.match(wizard, /上传 dbt ZIP 后，系统会自动解析并转换为内部模型包/);
	assert.match(wizard, /最大 32 MiB/);
	assert.match(wizard, /MODEL_IMPORT_ARCHIVE_UNSAFE_PATH/);
	assert.match(wizard, /MODEL_IMPORT_ARCHIVE_LENGTH_REQUIRED/);
	assert.match(wizard, /MODEL_IMPORT_ARCHIVE_MANIFEST_MISSING/);
	assert.match(wizard, /MODEL_IMPORT_ARCHIVE_SQL_MISSING/);
	assert.match(wizard, /<Archive/);
	assert.doesNotMatch(wizard, /accept="\.json,application\/json"/);
	assert.doesNotMatch(wizard, /file\.text\(\)/);
});

test("raw model package never crosses sessions and is cleared after preview or exit", () => {
	assert.doesNotMatch(state, /activeModelPackageImportSession|restoreModelPackageImportState/);
	assert.match(state, /case "PREVIEW_SUCCEEDED"/);
	assert.match(state, /modelPackage: null/);
	assert.match(wizard, /SENSITIVE_CLEARED/);
	assert.match(wizard, /createModelPackageImportState/);
});

test("a completed preview restarts from ZIP upload instead of returning to a cleared context package", () => {
	assert.match(wizard, /state\.step === 2 \? \(/);
	assert.match(wizard, /重新上传并预检/);
	assert.match(wizard, /onClick=\{invalidatePreview\}/);
	assert.match(wizard, /type: "PREVIEW_INVALIDATED"/);
});

test("unknown backend messages are not rendered and technical identifiers stay collapsed", () => {
	assert.doesNotMatch(wizard, /response\?\.message|response\.message|issue\.message/);
	assert.match(wizard, /stableImportMessages/);
	assert.match(wizard, /sanitizeModelImportDiagnosticCode/);
	assert.match(wizard, /state\.diagnosticCode/);
	assert.match(wizard, /label: "技术详情"/);
});

test("mobile preview renders sanitized issues and keeps code, field and dbt identity in technical details", () => {
	assert.match(wizard, /-mobile-technical/);
	assert.match(wizard, /issueMessage\(issue\)/);
	assert.match(wizard, /问题代码：\{issue\.code\}/);
	assert.match(wizard, /字段：\$\{issue\.fieldPath\}/);
	assert.match(wizard, /dbt 节点标识：\{item\.dbtUniqueId\}/);
});

test("client calls only the canonical preview, apply, query and retry endpoints", () => {
	assert.match(api, /\/dbt\/archive\/inspect/);
	assert.match(api, /\/dbt\/preview/);
	assert.match(api, /\/dbt\/apply/);
	assert.match(api, /encodeURIComponent\(runId\)/);
	assert.match(api, /getModelSpecImportApplyResult/);
	assert.match(api, /`\$\{MODEL_SPEC_IMPORT_RESOURCE\}\/\$\{encodeURIComponent\(runId\)\}\/apply`/);
	assert.match(api, /\/retry/);
	assert.doesNotMatch(api, /latestAttempt|applyResult/);
	assert.doesNotMatch(api, /as any/);
	assert.doesNotMatch(api, /vnext\/dbt\/import/);
});
