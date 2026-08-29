import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const componentRendererPath = new URL("./ComponentRenderer.tsx", import.meta.url);
const sourceColumnsPath = new URL("../renderers/shared/sourceColumns.ts", import.meta.url);
const designerDataBridgePath = new URL("../hooks/useDesignerDataBridge.ts", import.meta.url);

test("ComponentRenderer delegates source metadata and transient samples to the designer bridge", async () => {
    const [rendererSource, helperSource, bridgeSource] = await Promise.all([
        readFile(componentRendererPath, "utf8"),
        readFile(sourceColumnsPath, "utf8"),
        readFile(designerDataBridgePath, "utf8"),
    ]);

    assert.match(rendererSource, /from '\.\.\/hooks\/useDesignerDataBridge'/);
    assert.match(rendererSource, /useDesignerDataBridge\(/);
    assert.match(bridgeSource, /from ["']\.\.\/renderers\/shared\/sourceColumns["']/);
    assert.match(bridgeSource, /resolveSourceColumnsMeta\(data\)/);
    assert.match(bridgeSource, /shouldPersistSourceColumns/);
    assert.match(bridgeSource, /onDataFeedbackRef\.current/);
    assert.equal(rendererSource.includes("displayName: c.display_name || c.name"), false);
    assert.match(helperSource, /export function resolveSourceColumnsMeta/);
    assert.match(helperSource, /export function shouldPersistSourceColumns/);
});

test("ComponentRenderer passes config persistence callback into table renderer", async () => {
    const rendererSource = await readFile(componentRendererPath, "utf8");

    assert.match(rendererSource, /const persistConfigMeta = useCallback/);
    assert.match(rendererSource, /onConfigMeta: persistConfigMeta/);
});
