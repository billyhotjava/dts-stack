import { useCallback, useState } from "react";

export interface CrossFilter {
  sourceCardId: number;
  column: string;
  value: string;
}

export interface DashboardCrossFilterState {
  activeFilter: CrossFilter | null;
  setFilter: (filter: CrossFilter) => void;
  clearFilter: () => void;
  /**
   * For cards other than the source, injects an MBQL "=" filter clause
   * into the query parameters payload.  The source card is left untouched.
   */
  buildCrossFilterParams: (
    dashcardId: number,
    baseParams: unknown[],
  ) => unknown[];
}

/**
 * Dashboard-level cross-filtering hook.
 *
 * When a user clicks a dimension value in one card, all *other* cards on the
 * same dashboard are filtered by that dimension value.  The source card itself
 * is not re-filtered (it just highlights the active slice visually).
 */
export function useDashboardCrossFilter(): DashboardCrossFilterState {
  const [activeFilter, setActiveFilter] = useState<CrossFilter | null>(null);

  const setFilter = useCallback((filter: CrossFilter) => {
    setActiveFilter((prev) => {
      // Toggle off if clicking the same value again
      if (
        prev &&
        prev.sourceCardId === filter.sourceCardId &&
        prev.column === filter.column &&
        prev.value === filter.value
      ) {
        return null;
      }
      return filter;
    });
  }, []);

  const clearFilter = useCallback(() => {
    setActiveFilter(null);
  }, []);

  const buildCrossFilterParams = useCallback(
    (dashcardId: number, baseParams: unknown[]): unknown[] => {
      if (!activeFilter) return baseParams;
      // Don't filter the source card itself
      if (dashcardId === activeFilter.sourceCardId) return baseParams;

      // Append a cross-filter parameter entry.
      // The Metabase dashboard query API accepts `parameters` array where each
      // entry can target a dimension.  We use the generic "dimension" target
      // pointing to the column name, which the backend resolves via the card's
      // dataset_query metadata.
      const crossParam = {
        type: "category",
        value: activeFilter.value,
        target: ["dimension", ["field", activeFilter.column, null]],
      };

      return [...baseParams, crossParam];
    },
    [activeFilter],
  );

  return { activeFilter, setFilter, clearFilter, buildCrossFilterParams };
}
