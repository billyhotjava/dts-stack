import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const screenHeaderPath = new URL("./ScreenHeader.tsx", import.meta.url);

test("ScreenHeader validates and saves the draft before publishing", async () => {
	const source = await readFile(screenHeaderPath, "utf8");

	assert.match(source, /const payload = buildScreenPayload\(persistedConfig\)/);
	assert.match(source, /const validation = validateScreenPayload\(payload\)/);
	assert.match(source, /const screenId = await saveScreen\(\)/);
	assert.match(source, /await analyticsApi\.publishScreen\(screenId\)/);
});
