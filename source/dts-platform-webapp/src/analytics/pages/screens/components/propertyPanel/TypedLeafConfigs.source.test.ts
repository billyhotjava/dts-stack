import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const leafConfigPaths = [
    new URL("./BehaviorConfigSection.tsx", import.meta.url),
    new URL("./CardSourceColumnBindingsEditor.tsx", import.meta.url),
    new URL("./ChartAnnotationConfig.tsx", import.meta.url),
    new URL("./DataSourceConfigSection.tsx", import.meta.url),
    new URL("./helpers.tsx", import.meta.url),
    new URL("./PluginSchemaFieldsSection.tsx", import.meta.url),
    new URL("./PropertyPanel.tsx", import.meta.url),
    new URL("./ScrollBoardConfig.tsx", import.meta.url),
    new URL("./StaticDataEditor.tsx", import.meta.url),
    new URL("./TableConfig.tsx", import.meta.url),
];

test("leaf property config modules remain checked by TypeScript", async () => {
    const sources = await Promise.all(leafConfigPaths.map((path) => readFile(path, "utf8")));

    for (const source of sources) {
        assert.equal(source.includes("@ts-nocheck"), false);
    }
});
