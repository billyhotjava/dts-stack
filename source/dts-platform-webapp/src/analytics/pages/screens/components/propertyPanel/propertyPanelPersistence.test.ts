import assert from "node:assert/strict";
import test from "node:test";
import {
    readCollapsedSections,
    readLayoutClipboard,
    readPanelDensity,
    readStyleClipboard,
    writeCollapsedSections,
    writeNullableJson,
    writePanelDensity,
} from "./propertyPanelPersistence.ts";

function createMemoryStorage(): Storage {
    const map = new Map<string, string>();
    return {
        get length() {
            return map.size;
        },
        clear() {
            map.clear();
        },
        getItem(key: string) {
            return map.get(key) ?? null;
        },
        key(index: number) {
            return Array.from(map.keys())[index] ?? null;
        },
        removeItem(key: string) {
            map.delete(key);
        },
        setItem(key: string, value: string) {
            map.set(key, value);
        },
    };
}

test("property panel persistence validates density and collapsed sections", () => {
    const storage = createMemoryStorage();

    assert.equal(readPanelDensity(storage, "density"), "focus");
    writePanelDensity(storage, "density", "full");
    assert.equal(readPanelDensity(storage, "density"), "full");

    storage.setItem("sections", JSON.stringify(["a", 1, "b"]));
    assert.deepEqual(readCollapsedSections(storage, "sections", ["fallback"]), ["a", "b"]);

    writeCollapsedSections(storage, "sections", ["x", "y"]);
    assert.deepEqual(JSON.parse(storage.getItem("sections") || "[]"), ["x", "y"]);
});

test("property panel persistence rejects malformed clipboard payloads", () => {
    const storage = createMemoryStorage();

    storage.setItem("style", JSON.stringify({ type: "bar-chart", config: { color: "#fff" }, width: 100, height: 80, copiedAt: "now" }));
    assert.equal(readStyleClipboard(storage, "style")?.type, "bar-chart");
    storage.setItem("style", JSON.stringify({ type: "bar-chart" }));
    assert.equal(readStyleClipboard(storage, "style"), null);

    storage.setItem("layout", JSON.stringify({ x: 1, y: 2, width: 300, height: 200, copiedAt: "now" }));
    assert.equal(readLayoutClipboard(storage, "layout")?.width, 300);
    storage.setItem("layout", JSON.stringify({ x: 1, y: "bad", width: 300, height: 200 }));
    assert.equal(readLayoutClipboard(storage, "layout"), null);

    writeNullableJson(storage, "layout", null);
    assert.equal(storage.getItem("layout"), null);
});
