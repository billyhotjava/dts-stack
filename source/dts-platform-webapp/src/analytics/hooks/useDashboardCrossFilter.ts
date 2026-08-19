import { useCallback, useState } from "react";
import { buildTargetedCrossFilterParams } from "../pages/dashboard/dashboardInteractionModel";

export interface CrossFilter {
  sourceCardId: number;
  column: string;
  value: string;
  targetCardIds: number[];
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
      return buildTargetedCrossFilterParams(activeFilter, dashcardId, baseParams);
    },
    [activeFilter],
  );

  return { activeFilter, setFilter, clearFilter, buildCrossFilterParams };
}
