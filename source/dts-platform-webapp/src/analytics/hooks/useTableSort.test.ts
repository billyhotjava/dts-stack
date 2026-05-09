// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import {
	applySort,
	cycleSortState,
	dateComparator,
	numberComparator,
	stringComparator,
	type SortState,
} from "./useTableSort";

interface Row {
	id: string;
	name: string;
	count: number | null;
	updatedAt: string | null;
}

const rows: Row[] = [
	{ id: "a", name: "Beta", count: 5, updatedAt: "2026-04-01T00:00:00Z" },
	{ id: "b", name: "alpha", count: null, updatedAt: "2026-04-03T00:00:00Z" },
	{ id: "c", name: "gamma", count: 12, updatedAt: null },
	{ id: "d", name: "Alpha", count: 3, updatedAt: "2026-04-02T00:00:00Z" },
];

const columns = {
	name: stringComparator<Row>((r) => r.name),
	count: numberComparator<Row>((r) => r.count),
	updatedAt: dateComparator<Row>((r) => r.updatedAt),
};

describe("cycleSortState", () => {
	it("activates a fresh key as ascending", () => {
		const prev: SortState = { key: null, direction: null };
		expect(cycleSortState(prev, "name")).toEqual({ key: "name", direction: "asc" });
	});

	it("ascending → descending on same key", () => {
		expect(cycleSortState({ key: "name", direction: "asc" }, "name")).toEqual({
			key: "name",
			direction: "desc",
		});
	});

	it("descending → none on same key", () => {
		expect(cycleSortState({ key: "name", direction: "desc" }, "name")).toEqual({
			key: null,
			direction: null,
		});
	});

	it("switching to a different key resets to ascending", () => {
		expect(cycleSortState({ key: "name", direction: "desc" }, "count")).toEqual({
			key: "count",
			direction: "asc",
		});
	});
});

describe("applySort", () => {
	it("returns input identity when no key", () => {
		expect(applySort(rows, { key: null, direction: null }, columns)).toBe(rows);
	});

	it("returns input identity when comparator is missing", () => {
		expect(applySort(rows, { key: "missing", direction: "asc" }, columns)).toBe(rows);
	});

	it("sorts by date desc — missing treated as +Infinity (front in desc)", () => {
		const out = applySort(rows, { key: "updatedAt", direction: "desc" }, columns);
		// c (null) → front in desc, then 04-03 / 04-02 / 04-01.
		expect(out.map((r) => r.id)).toEqual(["c", "b", "d", "a"]);
	});

	it("sorts by date asc — missing at the back", () => {
		const out = applySort(rows, { key: "updatedAt", direction: "asc" }, columns);
		expect(out.map((r) => r.id)).toEqual(["a", "d", "b", "c"]);
	});

	it("sorts numbers naturally with missing as +Infinity", () => {
		const asc = applySort(rows, { key: "count", direction: "asc" }, columns);
		// 3 < 5 < 12 < null
		expect(asc.map((r) => r.id)).toEqual(["d", "a", "c", "b"]);
		const desc = applySort(rows, { key: "count", direction: "desc" }, columns);
		// reverse of the asc order
		expect(desc.map((r) => r.id)).toEqual(["b", "c", "a", "d"]);
	});

	it("is stable for ties", () => {
		const tied: Row[] = [
			{ id: "x1", name: "same", count: 1, updatedAt: null },
			{ id: "x2", name: "same", count: 1, updatedAt: null },
			{ id: "x3", name: "same", count: 1, updatedAt: null },
		];
		const out = applySort(tied, { key: "name", direction: "asc" }, columns);
		expect(out.map((r) => r.id)).toEqual(["x1", "x2", "x3"]);
	});
});

describe("comparator helpers", () => {
	it("string comparator: locale-aware, nulls last", () => {
		const cmp = stringComparator<{ v: string | null }>((x) => x.v);
		expect(cmp({ v: "a" }, { v: "b" })).toBeLessThan(0);
		expect(cmp({ v: null }, { v: "a" })).toBeGreaterThan(0);
		expect(cmp({ v: "a" }, { v: null })).toBeLessThan(0);
		expect(cmp({ v: null }, { v: null })).toBe(0);
	});

	it("number comparator: handles nulls and negatives", () => {
		const cmp = numberComparator<{ v: number | null }>((x) => x.v);
		expect(cmp({ v: -3 }, { v: 2 })).toBeLessThan(0);
		expect(cmp({ v: null }, { v: 0 })).toBeGreaterThan(0);
	});

	it("date comparator: ISO strings, invalid dates last", () => {
		const cmp = dateComparator<{ v: string | null }>((x) => x.v);
		expect(cmp({ v: "2026-01-01" }, { v: "2026-02-01" })).toBeLessThan(0);
		expect(cmp({ v: "not-a-date" }, { v: "2026-01-01" })).toBeGreaterThan(0);
		expect(cmp({ v: null }, { v: "2026-01-01" })).toBeGreaterThan(0);
	});
});
