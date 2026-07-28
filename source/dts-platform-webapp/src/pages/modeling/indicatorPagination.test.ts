import assert from "node:assert/strict";
import test from "node:test";
import { loadAllIndicatorPages } from "./indicatorPagination.ts";

test("loads indicator owners in ten-row pages until the server total is reached", async () => {
	const calls: Array<{ page: number; size: number }> = [];
	const result = await loadAllIndicatorPages(async (page, size) => {
		calls.push({ page, size });
		return {
			content: page === 0 ? [{ id: "one" }] : [{ id: "two" }],
			totalPages: 2,
		};
	});

	assert.deepEqual(result, [{ id: "one" }, { id: "two" }]);
	assert.deepEqual(calls, [
		{ page: 0, size: 10 },
		{ page: 1, size: 10 },
	]);
});

test("keeps compatibility with legacy array responses", async () => {
	const result = await loadAllIndicatorPages(async () => [{ id: "legacy" }]);
	assert.deepEqual(result, [{ id: "legacy" }]);
});
