type StorageLike = Pick<Storage, 'getItem'>;

export type RightPanelTab = 'style' | 'data' | 'interaction' | 'layer' | 'advanced';

const VALID_RIGHT_TABS = new Set<RightPanelTab>(['style', 'data', 'interaction', 'layer', 'advanced']);

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
    const lib = storage?.getItem('dts.analytics.screenDesigner.showLibraryPanel');
    const insp = storage?.getItem('dts.analytics.screenDesigner.showInspectorPanel');
    return {
        showLibraryPanel: lib !== null ? lib === 'true' : true,
        showInspectorPanel: insp !== null ? insp === 'true' : true,
    };
}
