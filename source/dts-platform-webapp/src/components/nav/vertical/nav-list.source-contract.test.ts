import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const navListPath = new URL("./nav-list.tsx", import.meta.url);

test("vertical nav leaves render without collapsible trigger wrappers", async () => {
	const source = await readFile(navListPath, "utf8");

	assert.equal(
		source.includes("return <li className=\"list-none\">{hasChild ? renderCollapsibleItem() : renderLeafItem()}</li>;"),
		true,
	);
	assert.equal(source.includes("<CollapsibleTrigger className=\"w-full\">"), false);
});
