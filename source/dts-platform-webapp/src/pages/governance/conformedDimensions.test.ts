import assert from "node:assert/strict";
import test from "node:test";
import {
	buildConformedDimensionReference,
	CONFORMED_DIMENSION_SEEDS,
	loadBusMatrix,
	recommendDimensionsForProcess,
	saveConformedDimensionReference,
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

test("dimension recommendations prefer dimensions registered for the process", () => {
	const storage = memoryStorage();
	toggleBusMatrixLink("domain-1", "process-1", "completion-status", storage);
	const recommended = recommendDimensionsForProcess("domain-1", "process-1", loadBusMatrix("domain-1", storage));
	assert.deepEqual(recommended.map((item) => item.dimensionId), ["completion-status"]);
});

test("dimension reuse writes a process-scoped reference metadata record", () => {
	const storage = memoryStorage();
	const dimension = CONFORMED_DIMENSION_SEEDS[0];
	const reference = buildConformedDimensionReference("domain-1", "process-1", dimension, "2026-07-16T00:00:00.000Z");
	saveConformedDimensionReference(reference, storage);
	assert.deepEqual(JSON.parse(storage.getItem("dts.conformed-dimension-reference.v1:domain-1:process-1") || "{}"), reference);
});
