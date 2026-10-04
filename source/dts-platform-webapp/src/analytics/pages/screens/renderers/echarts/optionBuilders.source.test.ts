import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const optionBuildersPath = new URL("./optionBuilders.ts", import.meta.url);

test("ECharts option builder helpers stay type-checked", async () => {
    const source = await readFile(optionBuildersPath, "utf8");

    assert.equal(source.includes("@ts-nocheck"), false);
    assert.match(source, /export const SCREEN_UI_FONT_FAMILY/);
    assert.match(source, /export function isLightColor/);
});
