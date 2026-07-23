import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import test from "node:test";

const helperUrl = new URL("./modelSpecSystemCode.ts", import.meta.url);

test("dimension identity codes are generated without user input", async () => {
	assert.equal(existsSync(helperUrl), true, "model system-code helper is missing");
	const { createDimensionSystemCode } = await import(helperUrl.href);
	assert.equal(
		createDimensionSystemCode({ randomUUID: () => "12345678-90ab-4def-8123-456789abcdef" }),
		"DIM_1234567890AB4DEF8123456789ABCDEF",
	);
});

test("Chrome-compatible getRandomValues fallback keeps 128 bits of collision resistance", async () => {
	assert.equal(existsSync(helperUrl), true, "model system-code helper is missing");
	const { createDimensionSystemCode } = await import(helperUrl.href);
	assert.equal(
		createDimensionSystemCode({
			getRandomValues: (values: Uint8Array) => {
				values.forEach((_, index) => {
					values[index] = index;
				});
				return values;
			},
		}),
		"DIM_000102030405060708090A0B0C0D0E0F",
	);
});

test("hierarchy codes are generated uniquely inside the current dimension profile", async () => {
	assert.equal(existsSync(helperUrl), true, "model system-code helper is missing");
	const { nextDimensionHierarchyCode } = await import(helperUrl.href);
	assert.equal(nextDimensionHierarchyCode([]), "HIERARCHY_1");
	assert.equal(
		nextDimensionHierarchyCode([{ code: "HIERARCHY_1" }, { code: "CUSTOM" }, { code: "HIERARCHY_3" }]),
		"HIERARCHY_4",
	);
});
