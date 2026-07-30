import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const pageUrl = new URL("./ModelSpecDetailPage.tsx", import.meta.url);
const canvasUrl = new URL("./components/ModelSpecEditorCanvas.tsx", import.meta.url);
const headerUrl = new URL("./components/ModelSpecDetailHeader.tsx", import.meta.url);
const noticesUrl = new URL("./components/ModelSpecDetailNotices.tsx", import.meta.url);
const issuePathUrl = new URL("./modelSpecIssueFieldPath.ts", import.meta.url);
const read = (url: URL) => readFileSync(url, "utf8");

test("model detail converges the three-stage journey into one logical canvas with scoped drawers", () => {
	for (const url of [canvasUrl, headerUrl, noticesUrl]) {
		assert.equal(existsSync(url), true, `${url.pathname} is missing`);
	}
	const page = read(pageUrl);
	const canvas = read(canvasUrl);

	assert.match(page, /ModelSpecEditorCanvas/);
	assert.match(page, /ModelSpecDetailHeader/);
	assert.match(page, /ModelSpecDetailNotices/);
	assert.doesNotMatch(page, /aria-label="模型阶段"/);
	assert.match(canvas, /data-testid="model-spec-editor-canvas"/);
	assert.match(canvas, /data-testid="model-spec-logical-canvas"/);
	assert.match(canvas, /Drawer[\s\S]*数据实现/);
	assert.match(canvas, /Drawer[\s\S]*发布结果/);
	assert.match(canvas, /onStageChange\("logical"\)/);
	assert.doesNotMatch(canvas, /from ["']@\/api\//);
});

test("the orchestrator page is split below the giant-page threshold without changing canonical APIs", () => {
	const page = read(pageUrl);
	assert.ok(page.split("\n").length <= 800, `ModelSpecDetailPage remains ${page.split("\n").length} lines`);
	assert.match(page, /getModelSpecStageGates/);
	assert.match(page, /updateModelSpec/);
	assert.match(page, /ModelSpecImplementationStage/);
	assert.match(page, /ModelSpecPhysicalAssetStage/);
});

test("the active drawer owns its gated primary action instead of hiding it behind the mask", () => {
	const page = read(pageUrl);
	const canvas = read(canvasUrl);

	assert.match(page, /primaryAction=\{activeStage === "logical" \? stageProjection\?\.primaryAction : undefined\}/);
	assert.match(page, /drawerAction=\{/);
	assert.match(canvas, /extra=\{activeStage === "implementation" \? activeDrawerAction : null\}/);
	assert.match(canvas, /extra=\{activeStage === "physical" \? activeDrawerAction : null\}/);
	assert.match(canvas, /ModelSpecPrimaryActionButton/);
});

test("client validation keeps a visible recovery message and moves to the first exact field error", () => {
	const page = read(pageUrl);
	const issuePath = read(issuePathUrl);

	assert.match(page, /handleModelSpecFormValidationError\(error, form, setSaveError\)/);
	assert.match(issuePath, /"errorFields" in error[\s\S]*onVisibleError\("请补齐标红字段后再保存"\)/);
	assert.match(issuePath, /form\.scrollToField\(firstError\.name/);
});
