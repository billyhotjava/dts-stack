import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const staticDataEditorPath = new URL("./StaticDataEditor.tsx", import.meta.url);
const cardBindingsEditorPath = new URL("./CardSourceColumnBindingsEditor.tsx", import.meta.url);
const quickActionsSectionPath = new URL("./QuickActionsSection.tsx", import.meta.url);
const chartAnnotationConfigPath = new URL("./ChartAnnotationConfig.tsx", import.meta.url);

test("table-style property editors use lucide icons instead of text symbols", async () => {
    const [staticDataSource, cardBindingsSource, quickActionsSource, chartAnnotationSource] = await Promise.all([
        readFile(staticDataEditorPath, "utf8"),
        readFile(cardBindingsEditorPath, "utf8"),
        readFile(quickActionsSectionPath, "utf8"),
        readFile(chartAnnotationConfigPath, "utf8"),
    ]);

    assert.match(staticDataSource, /import \{ Plus, X \} from 'lucide-react'/);
    assert.match(cardBindingsSource, /import \{ ArrowDown, ArrowUp, Plus, Trash2 \} from 'lucide-react'/);
    assert.match(quickActionsSource, /import \{ ArrowDown, ArrowLeft, ArrowRight, ArrowUp \} from 'lucide-react'/);
    assert.match(chartAnnotationSource, /import \{ Plus, Trash2 \} from 'lucide-react'/);
    assert.equal(cardBindingsSource.includes("@ts-nocheck"), false);
    assert.equal(chartAnnotationSource.includes("@ts-nocheck"), false);
    for (const source of [staticDataSource, cardBindingsSource, quickActionsSource, chartAnnotationSource]) {
        assert.equal(source.includes("+ 添加"), false);
        assert.doesNotMatch(source, />\s*×\s*<\/button>/);
        assert.equal(source.includes("↑"), false);
        assert.equal(source.includes("↓"), false);
        assert.equal(source.includes("←"), false);
        assert.equal(source.includes("→"), false);
    }
});
