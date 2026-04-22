import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const screensPagePath = new URL("./ScreensPage.tsx", import.meta.url);

test("ScreensPage does not depend on removed legacy page shell classes", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.equal(source.includes('className="page-container"'), false);
	assert.equal(source.includes('className="page-content"'), false);
});

test("ScreensPage opens editor in a new window from the management list", async () => {
	const source = await readFile(screensPagePath, "utf8");

	assert.match(
		source,
		/window\.open\(resolveRouteForOpen\(`\/bi\/screens\/\$\{id\}\/edit`\), '_blank', 'noopener,noreferrer'\)/,
	);
});
