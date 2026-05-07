import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const screensPagePath = new URL("./ScreensPage.tsx", import.meta.url);

test("ScreensPage does not depend on removed legacy page shell classes", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.equal(source.includes('className="page-container"'), false);
	assert.equal(source.includes('className="page-content"'), false);
});

test("ScreensPage opens editor in a new window from the management list", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.match(
		source,
		/window\.open\(resolveRouteForOpen\(`\/bi\/screens\/\$\{id\}\/edit`\), '_blank', 'noopener,noreferrer'\)/,
	);
});

test("ScreensPage hides management actions when row permissions do not allow them", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.match(source, /rowPermissions\.canEdit \? \(/);
	assert.match(source, /rowPermissions\.canManage \? \(/);
	assert.match(source, /rowPermissions\.canDelete \? \(/);
});

// Sprint-24 F3 回归：导入 JSON 缺失合法 classification 时必须先弹 IntakeModal，
// 否则后端会以 400 拒绝（must be one of PUBLIC/INTERNAL/SECRET/CONFIDENTIAL）。
test("ScreensPage import flow gates on classification before calling createScreen", async () => {
	const source = await readFile(screensPagePath, "utf8");

	// 必须读取并校验 importPreview.parsedSpec.classification
	assert.match(source, /importPreview\.parsedSpec[^)]*\)\.classification/);
	// 合法集合必须覆盖后端要求的四个枚举值
	assert.match(source, /'PUBLIC',\s*'INTERNAL',\s*'SECRET',\s*'CONFIDENTIAL'/);
	// 缺失合法密级时必须打开 importIntakeOpen，而不是直接调用 createScreen
	assert.match(source, /setImportIntakeOpen\(true\)/);
	// import-flow 专用的 IntakeModal 必须存在
	assert.match(source, /open=\{importIntakeOpen\}/);
});

// Sprint-24 F4 回归：大屏密级合规盘点入口必须对治理角色开放，
// 与后端 MetabaseAuth.SCREEN_AUDITOR_ROLES 保持一致。
test("ScreensPage unclassified audit gate accepts all governance roles", async () => {
	const source = await readFile(screensPagePath, "utf8");

	// 五个非 superuser 治理角色必须全部出现在前端守卫里
	assert.match(source, /'OP_ADMIN'/);
	assert.match(source, /'INST_DATA_OWNER'/);
	assert.match(source, /'DEPT_DATA_OWNER'/);
	assert.match(source, /'INST_LEADER'/);
	assert.match(source, /'DEPT_LEADER'/);
	// superuser 也必须放行
	assert.match(source, /'SUPERUSER'/);
	// 按钮文案改成「大屏盘点」（不是「裸屏盘点」）
	assert.match(source, /大屏盘点/);
	assert.equal(source.includes("裸屏"), false);
});
