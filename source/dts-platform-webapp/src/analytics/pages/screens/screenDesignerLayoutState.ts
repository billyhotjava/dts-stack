type StorageLike = Pick<Storage, 'getItem'>;

export type RightPanelTab = 'style' | 'data' | 'interaction' | 'layer' | 'advanced';
export type SidePanelKey = 'library' | 'inspector';

export interface SidePanelWidths {
    libraryWidth: number;
    inspectorWidth: number;
}

const VALID_RIGHT_TABS = new Set<RightPanelTab>(['style', 'data', 'interaction', 'layer', 'advanced']);
const SIDE_PANEL_VISIBILITY_STORAGE = {
    library: 'dts.analytics.screenDesigner.v2.showLibraryPanel',
    inspector: 'dts.analytics.screenDesigner.v2.showInspectorPanel',
} as const;
const SIDE_PANEL_WIDTH_STORAGE = {
    library: 'dts.analytics.screenDesigner.libraryPanelWidth',
    inspector: 'dts.analytics.screenDesigner.inspectorPanelWidth',
} as const;
const SIDE_PANEL_WIDTH_BOUNDS: Record<SidePanelKey, { min: number; max: number; fallback: number }> = {
    library: { min: 260, max: 420, fallback: 300 },
    inspector: { min: 300, max: 520, fallback: 360 },
};

export function resolveInitialRightPanelTab(storage?: StorageLike): RightPanelTab {
    const raw = storage?.getItem('dts.analytics.screenDesigner.rightPanelTab') as RightPanelTab | null;
    if (raw && VALID_RIGHT_TABS.has(raw)) return raw;
    return 'style';
}

export function resolveInitialFocusMode(storage?: StorageLike): boolean {
    return storage?.getItem('dts.analytics.screenDesigner.focusMode') === 'true';
}

export function resolveInitialSidePanelVisibility(storage?: StorageLike): {
    showLibraryPanel: boolean;
    showInspectorPanel: boolean;
} {
    const lib = storage?.getItem(SIDE_PANEL_VISIBILITY_STORAGE.library);
    const insp = storage?.getItem(SIDE_PANEL_VISIBILITY_STORAGE.inspector);
    return {
        showLibraryPanel: lib !== null ? lib === 'true' : true,
        showInspectorPanel: insp !== null ? insp === 'true' : true,
    };
}

export function getSidePanelVisibilityStorageKey(panel: SidePanelKey): string {
    return SIDE_PANEL_VISIBILITY_STORAGE[panel];
}

export function getSidePanelWidthStorageKey(panel: SidePanelKey): string {
    return SIDE_PANEL_WIDTH_STORAGE[panel];
}

export function clampSidePanelWidth(panel: SidePanelKey, value: number): number {
    const bounds = SIDE_PANEL_WIDTH_BOUNDS[panel];
    if (!Number.isFinite(value)) {
        return bounds.fallback;
    }
    return Math.max(bounds.min, Math.min(bounds.max, Math.round(value)));
}

export function resolveInitialSidePanelWidths(storage?: StorageLike): SidePanelWidths {
    const libraryRaw = Number(storage?.getItem(SIDE_PANEL_WIDTH_STORAGE.library));
    const inspectorRaw = Number(storage?.getItem(SIDE_PANEL_WIDTH_STORAGE.inspector));
    return {
        libraryWidth: clampSidePanelWidth('library', libraryRaw),
        inspectorWidth: clampSidePanelWidth('inspector', inspectorRaw),
    };
}
