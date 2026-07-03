import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const REPORT_TAB = readFileSync(new URL("./components/QualityReportTab.tsx", import.meta.url), "utf8");

test("quality report FAILED runs link to the repair workbench instead of a dead link", () => {
	// F5-T03：\"去修复\"必须真实跳转到数据修复 Tab，禁止无 onClick 的占位链接
	assert.match(REPORT_TAB, /useNavigate/);
	assert.match(REPORT_TAB, /去修复/);
	assert.match(REPORT_TAB, /tab=repair/);
	assert.doesNotMatch(REPORT_TAB, /<Typography\.Link>去修复<\/Typography\.Link>/);
});
