import { useCallback, useMemo, useState } from "react";

/**
 * Client-side table sort hook for analytics native-table pages.
 *
 * Mirrors antd Table's three-state cycle: 升序 → 降序 → 取消（回到原顺序）。
 * Designed for the Tailwind-styled `<table>`s under `src/analytics/pages/`
 * that cannot adopt antd `<Table>`'s built-in sorter without breaking the
 * page-specific look. Core comparator + cycle logic is pure (testable
 * without rendering); the hook is a thin React wrapper on top.
 */

export type SortDirection = "asc" | "desc" | null;

export interface SortState {
	key: string | null;
	direction: SortDirection;
}

export type Comparator<T> = (a: T, b: T) => number;

export interface UseTableSortOptions<T> {
	/** Map of sort key → comparator that returns negative/0/positive. */
	columns: Record<string, Comparator<T>>;
	/** Initial sort state. Omit for "no sort". */
	defaultSort?: { key: string; direction: Exclude<SortDirection, null> };
}

export interface UseTableSortResult<T> {
	/** Items in current sort order. Identity-stable when sort is unchanged. */
	sortedItems: T[];
	sortState: SortState;
	/** Cycle the given key through asc → desc → none. */
	requestSort: (key: string) => void;
}

/** Pure cycle: any non-active key starts asc; active key cycles asc→desc→none→asc. */
export function cycleSortState(prev: SortState, key: string): SortState {
	if (prev.key !== key) return { key, direction: "asc" };
	if (prev.direction === "asc") return { key, direction: "desc" };
	if (prev.direction === "desc") return { key: null, direction: null };
	return { key, direction: "asc" };
}

/** Pure sort: stable, returns input identity when no active sort/comparator. */
export function applySort<T>(items: T[], sortState: SortState, columns: Record<string, Comparator<T>>): T[] {
	if (!sortState.key || !sortState.direction) return items;
	const comparator = columns[sortState.key];
	if (!comparator) return items;
	const dir = sortState.direction === "asc" ? 1 : -1;
	const indexed = items.map((item, index) => ({ item, index }));
	indexed.sort((a, b) => {
		const cmp = comparator(a.item, b.item);
		if (cmp !== 0) return cmp * dir;
		return a.index - b.index;
	});
	return indexed.map((entry) => entry.item);
}

export function useTableSort<T>(items: T[], options: UseTableSortOptions<T>): UseTableSortResult<T> {
	const [sortState, setSortState] = useState<SortState>(() =>
		options.defaultSort
			? { key: options.defaultSort.key, direction: options.defaultSort.direction }
			: { key: null, direction: null },
	);

	const requestSort = useCallback((key: string) => {
		setSortState((prev) => cycleSortState(prev, key));
	}, []);

	const sortedItems = useMemo(() => applySort(items, sortState, options.columns), [items, options.columns, sortState]);

	return { sortedItems, sortState, requestSort };
}

/**
 * Helper comparators — supply a value extractor; null/undefined values are
 * treated as +Infinity so they appear at the end in ascending order and at
 * the start in descending order. This matches the platform-wide sorter
 * pattern `(a, b) => (a.field || "").localeCompare(b.field || "")`, which
 * antd Table uses by convention.
 */

function compareNullable<U>(a: U | null | undefined, b: U | null | undefined): number | null {
	const aMissing = a === null || a === undefined;
	const bMissing = b === null || b === undefined;
	if (aMissing && bMissing) return 0;
	if (aMissing) return 1;
	if (bMissing) return -1;
	return null;
}

export function stringComparator<T>(extract: (item: T) => string | null | undefined): Comparator<T> {
	return (a, b) => {
		const va = extract(a);
		const vb = extract(b);
		const missing = compareNullable(va, vb);
		if (missing !== null) return missing;
		return (va as string).localeCompare(vb as string);
	};
}

export function numberComparator<T>(extract: (item: T) => number | null | undefined): Comparator<T> {
	return (a, b) => {
		const va = extract(a);
		const vb = extract(b);
		const missing = compareNullable(va, vb);
		if (missing !== null) return missing;
		return (va as number) - (vb as number);
	};
}

export function dateComparator<T>(extract: (item: T) => string | number | Date | null | undefined): Comparator<T> {
	return (a, b) => {
		const va = extract(a);
		const vb = extract(b);
		const missing = compareNullable(va, vb);
		if (missing !== null) return missing;
		const ta = va instanceof Date ? va.getTime() : new Date(va as string | number).getTime();
		const tb = vb instanceof Date ? vb.getTime() : new Date(vb as string | number).getTime();
		// NaN dates sort last (treat as missing).
		if (Number.isNaN(ta) && Number.isNaN(tb)) return 0;
		if (Number.isNaN(ta)) return 1;
		if (Number.isNaN(tb)) return -1;
		return ta - tb;
	};
}
