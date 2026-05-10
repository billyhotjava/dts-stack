import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const chartUtilsPath = new URL("./chartUtils.ts", import.meta.url);

test("chartUtils remains under TypeScript checking", async () => {
    const source = await readFile(chartUtilsPath, "utf8");

    assert.equal(source.includes("@ts-nocheck"), false);
    assert.equal(source.includes("any"), false);
    assert.match(source, /export function resolveInteractionValue/);
    assert.match(source, /export function resolveComponentVariableVisibility/);
});
