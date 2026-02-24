/**
 * Client-side ScreenConfig diff engine.
 * Provides component-level and property-level change tracking between two ScreenConfig snapshots.
 */
import type { ScreenConfig, ScreenComponent, ScreenGlobalVariable } from './types';

/* ---------- public types ---------- */

export interface PropertyChange {
    path: string;
    oldValue: unknown;
    newValue: unknown;
}

export interface ComponentChange {
    componentId: string;
    componentName: string;
    componentType: string;
    changeType: 'added' | 'removed' | 'modified';
    propertyChanges?: PropertyChange[];
}

export interface VariableChange {
    key: string;
    label?: string;
    changeType: 'added' | 'removed' | 'modified';
    propertyChanges?: PropertyChange[];
}

export interface ConfigDiffSummary {
    componentsAdded: number;
    componentsRemoved: number;
    componentsModified: number;
    canvasChanged: boolean;
    variablesChanged: boolean;
    totalPropertyChanges: number;
}

export interface ConfigDiffResult {
    summary: ConfigDiffSummary;
    componentChanges: ComponentChange[];
    canvasChanges: PropertyChange[];
    variableChanges: VariableChange[];
}

/* ---------- internal helpers ---------- */

const CANVAS_KEYS: ReadonlyArray<keyof ScreenConfig> = [
    'width', 'height', 'backgroundColor', 'backgroundImage', 'theme',
];

/** Keys to skip during deep diff (internal / transient state). */
function shouldSkipKey(key: string): boolean {
    return key.startsWith('_') || key === 'id';
}

/** Shallow-equal for primitive values; for objects/arrays, always return false. */
function isPrimitiveEqual(a: unknown, b: unknown): boolean {
    if (a === b) return true;
    if (typeof a !== typeof b) return false;
    if (a == null && b == null) return true;
    // NaN === NaN should be true in diff context
    if (typeof a === 'number' && typeof b === 'number' && isNaN(a) && isNaN(b)) return true;
    return false;
}

/**
 * Recursively diff two values and collect property changes.
 * `prefix` is the dotted path to the current location.
 */
function deepDiff(oldVal: unknown, newVal: unknown, prefix: string, out: PropertyChange[]): void {
    if (isPrimitiveEqual(oldVal, newVal)) return;

    // Both are arrays
    if (Array.isArray(oldVal) && Array.isArray(newVal)) {
        const maxLen = Math.max(oldVal.length, newVal.length);
        if (maxLen > 50) {
            // For very large arrays, just record top-level change
            out.push({ path: prefix, oldValue: `[${oldVal.length} items]`, newValue: `[${newVal.length} items]` });
            return;
        }
        for (let i = 0; i < maxLen; i++) {
            deepDiff(
                i < oldVal.length ? oldVal[i] : undefined,
                i < newVal.length ? newVal[i] : undefined,
                `${prefix}[${i}]`,
                out,
            );
        }
        return;
    }

    // Both are plain objects
    if (oldVal && newVal && typeof oldVal === 'object' && typeof newVal === 'object'
        && !Array.isArray(oldVal) && !Array.isArray(newVal)) {
        const oldObj = oldVal as Record<string, unknown>;
        const newObj = newVal as Record<string, unknown>;
        const allKeys = new Set([...Object.keys(oldObj), ...Object.keys(newObj)]);
        for (const key of allKeys) {
            if (shouldSkipKey(key)) continue;
            const childPath = prefix ? `${prefix}.${key}` : key;
            deepDiff(oldObj[key], newObj[key], childPath, out);
        }
        return;
    }

    // Leaf-level difference
    out.push({ path: prefix, oldValue: oldVal, newValue: newVal });
}

/**
 * Build a lookup map of components by id.
 */
function buildComponentMap(components: ScreenComponent[]): Map<string, ScreenComponent> {
    const map = new Map<string, ScreenComponent>();
    for (const c of components) {
        map.set(c.id, c);
    }
    return map;
}

/**
 * Build a lookup map of global variables by key.
 */
function buildVariableMap(variables: ScreenGlobalVariable[]): Map<string, ScreenGlobalVariable> {
    const map = new Map<string, ScreenGlobalVariable>();
    for (const v of variables) {
        map.set(v.key, v);
    }
    return map;
}

/* ---------- public API ---------- */

/**
 * Compute a detailed diff between two ScreenConfig snapshots.
 *
 * `oldConfig` = the earlier version (e.g. v1).
 * `newConfig` = the later version (e.g. v2).
 */
export function diffScreenConfigs(
    oldConfig: ScreenConfig | null | undefined,
    newConfig: ScreenConfig | null | undefined,
): ConfigDiffResult {
    const oldComps = oldConfig?.components ?? [];
    const newComps = newConfig?.components ?? [];
    const oldVars = oldConfig?.globalVariables ?? [];
    const newVars = newConfig?.globalVariables ?? [];

    const oldCompMap = buildComponentMap(oldComps);
    const newCompMap = buildComponentMap(newComps);
    const oldVarMap = buildVariableMap(oldVars);
    const newVarMap = buildVariableMap(newVars);

    /* --- Component changes --- */
    const componentChanges: ComponentChange[] = [];

    // Added components (in new but not in old)
    for (const [id, comp] of newCompMap) {
        if (!oldCompMap.has(id)) {
            componentChanges.push({
                componentId: id,
                componentName: comp.name,
                componentType: comp.type,
                changeType: 'added',
            });
        }
    }

    // Removed components (in old but not in new)
    for (const [id, comp] of oldCompMap) {
        if (!newCompMap.has(id)) {
            componentChanges.push({
                componentId: id,
                componentName: comp.name,
                componentType: comp.type,
                changeType: 'removed',
            });
        }
    }

    // Modified components (in both, but differ)
    let totalPropertyChanges = 0;
    for (const [id, oldComp] of oldCompMap) {
        const newComp = newCompMap.get(id);
        if (!newComp) continue;

        const propChanges: PropertyChange[] = [];
        // Compare top-level component fields
        const topFields: Array<keyof ScreenComponent> = [
            'name', 'type', 'x', 'y', 'width', 'height',
            'zIndex', 'locked', 'visible', 'groupId', 'parentContainerId',
        ];
        for (const field of topFields) {
            deepDiff(
                (oldComp as unknown as Record<string, unknown>)[field],
                (newComp as unknown as Record<string, unknown>)[field],
                field,
                propChanges,
            );
        }
        // Compare config
        deepDiff(oldComp.config, newComp.config, 'config', propChanges);
        // Compare dataSource
        deepDiff(oldComp.dataSource, newComp.dataSource, 'dataSource', propChanges);
        // Compare interaction
        deepDiff(oldComp.interaction, newComp.interaction, 'interaction', propChanges);
        // Compare drillDown
        deepDiff(oldComp.drillDown, newComp.drillDown, 'drillDown', propChanges);

        if (propChanges.length > 0) {
            componentChanges.push({
                componentId: id,
                componentName: newComp.name,
                componentType: newComp.type,
                changeType: 'modified',
                propertyChanges: propChanges,
            });
            totalPropertyChanges += propChanges.length;
        }
    }

    /* --- Canvas changes --- */
    const canvasChanges: PropertyChange[] = [];
    for (const key of CANVAS_KEYS) {
        const oldVal = oldConfig ? (oldConfig as unknown as Record<string, unknown>)[key] : undefined;
        const newVal = newConfig ? (newConfig as unknown as Record<string, unknown>)[key] : undefined;
        deepDiff(oldVal, newVal, key, canvasChanges);
    }

    /* --- Variable changes --- */
    const variableChanges: VariableChange[] = [];
    for (const [key, newVar] of newVarMap) {
        if (!oldVarMap.has(key)) {
            variableChanges.push({ key, label: newVar.label, changeType: 'added' });
        }
    }
    for (const [key, oldVar] of oldVarMap) {
        if (!newVarMap.has(key)) {
            variableChanges.push({ key, label: oldVar.label, changeType: 'removed' });
        }
    }
    for (const [key, oldVar] of oldVarMap) {
        const newVar = newVarMap.get(key);
        if (!newVar) continue;
        const changes: PropertyChange[] = [];
        deepDiff(
            { label: oldVar.label, type: oldVar.type, defaultValue: oldVar.defaultValue, description: oldVar.description },
            { label: newVar.label, type: newVar.type, defaultValue: newVar.defaultValue, description: newVar.description },
            '',
            changes,
        );
        if (changes.length > 0) {
            variableChanges.push({ key, label: newVar.label, changeType: 'modified', propertyChanges: changes });
        }
    }

    /* --- Summary --- */
    const added = componentChanges.filter(c => c.changeType === 'added').length;
    const removed = componentChanges.filter(c => c.changeType === 'removed').length;
    const modified = componentChanges.filter(c => c.changeType === 'modified').length;

    return {
        summary: {
            componentsAdded: added,
            componentsRemoved: removed,
            componentsModified: modified,
            canvasChanged: canvasChanges.length > 0,
            variablesChanged: variableChanges.length > 0,
            totalPropertyChanges,
        },
        componentChanges,
        canvasChanges,
        variableChanges,
    };
}

/**
 * Format a diff result into a human-readable text summary.
 */
export function formatDiffSummary(diff: ConfigDiffResult): string {
    const { summary } = diff;
    const parts: string[] = [];
    if (summary.componentsAdded > 0) parts.push(`新增 ${summary.componentsAdded} 组件`);
    if (summary.componentsRemoved > 0) parts.push(`删除 ${summary.componentsRemoved} 组件`);
    if (summary.componentsModified > 0) parts.push(`修改 ${summary.componentsModified} 组件`);
    if (summary.canvasChanged) parts.push('画布属性变化');
    if (summary.variablesChanged) parts.push('全局变量变化');
    return parts.length > 0 ? parts.join(', ') : '无变化';
}

/**
 * Truncate a value for display purposes.
 */
export function formatDiffValue(value: unknown, maxLen = 60): string {
    if (value === undefined) return '(未定义)';
    if (value === null) return '(null)';
    if (typeof value === 'string') {
        return value.length > maxLen ? value.slice(0, maxLen) + '...' : value;
    }
    if (typeof value === 'number' || typeof value === 'boolean') {
        return String(value);
    }
    const json = JSON.stringify(value);
    return json.length > maxLen ? json.slice(0, maxLen) + '...' : json;
}
