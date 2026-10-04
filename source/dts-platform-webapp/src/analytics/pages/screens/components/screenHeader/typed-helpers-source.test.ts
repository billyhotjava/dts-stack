import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const files = [
	new URL("./helpers.ts", import.meta.url),
	new URL("./HeaderMenu.tsx", import.meta.url),
	new URL("./ThemeSelector.tsx", import.meta.url),
	new URL("../propertyPanel/types.ts", import.meta.url),
];

test("small screen editor helper modules stay out of ts-nocheck", async () => {
	const sources = await Promise.all(files.map((file) => readFile(file, "utf8")));

	for (const source of sources) {
		assert.equal(source.includes("@ts-nocheck"), false);
	}
});
