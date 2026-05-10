import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const echartsTypesPath = new URL("./types.ts", import.meta.url);

test("ECharts renderer shared types stay type-checked", async () => {
    const source = await readFile(echartsTypesPath, "utf8");

    assert.equal(source.includes("@ts-nocheck"), false);
    assert.match(source, /export interface EChartsRendererProps/);
    assert.match(source, /export interface ScreenRuntimeLike/);
});
