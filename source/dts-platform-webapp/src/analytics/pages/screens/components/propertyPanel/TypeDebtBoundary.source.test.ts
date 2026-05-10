import assert from "node:assert/strict";
import test from "node:test";
import { readdir, readFile } from "node:fs/promises";

const propertyPanelDir = new URL("./", import.meta.url);
const allowedUncheckedModules = new Set([
    "DataSourceConfigSection.tsx",
    "PropertyPanel.tsx",
]);

test("property panel TypeScript debt is pinned to the known large modules", async () => {
    const entries = await readdir(propertyPanelDir);
    const uncheckedFiles: string[] = [];

    for (const entry of entries) {
        if (!entry.endsWith(".tsx")) continue;
        const source = await readFile(new URL(entry, propertyPanelDir), "utf8");
        if (source.includes("@ts-nocheck")) {
            uncheckedFiles.push(entry);
        }
    }

    assert.deepEqual(
        uncheckedFiles.sort(),
        Array.from(allowedUncheckedModules).sort(),
    );
});
