import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const propertyPanelPath = new URL("./PropertyPanel.tsx", import.meta.url);
const persistencePath = new URL("./propertyPanelPersistence.ts", import.meta.url);

test("PropertyPanel delegates storage parsing to typed persistence helpers", async () => {
    const [propertyPanelSource, persistenceSource] = await Promise.all([
        readFile(propertyPanelPath, "utf8"),
        readFile(persistencePath, "utf8"),
    ]);

    assert.match(propertyPanelSource, /from '\.\/propertyPanelPersistence'/);
    assert.equal(propertyPanelSource.includes("sessionStorage.getItem"), false);
    assert.equal(propertyPanelSource.includes("localStorage.getItem"), false);
    assert.match(persistenceSource, /export function readStyleClipboard/);
    assert.match(persistenceSource, /export function readLayoutClipboard/);
});
