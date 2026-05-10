import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const designerCanvasPath = new URL("./DesignerCanvas.tsx", import.meta.url);

test("DesignerCanvas keeps core canvas code under TypeScript checks", async () => {
	const source = await readFile(designerCanvasPath, "utf8");

	assert.equal(source.includes("@ts-nocheck"), false);
	assert.equal(source.includes("apiClient.get<any>"), false);
	assert.equal(source.includes("(f: any)"), false);
	assert.match(source, /type ScreenFontAsset =/);
	assert.match(source, /function resolveScreenFontAssets/);
	assert.match(source, /type MouseEvent, type MutableRefObject/);
});
