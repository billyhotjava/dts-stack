import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const globalCssPath = new URL("../global.css", import.meta.url);

test("platform global stylesheet imports analytics token and utility styles", async () => {
	const source = await readFile(globalCssPath, "utf8");

	assert.equal(source.includes("@import './analytics/styles/tokens.css';"), true);
	assert.equal(source.includes("@import './analytics/styles/utilities.css';"), true);
});
