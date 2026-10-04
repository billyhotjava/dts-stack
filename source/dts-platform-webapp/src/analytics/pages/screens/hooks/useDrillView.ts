/**
 * useDrillView — In-page drill navigation for screen runtime.
 *
 * Unlike drill-down (which navigates into nested card data),
 * drill-view switches between "view layers" within the same screen:
 *   strategic → control → execution
 *
 * Supports breadcrumb-based navigation back to parent views.
 */
import { useState, useCallback, useMemo } from 'react';

export interface DrillViewEntry {
    viewId: string;
    label: string;
    params: Record<string, string>;
}

export interface DrillViewState {
    /** Current active view ID (null = root/strategic view) */
    activeViewId: string | null;
    /** Breadcrumb trail for navigation */
    breadcrumbs: DrillViewEntry[];
    /** Navigate to a child view */
    drillToView: (viewId: string, label: string, params?: Record<string, string>) => void;
    /** Navigate back to a specific breadcrumb level */
    navigateToLevel: (index: number) => void;
    /** Navigate back to root */
    navigateToRoot: () => void;
    /** Current drill depth (0 = root) */
    depth: number;
    /** Parameters from the current drill entry */
    currentParams: Record<string, string>;
}

export function useDrillView(): DrillViewState {
    const [breadcrumbs, setBreadcrumbs] = useState<DrillViewEntry[]>([]);

    const drillToView = useCallback((viewId: string, label: string, params: Record<string, string> = {}) => {
        setBreadcrumbs((prev) => [...prev, { viewId, label, params }]);
    }, []);

    const navigateToLevel = useCallback((index: number) => {
        setBreadcrumbs((prev) => prev.slice(0, index));
    }, []);

    const navigateToRoot = useCallback(() => {
        setBreadcrumbs([]);
    }, []);

    const activeViewId = breadcrumbs.length > 0 ? breadcrumbs[breadcrumbs.length - 1].viewId : null;
    const currentParams = breadcrumbs.length > 0 ? breadcrumbs[breadcrumbs.length - 1].params : {};

    return useMemo(() => ({
        activeViewId,
        breadcrumbs,
        drillToView,
        navigateToLevel,
        navigateToRoot,
        depth: breadcrumbs.length,
        currentParams,
    }), [activeViewId, breadcrumbs, currentParams, drillToView, navigateToLevel, navigateToRoot]);
}
