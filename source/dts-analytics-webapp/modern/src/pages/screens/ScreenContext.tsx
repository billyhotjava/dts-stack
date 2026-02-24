import { createContext, useContext, useReducer, ReactNode, useCallback, useState, useMemo } from 'react';
import type { ScreenState, ScreenAction, ScreenConfig, ScreenComponent } from './types';
import { SCREEN_SCHEMA_VERSION } from './specV2';
import { sanitizeParentContainerIds, wouldCreateParentCycle } from './componentHierarchy';

// ── ID generation (crypto.randomUUID for collision-resistance) ──────────────
export function generateId(prefix = 'comp'): string {
    return `${prefix}_${crypto.randomUUID().replace(/-/g, '').slice(0, 16)}`;
}

// ── Undo history cap ────────────────────────────────────────────────────────
const MAX_HISTORY = 80;

function pushHistory(
    history: ScreenConfig[],
    historyIndex: number,
    newConfig: ScreenConfig,
): { history: ScreenConfig[]; historyIndex: number } {
    const trimmed = history.slice(
        Math.max(0, historyIndex + 1 - MAX_HISTORY + 1),
        historyIndex + 1,
    );
    trimmed.push(newConfig);
    return { history: trimmed, historyIndex: trimmed.length - 1 };
}

// ── Defaults ────────────────────────────────────────────────────────────────
const defaultConfig: ScreenConfig = {
    schemaVersion: SCREEN_SCHEMA_VERSION,
    id: '',
    name: '未命名大屏',
    width: 1920,
    height: 1080,
    backgroundColor: '#0d1b2a',
    components: [],
    globalVariables: [],
};

const initialState: ScreenState = {
    config: defaultConfig,
    baselineConfig: defaultConfig,
    selectedIds: [],
    zoom: 100,
    showGrid: true,
    history: [defaultConfig],
    historyIndex: 0,
};

function applyAutoContainerBinding(components: ScreenComponent[], movedIds: string[]): ScreenComponent[] {
    if (!Array.isArray(components) || components.length === 0 || !Array.isArray(movedIds) || movedIds.length === 0) {
        return components;
    }
    const movedIdSet = new Set(movedIds);
    const containers = components
        .filter((item) => item.type === 'container' && item.visible)
        .sort((a, b) => b.zIndex - a.zIndex);

    return components.map((item) => {
        if (!movedIdSet.has(item.id) || item.type === 'container') {
            return item;
        }

        const centerX = item.x + item.width / 2;
        const centerY = item.y + item.height / 2;
        const target = containers.find((container) => (
            container.id !== item.id
            && centerX >= container.x
            && centerX <= container.x + container.width
            && centerY >= container.y
            && centerY <= container.y + container.height
        ));
        const nextParentId = target?.id;
        if (nextParentId) {
            if (wouldCreateParentCycle(components, item.id, nextParentId)) {
                return item;
            }
            if (item.parentContainerId === nextParentId) {
                return item;
            }
            return { ...item, parentContainerId: nextParentId };
        }
        if (!item.parentContainerId) {
            return item;
        }
        const { parentContainerId: _parentContainerId, ...rest } = item;
        return rest;
    });
}

function sanitizeComponents(components: ScreenComponent[]): ScreenComponent[] {
    return sanitizeParentContainerIds(components);
}

// ── Reducer ─────────────────────────────────────────────────────────────────
function screenReducer(state: ScreenState, action: ScreenAction): ScreenState {
    switch (action.type) {
        case 'SET_CONFIG': {
            const sanitizedConfig = {
                ...action.payload,
                components: sanitizeComponents(action.payload.components || []),
            };
            return {
                ...state,
                config: sanitizedConfig,
                ...pushHistory(state.history, state.historyIndex, sanitizedConfig),
            };
        }

        case 'LOAD_CONFIG': {
            const sanitizedConfig = {
                ...action.payload,
                components: sanitizeComponents(action.payload.components || []),
            };
            // Load config without adding to history (used when loading from API)
            return {
                ...state,
                config: sanitizedConfig,
                baselineConfig: sanitizedConfig,
                selectedIds: [],
                history: [sanitizedConfig],
                historyIndex: 0,
            };
        }

        case 'MARK_BASELINE': {
            return {
                ...state,
                baselineConfig: action.payload,
            };
        }

        // Phase 1.3: MERGE_CONFIG – merge partial updates inside reducer (avoids stale closure)
        case 'MERGE_CONFIG': {
            const merged: ScreenConfig = {
                ...state.config,
                ...action.payload,
                components: sanitizeComponents(
                    action.payload.components ?? state.config.components,
                ),
            };
            return {
                ...state,
                config: merged,
                ...pushHistory(state.history, state.historyIndex, merged),
            };
        }

        case 'ADD_COMPONENT': {
            const newConfig = {
                ...state.config,
                components: sanitizeComponents([...state.config.components, action.payload]),
            };
            return {
                ...state,
                config: newConfig,
                selectedIds: [action.payload.id],
                ...pushHistory(state.history, state.historyIndex, newConfig),
            };
        }

        case 'UPDATE_COMPONENT': {
            const nextComponents = state.config.components.map((comp) =>
                comp.id === action.payload.id ? { ...comp, ...action.payload.updates } : comp
            );
            const newComponents = sanitizeComponents(nextComponents);
            const newConfig = { ...state.config, components: newComponents };
            return {
                ...state,
                config: newConfig,
                ...pushHistory(state.history, state.historyIndex, newConfig),
            };
        }

        case 'DELETE_COMPONENTS': {
            const deletedIds = new Set(action.payload);
            const newComponents = state.config.components
                .filter((comp) => !deletedIds.has(comp.id))
                .map((comp) => {
                    if (!comp.parentContainerId || !deletedIds.has(comp.parentContainerId)) {
                        return comp;
                    }
                    const { parentContainerId: _parentContainerId, ...rest } = comp;
                    return rest;
                });
            const sanitizedComponents = sanitizeComponents(newComponents);
            const newConfig = { ...state.config, components: sanitizedComponents };
            return {
                ...state,
                config: newConfig,
                selectedIds: [],
                ...pushHistory(state.history, state.historyIndex, newConfig),
            };
        }

        // Phase 4.3: DUPLICATE_COMPONENTS – atomic copy+paste in reducer (no race condition)
        case 'DUPLICATE_COMPONENTS': {
            const { sourceIds } = action.payload;
            const selected = state.config.components.filter((c) => sourceIds.includes(c.id));
            if (selected.length === 0) return state;
            const maxZ = Math.max(...state.config.components.map((c) => c.zIndex), 0);
            const duplicated = selected.map((comp, idx) => ({
                ...comp,
                id: generateId(),
                x: comp.x + 20,
                y: comp.y + 20,
                zIndex: maxZ + idx + 1,
            }));
            const newConfig = {
                ...state.config,
                components: sanitizeComponents([...state.config.components, ...duplicated]),
            };
            return {
                ...state,
                config: newConfig,
                selectedIds: duplicated.map((c) => c.id),
                ...pushHistory(state.history, state.historyIndex, newConfig),
            };
        }

        case 'SELECT_COMPONENTS':
            return { ...state, selectedIds: action.payload };

        case 'PASTE_COMPONENTS': {
            const { components, offsetX = 20, offsetY = 20 } = action.payload;
            const maxZIndex = state.config.components.length > 0
                ? Math.max(...state.config.components.map(c => c.zIndex))
                : 0;

            const newComponents = components.map((comp, idx) => ({
                ...comp,
                id: generateId(),
                x: comp.x + offsetX,
                y: comp.y + offsetY,
                zIndex: maxZIndex + idx + 1,
            }));

            const newConfig = {
                ...state.config,
                components: sanitizeComponents([...state.config.components, ...newComponents]),
            };
            return {
                ...state,
                config: newConfig,
                selectedIds: newComponents.map(c => c.id),
                ...pushHistory(state.history, state.historyIndex, newConfig),
            };
        }

        case 'MOVE_COMPONENT': {
            const movedComponents = state.config.components.map((comp) =>
                comp.id === action.payload.id
                    ? { ...comp, x: action.payload.x, y: action.payload.y }
                    : comp
            );
            const newComponents = sanitizeComponents(
                applyAutoContainerBinding(movedComponents, [action.payload.id]),
            );
            const newConfig = { ...state.config, components: newComponents };
            // Don't add to history on every move (too many entries)
            return { ...state, config: newConfig };
        }

        case 'MOVE_COMPONENTS': {
            const posMap = new Map(action.payload.map((item) => [item.id, item]));
            const movedComponents = state.config.components.map((comp) => {
                const next = posMap.get(comp.id);
                if (!next) return comp;
                return { ...comp, x: next.x, y: next.y };
            });
            const newComponents = sanitizeComponents(
                applyAutoContainerBinding(
                    movedComponents,
                    action.payload.map((item) => item.id),
                ),
            );
            const newConfig = { ...state.config, components: newComponents };
            return { ...state, config: newConfig };
        }

        case 'TRANSFORM_COMPONENTS': {
            const transformMap = new Map(action.payload.map((item) => [item.id, item]));
            const newComponents = sanitizeComponents(state.config.components.map((comp) => {
                const next = transformMap.get(comp.id);
                if (!next) return comp;
                return {
                    ...comp,
                    x: next.x,
                    y: next.y,
                    width: next.width,
                    height: next.height,
                };
            }));
            const newConfig = { ...state.config, components: newComponents };
            return { ...state, config: newConfig };
        }

        case 'RESIZE_COMPONENT': {
            const newComponents = sanitizeComponents(state.config.components.map((comp) =>
                comp.id === action.payload.id
                    ? { ...comp, width: action.payload.width, height: action.payload.height }
                    : comp
            ));
            const newConfig = { ...state.config, components: newComponents };
            return { ...state, config: newConfig };
        }

        case 'REORDER_LAYER': {
            const { id, direction } = action.payload;
            const components = [...state.config.components];
            const index = components.findIndex((c) => c.id === id);
            if (index === -1) return state;

            const maxZIndex = Math.max(...components.map((c) => c.zIndex));
            const minZIndex = Math.min(...components.map((c) => c.zIndex));

            const newComponents = components.map((comp) => {
                if (comp.id !== id) return comp;
                switch (direction) {
                    case 'up':
                        return { ...comp, zIndex: Math.min(comp.zIndex + 1, maxZIndex + 1) };
                    case 'down':
                        return { ...comp, zIndex: Math.max(comp.zIndex - 1, 0) };
                    case 'top':
                        return { ...comp, zIndex: maxZIndex + 1 };
                    case 'bottom':
                        return { ...comp, zIndex: minZIndex - 1 };
                    default:
                        return comp;
                }
            });

            const newConfig = { ...state.config, components: newComponents };
            return {
                ...state,
                config: newConfig,
                ...pushHistory(state.history, state.historyIndex, newConfig),
            };
        }

        case 'SET_ZOOM':
            return { ...state, zoom: action.payload };

        case 'TOGGLE_GRID':
            return { ...state, showGrid: !state.showGrid };

        case 'UNDO': {
            if (state.historyIndex <= 0) return state;
            const newIndex = state.historyIndex - 1;
            return {
                ...state,
                config: state.history[newIndex],
                historyIndex: newIndex,
            };
        }

        case 'REDO': {
            if (state.historyIndex >= state.history.length - 1) return state;
            const newIndex = state.historyIndex + 1;
            return {
                ...state,
                config: state.history[newIndex],
                historyIndex: newIndex,
            };
        }

        case 'SNAPSHOT': {
            if (state.history[state.historyIndex] === state.config) {
                return state;
            }
            return {
                ...state,
                ...pushHistory(state.history, state.historyIndex, state.config),
            };
        }

        default:
            return state;
    }
}

interface ScreenContextValue {
    state: ScreenState;
    dispatch: React.Dispatch<ScreenAction>;
    addComponent: (component: ScreenComponent) => void;
    updateComponent: (id: string, updates: Partial<ScreenComponent>) => void;
    deleteComponents: (ids: string[]) => void;
    selectComponents: (ids: string[]) => void;
    undo: () => void;
    redo: () => void;
    canUndo: boolean;
    canRedo: boolean;
    // Clipboard
    clipboard: ScreenComponent[];
    copyComponents: () => void;
    pasteComponents: () => void;
    // Save/Load
    loadConfig: (config: ScreenConfig) => void;
    markBaseline: (config: ScreenConfig) => void;
    updateConfig: (updates: Partial<ScreenConfig>) => void;
    updateSelectedComponents: (updates: Partial<ScreenComponent>) => void;
    groupSelected: () => void;
    ungroupSelected: () => void;
    alignSelected: (mode: 'left' | 'right' | 'top' | 'bottom' | 'h-center' | 'v-center') => void;
    distributeSelected: (mode: 'horizontal' | 'vertical') => void;
    duplicateSelected: () => void;
    snapshotTransform: () => void;
    snapGuides: { x: number[]; y: number[] };
    setSnapGuides: (guides: { x?: number[]; y?: number[] }) => void;
    clearSnapGuides: () => void;
    isSaving: boolean;
    setIsSaving: (saving: boolean) => void;
}

const ScreenContext = createContext<ScreenContextValue | null>(null);

export function ScreenProvider({ children }: { children: ReactNode }) {
    const [state, dispatch] = useReducer(screenReducer, initialState);
    const [clipboard, setClipboard] = useState<ScreenComponent[]>([]);
    const [isSaving, setIsSaving] = useState(false);
    const [snapGuides, setSnapGuidesState] = useState<{ x: number[]; y: number[] }>({ x: [], y: [] });

    const addComponent = useCallback((component: ScreenComponent) => {
        dispatch({ type: 'ADD_COMPONENT', payload: component });
    }, []);

    const updateComponent = useCallback((id: string, updates: Partial<ScreenComponent>) => {
        dispatch({ type: 'UPDATE_COMPONENT', payload: { id, updates } });
    }, []);

    const deleteComponents = useCallback((ids: string[]) => {
        dispatch({ type: 'DELETE_COMPONENTS', payload: ids });
    }, []);

    const selectComponents = useCallback((ids: string[]) => {
        dispatch({ type: 'SELECT_COMPONENTS', payload: ids });
    }, []);

    const undo = useCallback(() => {
        dispatch({ type: 'UNDO' });
    }, []);

    const redo = useCallback(() => {
        dispatch({ type: 'REDO' });
    }, []);

    const copyComponents = useCallback(() => {
        const selectedComps = state.config.components.filter(c => state.selectedIds.includes(c.id));
        if (selectedComps.length > 0) {
            setClipboard(selectedComps);
        }
    }, [state.config.components, state.selectedIds]);

    const pasteComponents = useCallback(() => {
        if (clipboard.length > 0) {
            dispatch({ type: 'PASTE_COMPONENTS', payload: { components: clipboard } });
        }
    }, [clipboard]);

    const loadConfig = useCallback((config: ScreenConfig) => {
        dispatch({ type: 'LOAD_CONFIG', payload: config });
    }, []);

    // Phase 1.3: updateConfig dispatches MERGE_CONFIG to avoid stale closure
    const updateConfig = useCallback((updates: Partial<ScreenConfig>) => {
        dispatch({ type: 'MERGE_CONFIG', payload: updates });
    }, []);

    const markBaseline = useCallback((config: ScreenConfig) => {
        dispatch({ type: 'MARK_BASELINE', payload: config });
    }, []);

    const updateSelectedComponents = useCallback((updates: Partial<ScreenComponent>) => {
        if (state.selectedIds.length === 0) return;
        const idSet = new Set(state.selectedIds);
        const newComponents = state.config.components.map((comp) =>
            idSet.has(comp.id) ? { ...comp, ...updates } : comp,
        );
        dispatch({ type: 'SET_CONFIG', payload: { ...state.config, components: newComponents } });
    }, [state.config, state.selectedIds]);

    const groupSelected = useCallback(() => {
        if (state.selectedIds.length < 2) return;
        const idSet = new Set(state.selectedIds);
        const groupId = generateId('grp');
        const newComponents = state.config.components.map((comp) =>
            idSet.has(comp.id) ? { ...comp, groupId } : comp,
        );
        dispatch({ type: 'SET_CONFIG', payload: { ...state.config, components: newComponents } });
    }, [state.config, state.selectedIds]);

    const ungroupSelected = useCallback(() => {
        if (state.selectedIds.length === 0) return;
        const idSet = new Set(state.selectedIds);
        const newComponents = state.config.components.map((comp) => {
            if (!idSet.has(comp.id) || !comp.groupId) {
                return comp;
            }
            const { groupId: _groupId, ...rest } = comp;
            return rest;
        });
        dispatch({ type: 'SET_CONFIG', payload: { ...state.config, components: newComponents } });
    }, [state.config, state.selectedIds]);

    const alignSelected = useCallback((mode: 'left' | 'right' | 'top' | 'bottom' | 'h-center' | 'v-center') => {
        const selected = state.config.components.filter((comp) => state.selectedIds.includes(comp.id));
        if (selected.length < 2) return;

        const target = {
            left: Math.min(...selected.map((comp) => comp.x)),
            right: Math.max(...selected.map((comp) => comp.x + comp.width)),
            top: Math.min(...selected.map((comp) => comp.y)),
            bottom: Math.max(...selected.map((comp) => comp.y + comp.height)),
        };
        const center = {
            x: selected.reduce((sum, comp) => sum + comp.x + comp.width / 2, 0) / selected.length,
            y: selected.reduce((sum, comp) => sum + comp.y + comp.height / 2, 0) / selected.length,
        };

        const idSet = new Set(state.selectedIds);
        const newComponents = state.config.components.map((comp) => {
            if (!idSet.has(comp.id)) return comp;
            if (mode === 'left') return { ...comp, x: target.left };
            if (mode === 'right') return { ...comp, x: target.right - comp.width };
            if (mode === 'top') return { ...comp, y: target.top };
            if (mode === 'bottom') return { ...comp, y: target.bottom - comp.height };
            if (mode === 'h-center') return { ...comp, x: Math.round(center.x - comp.width / 2) };
            return { ...comp, y: Math.round(center.y - comp.height / 2) };
        });

        dispatch({ type: 'SET_CONFIG', payload: { ...state.config, components: newComponents } });
    }, [state.config, state.selectedIds]);

    const distributeSelected = useCallback((mode: 'horizontal' | 'vertical') => {
        const selected = state.config.components.filter((comp) => state.selectedIds.includes(comp.id));
        if (selected.length < 3) return;

        const sorted = [...selected].sort((a, b) => (mode === 'horizontal' ? a.x - b.x : a.y - b.y));
        if (mode === 'horizontal') {
            const start = sorted[0].x;
            const end = sorted[sorted.length - 1].x + sorted[sorted.length - 1].width;
            const totalWidth = sorted.reduce((sum, comp) => sum + comp.width, 0);
            const gap = (end - start - totalWidth) / (sorted.length - 1);
            let cursor = start;
            const nextPos = new Map<string, number>();
            for (const comp of sorted) {
                nextPos.set(comp.id, Math.round(cursor));
                cursor += comp.width + gap;
            }
            const newComponents = state.config.components.map((comp) =>
                nextPos.has(comp.id) ? { ...comp, x: nextPos.get(comp.id) as number } : comp,
            );
            dispatch({ type: 'SET_CONFIG', payload: { ...state.config, components: newComponents } });
            return;
        }

        const start = sorted[0].y;
        const end = sorted[sorted.length - 1].y + sorted[sorted.length - 1].height;
        const totalHeight = sorted.reduce((sum, comp) => sum + comp.height, 0);
        const gap = (end - start - totalHeight) / (sorted.length - 1);
        let cursor = start;
        const nextPos = new Map<string, number>();
        for (const comp of sorted) {
            nextPos.set(comp.id, Math.round(cursor));
            cursor += comp.height + gap;
        }
        const newComponents = state.config.components.map((comp) =>
            nextPos.has(comp.id) ? { ...comp, y: nextPos.get(comp.id) as number } : comp,
        );
        dispatch({ type: 'SET_CONFIG', payload: { ...state.config, components: newComponents } });
    }, [state.config, state.selectedIds]);

    // Phase 4.3: atomic duplicate – no clipboard race condition
    const duplicateSelected = useCallback(() => {
        if (state.selectedIds.length === 0) return;
        dispatch({ type: 'DUPLICATE_COMPONENTS', payload: { sourceIds: state.selectedIds } });
    }, [state.selectedIds]);

    const snapshotTransform = useCallback(() => {
        dispatch({ type: 'SNAPSHOT' });
    }, []);

    const setSnapGuides = useCallback((guides: { x?: number[]; y?: number[] }) => {
        setSnapGuidesState({
            x: guides.x ?? [],
            y: guides.y ?? [],
        });
    }, []);

    const clearSnapGuides = useCallback(() => {
        setSnapGuidesState({ x: [], y: [] });
    }, []);

    const canUndo = state.historyIndex > 0;
    const canRedo = state.historyIndex < state.history.length - 1;

    // Phase 1.1: useMemo around Provider value
    const contextValue = useMemo<ScreenContextValue>(() => ({
        state,
        dispatch,
        addComponent,
        updateComponent,
        deleteComponents,
        selectComponents,
        undo,
        redo,
        canUndo,
        canRedo,
        clipboard,
        copyComponents,
        pasteComponents,
        loadConfig,
        markBaseline,
        updateConfig,
        updateSelectedComponents,
        groupSelected,
        ungroupSelected,
        alignSelected,
        distributeSelected,
        duplicateSelected,
        snapshotTransform,
        snapGuides,
        setSnapGuides,
        clearSnapGuides,
        isSaving,
        setIsSaving,
    }), [
        state, canUndo, canRedo, clipboard, snapGuides, isSaving,
        // useCallback refs are stable and won't trigger extra renders
        addComponent, updateComponent, deleteComponents, selectComponents,
        undo, redo, copyComponents, pasteComponents, loadConfig,
        markBaseline, updateConfig, updateSelectedComponents,
        groupSelected, ungroupSelected, alignSelected, distributeSelected,
        duplicateSelected, snapshotTransform, setSnapGuides, clearSnapGuides, setIsSaving, dispatch,
    ]);

    return (
        <ScreenContext.Provider value={contextValue}>
            {children}
        </ScreenContext.Provider>
    );
}

export function useScreen() {
    const context = useContext(ScreenContext);
    if (!context) {
        throw new Error('useScreen must be used within a ScreenProvider');
    }
    return context;
}
