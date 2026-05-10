import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const componentRendererPath = new URL("./ComponentRenderer.tsx", import.meta.url);
const sourceColumnsPath = new URL("../renderers/shared/sourceColumns.ts", import.meta.url);

test("ComponentRenderer delegates source column metadata persistence to a shared helper", async () => {
    const [rendererSource, helperSource] = await Promise.all([
        readFile(componentRendererPath, "utf8"),
        readFile(sourceColumnsPath, "utf8"),
    ]);

    assert.match(rendererSource, /from '\.\.\/renderers\/shared\/sourceColumns'/);
    assert.match(rendererSource, /resolveSourceColumnsMeta\(cardData\)/);
    assert.match(rendererSource, /shouldPersistSourceColumns/);
    assert.equal(rendererSource.includes("displayName: c.display_name || c.name"), false);
    assert.match(helperSource, /export function resolveSourceColumnsMeta/);
    assert.match(helperSource, /export function shouldPersistSourceColumns/);
});
