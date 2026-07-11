import assert from "node:assert/strict";
import test from "node:test";
import {
	CONFORMED_DIMENSION_SEEDS,
	loadBusMatrix,
	toggleBusMatrixLink,
} from "./conformedDimensions.ts";

const memoryStorage = () => {
	const values = new Map<string, string>();
	return {
		getItem: (key: string) => values.get(key) ?? null,
		setItem: (key: string, value: string) => values.set(key, value),
		removeItem: (key: string) => values.delete(key),
	};
};

test("canonical dimension registry contains eight reusable dimensions", () => {
	assert.equal(CONFORMED_DIMENSION_SEEDS.length, 8);
	assert.equal(new Set(CONFORMED_DIMENSION_SEEDS.map((item) => item.dimensionId)).size, 8);
	for (const item of CONFORMED_DIMENSION_SEEDS) {
		assert.ok(item.name);
		assert.ok(item.sourceModel?.startsWith("dim_"));
		assert.ok(item.domainIds.length > 0);
	}
});

test("bus matrix toggle persists and restores process-dimension links", () => {
	const storage = memoryStorage();
	const first = toggleBusMatrixLink("domain-1", "process-1", "dim-status", storage);
	assert.deepEqual(first.links, { "process-1": ["dim-status"] });
	assert.deepEqual(loadBusMatrix("domain-1", storage), first);

	const second = toggleBusMatrixLink("domain-1", "process-1", "dim-status", storage);
	assert.deepEqual(second.links, {});
});
