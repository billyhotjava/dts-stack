import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const analyticsApiPath = new URL("../../api/analyticsApi.ts", import.meta.url);
const specV2Path = new URL("./specV2.ts", import.meta.url);

test("screen write API uses the shared ScreenWritePayload contract", async () => {
	const [analyticsApiSource, specV2Source] = await Promise.all([
		readFile(analyticsApiPath, "utf8"),
		readFile(specV2Path, "utf8"),
	]);

	assert.match(analyticsApiSource, /createScreen:\s*\(body:\s*ScreenWritePayload\)/);
	assert.match(analyticsApiSource, /updateScreen:\s*\(id: string \| number, body: ScreenWritePayload\)/);
	assert.match(specV2Source, /buildScreenPayload\(config: ScreenConfig\): ScreenWritePayload/);
});
