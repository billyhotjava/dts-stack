import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const shimPath = new URL("./EChartsRenderer.tsx", import.meta.url);

test("EChartsRenderer shim stays type-checked", async () => {
    const source = await readFile(shimPath, "utf8");

    assert.equal(source.includes("@ts-nocheck"), false);
    assert.match(source, /export \{ renderECharts \} from '\.\/echarts';/);
    assert.match(source, /export type \{ EChartsRendererProps \} from '\.\/echarts';/);
});
