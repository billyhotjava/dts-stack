import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const layerPanelPath = new URL("./LayerPanel.tsx", import.meta.url);

test("LayerPanel stays type-checked and avoids mutating component order while rendering", async () => {
	const source = await readFile(layerPanelPath, "utf8");

	assert.equal(source.includes("@ts-nocheck"), false);
	assert.match(source, /type DragEvent, type MouseEvent, type ReactNode/);
	assert.match(source, /import type \{ ScreenComponent \} from '\.\.\/types'/);
	assert.equal(source.includes("config.components.sort("), false);
	assert.match(source, /config\.components\.slice\(\)\.sort/);
});
