import assert from "node:assert/strict";
import test from "node:test";
import { collectCompletePages } from "./qualityTypes.ts";

test("an unpaged issue list is never treated as a complete duplicate check", async () => {
	const result = await collectCompletePages(async () => [{ id: "issue-1" }]);
	assert.deepEqual(result, { rows: [{ id: "issue-1" }], complete: false });
});

test("collects every page only when the issue API supplies an exact total", async () => {
	const calls: number[] = [];
	const result = await collectCompletePages(async (page) => {
		calls.push(page);
		return {
			content: page === 0 ? [{ id: "issue-1" }, { id: "issue-2" }] : [{ id: "issue-3" }],
			totalElements: 3,
		};
	}, 2);

	assert.deepEqual(calls, [0, 1]);
	assert.deepEqual(result, {
		rows: [{ id: "issue-1" }, { id: "issue-2" }, { id: "issue-3" }],
		complete: true,
	});
});

test("a truncated or repeated page fails closed even when a total is advertised", async () => {
	const result = await collectCompletePages(async () => ({
		content: [{ id: "issue-1" }],
		total: 2,
	}));

	assert.deepEqual(result, { rows: [{ id: "issue-1" }], complete: false });
});

test("an empty first page with a positive total fails closed without an unbounded paging loop", async () => {
	const calls: number[] = [];
	const result = await collectCompletePages(async (page) => {
		calls.push(page);
		return { content: [], total: 10_000 };
	});

	assert.deepEqual(calls, [0]);
	assert.deepEqual(result, { rows: [], complete: false });
});
