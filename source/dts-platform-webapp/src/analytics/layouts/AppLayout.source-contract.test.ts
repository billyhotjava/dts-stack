import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const appLayoutPath = new URL("./AppLayout.tsx", import.meta.url);

test("AppLayout does not rely on Tailwind important utility prefixes for Ant layout shells", async () => {
	const source = await readFile(appLayoutPath, "utf8");

	assert.equal(source.includes('className="!sticky'), false);
	assert.equal(source.includes('className="!flex'), false);
	assert.equal(source.includes('className="!px-4'), false);
	assert.equal(source.includes('className="!bg-white'), false);
	assert.equal(source.includes('className="!h-12'), false);
	assert.equal(source.includes('className="!leading-[48px]'), false);
	assert.equal(source.includes('className="!px-4 !pb-4'), false);
});
