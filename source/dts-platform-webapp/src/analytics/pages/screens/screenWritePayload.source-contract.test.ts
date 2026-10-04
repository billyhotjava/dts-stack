import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const analyticsApiPath = new URL("../../api/analyticsApi.ts", import.meta.url);
const screenSpecPath = new URL("./screenSpec.ts", import.meta.url);

test("screen write API uses the shared ScreenWritePayload contract", async () => {
	const [analyticsApiSource, screenSpecSource] = await Promise.all([
		readFile(analyticsApiPath, "utf8"),
		readFile(screenSpecPath, "utf8"),
	]);

	assert.match(analyticsApiSource, /createScreen:\s*\(body:\s*ScreenWritePayload\)/);
	assert.match(analyticsApiSource, /updateScreen:\s*\(id: string \| number, body: ScreenWritePayload\)/);
	assert.match(screenSpecSource, /buildScreenPayload\(config: ScreenConfig\): ScreenWritePayload/);
});

test("screen write API exposes a lightweight domain update helper", async () => {
	const analyticsApiSource = await readFile(analyticsApiPath, "utf8");

	assert.match(analyticsApiSource, /updateScreenDomain:\s*\(id: string \| number, domainId\?: string \| null\)/);
	assert.match(analyticsApiSource, /"PUT",\s*\{\s*domainId:\s*domainId \|\| null\s*\}/);
});
