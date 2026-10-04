import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const screenJumpPickerPath = new URL("./ScreenJumpPicker.tsx", import.meta.url);

test("ScreenJumpPicker keeps dropdown affordances typed and accessible", async () => {
    const source = await readFile(screenJumpPickerPath, "utf8");

    assert.equal(source.includes("@ts-nocheck"), false);
    assert.match(source, /import \{ Check, ChevronDown \} from 'lucide-react'/);
    assert.match(source, /aria-haspopup="listbox"/);
    assert.match(source, /role="listbox"/);
    assert.match(source, /role="option"/);
    assert.equal(source.includes("✓"), false);
});
