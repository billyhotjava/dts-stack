import { useCallback, useState } from "react";

export interface DrillFilter {
  column: string;
  value: string;
  displayLabel: string;
}

export interface DrillFilterState {
  filters: DrillFilter[];
  addFilter: (filter: DrillFilter) => void;
  removeFiltersFrom: (index: number) => void;
  clearAll: () => void;
  applyToDatasetQuery: (baseQuery: Record<string, unknown>) => Record<string, unknown>;
  /** Build a detail query that removes aggregation/breakout and filters to the given dimension value. */
  buildDetailQuery: (baseQuery: Record<string, unknown>, drillFilter: DrillFilter) => Record<string, unknown>;
}

/**
 * Hook that manages a stack of drill-through filters.
 * Each filter represents a dimension value the user clicked on to drill into.
 */
export function useDrillFilter(): DrillFilterState {
  const [filters, setFilters] = useState<DrillFilter[]>([]);

  const addFilter = useCallback((filter: DrillFilter) => {
    setFilters((prev) => {
      // If the same column already exists at the end, replace it instead of stacking
      const existing = prev.findIndex((f) => f.column === filter.column);
      if (existing >= 0) {
        // Remove from that index onward, then add the new filter
        return [...prev.slice(0, existing), filter];
      }
      return [...prev, filter];
    });
  }, []);

  const removeFiltersFrom = useCallback((index: number) => {
    setFilters((prev) => prev.slice(0, index));
  }, []);

  const clearAll = useCallback(() => {
    setFilters([]);
  }, []);

  const applyToDatasetQuery = useCallback(
    (baseQuery: Record<string, unknown>): Record<string, unknown> => {
      if (filters.length === 0) return baseQuery;

      const queryType = baseQuery.type as string;

      if (queryType === "native") {
        // For native queries, wrap the SQL with drill filters as a subquery
        const nativeObj = (baseQuery.native ?? {}) as Record<string, unknown>;
        const originalSql = String(nativeObj.query ?? "");
        if (!originalSql.trim()) return baseQuery;

        const whereClauses = filters.map(
          (f) => `"${f.column}" = '${f.value.replace(/'/g, "''")}'`
        );
        const wrappedSql = `SELECT * FROM (${originalSql}) AS __drill WHERE ${whereClauses.join(" AND ")}`;

        return {
          ...baseQuery,
          native: { ...nativeObj, query: wrappedSql },
        };
      }

      // For "query" (MBQL) type
      const inner = (baseQuery.query ?? {}) as Record<string, unknown>;
      const existingFilter = inner.filter as unknown[] | undefined;

      // Build MBQL filter clauses for each drill filter
      const drillClauses: unknown[] = filters.map((f) => [
        "=",
        ["field", f.column, null],
        f.value,
      ]);

      let mergedFilter: unknown;
      if (!existingFilter || (Array.isArray(existingFilter) && existingFilter.length === 0)) {
        // No existing filter
        if (drillClauses.length === 1) {
          mergedFilter = drillClauses[0];
        } else {
          mergedFilter = ["and", ...drillClauses];
        }
      } else if (Array.isArray(existingFilter) && existingFilter[0] === "and") {
        // Existing "and" filter — append drill clauses
        mergedFilter = [...existingFilter, ...drillClauses];
      } else {
        // Single existing filter — combine with "and"
        mergedFilter = ["and", existingFilter, ...drillClauses];
      }

      return {
        ...baseQuery,
        query: { ...inner, filter: mergedFilter },
      };
    },
    [filters]
  );

  const buildDetailQuery = useCallback(
    (baseQuery: Record<string, unknown>, drillFilter: DrillFilter): Record<string, unknown> => {
      const queryType = baseQuery.type as string;
      const DETAIL_LIMIT = 200;

      if (queryType === "native") {
        // For native queries, wrap in subquery with WHERE clause and no aggregation
        const nativeObj = (baseQuery.native ?? {}) as Record<string, unknown>;
        const originalSql = String(nativeObj.query ?? "");
        if (!originalSql.trim()) return baseQuery;

        // Build WHERE clauses: existing drill filters + the detail filter
        const allFilters = [...filters, drillFilter];
        const whereClauses = allFilters.map(
          (f) => `"${f.column}" = '${f.value.replace(/'/g, "''")}'`
        );
        const wrappedSql = `SELECT * FROM (${originalSql}) AS __detail WHERE ${whereClauses.join(" AND ")} LIMIT ${DETAIL_LIMIT}`;

        return {
          ...baseQuery,
          native: { ...nativeObj, query: wrappedSql },
        };
      }

      // For "query" (MBQL) type: remove aggregation & breakout, add drill filter
      const inner = (baseQuery.query ?? {}) as Record<string, unknown>;
      const existingFilter = inner.filter as unknown[] | undefined;

      // Build filter clauses: existing drill filters + the detail filter
      const allFilters = [...filters, drillFilter];
      const drillClauses: unknown[] = allFilters.map((f) => [
        "=",
        ["field", f.column, null],
        f.value,
      ]);

      let mergedFilter: unknown;
      if (!existingFilter || (Array.isArray(existingFilter) && existingFilter.length === 0)) {
        if (drillClauses.length === 1) {
          mergedFilter = drillClauses[0];
        } else {
          mergedFilter = ["and", ...drillClauses];
        }
      } else if (Array.isArray(existingFilter) && existingFilter[0] === "and") {
        mergedFilter = [...existingFilter, ...drillClauses];
      } else {
        mergedFilter = ["and", existingFilter, ...drillClauses];
      }

      // Build new query without aggregation/breakout
      const detailInner: Record<string, unknown> = {
        "source-table": inner["source-table"],
        filter: mergedFilter,
        limit: DETAIL_LIMIT,
      };
      // Preserve fields if the original query had them
      if (inner.fields) {
        detailInner.fields = inner.fields;
      }

      return {
        ...baseQuery,
        query: detailInner,
      };
    },
    [filters]
  );

  return { filters, addFilter, removeFiltersFrom, clearAll, applyToDatasetQuery, buildDetailQuery };
}
