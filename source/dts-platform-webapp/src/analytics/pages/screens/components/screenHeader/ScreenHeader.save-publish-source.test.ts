import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const screenHeaderPath = new URL("./ScreenHeader.tsx", import.meta.url);

test("ScreenHeader validates and saves the draft before publishing", async () => {
	const source = await readFile(screenHeaderPath, "utf8");

	assert.match(source, /const payload = buildScreenPayload\(persistedConfig\)/);
	assert.match(source, /const validation = validateScreenPayload\(payload\)/);
	assert.match(source, /const screenId = await saveScreen\(\)/);
	assert.match(source, /await analyticsApi\.publishScreen\(screenId\)/);
});

test("ScreenHeader does not expose editor security governance placeholders", async () => {
	const source = await readFile(screenHeaderPath, "utf8");

	assert.equal(source.includes("tools-security"), false);
	assert.equal(source.includes("安全与治理"), false);
	assert.equal(source.includes("ScreenSharePanel"), false);
	assert.equal(source.includes("ScreenAuditPanel"), false);
	assert.equal(source.includes("ScreenCompliancePanel"), false);
});

// 回归：ScreenHeader.tsx 含 @ts-nocheck，TypeScript 不会查 ReferenceError。
// 用过的 helpers 必须显式 import，否则运行时 ReferenceError 把整个编辑器打挂。
test("ScreenHeader explicitly imports every helper symbol it references", async () => {
	const source = await readFile(screenHeaderPath, "utf8");

	// THEME_OPTIONS 在 normalizeTheme + themeLabel 处使用，必须列在 from './helpers' 的 import 里
	assert.match(source, /THEME_OPTIONS/);
	assert.match(
		source,
		/import\s*\{[^}]*\bTHEME_OPTIONS\b[^}]*\}\s*from\s*['"]\.\/helpers['"];?/,
	);
});

// Sprint-24 F3：未设密级的大屏（含历史老大屏）应给出非阻塞提示，引导补登。
// 不能再用「打不开就报 ReferenceError」这种破体验作为提醒。
test("ScreenHeader shows a non-blocking notice when classification is missing", async () => {
	const source = await readFile(screenHeaderPath, "utf8");

	assert.match(source, /analytics-screen-header-classification-missing/);
	assert.match(source, /!config\.classification && permissions\.canEdit/);
});

// Sprint-24 F3：classification 走专属 PATCH 端点。后端 PUT /{id} 故意不接受
// classification（owner-only / 降级 reason / 独立审计），所以前端 saveScreen 必须
// 在检测到 classification 相对 baseline 有变化时，先调 PATCH /classification，
// 否则用户在属性面板改的密级不会落库 → 看起来「保存失败」。
test("ScreenHeader saveScreen patches classification before PUT when changed", async () => {
	const source = await readFile(screenHeaderPath, "utf8");

	// 必须从 baseline 读取原 classification 做 diff
	assert.match(source, /baseline as \{[^}]*classification[^}]*\}/);
	// 必须比较新值与基线值
	assert.match(source, /nextClassification && nextClassification !== baseClassification/);
	// 必须调用 updateScreenClassification PATCH 端点
	assert.match(source, /analyticsApi\.updateScreenClassification\(\s*id\s*,\s*nextClassification/);
	// 403 / 400 必须有友好的错误引导，不能让 owner-only / reason 缺失把用户卡住没提示
	assert.match(source, /密级修改失败：仅大屏 owner/);
});
