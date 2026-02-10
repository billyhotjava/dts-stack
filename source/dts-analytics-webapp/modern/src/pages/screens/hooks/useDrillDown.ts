import { useState, useCallback, useMemo } from 'react';
import type { DrillDownConfig } from '../types';

interface DrillEntry {
    label: string;
    clickedValue: string;
}

interface Breadcrumb {
    label: string;
    depth: number;
}

interface DrillState {
    effectiveCardId: number | undefined;
    queryParameters: Array<{ name: string; value: string }> | undefined;
    breadcrumbs: Breadcrumb[];
    canDrillDown: boolean;
    handleDrill: (clickedValue: string) => void;
    handleRollUp: (targetDepth: number) => void;
}

export function useDrillDown(
    rootCardId: number | undefined,
    drillConfig: DrillDownConfig | undefined,
): DrillState {
    const [stack, setStack] = useState<DrillEntry[]>([]);
    const levels = drillConfig?.levels ?? [];
    const depth = stack.length;

    const effectiveCardId = useMemo(() => {
        if (!rootCardId || !drillConfig?.enabled) return rootCardId;
        if (depth === 0) return rootCardId;
        return levels[depth - 1]?.cardId ?? rootCardId;
    }, [rootCardId, drillConfig?.enabled, depth, levels]);

    const queryParameters = useMemo(() => {
        if (!drillConfig?.enabled || depth === 0) return undefined;
        const level = levels[depth - 1];
        const entry = stack[depth - 1];
        if (!level || !entry) return undefined;
        return [{ name: level.paramName, value: entry.clickedValue }];
    }, [drillConfig?.enabled, depth, levels, stack]);

    const breadcrumbs = useMemo<Breadcrumb[]>(() => {
        if (!drillConfig?.enabled || depth === 0) return [];
        const crumbs: Breadcrumb[] = [{ label: '全部', depth: 0 }];
        for (let i = 0; i < depth; i++) {
            const level = levels[i];
            const entry = stack[i];
            crumbs.push({
                label: `${level?.label ?? ''}: ${entry.clickedValue}`,
                depth: i + 1,
            });
        }
        return crumbs;
    }, [drillConfig?.enabled, depth, levels, stack]);

    const canDrillDown = drillConfig?.enabled === true && depth < levels.length;

    const handleDrill = useCallback((clickedValue: string) => {
        if (!canDrillDown) return;
        const level = levels[depth];
        setStack((prev) => [...prev, { label: level.label, clickedValue }]);
    }, [canDrillDown, depth, levels]);

    const handleRollUp = useCallback((targetDepth: number) => {
        if (targetDepth < 0) return;
        setStack((prev) => prev.slice(0, targetDepth));
    }, []);

    return { effectiveCardId, queryParameters, breadcrumbs, canDrillDown, handleDrill, handleRollUp };
}
