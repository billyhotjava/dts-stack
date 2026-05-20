import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const tableRendererPath = new URL("./TableRenderer.tsx", import.meta.url);

test("TableRenderer remains type-checked and uses icon sort indicators", async () => {
    const source = await readFile(tableRendererPath, "utf8");

    assert.equal(source.includes("@ts-nocheck"), false);
    assert.match(source, /import \{ ArrowDown, ArrowUp \} from 'lucide-react'/);
    assert.equal(source.includes("'▲'"), false);
    assert.equal(source.includes("'▼'"), false);
    assert.equal(source.includes("ComponentType"), false);
    assert.doesNotMatch(source, /\brenderUnavailableState,\s*\n\s*\}/);
});

test("TableRenderer consumes resolved column metadata for width, sorting, and frozen columns", async () => {
    const source = await readFile(tableRendererPath, "utf8");

    assert.match(source, /resolveFrozenColumnOffsets/);
    assert.match(source, /columnMeta\[i\]\?\.widthCss/);
    assert.match(source, /columnMeta\[i\]\?\.sortable !== false/);
    assert.match(source, /activeTableSort/);
    assert.match(source, /frozenColumnOffsets\[colIndex\]/);
});
