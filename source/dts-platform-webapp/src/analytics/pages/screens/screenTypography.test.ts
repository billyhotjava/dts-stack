import assert from "node:assert/strict";
import test from "node:test";

import {
	SCREEN_DEFAULT_FONT_FAMILY,
	resolveScreenFontFamily,
} from "./screenTypography";

test("resolveScreenFontFamily falls back to the unified screen font stack", () => {
	assert.equal(resolveScreenFontFamily(undefined), SCREEN_DEFAULT_FONT_FAMILY);
	assert.equal(resolveScreenFontFamily(""), SCREEN_DEFAULT_FONT_FAMILY);
});

test("resolveScreenFontFamily lets component font override global font", () => {
	assert.equal(resolveScreenFontFamily("Noto Sans SC", "PingFang SC"), "PingFang SC");
	assert.equal(resolveScreenFontFamily("Noto Sans SC"), "Noto Sans SC");
});
