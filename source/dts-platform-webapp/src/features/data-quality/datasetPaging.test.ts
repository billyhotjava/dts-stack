import assert from "node:assert/strict";
import test from "node:test";
import { collectDatasetPages } from "./datasetPaging.ts";

test("loads every default-lake dataset page using the backend maximum page size", async () => {
	const calls: Array<{ page: number; size: number }> = [];
	const rows = await collectDatasetPages(async (page, size) => {
		calls.push({ page, size });
		return {
			content:
				page === 0
					? Array.from({ length: 200 }, (_, index) => ({ id: `dataset-${index}` }))
					: Array.from({ length: 35 }, (_, index) => ({ id: `dataset-${200 + index}` })),
			total: 235,
		};
	});

	assert.equal(rows.length, 235);
	assert.deepEqual(calls, [
		{ page: 0, size: 200 },
		{ page: 1, size: 200 },
	]);
});

test("deduplicates datasets across pages when the backend reports a total", async () => {
	const pages = [[{ id: "a" }, { id: "b" }], [{ id: "b" }, { id: "c" }], []];
	const rows = await collectDatasetPages(async (page) => ({ content: pages[page], total: 3 }));

	assert.deepEqual(
		rows.map((row) => row.id),
		["a", "b", "c"],
	);
});

test("stops after a short page when the backend omits pagination totals", async () => {
	const calls: number[] = [];
	const rows = await collectDatasetPages(async (page) => {
		calls.push(page);
		return { content: [{ id: "a" }, { id: "b" }] };
	});

	assert.deepEqual(calls, [0]);
	assert.deepEqual(
		rows.map((row) => row.id),
		["a", "b"],
	);
});

test("stops when a repeated page contributes no new dataset ids", async () => {
	const calls: number[] = [];
	const repeated = [{ id: "a" }, { id: "b" }];
	const rows = await collectDatasetPages(async (page) => {
		calls.push(page);
		return { content: repeated, total: 1000 };
	});

	assert.deepEqual(calls, [0, 1]);
	assert.deepEqual(
		rows.map((row) => row.id),
		["a", "b"],
	);
});
