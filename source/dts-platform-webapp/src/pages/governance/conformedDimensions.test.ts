import assert from "node:assert/strict";
import test from "node:test";
import {
	buildConformedDimensionReference,
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
	const catalog = [
		{
			version: 1 as const,
			dimensionId: "status",
			name: "状态",
			sourceModel: "dim_status",
			domainIds: ["domain-1"],
			confirmed: true,
		},
		{
			version: 1 as const,
			dimensionId: "region",
			name: "区域",
			sourceModel: "dim_region",
			domainIds: ["domain-1"],
			confirmed: true,
		},
		{
			version: 1 as const,
			dimensionId: "candidate-status",
			name: "候选状态",
			sourceModel: "dim_candidate_status",
			domainIds: ["domain-1"],
			confirmed: false,
		},
	];
	toggleBusMatrixLink("domain-1", "process-1", "status", storage);
	toggleBusMatrixLink("domain-1", "process-1", "candidate-status", storage);
	const recommended = recommendDimensionsForProcess("process-1", catalog, loadBusMatrix("domain-1", storage));
	assert.deepEqual(
		recommended.map((item) => item.dimensionId),
		["status"],
	);
});

test("dimension reuse writes a process-scoped reference metadata record", () => {
	const storage = memoryStorage();
	const dimension = {
		version: 1 as const,
		dimensionId: "status",
		name: "状态",
		sourceModel: "dim_status",
		domainIds: ["domain-1"],
		confirmed: true,
	};
	const reference = buildConformedDimensionReference("domain-1", "process-1", dimension, "2026-07-16T00:00:00.000Z");
	saveConformedDimensionReference(reference, storage);
	assert.deepEqual(
		JSON.parse(storage.getItem("dts.conformed-dimension-reference.v1:domain-1:process-1") || "{}"),
		reference,
	);
});
