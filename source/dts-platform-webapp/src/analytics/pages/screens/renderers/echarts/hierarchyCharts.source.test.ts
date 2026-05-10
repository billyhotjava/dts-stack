import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const hierarchyChartsPath = new URL("./hierarchyCharts.ts", import.meta.url);

test("hierarchy-family ECharts renderer stays type-checked without explicit any", async () => {
    const source = await readFile(hierarchyChartsPath, "utf8");

    assert.equal(source.includes("@ts-nocheck"), false);
    assert.equal(source.includes("Record<string, any>"), false);
    assert.equal(source.includes("as any"), false);
    assert.match(source, /export function renderHierarchyChart/);
    assert.match(source, /case 'sankey-chart'/);
    assert.match(source, /case 'tree-chart'/);
});
