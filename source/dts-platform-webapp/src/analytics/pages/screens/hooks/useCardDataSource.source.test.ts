import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const hookPath = new URL("./useCardDataSource.ts", import.meta.url);

test("useCardDataSource stays under TypeScript checking", async () => {
    const source = await readFile(hookPath, "utf8");

    assert.equal(source.includes("@ts-nocheck"), false);
    assert.match(source, /function toCardData/);
    assert.match(source, /function normalizeRows/);
    assert.match(source, /export function useCardDataSource/);
});
