import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const componentLibraryPanelPath = new URL("./ComponentLibraryPanel.tsx", import.meta.url);

test("ComponentLibraryPanel uses icon components and respects editor readonly mode", async () => {
	const source = await readFile(componentLibraryPanelPath, "utf8");

	assert.match(source, /from 'lucide-react'/);
	assert.match(source, /const \{ editorReadonly \} = useScreen\(\)/);
	assert.match(source, /canDrag: !disabled/);
	assert.match(source, /analytics-screen-library-readonly-note/);
	assert.match(source, /renderLibraryIcon/);
	assert.equal(source.includes("★"), false);
	assert.equal(source.includes("☆"), false);
	assert.equal(source.includes("🔌"), false);
	assert.equal(source.includes("⏱"), false);
});
