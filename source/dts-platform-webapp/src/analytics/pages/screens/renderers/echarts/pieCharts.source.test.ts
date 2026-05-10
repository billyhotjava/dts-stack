import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const pieChartsPath = new URL("./pieCharts.ts", import.meta.url);

test("pie-family ECharts renderer stays type-checked", async () => {
    const source = await readFile(pieChartsPath, "utf8");

    assert.equal(source.includes("@ts-nocheck"), false);
    assert.match(source, /export function renderPieChart/);
    assert.match(source, /case 'pie-chart'/);
    assert.match(source, /case 'gauge-chart'/);
    assert.match(source, /case 'funnel-chart'/);
});
