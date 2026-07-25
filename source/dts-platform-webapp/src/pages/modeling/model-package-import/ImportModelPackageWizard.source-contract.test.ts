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
	for (const label of ["上传模型包", "建设上下文", "预检确认", "导入结果"]) assert.match(wizard, new RegExp(label));
	assert.match(wizard, /isSelectablePreviewItem/);
	assert.match(wizard, /isPreviewApplicable/);
	assert.match(wizard, /仅重试失败项/);
	assert.match(wizard, /查看模型/);
	assert.match(wizard, /getModelSpecImportPreviewRun/);
	assert.match(wizard, /getModelSpecImportApplyResult/);
	assert.match(wizard, /preview\.planId !== lockedPlanId/);
	assert.match(wizard, /resolveModelPackageImportIdempotencySlot/);
});

test("raw model package never crosses sessions and is cleared after preview or exit", () => {
	assert.doesNotMatch(state, /activeModelPackageImportSession|restoreModelPackageImportState/);
	assert.match(state, /case "PREVIEW_SUCCEEDED"/);
	assert.match(state, /modelPackage: null/);
	assert.match(wizard, /SENSITIVE_CLEARED/);
	assert.match(wizard, /createModelPackageImportState/);
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
