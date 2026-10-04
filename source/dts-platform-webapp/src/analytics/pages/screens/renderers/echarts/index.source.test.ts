import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const indexPath = new URL("./index.ts", import.meta.url);

test("ECharts renderer dispatcher stays type-checked", async () => {
    const source = await readFile(indexPath, "utf8");

    assert.equal(source.includes("@ts-nocheck"), false);
    assert.match(source, /export function renderECharts/);
    assert.match(source, /renderAxisChart\(type, props\)/);
    assert.match(source, /renderExtendedChart\(type, props\)/);
});
