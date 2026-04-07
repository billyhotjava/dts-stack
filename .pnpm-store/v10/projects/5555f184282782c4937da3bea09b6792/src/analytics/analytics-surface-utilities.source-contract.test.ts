import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const utilitiesCssPath = new URL("./styles/utilities.css", import.meta.url);

test("analytics utility stylesheet defines surface and border token classes used by screens pages", async () => {
	const source = await readFile(utilitiesCssPath, "utf8");

	for (const requiredClass of [
		".bg-surface-card",
		".bg-surface-page",
		".bg-surface-muted",
		".bg-surface-workspace",
		".border-border-default",
		".border-border-strong",
	]) {
		assert.equal(source.includes(requiredClass), true, `${requiredClass} should exist in analytics utilities`);
	}
});
